package com.bandwa.openadb.presentation.main.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutSettingsUIState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAboutInfo()
    }

    private fun loadAboutInfo() = viewModelScope.launch {
        val versionName = runCatching { context.getAppVersionName() }
            .getOrDefault("未知版本")

        _uiState.update { it.copy(appVersionName = versionName) }
    }
}
