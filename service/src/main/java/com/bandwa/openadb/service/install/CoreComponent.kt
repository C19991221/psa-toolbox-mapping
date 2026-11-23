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

    /** 装完等宿主回报的上限。正常 2~5 秒就回来了，30 秒足够覆盖慢启动。 */
    private const val VERIFY_TIMEOUT_MS = 30_000L

    private const val POLL_MS = 500L

    /** 注入结果。`rolledBack` = 已自动退回 `/system` 原厂件。 */
    data class Result(
        val success: Boolean,
        val rolledBack: Boolean = false,
        val message: String = "",
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
        onComplete: (Result) -> Unit,
    ) {
        Log.i(
            TAG,
            "install core: trigger=$trigger force=$force ${payloadFingerprint(context)}",
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
                ModApk.Outcome.Skipped -> onComplete(Result(true, message = "已是最新，无需重装"))
                ModApk.Outcome.Failed -> onComplete(Result(false, message = "打包或安装失败"))
                ModApk.Outcome.Installed -> verify(context, trigger, reportsBefore, onComplete)
            }
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
        onComplete: (Result) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            when (val check = awaitHookReport(context, reportsBefore)) {
                Check.Passed -> {
                    Log.i(TAG, "post-install check passed: trigger=$trigger")
                    withContext(Dispatchers.Main) { onComplete(Result(true)) }
                }
                Check.ProcessAlive -> {
                    // 宿主进程活着但桥接没连上：多半是工具箱这边的问题（桥接服务没起来），
                    // 不是包坏了。这种情况**绝不能回滚** —— 回滚一个能跑的包比不检查更糟。
                    Log.w(TAG, "post-install check inconclusive: host alive without hook report")
                    withContext(Dispatchers.Main) {
                        onComplete(Result(true, message = "组件已启动，但未收到回报，请稍后确认功能是否正常"))
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
