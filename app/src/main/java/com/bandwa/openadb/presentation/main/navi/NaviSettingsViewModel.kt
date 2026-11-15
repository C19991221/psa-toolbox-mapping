package com.bandwa.openadb.presentation.main.navi

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.presentation.main.binding.AppKeyBindingStore
import com.bandwa.openadb.util.HackAmap.clearAmapActivationCode
import com.bandwa.openadb.util.HackAmap.isAmapSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NaviSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NaviSettingsUIState())
    val uiState = _uiState.asStateFlow()

    init {
        restoreBindings()
        checkAmapSignature()
    }

    private fun restoreBindings() {
        _uiState.update {
            it.copy(binding = AppKeyBindingStore.read(context, PROP_SHORT_CLICK, PROP_LONG_CLICK))
        }
    }

    private fun checkAmapSignature() = viewModelScope.launch {
        val safe = withContext(Dispatchers.IO) { isAmapSafe(context) }
        _uiState.update { it.copy(isAmapSafe = safe) }
    }

    fun loadInstalledApps() = viewModelScope.launch {
        val apps = AppKeyBindingStore.loadInstalledApps(context)
        _uiState.update { it.copy(binding = it.binding.copy(installedApps = apps)) }
    }

    fun selectShortClickApp(packageName: String) {
        val app = AppKeyBindingStore.save(context, PROP_SHORT_CLICK, packageName)
        _uiState.update { it.copy(binding = it.binding.copy(shortClickApp = app)) }
        Toast.makeText(context, "短按APP设置成功", Toast.LENGTH_SHORT).show()
    }

    fun selectLongClickApp(packageName: String) {
        val app = AppKeyBindingStore.save(context, PROP_LONG_CLICK, packageName)
        _uiState.update { it.copy(binding = it.binding.copy(longClickApp = app)) }
        Toast.makeText(context, "长按APP设置成功", Toast.LENGTH_SHORT).show()
    }

    fun activateAmap() {
        clearAmapActivationCode()
        Toast.makeText(context, "激活高德成功！", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val PROP_SHORT_CLICK = "persist.bandwa.map.shortclick"
        const val PROP_LONG_CLICK = "persist.bandwa.map.longclick"
    }
}
