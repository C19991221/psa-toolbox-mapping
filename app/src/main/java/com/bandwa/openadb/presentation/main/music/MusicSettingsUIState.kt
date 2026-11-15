package com.bandwa.openadb.presentation.main.music

import com.bandwa.openadb.presentation.main.binding.AppKeyBindingState

data class MusicSettingsUIState(
    val downloadProgress: Float = -1f,
    val downloadProgressText: String = "",

    val binding: AppKeyBindingState = AppKeyBindingState(),
)
