package com.bandwa.openadb.presentation.main.person

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.LiquidDropdownMenu
import com.bandwa.openadb.component.LiquidDropdownMenuItem
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.GlassyContainer
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.presentation.ui.shapes
import com.bandwa.openadb.service.install.ModApk
import com.bandwa.openadb.service.install.LauncherSupport
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 与 `Hook.DEFAULT_WALLPAPER_PATH` 保持一致（`Hook.java:36`）；那边是 private，这里只能重写一份。 */
private const val DEFAULT_WALLPAPER_PATH = "/storage/emulated/0/launcherPic/wallpaper.png"

@Composable
fun LauncherSettingsDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var picQrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var showDeploymentConfirm by remember { mutableStateOf(false) }
    var deploymentStatus by remember { mutableStateOf<String?>(null) }
    var deployProgress by remember { mutableStateOf(0f) }
    var deployProgressText by remember { mutableStateOf("") }

    // 桌面能不能换壁纸 / 调透明度，取决于装的是什么桌面（原厂 / 我们注入的 / 用户自己的改版）。
    // 以前这里只查 dex 里有没有我们的 Hook 类，用户那份 GenPanda 桌面会被误判成「没部署」，
    // 一点就弹「即将开始部署」，装下去等于把他自己挑的桌面刷掉（用户 m06708）。
    // 桌面是用户自己装的改版（比如 GenPanda 那份）：能用，也不归我们管，
    // 所以不弹框打断，只在页面里挂一行说明，避免用户以为是工具箱改的。
    var launcherInfo by remember { mutableStateOf<LauncherSupport.Info?>(null) }
    var launcherProbing by remember { mutableStateOf(true) }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launcherProbing = true
        launcherInfo = LauncherSupport.detect(context)
        launcherProbing = false
    }

    var currentOpacity by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("card_alpha", null).let {
            when (it) {
                "1.0" -> "极低"
                "0.9" -> "较低"
                "0.75" -> "适中"
                "0.5" -> "较高"
                "0.25" -> "极高"
                "0.0" -> "全透"
                else -> "适中"
            }
        })
    }

    if (showDeploymentConfirm) {
        LiquidDialog(
            onDismissRequest = { showDeploymentConfirm = false },
            title = "PSA工具箱桌面环境",
            content = "检测到未部署PSA工具箱桌面环境或版本过低\n\n即将开始部署，大约需要几分钟时间，请耐心等待。",
            onConfirm = {
                showDeploymentConfirm = false
                deploymentStatus = "deploying"
                ModApk.packageMod(context, "com.android.launcher") { success ->
                    scope.launch {
                        LauncherSupport.invalidate()
                        if (success) {
                            deploymentStatus = "completing"
                            delay(1000)
                            withContext(Dispatchers.IO) {
                                ShellUtils.exec("am force-stop com.android.launcher")
                            }
                        } else {
                            deploymentStatus = "error"
                        }
                    }
                }
            },
        )
    }

    // 桌面是原厂：换壁纸 / 调透明度都用不了。直接说清楚，不要引导用户以为点一下就能修
    // （用户 m06708：「如果没修补桌面软件，当点击桌面自定义时就提示没有修复桌面相关无法使用」）。
    if (showUnavailableDialog) {
        LiquidDialog(
            onDismissRequest = { showUnavailableDialog = false },
            title = "桌面自定义用不了",
            content = "这台车机现在用的是原厂桌面，没有换壁纸和调卡片透明度的能力。\n\n" +
                "需要先在「维护诊断 → 修复PSA工具箱」里修复桌面相关功能，" +
                "修复时选择「公共服务和桌面一起修复」。",
            confirmText = "知道了",
        )
    }

    // 桌面是用户自己装的改版（比如 GenPanda 那份）：能用，但不归我们管，
    // 任何自动部署都不要碰它 —— 在页面里挂一行说明即可，不弹框打断。

    LaunchedEffect(deploymentStatus) {
        when (deploymentStatus) {
            "deploying" -> {
                deployProgress = 0f
                deployProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    deployProgress += increment
                }
            }
            "completing" -> {
                deployProgressText = "即将完成..."
                val remaining = 1.0f - deployProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    deployProgress += increment
                }
                deployProgress = 1.0f
                deploymentStatus = "success"
            }
        }
    }

    when (deploymentStatus) {
        "deploying", "completing" -> {
            val animatedProgress by animateFloatAsState(
                targetValue = deployProgress,
                animationSpec = tween(durationMillis = 2000)
            )
            LiquidProgressDialog(
                onDismissRequest = {},
                title = "正在部署环境",
                progress = animatedProgress,
                progressText = deployProgressText,
            )
        }
        "success" -> {
            LiquidDialog(
                onDismissRequest = { deploymentStatus = null },
                title = "部署成功",
                content = "环境部署已完成，现在可以更换壁纸了。",
            )
        }
        "error" -> {
            LiquidDialog(
                onDismissRequest = { deploymentStatus = null },
                title = "部署失败",
                content = "环境部署失败，请检查存储空间或重试。",
                confirmText = "重试",
                onConfirm = { showDeploymentConfirm = true },
            )
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(
            LocalThemeManager provides themeManager,
            LocalFontScale provides themeManager.fontScale
        ) {
            PSAHelperTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlassyContainer(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(0.85f),
                        shape = shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "原厂桌面设置",
                                    style = AppTheme.typography.titleMedium,
                                    color = AppTheme.colors.textColorPrimary
                                )

                                IconButton(
                                    onClick = onDismissRequest
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = AppTheme.colors.textColorPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                SelectionText(
                                    modifier = Modifier.padding(top = 16.dp),
                                    text = when (launcherInfo?.state) {
                                        // 桌面是用户自己装的改版：能用，但不是我们装的，
                                        // 说清楚一句，免得用户以为是工具箱改的。
                                        LauncherSupport.State.FOREIGN_PATCHED ->
                                            "当前桌面是你自己安装的改版，换壁纸和卡片透明度都能用\n" +
                                                "工具箱不会覆盖它；若换壁纸没效果，请先换回原厂桌面再试"
                                        // 原厂桌面：直接告诉他这两项暂时用不了，以及怎么才能用。
                                        LauncherSupport.State.STOCK ->
                                            "当前是原厂桌面，换壁纸和卡片透明度暂不可用\n" +
                                                "到「维护诊断 → 修复PSA工具箱」选择「一起修复」后可用"
                                        else ->
                                            "可自由更换桌面壁纸，支持一键恢复原厂壁纸\n若设置不生效，请恢复一次原厂壁纸"
                                    }
                                )

                                picQrCodeBitmap?.let {
                                    Text(
                                        modifier = Modifier
                                            .padding(bottom = 16.dp),
                                        text = "请用手机扫描二维码上传壁纸",
                                        style = AppTheme.typography.titleMedium,
                                        color = AppTheme.colors.textColorPrimary
                                    )
                                    Image(
                                        modifier = Modifier
                                            .padding(bottom = 16.dp),
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "QR Code"
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start)
                                ) {
                                    TextButton(
                                        modifier = Modifier.width(220.dp),
                                        text = if (launcherProbing) "正在检查桌面…" else "更改桌面壁纸"
                                    ) {
                                        if (launcherProbing) return@TextButton
                                        val compatMode = context
                                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .getBoolean(Utils.COMPAT_MODE_KEY, true)
                                        val info = launcherInfo
                                        when {
                                            // 原厂桌面：没有这个能力，直接说清楚，不要引导用户去「部署」。
                                            info == null || !info.canCustomize -> showUnavailableDialog = true
                                            // 我们的旧版：问一句要不要更新。
                                            info.state == LauncherSupport.State.OURS_STALE && !compatMode ->
                                                showDeploymentConfirm = true
                                            // 自己的最新版、或用户自己装的改版（能力齐全）：直接开二维码。
                                            else -> showQRCode(context, "pic") { bitmap ->
                                                picQrCodeBitmap = bitmap
                                            }
                                        }
                                    }

                                    TextButton(
                                        modifier = Modifier.width(220.dp),
                                        text = "恢复原厂壁纸"
                                    ) {
                                        restoreStockLauncher(context) { picQrCodeBitmap = null }
                                    }
                                }

                                SelectionText(
                                    modifier = Modifier.padding(top = 32.dp),
                                    text = "修改桌面卡片透明度"
                                )

                                Box {
                                    TextButton(text = currentOpacity) {
                                        val info = launcherInfo
                                        if (info == null || !info.canCustomize) {
                                            showUnavailableDialog = true
                                        } else {
                                            expanded = !expanded
                                        }
                                    }

                                    LiquidDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        val opacityOptions = listOf(
                                            "极低" to "1.0",
                                            "较低" to "0.9",
                                            "适中" to "0.75",
                                            "较高" to "0.5",
                                            "极高" to "0.25",
                                            "全透" to "0.0"
                                        )

                                        opacityOptions.forEach { (label, value) ->
                                            LiquidDropdownMenuItem(
                                                text = label,
                                                selected = label == currentOpacity,
                                                onClick = {
                                                    updateOpacity(context, value, label) { currentOpacity = label }
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 恢复原厂壁纸。
 *
 * 这里**只删壁纸文件**，不卸载桌面包 —— 以前是 `ApkInstaller.uninstall("com.android.launcher")`，
 * 等于把 `/data` 里我们改造过的桌面整个卸掉、回落到 `/system` 原厂版，用户点「恢复原厂壁纸」
 * 结果桌面卡片缩放/删除这些功能一起没了（用户 m06160 报的就是这个）。
 *
 * 壁纸本身是 `Hook.applyWallpaper`（`Hook.java:201`）在桌面 View 上 `setBackground` 贴的：
 * 配置里的路径（默认 `/storage/emulated/0/launcherPic/wallpaper.png`）文件不存在就直接
 * `return`，什么都不贴 —— 所以删掉文件 + 重启一次桌面就等于恢复原厂壁纸。
 */
private fun restoreStockLauncher(context: Context, onSuccess: () -> Unit) {
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        val removed = withContext(Dispatchers.IO) {
            runCatching {
                val configured = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("wallpaper_path", null)
                val targets = listOfNotNull(DEFAULT_WALLPAPER_PATH, configured).distinct()
                var any = false
                targets.forEach { path ->
                    val file = File(path)
                    if (file.exists() && file.delete()) any = true
                }
                any
            }.getOrDefault(false)
        }

        // 不管文件在不在都重启一次桌面：文件可能是上次就删掉了，但桌面还挂着旧背景。
        withContext(Dispatchers.IO) {
            runCatching { ShellUtils.exec("am force-stop com.android.launcher") }
        }

        onSuccess()
        Toast.makeText(
            context,
            if (removed) "已恢复原厂壁纸" else "壁纸本来就是原厂的，已刷新桌面",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun updateOpacity(
    context: Context,
    opacityValue: String,
    opacityLabel: String,
    onOpacityChanged: () -> Unit
) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString("card_alpha", opacityValue)
    }
    onOpacityChanged()

    Toast.makeText(context, "透明度已修改为: $opacityLabel", Toast.LENGTH_SHORT).show()
    CoroutineScope(Dispatchers.IO).launch {
        ShellUtils.exec("am force-stop com.android.launcher")
    }
}
