package com.bandwa.openadb.service.install

import android.content.Context
import android.util.Log
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.apktools.ApkInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「桌面部分到底能不能用」的判定 —— 换壁纸、调卡片透明度都走它。
 *
 * 以前只有一条判据：dex 里有没有 `Lcom/bandwa/hook/Hook;`（我们自己注入的那套）。
 * 用户 2026-11-24（m06708）指出他日常用的是另一个来源的桌面包
 * （`408桌面（6.0车机）_GenPanda1.0.19`），那份是**源码级重写**，dex 里
 * 一个 Hook 类都没有，却同样具备这两项能力（实测：`classes.dex` 里有
 * `/storage/emulated/0/launcherPic/wallpaper.png` 与 `persist.bandwa.card.alpha`
 * 两个串，正好就是壁纸路径与卡片透明度属性名）。
 *
 * 所以判据扩成三步，顺序从「最确定」到「最宽松」：
 * 1. 有 Hook 类 ⇒ 我们注入的。再用 [HookState] 回报的版本区分新旧。
 * 2. 没有 Hook 类，但两个能力标志串都在 ⇒ 别人改过的（如 GenPanda），能用，
 *    但**绝不能碰** —— 覆盖它等于把用户自己精心挑的桌面刷掉。
 * 3. 都没有 ⇒ 原厂桌面，桌面自定义用不了。
 *
 * 扫 dex 是几十 MB 的 IO，结果在进程内缓存；装完/退回原厂之后调 [invalidate]。
 */
object LauncherSupport {

    private const val TAG = "LauncherSupport"

    const val LAUNCHER_PACKAGE = "com.android.launcher"

    private const val MARKER_HOOK = "Lcom/bandwa/hook/Hook;"

    /** 壁纸落盘路径，`Hook.applyWallpaper` 与源码级改法都用它。 */
    private const val MARKER_WALLPAPER = "/storage/emulated/0/launcherPic/wallpaper.png"

    /** 卡片透明度属性名，`Hook.applyCardAlpha` 与源码级改法都用它。 */
    private const val MARKER_CARD_ALPHA = "persist.bandwa.card.alpha"

    enum class State {
        /** 原厂桌面，没有桌面自定义能力。 */
        STOCK,

        /** 我们注入的，且版本与当前 Hook 一致。 */
        OURS_CURRENT,

        /** 我们注入的，但版本比当前 Hook 旧（该更新了）。 */
        OURS_STALE,

        /** 别人改过的（如 GenPanda），能力齐全但不归我们管，不要覆盖。 */
        FOREIGN_PATCHED,
    }

    data class Info(
        val state: State,
        val apkPath: String?,
    ) {
        /** 能不能换壁纸 / 调卡片透明度。 */
        val canCustomize: Boolean get() = state != State.STOCK

        /** 修复时要不要弹「桌面注入 / 不注入」的选择 —— 只有真没有的时候才问。 */
        val needsInjectPrompt: Boolean get() = state == State.STOCK

        /** 是我们自己的注入版：修复时可以放心跟着一起更新。 */
        val isOurs: Boolean get() = state == State.OURS_CURRENT || state == State.OURS_STALE

        /** 用户自己的改版桌面：任何自动化都不要动它。 */
        val isForeign: Boolean get() = state == State.FOREIGN_PATCHED
    }

    @Volatile
    private var cached: Info? = null

    /** 安装 / 卸载 / 退回原厂之后调用，下次 [detect] 重新扫。 */
    fun invalidate() {
        cached = null
    }

    suspend fun detect(context: Context, force: Boolean = false): Info {
        if (!force) cached?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = detectNow(context)
            cached = info
            info
        }
    }

    private fun detectNow(context: Context): Info {
        val apkPath = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(LAUNCHER_PACKAGE, 0).applicationInfo?.sourceDir
        }.getOrNull()

        if (apkPath.isNullOrBlank()) {
            Log.w(TAG, "launcher not installed")
            return Info(State.STOCK, null)
        }

        val hasHook = runCatching { ApkInspector.containsDexString(apkPath, MARKER_HOOK) }
            .getOrDefault(false)

        // 两个串一次扫完（launcher 两个 dex 加起来约 15 MB，逐条查会扫好多遍）。
        val missing = runCatching {
            ApkInspector.missingDexStrings(apkPath, listOf(MARKER_WALLPAPER, MARKER_CARD_ALPHA))
        }.getOrDefault(listOf(MARKER_WALLPAPER, MARKER_CARD_ALPHA))
        val hasAbility = missing.isEmpty()

        val reported = HookState.versionOf(LAUNCHER_PACKAGE)

        val state = when {
            hasHook -> if (reported != null && reported != Hook.LAUNCHER_VERSION) {
                State.OURS_STALE
            } else {
                State.OURS_CURRENT
            }
            // 有 Hook 类就是我们的包，哪怕回报还没来（宿主刚重启、桥接还没连上）。
            hasAbility -> State.FOREIGN_PATCHED
            else -> State.STOCK
        }

        Log.i(
            TAG,
            "launcher state=$state hasHook=$hasHook hasAbility=$hasAbility " +
                "reported=$reported path=$apkPath",
        )
        return Info(state, apkPath)
    }
}
