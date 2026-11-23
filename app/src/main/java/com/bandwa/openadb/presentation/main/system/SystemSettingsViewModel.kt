package com.bandwa.openadb.presentation.main.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.config.RemoteEndpoints
import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_FULLSCREEN_GESTURE
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_PROXY_AIR_CONDITION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_FULLSCREEN_GESTURE
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_PROXY_AIR_CONDITION
import com.bandwa.openadb.util.ADBControl
import com.bandwa.openadb.util.GestureBarController
import com.bandwa.openadb.util.NetworkUtils.isConnectedToWifi
import com.bandwa.openadb.util.PsaExt
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.HVAC_HOOK_KEY
import com.bandwa.openadb.util.apk.RemoteHttp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val toolBoxServiceWrapper: ToolBoxServiceWrapper
) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemSettingsUIState())
    val uiState = _uiState.asStateFlow()

    private lateinit var wifiStateReceiver: BroadcastReceiver

    init {
        monitorWifiState()
        val adbEnable = ADBControl.isAdbWirelessEnabled()
        val isProxyAirConditionChecked = sharedPreferences.getBoolean(HVAC_HOOK_KEY, false)
        val isFullscreenGestureChecked = sharedPreferences.getBoolean(GestureBarController.PREF_ENABLED, false)
        _uiState.value = SystemSettingsUIState(
            isAdbSwitchChecked = adbEnable,
            isAdbEnable = adbEnable,
            isProxyAirConditionChecked = isProxyAirConditionChecked,
            isFullscreenGestureChecked = isFullscreenGestureChecked,
            isAutoBrightAvmChecked = PsaExt.autoBrightAvmOn(context)
        )
        fetchWebContent()
        getFotaEnable()
    }

    private fun monitorWifiState() = viewModelScope.launch {
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                    if (context?.let { isConnectedToWifi(it) } == true) {
                        val adbEnable = ADBControl.isAdbWirelessEnabled()
                        _uiState.update {
                            it.copy(
                                isAdbSwitchChecked = adbEnable,
                                isAdbEnable = adbEnable,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isAdbSwitchChecked = false,
                                isAdbEnable = false,
                            )
                        }
                    }
                }
            }
        }
        context.registerReceiver(
            wifiStateReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
    }

    private fun fetchWebContent() {
        viewModelScope.launch {
            val url = RemoteEndpoints.info("/psatoolbox.html")
            val content = if (url.isEmpty()) {
                "暂无新公告"
            } else {
                try {
                    RemoteHttp.fetchText(url).ifBlank { "暂无新公告" }
                } catch (e: Exception) {
                    "暂无新公告"
                }
            }
            _uiState.update { it.copy(webContent = content) }
        }
    }

    fun refreshAdbState(checked: Boolean) = viewModelScope.launch {
        val isConnected = isConnectedToWifi(context)
        if (checked) {
            if (isConnected) {
                ADBControl.openADB()
                Toast.makeText(context, "已打开无线ADB", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "未连接手机热点，开启失败！", Toast.LENGTH_SHORT).show()
            }
        } else {
            ADBControl.closeADB()
            Toast.makeText(context, "已关闭无线ADB", Toast.LENGTH_SHORT).show()
        }
        _uiState.update {
            it.copy(
                isAdbSwitchChecked = checked && isConnected,
                isAdbEnable = checked && isConnected,
            )
        }
    }

    private fun getFotaEnable() = viewModelScope.launch {
        var isFotaInstalled = true
        val isFotaEnabled = try {
            val appInfo = context.packageManager.getApplicationInfo(
                "com.abfota.systemUpdate",
                PackageManager.GET_META_DATA
            )
            appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            isFotaInstalled = false
            false
        }
        _uiState.update {
            it.copy(
                isFotaEnabled = isFotaEnabled,
                isFotaInstalled = isFotaInstalled
            )
        }
    }

    fun changeFotaEnable(checked: Boolean) = viewModelScope.launch {
        val fotaPackageName = "com.abfota.systemUpdate"
        val command = if (checked) {
            "pm enable $fotaPackageName"
        } else {
            "pm disable-user $fotaPackageName"
        }
        if (!ShellUtils.exec(command).isSuccess) {
            return@launch
        }
        if (checked) {
            Toast.makeText(
                context, "已启用系统更新", Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context, "已禁用系统更新", Toast.LENGTH_SHORT
            ).show()
        }
        _uiState.update {
            it.copy(isFotaEnabled = checked)
        }
    }


    fun changeProxyAirConditionState(checked: Boolean) {
        sharedPreferences.edit().putBoolean(HVAC_HOOK_KEY, checked).apply()
        if (checked) {
            Toast.makeText(context, "已开启实体空调键", Toast.LENGTH_SHORT).show()
            toolBoxServiceWrapper.startService(ACTION_START_PROXY_AIR_CONDITION)
        } else {
            Toast.makeText(context, "已关闭实体空调键", Toast.LENGTH_SHORT).show()
            toolBoxServiceWrapper.startService(ACTION_STOP_PROXY_AIR_CONDITION)
        }
        _uiState.update { it.copy(isProxyAirConditionChecked = checked) }
    }

    fun changeAutoBrightAvmState(checked: Boolean) {
        PsaExt.setAutoBrightAvm(context, checked)
        if (checked) {
            Toast.makeText(context, "已开启：倒挡/360 全景时自动最亮", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "已关闭倒挡自动最亮", Toast.LENGTH_SHORT).show()
        }
        _uiState.update { it.copy(isAutoBrightAvmChecked = checked) }
    }

    fun changeFullscreenGestureState(checked: Boolean) {
        sharedPreferences.edit().putBoolean(GestureBarController.PREF_ENABLED, checked).apply()
        if (checked) {
            toolBoxServiceWrapper.startService(ACTION_START_FULLSCREEN_GESTURE)
            Toast.makeText(context, "全面屏手势已开启", Toast.LENGTH_SHORT).show()
        } else {
            toolBoxServiceWrapper.startService(ACTION_STOP_FULLSCREEN_GESTURE)
            Toast.makeText(context, "全面屏手势已关闭", Toast.LENGTH_SHORT).show()
        }
        _uiState.update { it.copy(isFullscreenGestureChecked = checked) }
    }

    override fun onCleared() {
        context.unregisterReceiver(wifiStateReceiver)
    }
}
