package com.bandwa.openadb.presentation.main.about

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.R
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SectionTitle
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.util.PsaExt

@Composable
fun AboutSettingsRoute(
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AboutSettingsScreen(
        uiState = uiState,
        onCheckUpdate = viewModel::checkUpdate,
        onCancelDownload = viewModel::cancelDownload,
        onMinimizeDownload = viewModel::minimizeDownload,
        onShowDownloadDialog = viewModel::showDownloadDialog,
        onReleaseNoteShown = viewModel::consumeReleaseNote,
    )
}

/**
 * 「关于软件」页。
 *
 * 只保留 GPL-3.0 对我们的强制项与正常的署名：
 * - §5(a) 改过什么、什么时候改的 → [LegalNotices.MODIFIED_NOTICE]
 * - §5(b) 原版权声明 + 仍以 GPL-3.0 发布 → 版权行（[LegalNotices.COPYRIGHT]）
 * - §5(d) 合适的法律声明（版权 / 无担保 / 许可证及查看途径）→ 「GPL-3.0 许可证」按钮
 *
 * 原版「支持开发组」一段（bandwa 的赞赏二维码 + AI 工具 Logo）**不在 GPL 要求之列**，
 * 我们自己的分发继续挂着它会让打赏错付 ⇒ 2026-11-17 整段删除。
 * 贡献者头像与致谢属正常署名，保留。
 */
@Composable
fun AboutSettingsScreen(
    uiState: AboutSettingsUIState,
    onCheckUpdate: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onMinimizeDownload: () -> Unit = {},
    onShowDownloadDialog: () -> Unit = {},
    onReleaseNoteShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLicense by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceMs = 5000L

    // 连点版本号时复用同一个 Toast 实例：每次都新建会排成一队，
    // 看起来就是"提示跟不上点的速度"（2026-11-16 用户反馈）。
    val tapToast = remember { Toast.makeText(context, "", Toast.LENGTH_SHORT) }

    val contributors = listOf(
        R.drawable.contributor1,
        R.drawable.contributor2,
        R.drawable.contributor3,
        R.drawable.contributor4,
        R.drawable.contributor5,
        R.drawable.contributor6,
        R.drawable.contributor7,
        R.drawable.contributor8
    )

    infoDialog?.let {
        LiquidDialog(
            onDismissRequest = { infoDialog = null },
            title = it.first,
            content = it.second,
        )
    }

    if (showLicense) {
        LiquidDialog(
            onDismissRequest = { showLicense = false },
            title = "GPL-3.0 许可证",
            content = LegalNotices.LICENSE_SUMMARY,
        )
    }

    if (uiState.downloadProgress >= 0f && uiState.downloadDialogVisible) {
        LiquidProgressDialog(
            onDismissRequest = { },
            title = "下载更新",
            progress = uiState.downloadProgress,
            progressText = uiState.downloadProgressText,
            cancelText = "取消下载",
            onCancel = onCancelDownload,
            secondaryText = "后台下载",
            onSecondary = onMinimizeDownload,
        )
    }

    uiState.releaseNote?.let { note ->
        LiquidDialog(
            onDismissRequest = onReleaseNoteShown,
            title = "新版特性",
            content = note,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "关于软件")

        // ---- 版本号：连点 10 次解锁工程模式入口 ----
        SectionTitle(modifier = Modifier.padding(top = 24.dp), text = "软件信息")

        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.clickable {
                    if (PsaExt.tapVersion(context)) {
                        Toast.makeText(context, "工程模式入口已解锁（在「专家模式」最下方）", Toast.LENGTH_LONG).show()
                    } else {
                        // 每一下都给反馈，且用短 Toast 且每次都取消前一条 —— 连点时
                        // 系统会把 Toast 排成一队，看起来就是"提示跟不上点的速度"。
                        val left = PsaExt.engineerTapsLeft(context)
                        if (left > 0) {
                            tapToast.cancel()
                            tapToast.setText("再点 $left 次解锁工程模式")
                            tapToast.show()
                        }
                    }
                },
                text = uiState.appVersionName,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary,
            )

            Spacer(modifier = Modifier.weight(1f))

            // 许可证入口：GPL-3.0 §5(d) 的「合适的法律声明」，点开看要点与全文地址。
            TextButton(text = "GPL-3.0 许可证") { showLicense = true }

            Spacer(modifier = Modifier.width(16.dp))

            // 检查更新贴着版本号右端（2026-11-16 用户要求：不再单独占一段）。
            // 下载中按钮自带进度填充，点它重新打开进度弹窗；取消/后台下载在弹窗里。
            val downloading = uiState.downloadProgress >= 0f
            TextButton(
                text = if (downloading) {
                    "下载中 ${(uiState.downloadProgress * 100).toInt().coerceIn(0, 100)}%"
                } else {
                    "检查更新"
                },
                progress = if (downloading) uiState.downloadProgress else null,
            ) {
                if (downloading) {
                    onShowDownloadDialog()
                    return@TextButton
                }
                val now = System.currentTimeMillis()
                if (now - lastClickTime > debounceMs) {
                    lastClickTime = now
                    onCheckUpdate()
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }
        }

        // ---- 基于原版修改的说明（GPL-3.0 §5(a)）----
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = LegalNotices.MODIFIED_NOTICE,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary,
        )

        // 版权行与致谢：原版字面量照抄（§5(b) 保留版权声明）。
        Text(
            modifier = Modifier.padding(top = 15.dp),
            text = "${uiState.appVersionName} | ${LegalNotices.COPYRIGHT}\n" + LegalNotices.THANKS,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary
        )

        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            contributors.forEach { contributorId ->
                Image(
                    painter = painterResource(id = contributorId),
                    contentDescription = "贡献者头像",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 12.dp)
                )
            }
        }
    }
}

fun Context.getAppVersionName(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return packageInfo?.versionName ?: run {
        "未知版本"
    }
}

@Composable
@CombinedThemePreviews
fun AboutSettingsScreenPreview() {
    PSAHelperThemePreview {
        AboutSettingsScreen(uiState = AboutSettingsUIState())
    }
}
