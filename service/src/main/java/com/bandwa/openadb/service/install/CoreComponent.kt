package com.bandwa.openadb.service.install

import android.content.Context
import android.util.Log
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 核心组件（`com.cusc.misc`）注入的**唯一入口**。
 *
 * 用户 2026-11-23 要求「手动点击和自动检查的注入内容要确定一样」。两条路原本各自
 * 直接调 [ModApk.packageMod]，虽然载荷都来自 `assets/modapk/com.cusc.misc/`，
 * 但那属于「碰巧一样」；现在统一走这里，并在开始前打同一行指纹日志
 * （Hook 版本 + 载荷配置版本），出问题时可以拿日志逐字比对。
 *
 * 两者的**唯一**语义差别是 [force]：
 * - 自动安装（首次打开、版本不匹配）：`force = false` —— 已经是补丁版就跳过，不白折腾。
 * - 手动「修复PSA工具箱」：`force = true` —— 用户明确要求重打，即使看着正常也重来。
 *
 * 载荷本身（config.json + psa_*.smali + hook.dex）完全相同，`force` 只影响
 * 「要不要跳过」，不影响打进 dex 的内容。
 *
 * ## 装完自检 + 自动回滚（2026-11-23，审计 H2）
 *
 * 「装不上」会自动回落到 `/system` 原厂件，这条本来就可靠；真正没兜底的是
 * **「装上了、宿主一启动就崩」** —— 包已经替换成功，代码却每次都在
 * `MiscApplication.onCreate` 里炸，界面上没有任何路可以撤掉它。
 *
 * 所以装完必须验证：安装过程本身会杀掉宿主的旧进程，这里主动把它拉起来，
 * 然后等 Hook 通过桥接回报（[HookState.reportCount] 变大）。回报没来就说明
 * 新代码起不来 —— 立刻调用 [ModApk.restoreStock] 把更新卸掉，系统自动回落
 * 到 `/system` 原厂件。宁可白装一次，也不留一个开不了机的包在车上。
 */
object CoreComponent {

    const val TAG = "CoreComponent"
    const val CORE_PACKAGE = "com.cusc.misc"

    /**
     * 修复时怎么处理车机桌面（换壁纸、卡片透明度那套）。
     *
     * 用户 2026-11-24（m06708）要求：「修复时提示桌面的修复是注入还是不注入，
     * 不注入就只修复公共服务」。所以「要不要动桌面」必须由用户在界面上决定，
     * 而这个决定只在**桌面还没被改过**的时候才值得问 —— 已经是我们改过的
     * 就直接跟着更新，已经是别人改过的（如 GenPanda）就一律别碰。
     */
    enum class LauncherMode {
        /** 按检测结果自动决定：我们的就更新、别人的跳过、原厂的不动。 */
        AUTO,

        /** 用户明确要求一起修复桌面（桌面是原厂时才有意义）。 */
        INJECT,

        /** 用户选择「只修复公共服务」。 */
        SKIP,
    }

    /** 装完等宿主回报的上限。正常 2~5 秒就回来了，30 秒足够覆盖慢启动。 */
    private const val VERIFY_TIMEOUT_MS = 30_000L

    /** 桌面那份包约 29 MB，打包 + 安装比核心组件慢，给足时间。 */
    private const val LAUNCHER_TIMEOUT_MS = 180_000L

    private const val POLL_MS = 500L

    /** 注入结果。`rolledBack` = 已自动退回 `/system` 原厂件。 */
    data class Result(
        val success: Boolean,
        val rolledBack: Boolean = false,
        val message: String = "",
        /** 桌面那一步的说明；空串表示这次没动桌面。 */
        val launcherNote: String = "",
    )

    /** 载荷指纹：写进日志，用于比对「自动」与「手动」两次注入是否同一套内容。 */
    fun payloadFingerprint(context: Context): String {
        val configVersion = runCatching {
            val text = context.assets.open("modapk/$CORE_PACKAGE/config.json")
                .use { it.reader().readText() }
            Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "?"
        }.getOrDefault("?")
        return "hookVersion=${Hook.VERSION} configVersion=$configVersion"
    }

    /** 当前核心组件是不是注入版（在 `/data` 里），而不是 `/system` 原厂件。 */
    fun isPatched(context: Context): Boolean = ModApk.isPatchedInstall(context, CORE_PACKAGE)

