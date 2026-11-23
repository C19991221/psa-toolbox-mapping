package com.bandwa.openadb.util

import android.content.Context

/**
 * 清理类开关的**一次性默认值迁移**。
 *
 * 背景（2026-11-23 用户要求）：维护诊断里的内存/存储自动清理原来默认是开的，
 * 车机上电就会自己清东西。现在统一改成**默认全关**，只有用户自己打开才清。
 *
 * 已经装过旧版本的车，偏好里已经写着 `true`，光改默认值不会生效 ⇒ 用这里的版本号做一次性迁移：
 * 首次安装（本来就没有这些键）与下次在线更新（版本号从 1 跳到 2）都会跑一遍，把开关全部置 false。
 *
 * 以后如果还要再改默认值，把 [VERSION] +1 即可。
 */
object CleanDefaults {

    private const val PREF = "psa_clean_defaults"
    private const val K_VERSION = "version"
    private const val VERSION = 2

    /** 幂等：已经迁移过就直接返回。 */
    fun applyIfNeeded(ctx: Context) {
        val sp = runCatching { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE) }.getOrNull() ?: return
        if (sp.getInt(K_VERSION, 1) >= VERSION) return

        runCatching {
            MemGuard.setEnabled(ctx, false)
            MemGuard.setLevel2(ctx, false)
            StorageClean.setEnabled(ctx, false)
            StorageClean.setJavaDeep(ctx, false)
            StorageClean.setKwPlayCache(ctx, false)
            StorageClean.setCrashDiag(ctx, false)
            StorageClean.setOtherAppCache(ctx, false)
            ctx.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("shouldAutoClearSD", false).apply()
        }

        sp.edit().putInt(K_VERSION, VERSION).apply()
    }
}
