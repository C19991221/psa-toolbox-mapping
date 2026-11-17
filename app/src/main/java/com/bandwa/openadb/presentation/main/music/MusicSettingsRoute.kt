package com.bandwa.openadb.presentation.main.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

// 多媒体插件还没有可分发安装包，ota/tools/mediaplugin/ 是空的，点下去只会 404。
// 入口先隐藏，包补上后改回 true；ViewModel 的 installMediaPlugin() 与远程地址常量都不动。
private const val SHOW_MEDIA_PLUGIN = false
// 车上没有雪铁龙的 HiCar 修复包，按钮隐藏避免点了下不到东西。
// ViewModel 的 fixHiCar("citroen") 分支与 citroen 的远程地址常量都不动。
private const val SHOW_HICAR_CITROEN = false

@Composable
fun AppsSettingsRoute(
    viewModel: MusicSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 注入是异步的：每次回到本页都重读一次，否则注入完看到还是旧的「灰」。
    LaunchedEffect(Unit) { viewModel.refresh() }

    AppsSettingsScreen(
        uiState = uiState,
        fixHiCar = viewModel::fixHiCar,
        fixCarLife = viewModel::fixCarLife,
        fixMediaPlugin = viewModel::installMediaPlugin,
        onVolumeNavChange = viewModel::setVolumeNav,
    )
}

@Composable
fun AppsSettingsScreen(
    uiState: MusicSettingsUIState,
    fixHiCar: (String) -> Unit,
    fixCarLife: () -> Unit,
    fixMediaPlugin: () -> Unit = {},
    onVolumeNavChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showMediaPluginConfirm by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    val debounceInterval = 5000L

    var lastClickTime by remember { mutableLongStateOf(0L) }

    if (uiState.downloadProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "下载进度",
            progress = uiState.downloadProgress,
            progressText = uiState.downloadProgressText,
        )
    }

    if (showMediaPluginConfirm) {
        LiquidDialog(
            onDismissRequest = { showMediaPluginConfirm = false },
            title = "安装多媒体插件",
            content = "即将安装多媒体插件，用于原厂桌面的音乐卡片兼容第三方音乐APP，请确认。",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                showMediaPluginConfirm = false
                fixMediaPlugin()
            },
        )
    }

    if (infoDialogContent != null) {
        LiquidDialog(
            onDismissRequest = { infoDialogContent = null },
            title = "功能说明",
            content = infoDialogContent!!,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "音乐助手")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第三方音乐方控运行中 [方向盘右滚轮上拨为上一曲，下拨为下一曲，按下为播放/暂停]",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color = Color.Green, shape = CircleShape)
            )
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "修复Hicar/Carlife方控",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "点击后会自动下载修复了方控和解锁60帧版本的原厂修改版Hicar/Carlife" +
                    if (SHOW_HICAR_CITROEN) "\n\n雪铁龙版本由车友 L 制作" else ""
            }
        )

        Row {
            TextButton(text = "修复Hicar方控 [标致]") {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    fixHiCar("peugeot")
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }

            if (SHOW_HICAR_CITROEN) {
                TextButton(modifier = Modifier.padding(start = 56.dp), text = "修复Hicar方控 [雪铁龙]") {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastClickTime > debounceInterval) {
                        lastClickTime = currentTime
                        fixHiCar("citroen")
                    } else {
                        Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                    }
                }
            }

            TextButton(modifier = Modifier.padding(start = 56.dp), text = "修复Carlife方控") {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    fixCarLife()
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }
        }

        if (SHOW_MEDIA_PLUGIN) {
            // 音乐按键的绑定已合并到「按键映射」页统一管理。
            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "多媒体插件"
            )

            TextButton(text = "安装多媒体插件") {
                showMediaPluginConfirm = true
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 40.dp).wrapContentSize(),
            text = "导航播报时旋钮调导航音量",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "车机的中控音量旋钮不产生按键事件，由公共服务自己判断该调哪一组音量 ——" +
                    "判断依据是「当前谁占着音频焦点」。\n\n" +
                    "车载 CarPlay（DiPlay）播放导航播报时用的是降低背景音量的方式，音频焦点一直握在它手里，" +
                    "于是播报期间转旋钮调的是媒体音量，导航声音大小一点没变。\n\n" +
                    "打开这个开关后，只要满足两个条件，旋钮就会改调导航音量：\n" +
                    "1. 当前音频焦点是 DiPlay（com.shihab.diplay）；\n" +
                    "2. DiPlay 自己写了 Settings.Global 的 diplay_nav_prompt = 1，表示「我正在播报」。\n\n" +
                    "⚠ 必须配合 DiPlay 客户端：这个开关只负责在车机侧改判音量组，" +
                    "「现在是否在播报」这个信号只有 DiPlay 知道，得由它来写。" +
                    "没装 DiPlay、或 DiPlay 不写这个值，开关打开也不会有任何变化。\n\n" +
                    "开关默认关闭。仅部分车机系统版本支持（公共服务里要有对应的注入点）。"
            }
        )

        if (uiState.volumeNavAvailable) {
            SwitchGroup(
                check = uiState.volumeNav,
                title = "导航播报时，中控旋钮调导航音量而不是媒体音量",
                description = if (uiState.volumeNav) {
                    "已开启：需 DiPlay 客户端配合写播报状态，否则不生效"
                } else {
                    "已关闭：与原厂行为一致"
                },
                onCheckedChange = onVolumeNavChange,
            )
        } else {
            SwitchGroup(
                check = false,
                title = "导航播报时，中控旋钮调导航音量而不是媒体音量",
                description = "不可用：本车机版本没有这条补丁的注入点，或公共服务还没注入",
                isEnable = false,
                onCheckedChange = {},
            )
        }
    }
}

@Composable
@CombinedThemePreviews
fun AppsSettingsScreenPreview() {
    PSAHelperThemePreview {
        AppsSettingsScreen(
            uiState = MusicSettingsUIState(),
            fixHiCar = {},
            fixCarLife = {},
            fixMediaPlugin = {}
        )
    }
}
