package com.bandwa.openadb.presentation.main.person

import android.graphics.Bitmap

data class PersonSettingsUIState(
    val shouldRemindCloseAss: Boolean = false,

    val welcomeQrCodeBitmap: Bitmap? = null,

    val apkQrCodeBitmap: Bitmap? = null,

    val shafaInstallProgress: Float = -1f,
    val shafaInstallProgressText: String = "",

    val remoteInstallRunning: Boolean = false,
    val remoteInstallProgress: Float = 0f,
    val remoteInstallProgressText: String = "",
)
