package com.bandwa.openadb.presentation.main.navi

data class NaviSettingsUIState(
    val isAmapSafe: Boolean? = null,
    /** 仪表投屏保活开关的当前值。 */
    val naviKeep: Boolean = false,
    /**
     * 这个开关能不能用：注入时那一条补丁真的打进 misc 的 dex 了才为 true。
     * 为 false 时开关灰色 —— 老版本注入层里没有 Hook.isNaviKeep()，点了就是空操作。
     */
    val naviKeepAvailable: Boolean = false,
    /** 导航播报时旋钮调导航音量 的当前值（2026-11-23 从「音乐助手」页搬过来）。 */
    val volumeNav: Boolean = false,
    /**
     * 能不能用：注入时那一条补丁真的进了 misc 的 dex 才为 true。
     * 为 false 时开关灰色 —— 老版本注入层里没有 Hook.mapNavPkg()。
     */
    val volumeNavAvailable: Boolean = false,
)
