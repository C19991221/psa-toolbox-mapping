package com.bandwa.openadb.service.install

import android.content.Context
import android.util.Log
import com.bandwa.hook.Hook

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
 */
object CoreComponent {

    const val TAG = "CoreComponent"
    const val CORE_PACKAGE = "com.cusc.misc"

    /** 载荷指纹：写进日志，用于比对「自动」与「手动」两次注入是否同一套内容。 */
    fun payloadFingerprint(context: Context): String {
        val configVersion = runCatching {
            val text = context.assets.open("modapk/$CORE_PACKAGE/config.json")
                .use { it.reader().readText() }
            Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "?"
        }.getOrDefault("?")
        return "hookVersion=${Hook.VERSION} configVersion=$configVersion"
    }

    fun install(
        context: Context,
        trigger: String,
        force: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        Log.i(
            TAG,
            "install core: trigger=$trigger force=$force ${payloadFingerprint(context)}",
        )
        ModApk.packageMod(context, CORE_PACKAGE, force = force) { success ->
            Log.i(TAG, "install core finished: trigger=$trigger force=$force success=$success")
            onComplete(success)
        }
    }
}
