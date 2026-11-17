package com.bandwa.openadb.presentation.main.music

data class MusicSettingsUIState(
    val downloadProgress: Float = -1f,
    val downloadProgressText: String = "",
    /** 导航播报时旋钮调导航音量 的当前值。 */
    val volumeNav: Boolean = false,
    /**
     * 能不能用：注入时那一条补丁真的进了 misc 的 dex 才为 true。
     * 为 false 时开关灰色 —— 老版本注入层里没有 Hook.mapNavPkg()。
     */
    val volumeNavAvailable: Boolean = false,
)
