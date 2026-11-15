package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

object OpenSysSetting {
    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        context.startActivity(intent)
    }

    fun openRunningServices(context: Context) {
        context.startActivity(
            Intent()
                .setClassName("com.android.settings", "com.android.settings.SubSettings")
                .putExtra(
                    ":settings:show_fragment",
                    "com.android.settings.applications.RunningServices"
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
