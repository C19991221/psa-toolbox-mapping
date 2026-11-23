package com.bandwa.openadb.presentation.main.about

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.config.RemoteEndpoints
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.service.install.RemoteApkSpec
import com.bandwa.openadb.util.PsaExt
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.apk.ApkDownloader
import com.bandwa.openadb.util.apk.RemoteHttp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutSettingsUIState())
    val uiState = _uiState.asStateFlow()

    /** 正在跑的检查/下载任务，供「取消下载」用。 */
    private var updateJob: Job? = null

    init {
        loadAboutInfo()
    }

    private fun loadAboutInfo() = viewModelScope.launch {
        val versionName = runCatching { context.getAppVersionName() }
            .getOrDefault("未知版本")

        _uiState.update { it.copy(appVersionName = versionName) }
    }

    /** 身份卡要读包信息与系统属性，放 IO 线程；切回本页时重读一次。 */
    fun reloadIdentity() = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { PsaExt.identityText(context) }
        _uiState.update { it.copy(identity = text) }
    }

    /**
     * 检查更新（**只检查、不下载**）。
     *
     * 用户 2026-11-23 要求：不管有没有新版本，都先把更新日志摆出来，
     * 由他自己决定「保持当前版本」还是「立即更新」⇒ 下载动作在 [startDownload]。
     */
    fun checkUpdate() {
        if (updateJob?.isActive == true) {
            Toast.makeText(context, "正在检查，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        updateJob = viewModelScope.launch {
            if (!RemoteEndpoints.isOtaConfigured) {
                Toast.makeText(context, "未配置更新服务", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!com.bandwa.openadb.util.NetworkUtils.isNetSystemUsable(context)) {
                Toast.makeText(context, "网络不可用，请先连热点", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "正在检查更新", Toast.LENGTH_SHORT).show()
            val remoteVersion = withContext(Dispatchers.IO) {
                runCatching {
                    RemoteHttp.fetchText(RemoteEndpoints.ota("/releases/psaboxlite/lasted_version"))
                }.getOrNull()?.trim().orEmpty()
            }
            val localVersion = runCatching { context.getAppVersionName() }.getOrDefault("")
            val hasNew = remoteVersion.isNotEmpty() && remoteVersion != localVersion.trim()
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    RemoteHttp.fetchText(RemoteEndpoints.ota("/releases/psaboxlite/lasted_info"))
                }.getOrNull()?.trim().orEmpty()
            }
            if (remoteVersion.isEmpty()) {
                Toast.makeText(context, "检查失败，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            _uiState.update {
                it.copy(
                    updatePrompt = UpdatePrompt(
                        remoteVersion = remoteVersion,
                        info = info.ifBlank { "本次更新没有填写更新日志。" },
                        hasNew = hasNew,
                    )
                )
            }
        }
    }

    /** 用户在新版日志框里点了「立即更新」——这时才开始下载安装。 */
    fun startDownload() {
        val prompt = _uiState.value.updatePrompt
        _uiState.update { it.copy(updatePrompt = null) }
        if (prompt == null || !prompt.hasNew) return
        if (updateJob?.isActive == true) {
            Toast.makeText(context, "正在下载，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        updateJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadProgress = 0f,
                    downloadProgressText = "正在下载最新版本…",
                    downloadDialogVisible = true,
                )
            }
            try {
                when (val downloaded = ApkInstaller.download(
                    context,
                    RemoteApkSpec(
                        url = RemoteEndpoints.ota("/releases/psaboxlite/lasted.apk"),
                        md5Url = RemoteEndpoints.ota("/releases/psaboxlite/lasted_md5"),
                        packageName = context.packageName,
                        fileName = UPDATE_FILE_NAME,
                    ),
                ) { progress, read, total ->
                    val (fraction, text) = Utils.downloadProgressState(progress, read, total)
                    _uiState.update { it.copy(downloadProgress = fraction, downloadProgressText = text) }
                }) {
                    is ApkInstaller.DownloadResult.Ready -> {
                        Toast.makeText(context, "新版本已下载，即将开始安装", Toast.LENGTH_SHORT).show()
                        ApkInstaller.start(context, downloaded.apkFile, context.packageName)
                    }

                    is ApkInstaller.DownloadResult.Failure ->
                        Toast.makeText(context, downloaded.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                // 取消与正常结束都走这里：进度归零、弹窗关掉，按钮回到「检查更新」。
                _uiState.update { it.copy(downloadProgress = -1f, downloadDialogVisible = false) }
            }
        }
    }

    /** 更新日志框里点「保持当前版本」/ 遮罩关闭。 */
    fun dismissUpdatePrompt() {
        _uiState.update { it.copy(updatePrompt = null) }
    }

    /**
     * 取消下载：停掉协程并**删掉已下到一半的文件**（`.part` 与已落盘的成品都要清），
     * 不留半包给下次校验添乱。
     *
     * 先 `cancelAndJoin` 等下载协程真的退出（它会自己删 `.part`），再兜底扫一遍目录 ——
     * 直接 cancel 就删，有可能撞上还在写的文件。
     */
    fun cancelDownload() {
        val job = updateJob
        updateJob = null
        viewModelScope.launch {
            if (job != null && job.isActive) {
                job.cancelAndJoin()
            }
            withContext(Dispatchers.IO) { deleteUpdateFiles() }
            _uiState.update {
                it.copy(downloadProgress = -1f, downloadProgressText = "", downloadDialogVisible = false)
            }
            Toast.makeText(context, "已取消下载，临时文件已删除", Toast.LENGTH_SHORT).show()
        }
    }

    /** 后台下载：只关弹窗，任务继续跑，进度改在「检查更新」按钮上显示。 */
    fun minimizeDownload() {
        _uiState.update { it.copy(downloadDialogVisible = false) }
    }

    /** 下载中点按钮重新打开进度弹窗。 */
    fun showDownloadDialog() {
        if (_uiState.value.downloadProgress >= 0f) {
            _uiState.update { it.copy(downloadDialogVisible = true) }
        }
    }

    private fun deleteUpdateFiles() {
        val dir = ApkDownloader.downloadDir(context)
        // 删不掉不致命：下次下载会覆盖同名 .part，成品也会被 MD5 校验挡住。
        listOf(File(dir, UPDATE_FILE_NAME), File(dir, "$UPDATE_FILE_NAME.part"))
            .forEach { it.takeIf(File::exists)?.delete() }
    }

    private companion object {
        const val UPDATE_FILE_NAME = "psaboxlite_update.apk"
    }
}