    fun install(
        context: Context,
        trigger: String,
        force: Boolean,
        launcherMode: LauncherMode = LauncherMode.AUTO,
        onComplete: (Result) -> Unit,
    ) {
        Log.i(
            TAG,
            "install core: trigger=$trigger force=$force launcher=${launcherMode.name} " +
                payloadFingerprint(context),
        )
        val reportsBefore = HookState.reportCount(CORE_PACKAGE)
        ModApk.packageModResult(context, CORE_PACKAGE, force = force) { outcome ->
            Log.i(
                TAG,
                "install core finished: trigger=$trigger force=$force outcome=$outcome",
            )
            when (outcome) {
                // 跳过 = 什么都没装、宿主也没被杀，绝不能拿它做「装完自检」，
                // 否则会把一个本来正常的组件误判成起不来、白回滚一次。
                // 但桌面那一步还是要走：核心组件早就好了、用户可能就是来修桌面的。
                ModApk.Outcome.Skipped -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        LauncherSupport.invalidate()
                        val note = applyLauncher(context, launcherMode)
                        withContext(Dispatchers.Main) {
                            onComplete(Result(true, message = "已是最新，无需重装", launcherNote = note))
                        }
                    }
                }
                ModApk.Outcome.Failed -> onComplete(Result(false, message = "打包或安装失败"))
                ModApk.Outcome.Installed -> verify(context, trigger, reportsBefore, launcherMode, onComplete)
            }
        }
    }

    /**
     * 核心组件确认没问题之后，按用户的意愿处理桌面。
     *
     * 规则（用户 m06708 定）：
     * - [LauncherMode.SKIP]：一概不动。
     * - 桌面已经是别人改过的（GenPanda 那种）：**也不动**，哪怕用户点了「一起修复」——
     *   覆盖别人的改版等于把用户自己挑好的桌面刷掉，这个代价比「少一个桌面功能」大。
     * - 桌面是我们自己的：跟着更新到当前版本。用户点没点「一起修复」都更新，因为那是
     *   我们自己装上去的东西，不跟着更新反而会出现「桌面功能忽然失效」。
     * - 桌面是原厂：只有用户明确 [LauncherMode.INJECT] 时才注入。
     *
     * @return 一句话说明桌面做了什么；空串表示没动（界面就不提这件事）。
     */
    private suspend fun applyLauncher(
        context: Context,
        mode: LauncherMode,
    ): String {
        if (mode == LauncherMode.SKIP) {
            Log.i(TAG, "launcher: skipped by user choice")
            return ""
        }
        val info = LauncherSupport.detect(context, force = true)
        return when (info.state) {
            LauncherSupport.State.FOREIGN_PATCHED -> {
                Log.i(TAG, "launcher: foreign patched build, leave it alone")
                "桌面用的是你自己的改版，没有动它"
            }
            LauncherSupport.State.OURS_CURRENT -> {
                Log.i(TAG, "launcher: ours and current, nothing to do")
                ""
            }
            LauncherSupport.State.OURS_STALE -> deployLauncher(context, force = true)
            LauncherSupport.State.STOCK -> if (mode == LauncherMode.INJECT) {
                deployLauncher(context, force = true)
            } else {
                Log.i(TAG, "launcher: stock and user chose not to inject")
                ""
            }
        }
    }

    /** 真去打补丁 + 装桌面；回来一句话说明结果。 */
    private suspend fun deployLauncher(context: Context, force: Boolean): String {
        val done = withTimeoutSuspend(LAUNCHER_TIMEOUT_MS) { cb ->
            ModApk.packageMod(
                context,
                LauncherSupport.LAUNCHER_PACKAGE,
                force = force,
                onComplete = cb,
            )
        }
        LauncherSupport.invalidate()
        return if (done) {
            // 桌面是常驻进程，不重启就一直跑旧的。
            runCatching {
                ShellUtils.execArgs(listOf("am", "force-stop", LauncherSupport.LAUNCHER_PACKAGE))
            }
            Log.i(TAG, "launcher: deployed")
            "桌面自定义功能已一起修复"
        } else {
            Log.e(TAG, "launcher: deploy failed")
            "核心组件已修好，但桌面没有修成功，壁纸和卡片透明度暂时用不了"
        }
    }


    /**
     * 退回 `/system` 原厂件（界面上的「恢复原厂核心组件」）。
     *
     * 与 [install] 的自动回滚走同一条路，只是这里由用户主动触发。
     */
    fun restoreStock(context: Context, onComplete: (Boolean) -> Unit) {
        Log.i(TAG, "restore stock requested by user")
        ModApk.restoreStock(context, CORE_PACKAGE, onComplete)
    }

    /** 拉起宿主进程并等它回报；没回报就自动回滚，然后把结果交给调用方。 */
    private fun verify(
        context: Context,
        trigger: String,
        reportsBefore: Long,
        launcherMode: LauncherMode,
        onComplete: (Result) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            when (val check = awaitHookReport(context, reportsBefore)) {
                Check.Passed -> {
                    Log.i(TAG, "post-install check passed: trigger=$trigger")
                    LauncherSupport.invalidate()
                    val note = applyLauncher(context, launcherMode)
                    withContext(Dispatchers.Main) {
                        onComplete(Result(true, launcherNote = note))
                    }
                }
                Check.ProcessAlive -> {
                    // 宿主进程活着但桥接没连上：多半是工具箱这边的问题（桥接服务没起来），
                    // 不是包坏了。这种情况**绝不能回滚** —— 回滚一个能跑的包比不检查更糟。
                    Log.w(TAG, "post-install check inconclusive: host alive without hook report")
                    LauncherSupport.invalidate()
                    val note = applyLauncher(context, launcherMode)
                    withContext(Dispatchers.Main) {
                        onComplete(
                            Result(
                                success = true,
                                message = "组件已启动，但未收到回报，请稍后确认功能是否正常",
                                launcherNote = note,
                            )
                        )
                    }
                }
                Check.Dead -> {
                    Log.e(TAG, "post-install check failed: host did not survive, rolling back")
                    val restored = runCatching {
                        withTimeoutSuspend(VERIFY_TIMEOUT_MS) { done ->
                            ModApk.restoreStock(context, CORE_PACKAGE) { done(it) }
                        }
                    }.getOrDefault(false)
                    Log.e(TAG, "auto rollback finished: restored=$restored")
                    withContext(Dispatchers.Main) {
                        onComplete(
                            Result(
                                success = false,
                                rolledBack = restored,
                                message = if (restored) {
                                    "新组件装上后没有成功启动，已自动退回原厂组件"
                                } else {
                                    "新组件装上后没有成功启动，自动回滚也没成功"
                                },
                            )
                        )
                    }
                }
            }
        }
    }

    private enum class Check {
        /** 桥接收到宿主回报，新代码确实跑起来了。 */
        Passed,

        /** 没回报，但宿主进程活着 —— 结论不确定，不动它。 */
        ProcessAlive,

        /** 没回报且宿主进程根本没活下来 —— 可以判定这个包装上就崩。 */
        Dead,
    }

    /**
     * 拉起宿主主进程，等它通过桥接回报一次，并同时看进程活没活。
     *
     * 安装过程会杀掉宿主旧进程，所以这里 `start-service` 起来的一定是新代码；
     * 新代码在 `MiscApplication.onCreate` 里调 `Hook.install`，桥接连上后
     * `registerCallbackEx` 会让 [HookState.reportCount] +1。
     *
     * 「装上了就崩」的判据必须是**两个信号同时否**：没回报 **且** 进程不在。
     * 只看回报会误伤 —— 桥接没连上时进程可能是好的。
     */
    private suspend fun awaitHookReport(context: Context, reportsBefore: Long): Check {
        // MiscService 是 misc 自己 manifest 里 exported=true 的服务，拉它即进主进程。
        runCatching {
            ShellUtils.execArgs(
                listOf("am", "start-service", "-n", "$CORE_PACKAGE/com.cusc.misc.MiscService")
            )
        }
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (HookState.reportCount(CORE_PACKAGE) > reportsBefore) return Check.Passed
            delay(POLL_MS)
        }
        return if (isHostProcessAlive()) Check.ProcessAlive else Check.Dead
    }

    /** `pidof` 有输出 = 宿主进程在。 */
    private fun isHostProcessAlive(): Boolean = runCatching {
        val r = ShellUtils.execArgs(listOf("pidof", CORE_PACKAGE))
        r.isSuccess && r.output.isNotBlank()
    }.getOrDefault(false)

    /** [ModApk.restoreStock] 是回调式，这里换成可等、带超时的形式。 */
    private suspend fun withTimeoutSuspend(
        timeoutMs: Long,
        block: ((Boolean) -> Unit) -> Unit,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            block { ok -> if (cont.isActive) cont.resume(ok) }
        }
    } ?: false
}
