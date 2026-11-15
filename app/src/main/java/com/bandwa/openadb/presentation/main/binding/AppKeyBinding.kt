package com.bandwa.openadb.presentation.main.binding

import android.content.Context
import com.bandwa.openadb.entity.MusicPlayer
import com.bandwa.openadb.util.Utils.getAppByPackageName
import com.bandwa.openadb.util.Utils.getAllIconPackages
import com.bandwa.openadb.util.Utils.getSystemProp
import com.bandwa.openadb.util.Utils.setSystemProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppKeyBindingState(
    val shortClickApp: MusicPlayer? = null,
    val longClickApp: MusicPlayer? = null,

    val installedApps: List<MusicPlayer> = emptyList(),
)

object AppKeyBindingStore {
    fun read(context: Context, shortClickProp: String, longClickProp: String): AppKeyBindingState =
        AppKeyBindingState(
            shortClickApp = getAppByPackageName(context, getSystemProp(shortClickProp)),
            longClickApp = getAppByPackageName(context, getSystemProp(longClickProp)),
        )

    fun save(context: Context, propKey: String, packageName: String): MusicPlayer? {
        setSystemProp(propKey, packageName)
        return getAppByPackageName(context, packageName)
    }

    suspend fun loadInstalledApps(context: Context): List<MusicPlayer> =
        withContext(Dispatchers.IO) { getAllIconPackages(context) }
}
