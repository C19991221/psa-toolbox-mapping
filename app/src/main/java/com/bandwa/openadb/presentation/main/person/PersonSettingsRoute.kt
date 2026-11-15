package com.bandwa.openadb.presentation.main.person

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.main.person.LauncherSettingsDialog
import com.bandwa.openadb.presentation.dialog.ToolBoxSettingsDialog
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidInputDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun PersonSettingsRoute(
    viewModel: PersonSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PersonSettingsScreen(
        uiState = uiState,
        onRemindCloseAssChange = viewModel::setShouldRemindCloseAss,
        onRequestWelcomeQrCode = viewModel::requestWelcomeQrCode,
        onRequestApkQrCode = viewModel::requestApkQrCode,
        onInstallShafa = viewModel::installShafaApp,
        onDismissRemoteInstall = viewModel::dismissRemoteInstall,
        onUpdateAvatar = viewModel::updateAvatarFromQq,
    )
}

@Composable
fun PersonSettingsScreen(
    uiState: PersonSettingsUIState,
    onRemindCloseAssChange: (Boolean) -> Unit,
    onRequestWelcomeQrCode: () -> Unit,
    onRequestApkQrCode: () -> Unit,
    onInstallShafa: () -> Unit,
    onDismissRemoteInstall: () -> Unit,
    onUpdateAvatar: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    var showLauncherSettingsDialog by remember { mutableStateOf(false) }
    var showToolBoxSettingsDialog by remember { mutableStateOf(false) }
    var showInstallConfirmDialog by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }
    var showAvatarInputDialog by remember { mutableStateOf(false) }

    val themeManager = LocalThemeManager.current

    if (showInstallConfirmDialog) {
        LiquidDialog(
            onDismissRequest = { showInstallConfirmDialog = false },
            title = "安装确认",
            content = "您是否想要安装沙发管家作为应用市场？此APP版权归沙发网所有。车机性能有限，请勿安装过多APP。",
            confirmText = "是",
            cancelText = "否",
            onConfirm = {
                showInstallConfirmDialog = false
                onInstallShafa()
            },
        )
    }

    if (uiState.shafaInstallProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "安装沙发管家",
            progress = uiState.shafaInstallProgress,
            progressText = uiState.shafaInstallProgressText,
        )
    }

    if (uiState.remoteInstallRunning) {
        val animatedProgress by animateFloatAsState(
            targetValue = uiState.remoteInstallProgress,
            animationSpec = tween(durationMillis = 1000),
            label = "remoteInstallProgress"
        )
        LiquidProgressDialog(
            onDismissRequest = onDismissRemoteInstall,
            title = "远程 APP 安装",
            progress = animatedProgress,
            progressText = uiState.remoteInstallProgressText,
        )
    }

    if (infoDialogContent != null) {
        LiquidDialog(
            onDismissRequest = { infoDialogContent = null },
            title = "功能说明",
            content = infoDialogContent!!,
        )
    }

    if (showAvatarInputDialog) {
        LiquidInputDialog(
            onDismissRequest = { showAvatarInputDialog = false },
            title = "车机头像修改",
            hint = "请输入QQ号",
            confirmText = "确定",
            cancelText = "取消",
            keyboardType = KeyboardType.Number,
            onConfirm = { qqNumber ->
                onUpdateAvatar(qqNumber)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 40.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        SelectionText(text = "开机欢迎语")
        SwitchGroup(
            check = uiState.shouldRemindCloseAss,
            title = "打开后，开机默认语音提醒关闭启停，可自定义语音内容",
            onCheckedChange = onRemindCloseAssChange
        )

        if (uiState.shouldRemindCloseAss) {
            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "自定义欢迎语"
            )

            uiState.welcomeQrCodeBitmap?.let {
                Text(
                    modifier = Modifier.padding(bottom = 16.dp),
                    text = "请用手机扫描二维码上传欢迎语",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textColorPrimary
                )
                Image(
                    modifier = Modifier.padding(bottom = 16.dp),
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR Code"
                )
            }

            TextButton(text = "上传欢迎语") {
                onRequestWelcomeQrCode()
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "APP 网页无线安装",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "无需 ADB，手机扫码上传 APK 即可自动在车机安装"
            }
        )

        uiState.apkQrCodeBitmap?.let {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = "请用手机扫描二维码上传并安装 APK",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorPrimary
            )
            Image(
                modifier = Modifier.padding(bottom = 16.dp),
                bitmap = it.asImageBitmap(),
                contentDescription = "APK QR Code"
            )
        }

        TextButton(text = "扫码上传 APK") {
            onRequestApkQrCode()
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp).wrapContentSize(),
            text = "一键安装沙发车载应用市场，方便小白安装APP\n车机空间性能有限，请勿安装过多APP"
        )
        TextButton(text = "安装应用市场") {
            showInstallConfirmDialog = true
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "原厂桌面修改",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "可在此菜单中更换桌面壁纸、恢复原厂壁纸以及修改桌面卡片透明度"
            }
        )
        TextButton(text = "修改桌面") {
            showLauncherSettingsDialog = true
        }

        if (showLauncherSettingsDialog) {
            LauncherSettingsDialog(
                themeManager = themeManager,
                onDismissRequest = { showLauncherSettingsDialog = false }
            )
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "可一键将侧边栏头像修改为所输入QQ号的头像\n为防止头像失效，请不要在车机上的会员中心登录"
        )
        TextButton(text = "车机头像修改") {
            showAvatarInputDialog = true
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "工具箱个性化设置",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "可在此菜单中设置工具箱背景颜色、深浅色模式以及字体大小"
            }
        )
        TextButton(text = "工具箱设置") {
            showToolBoxSettingsDialog = true
        }

        if (showToolBoxSettingsDialog) {
            ToolBoxSettingsDialog(
                themeManager = themeManager,
                onDismissRequest = { showToolBoxSettingsDialog = false }
            )
        }
    }
}

@Composable
@CombinedThemePreviews
fun PersonSettingsScreenPreview() {
    PSAHelperThemePreview {
        PersonSettingsScreen(
            uiState = PersonSettingsUIState(),
            onRemindCloseAssChange = {},
            onRequestWelcomeQrCode = {},
            onRequestApkQrCode = {},
            onInstallShafa = {},
            onDismissRemoteInstall = {},
            onUpdateAvatar = {},
        )
    }
}
