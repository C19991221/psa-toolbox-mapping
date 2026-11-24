package com.bandwa.openadb.util

import android.content.Context

/**
 * 兼容模式的**一次性默认值迁移**。
 *
 * 背景（2026-11-24 用户 m06708 要求）：「咱们这个开发版本以后兼容模式默认打开」。
 *
 * 兼容模式开着时，工具箱不会自动去查核心组件与桌面、也不会自动弹安装/部署框，
 * 什么都要用户自己点 —— 开发阶段这样最不容易误伤车机。
 *
 * 光把读点的默认值从 false 改成 true 不够：已经装过旧版本的车，偏好里明确写着
 * `compat_mode=false`，改默认值对它无效 ⇒ 照样用版本号做一次性迁移。
 *
 * 注意：这里只迁移**一次**。用户在专家模式里手动关掉之后，下次启动仍是关的，
 * 不会每次开机又被强行打开。
 */
object CompatDefaults {

    private const val PREF = "psa_compat_defaults"
    private const val K_VERSION = "version"
    private const val VERSION = 1

    /** 幂等：升级后跑一次，把兼容模式置为开。 */
    fun applyIfNeeded(ctx: Context) {
        val sp = runCatching { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE) }.getOrNull() ?: return
        if (sp.getInt(K_VERSION, 0) >= VERSION) return

        runCatching {
            ctx.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(Utils.COMPAT_MODE_KEY, true).apply()
        }

        sp.edit().putInt(K_VERSION, VERSION).apply()
    }
}
