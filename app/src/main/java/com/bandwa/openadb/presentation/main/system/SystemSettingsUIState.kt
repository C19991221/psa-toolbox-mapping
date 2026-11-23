package com.bandwa.openadb.presentation.main.system

data class SystemSettingsUIState(
    val isAdbSwitchChecked: Boolean = false,
    val isProxyAirConditionChecked: Boolean = false,
    val isFullscreenGestureChecked: Boolean = false,
    val isAutoBrightAvmChecked: Boolean = false,
    val isAdbEnable: Boolean = false,
    val isFotaEnabled: Boolean = true,
    val isFotaInstalled: Boolean = true,

    val webContent: String = "加载中...",
)
