package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import com.bandwa.openadb.util.apktools.ApkInspector

/**
 * 按键映射（原按键映射软件）并进来之后需要的那几个「车机侧小开关」与身份信息。
 *
 * 这里只放**跨页面共用**的东西：维护诊断用它读运行状态，关于软件用它改开机行为。
 * 与 `com.psa.keyconfig` 原实现保持同一套语义，键名/属性名都没改，便于两边互认。
 */
object PsaExt {

    /** 开机自动开 WiFi。公共服务的 `psaBoot` 读它：空 / "1" ⇒ 开，"0" ⇒ 不动。 */
    const val PROP_BOOT_WIFI = "persist.psa.boot.wifi"

    /** 车机自带的工程模式界面（`com.cusc.misc` 里的 Activity）。 */
    const val MISC_DEBUG_ACTIVITY = "com.cusc.misc.view.activity.DebugActivity"

    /**
     * 仪表投屏不被高德空报文清除。必须与 `hook/Hook.java` 里的同名常量保持一致。
     *
     * 默认关：关着的时候 misc 走的是原厂那条路，行为与原厂逐字节等价。
     */
    const val PROP_NAVI_KEEP = "persist.psa.navi.keep"

    /**
     * 导航播报时中控旋钮调导航音量（仅 DiPlay 客户端）。同样必须与 Hook.java 一致。
     */
    const val PROP_VOLUME_NAV = "persist.psa.volume.nav"

    /**
     * 两个补丁在 dex 里的方法名，用来判断「注入时这一条到底打没打上」。
     *
     * 只查方法名而不是注释：smali 注释不会进 dex 字符串表。这两个名字在
     * 我们注入之外的地方不存在，所以命中就等于补丁在。
     */
    private const val MARK_NAVI_KEEP = "isNaviKeep"
    private const val MARK_VOLUME_NAV = "mapNavPkg"

    const val PKG_MISC = "com.cusc.misc"
    const val PKG_MEDIA = "com.cusc.media"
    const val PKG_LAUNCHER = "com.android.launcher"

    private const val PREF = "psa_toolbox_ext"
    private const val K_ENGINEER = "engineer_entry"
    private const val K_FULLSCREEN = "fullscreen"
    private const val K_ENGINEER_TAPS = "engineer_taps"

    /** 解锁工程模式入口需要连点的次数。 */
    const val ENGINEER_TAPS = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /* ---------------- 开机 WiFi ---------------- */

    /** 默认开：属性非 "0" 就算开。 */
    fun bootWifiOn(): Boolean = !"0".equals(Utils.getSystemProp(PROP_BOOT_WIFI).ifEmpty { "1" })

    /** 写属性并回读校验；返回 false = 这台车机不让写。 */
    fun setBootWifi(on: Boolean): Boolean {
        val v = if (on) "1" else "0"
        Utils.setSystemProp(PROP_BOOT_WIFI, v)
        return Utils.getSystemProp(PROP_BOOT_WIFI) == v
    }

    /* ---------------- 界面全屏 ---------------- */

