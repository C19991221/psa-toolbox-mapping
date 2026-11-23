package com.bandwa.openadb.presentation.main.navi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SectionTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun NaviSettingsRoute(
    viewModel: NaviSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 注入是异步的：每次回到本页都重读一次，否则注入完看到还是旧的「灰」。
    LaunchedEffect(Unit) { viewModel.refresh() }

    NaviSettingsScreen(
        uiState = uiState,
        onActivateAmap = viewModel::activateAmap,
        onNaviKeepChange = viewModel::setNaviKeep,
        onVolumeNavChange = viewModel::setVolumeNav,
    )
}

@Composable
fun NaviSettingsScreen(
    uiState: NaviSettingsUIState,
    onActivateAmap: () -> Unit,
    onNaviKeepChange: (Boolean) -> Unit = {},
    onVolumeNavChange: (Boolean) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    var showActivationConfirm by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    if (showActivationConfirm) {
        LiquidDialog(
            onDismissRequest = { showActivationConfirm = false },
            title = "操作确认",
            content = "是否尝试激活高德？这会清除高德地图的现有数据。",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                showActivationConfirm = false
                onActivateAmap()
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
        PageTitle(text = "导航助手")

        SelectionText(
            modifier = Modifier.padding(top = 28.dp).wrapContentSize(),
            text = "高德签名冲突检测，点击右边圆点查看说明",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "如果安装了公版高德(可在线升级)且车机系统进行了OTA升级，则会触发此异常。常见解决办法:\n\n1. 直接恢复出厂设置，适合小白\n2. 再次覆盖system分区里的原厂高德，可做好备份，方便日后恢复，参照逐梦汴梁城的最新教程\n3. 只要不卸载高德，则不会触发变砖，但仍有安全隐患，请参照前两条方法彻底解决"
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "高德签名检测",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary
            )
            Spacer(modifier = Modifier.width(20.dp))
            uiState.isAmapSafe?.let { safe ->
                val text = if (safe) "正常" else "异常，请勿长按桌面卸载高德，否则必砖！"
                val color = if (safe) AppTheme.colors.textColorSuccess else AppTheme.colors.textColorError
                Text(text = text, style = AppTheme.typography.titleMedium, color = color)
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp).wrapContentSize(),
            text = "激活高德")
        TextButton(text = "尝试激活高德") {
            showActivationConfirm = true
        }

        // ── DiPlay 相关（仪表投屏 + 旋钮音量）──────────────────────────────
        // 这两项都是为车载 CarPlay（DiPlay, com.shihab.diplay）服务的，
        // 2026-11-23 用户要求归到一类，原来「旋钮音量」在「音乐助手」页。
        //
        // 标题用 SectionTitle（L2：21sp + 粗体 + 主色 + 下划线），不能用 SelectionText ——
        // 后者是 L3 条目标题（18sp + 次级灰），和下面两个开关的标题同一个样式，
        // 用户一眼看不出这是「分类」（2026-11-23 用户指出「不同级别颜色没区分」）。
        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "DiPlay 相关")

        SelectionText(
            modifier = Modifier.padding(top = 20.dp).wrapContentSize(),
            text = "仪表投屏",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "车机高德即使没在导航，也会每秒发一条空的导航报文。" +
                    "公共服务会把它当成「仪表清屏」指令写进仪表，于是第三方导航投出去的画面被每秒清一次，仪表一直闪回原厂罗盘。\n\n" +
                    "打开这个开关后，空报文不再变成清屏指令，第三方导航（DiPlay / CarLife / HiCar）投到仪表的画面就能稳定显示。\n\n" +
                    "高德自己退出导航时的清屏不受影响，导航过程中也不会被打断。\n\n" +
                    "开关默认关闭：关着的时候车机行为与出厂完全一致。"
            }
        )

        if (uiState.naviKeepAvailable) {
            SwitchGroup(
                check = uiState.naviKeep,
                title = "保持仪表投屏，不被高德空报文清掉",
                description = if (uiState.naviKeep) {
                    "已开启：第三方导航的画面不会再被每秒清屏"
                } else {
                    "已关闭：与原厂行为一致"
                },
                onCheckedChange = onNaviKeepChange,
            )
        } else {
            SwitchGroup(
                check = false,
                title = "保持仪表投屏，不被高德空报文清掉",
                description = "不可用：公共服务还没有注入这一条补丁，请先点「维护诊断 → 修复PSA工具箱」",
                isEnable = false,
                onCheckedChange = {},
            )
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

        // 导航按键的绑定已合并到「按键映射」页统一管理（2026-11-16）。
    }
}

@Composable
@CombinedThemePreviews
fun NaviSettingsScreenPreview() {
    PSAHelperThemePreview {
        NaviSettingsScreen(
            uiState = NaviSettingsUIState(),
            onActivateAmap = {},
        )
    }
}
