package com.bandwa.openadb.presentation.main.navi

import com.bandwa.openadb.presentation.main.binding.AppKeyBindingState

data class NaviSettingsUIState(
    val isAmapSafe: Boolean? = null,

    val binding: AppKeyBindingState = AppKeyBindingState(),
)