    /**
     * 本应用自己的全屏偏好（默认关）。
     *
     * 默认关的原因：车机左侧有一条约 128px 的系统停靠栏，全屏会把它遮掉。
     */
    fun fullscreenWanted(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_FULLSCREEN, false) }.getOrDefault(false)

    fun setFullscreen(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_FULLSCREEN, on).apply()
    }

    /* ---------------- 工程模式入口 ---------------- */

    fun engineerUnlocked(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_ENGINEER, false) }.getOrDefault(false)

    /**
     * 「关于软件」里连点版本号：每次加一，到 [ENGINEER_TAPS] 次解锁。
     *
     * @return 本次点击后是否**刚好**解锁（界面据此给一次提示）。
     */
    fun tapVersion(ctx: Context): Boolean {
        val p = prefs(ctx)
        val n = p.getInt(K_ENGINEER_TAPS, 0) + 1
        if (n >= ENGINEER_TAPS) {
            p.edit().putBoolean(K_ENGINEER, true).putInt(K_ENGINEER_TAPS, 0).apply()
            return true
        }
        p.edit().putInt(K_ENGINEER_TAPS, n).apply()
        return false
    }

    fun lockEngineer(ctx: Context) {
        prefs(ctx).edit().putBoolean(K_ENGINEER, false).putInt(K_ENGINEER_TAPS, 0).apply()
    }

    /** 还剩几次能解锁（0 = 已解锁或已到点）。 */
    fun engineerTapsLeft(ctx: Context): Int =
        if (engineerUnlocked(ctx)) 0
        else (ENGINEER_TAPS - prefs(ctx).getInt(K_ENGINEER_TAPS, 0)).coerceAtLeast(0)

    /** 打开车机自带工程模式；失败返回 false（有些车型没有这个 Activity）。 */
    fun openEngineer(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent().apply {
                setClassName(PKG_MISC, MISC_DEBUG_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)

    /* ---------------- 公共服务注入层里的两个开关 ---------------- */

    /**
     * 开关现在的值。属性没写过 ⇒ false（与原厂一致）。
     */
    fun naviKeepOn(): Boolean = "1" == Utils.getSystemProp(PROP_NAVI_KEEP)

    fun volumeNavOn(): Boolean = "1" == Utils.getSystemProp(PROP_VOLUME_NAV)

    /** 写属性并回读校验；返回 false = 这台车机不让写。 */
    fun setFlag(prop: String, on: Boolean): Boolean {
        val v = if (on) "1" else "0"
        Utils.setSystemProp(prop, v)
        return Utils.getSystemProp(prop) == v
    }

    /* ---------------- 倒挡 / 360 自动最亮 ---------------- */

    /**
     * 倒挡或 360 全景显示时把屏幕调到最亮，退出后恢复原亮度。默认关。
     *
     * 存的是本应用自己的 SharedPreferences（不是 system 属性）：只有公共服务的
     * 常驻进程与界面两处读它，没必要占一个 persist 属性。
     */
    private const val K_AUTO_BRIGHT_AVM = "auto_bright_avm"

    fun autoBrightAvmOn(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_AUTO_BRIGHT_AVM, false) }.getOrDefault(false)

    fun setAutoBrightAvm(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_AUTO_BRIGHT_AVM, on).apply()
    }

    /* ---------------- 启动页面 ---------------- */

    /**
     * 启动时直接进入哪个页面。默认关（= 每次从「系统设置」开始，和原版一致）。
     *
     * `start_page_id`：`0` 表示「上次的页面」，其余是左侧菜单的 tab id
     * （见 `MainActivity` 的 `TabItem`，例如 1 系统设置 / 10 按键映射 / 9 关于软件）。
     */
    private const val K_START_PAGE_ON = "start_page_on"
    private const val K_START_PAGE_ID = "start_page_id"
    private const val K_LAST_TAB = "last_tab"

    fun startPageOn(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_START_PAGE_ON, false) }.getOrDefault(false)

    fun setStartPageOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_START_PAGE_ON, on).apply()
    }

    fun startPageId(ctx: Context): Int =
        runCatching { prefs(ctx).getInt(K_START_PAGE_ID, 0) }.getOrDefault(0)

    fun setStartPageId(ctx: Context, id: Int) {
        prefs(ctx).edit().putInt(K_START_PAGE_ID, id).apply()
    }

    fun lastTab(ctx: Context): Int =
        runCatching { prefs(ctx).getInt(K_LAST_TAB, 1) }.getOrDefault(1)

    fun setLastTab(ctx: Context, id: Int) {
        prefs(ctx).edit().putInt(K_LAST_TAB, id).apply()
    }

    /**
     * 这两个开关能不能用：**注入时对应的那一条真的打进 dex 了**才亮。
     *
     * 为什么不用 `HookState.versionOf(PKG_MISC) == Hook.VERSION`：那个值是宿主启动后
     * 回调上来的内存态，注入完到宿主重启之间一直是 null，界面会误灰；而且它只说明
     * 「注入层在场」，不说明「这一条补丁在场」—— 老版本注入层里根本没有这两个方法。
     * 直接扫已装 misc 的 dex 更准确，也立刻能出结果。
     */
    fun patchApplied(ctx: Context, marker: String): Boolean = runCatching {
        ApkInspector.containsDexString(installedApkPath(ctx, PKG_MISC), marker)
    }.getOrDefault(false)

    fun naviKeepAvailable(ctx: Context): Boolean = patchApplied(ctx, MARK_NAVI_KEEP)

    fun volumeNavAvailable(ctx: Context): Boolean = patchApplied(ctx, MARK_VOLUME_NAV)

    private fun installedApkPath(ctx: Context, pkg: String): String? = runCatching {
        ctx.packageManager.getPackageInfo(pkg, 0).applicationInfo.sourceDir
    }.getOrNull()

    /* ---------------- 身份信息 ---------------- */

    /** 一行的身份摘要：车型 / 版本 / 三个核心件的版本。 */
    fun identityLine(ctx: Context): String = buildString {
        append(model())
        append(" · ")
        append(androidVersion())
    }

    fun model(): String {
        val m = Utils.getSystemProp("ro.product.model").ifEmpty { Build.MODEL }
        return if (TextUtils.isEmpty(m)) "未知车型" else m
    }

    fun androidVersion(): String {
        val flavor = Utils.getSystemProp("ro.build.version.flavor")
        val rel = Utils.getSystemProp("ro.build.version.release").ifEmpty { Build.VERSION.RELEASE }
        val sdk = Utils.getSystemProp("ro.build.version.sdk").ifEmpty { Build.VERSION.SDK_INT.toString() }
        return if (flavor.isEmpty()) "Android $rel（API $sdk）" else "Android $flavor（API $sdk）"
    }

    /** 显示串（车机上通常是 `3VS222_SW6.0.0_…` 这种）。 */
    fun buildDisplay(): String {
        val d = Utils.getSystemProp("ro.build.display.id")
        return if (d.isEmpty()) Utils.getSystemProp("ro.build.id").ifEmpty { "-" } else d
    }

    /** 车架号 / 序列号（脱敏前的原文，只在本机显示）。 */
    fun serial(): String {
        for (k in arrayOf("persist.sys.serialno", "ro.serialno", "persist.sys.vincode")) {
            val v = Utils.getSystemProp(k)
            if (v.isNotEmpty()) return v
        }
        return "-"
    }

    /** `包名 versionName (versionCode)`；没装返回「未安装」。 */
    fun ver(ctx: Context, pkg: String): String = runCatching {
        val pi = ctx.packageManager.getPackageInfo(pkg, 0)
        "${pi.versionName} (${pi.versionCode})"
    }.getOrDefault("未安装")

    /** 身份卡正文（多行）。 */
    /**
     * 关于页要显示的信息：只列 PSA工具箱 会去改的那几个组件。
     * 车型 / 系统版本 / 编译号 / 序列号 属于车机自身，用户 2026-11-16 明确要求删掉。
     */
    fun identityText(ctx: Context): String = buildString {
        append("公共服务：").append(ver(ctx, PKG_MISC)).append('\n')
        append("多媒体：").append(ver(ctx, PKG_MEDIA)).append('\n')
        append("桌面：").append(ver(ctx, PKG_LAUNCHER))
    }
}
