package com.bandwa.openadb.presentation.main.navi

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.HackAmap.clearAmapActivationCode
import com.bandwa.openadb.util.HackAmap.isAmapSafe
import com.bandwa.openadb.util.PsaExt
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
        checkAmapSignature()
        refresh()
    }

    private fun checkAmapSignature() = viewModelScope.launch {
        val safe = withContext(Dispatchers.IO) { isAmapSafe(context) }
        _uiState.update { it.copy(isAmapSafe = safe) }
    }

    /**
     * 每次进页面都重新读一遍。
     *
     * 注入是异步的，而且 ViewModel 挂在 Activity 上不会因为切页重建 ——
     * 注入完成后回到本页，只靠 init 那次读到的还是「没注入」的旧状态。
     */
    fun refresh() = viewModelScope.launch {
        val available = withContext(Dispatchers.IO) { PsaExt.naviKeepAvailable(context) }
        val on = withContext(Dispatchers.IO) { PsaExt.naviKeepOn() }
        _uiState.update { it.copy(naviKeepAvailable = available, naviKeep = on) }
    }

    fun setNaviKeep(on: Boolean) {
        val written = PsaExt.setFlag(PsaExt.PROP_NAVI_KEEP, on)
        // 写失败就不改界面上的状态，免得开关位置骗人。
        if (written) {
            _uiState.update { it.copy(naviKeep = on) }
        } else {
            Toast.makeText(context, "写属性失败，这台车机不让改", Toast.LENGTH_SHORT).show()
        }
    }

    fun activateAmap() {
        clearAmapActivationCode()
        Toast.makeText(context, "激活高德成功！", Toast.LENGTH_SHORT).show()
    }
}
