package com.bandwa.openadb.presentation.main.expert

import android.graphics.Bitmap

data class ExpertModeUIState(
    val isListMod: Boolean = false,

    val isCompatMode: Boolean = false,

    val isAppAutoGrant: Boolean = true,

    val isMtkLogEnabled: Boolean = false,

    val shouldRunUserSh: Boolean = false,

    val scriptQrCodeBitmap: Bitmap? = null,
)
