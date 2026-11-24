package com.bandwa.openadb.presentation.main.tools

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.bandwa.openadb.presentation.ui.md_theme_dark_surfaceTint
import com.bandwa.openadb.presentation.ui.success_500
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.service.install.CoreComponent
import com.bandwa.openadb.service.install.PreflightCheck
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToolsSettingsRoute(
    viewModel: ToolsSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ToolsSettingsScreen(
        uiState = uiState,
        changeOnlyUnlockTrunk = viewModel::changeOnlyUnlockTrunk,
        fixUDisk = viewModel::fixUDisk,
    )
}

@Composable
fun ToolsSettingsScreen(
    uiState: ToolsSettingsUIState,
    changeOnlyUnlockTrunk: (Boolean) -> Unit,
    fixUDisk: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showRepairConfirmDialog by remember { mutableStateOf(false) }
    // 注入前预检（用户 2026-11-23 m05704 要求）：点「修复」先只读检查一遍，
    // 用大白话把「能不能修」和「哪里不行」摆出来，再让用户决定。
    var preflightReport by remember { mutableStateOf<PreflightCheck.Report?>(null) }
    var preflightRunning by remember { mutableStateOf(false) }
    var repairStatus by remember { mutableStateOf<String?>(null) }
    var repairProgress by remember { mutableStateOf(0f) }
    var repairProgressText by remember { mutableStateOf("") }
    var appsAnalysisInfo by remember { mutableStateOf<String?>(null) }
    var repairFailure by remember {
        mutableStateOf<CoreComponent.Result?>(null)
    }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }
    // 修复前问一句「桌面一起修吗」——只有桌面还是原厂时才有意义（用户 m06708）。
    // 已经是我们的桌面就跟着更新，是用户自己装的改版就不碰，这两种都不问。
    var showLauncherChoice by remember { mutableStateOf(false) }
    var pendingRepairTrigger by remember { mutableStateOf("手动修复PSA工具箱") }
    // 这次桌面做了什么（空串 = 没动，界面不提）。
    var launcherNote by remember { mutableStateOf("") }
    // 当前核心组件是不是注入版。PackageManager 是 IPC，放到 IO 线程查；
    // 回滚成功和修复成功后都要重算，所以用 tick 触发。
    var corePatched by remember { mutableStateOf(false) }
    var corePatchedTick by remember { mutableStateOf(0) }
    LaunchedEffect(corePatchedTick) {
        corePatched = withContext(Dispatchers.IO) { CoreComponent.isPatched(context) }
    }

    // 真正开始修：把 launcherMode 一路带下去，桌面那一步由 CoreComponent 自己判。
    val startRepair: (String, CoreComponent.LauncherMode) -> Unit = { trigger, mode ->
        repairStatus = "installing"
        CoreComponent.install(
            context,
            trigger = trigger,
            force = true,
            launcherMode = mode,
        ) { result ->
            repairFailure = if (result.success) null else result
            launcherNote = result.launcherNote
            repairStatus = if (result.success) "completing" else "error"
            corePatchedTick++
            if (result.success) {
                // 修复成功解除启动时的「暂不修复」静音（键在 MainActivity 里定义，
                // 这里直接用同一个字符串，避免为一个常量跨模块暴露）。
                context.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean("core_prompt_muted", false).apply()
            }
        }
    }

    // 修之前先看桌面：原厂的问一句要不要一起修；已经改过的别问，按检测结果自动处理。
    val askThenRepair: (String) -> Unit = { trigger ->
        pendingRepairTrigger = trigger
        scope.launch {
            val info = com.bandwa.openadb.service.install.LauncherSupport
                .detect(context, force = true)
            if (info.needsInjectPrompt) {
                showLauncherChoice = true
            } else {
                startRepair(trigger, CoreComponent.LauncherMode.AUTO)
            }
        }
    }

    if (showRestoreConfirmDialog) {
        LiquidDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = "恢复原厂核心组件",
            content = "会卸载当前的注入版，退回车机 /system 里的原厂组件。\n\n" +
                "卸载后按键映射、360 全景、媒体接管等全部恢复原厂行为，" +
                "要重新使用需要再点一次「修复PSA工具箱」。\n\n" +
                "只有在注入版明显不正常（比如启动就崩）时才需要这样做。",
            cancelText = "取消",
            confirmText = "恢复原厂",
            onConfirm = {
                showRestoreConfirmDialog = false
                restoreStatus = "restoring"
                CoreComponent.restoreStock(context) { ok ->
                    restoreStatus = if (ok) "success" else "error"
                    corePatchedTick++
                }
            },
        )
    }

    if (restoreStatus == "success") {
        LiquidDialog(
            onDismissRequest = { restoreStatus = null },
            title = "已恢复原厂",
            content = "核心组件已退回原厂版本，车机功能正常。" +
                "要重新使用工具箱的按键映射等功能，请点「修复PSA工具箱」。",
            confirmText = "好的",
        )
    }

    if (restoreStatus == "error") {
        LiquidDialog(
            onDismissRequest = { restoreStatus = null },
            title = "恢复失败",
            content = "没能退回原厂组件。可能是系统正忙，或 /system 里没有可用的原厂件。\n\n" +
                "可以重启车机后再试一次。",
            confirmText = "知道了",
        )
    }

    // 预检结果：通过就直接问「要不要开始」，不通过只给「知道了」。
    // 两种都用同一个对话框，避免用户点两下才看到结论。
    preflightReport?.let { report ->
        LiquidDialog(
            onDismissRequest = { preflightReport = null },
            title = report.headline,
            content = report.asText(),
            confirmText = if (report.canProceed) "开始修复" else "知道了",
            onConfirm = {
                preflightReport = null
                if (report.canProceed) {
                    // 手动修复走与自动安装同一个入口（CoreComponent），只有 force 不同。
                    askThenRepair("手动修复PSA工具箱")
                }
            },
            cancelText = if (report.canProceed) "取消" else null,
            onCancel = if (report.canProceed) ({ preflightReport = null }) else null,
        )
    }

    if (preflightRunning) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "正在检查车机环境",
            progress = null,
            progressText = "只读检查，不会改动任何东西",
        )
    }

    if (showRepairConfirmDialog) {
        LiquidDialog(
            onDismissRequest = { showRepairConfirmDialog = false },
            title = "修复确认",
            content = "修复功能将重新安装核心组件，建议仅在工具箱工作异常时使用。是否继续？",
            cancelText = "取消",
            confirmText = "立即修复",
            onConfirm = {
                showRepairConfirmDialog = false
                askThenRepair("手动修复PSA工具箱")
            },
        )
    }

    // 桌面还是原厂时才问这一句（用户 m06708）。
    if (showLauncherChoice) {
        LiquidDialog(
            onDismissRequest = { },
            title = "桌面要不要一起修复",
            content = "桌面自定义（换壁纸、调卡片透明度、卡片缩放删除）需要改造车机桌面。" +
                "\n\n" +
                "一起修复：上面这些功能都能用，桌面会由工具箱接管。\n" +
                "只修公共服务：按键映射、360 全景等照常可用，换壁纸和卡片透明度暂时用不了。",
            confirmText = "一起修复",
            onConfirm = {
                showLauncherChoice = false
                startRepair(pendingRepairTrigger, CoreComponent.LauncherMode.INJECT)
            },
            cancelText = "只修公共服务",
            onCancel = {
                showLauncherChoice = false
                startRepair(pendingRepairTrigger, CoreComponent.LauncherMode.SKIP)
            },
        )
    }

    LaunchedEffect(repairStatus) {
        when (repairStatus) {
            "installing" -> {
                repairProgress = 0f
                repairProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    repairProgress += increment
                }
            }
            "completing" -> {
                repairProgressText = "即将完成..."
                val remaining = 1.0f - repairProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    repairProgress += increment
                }
                repairProgress = 1.0f
                repairStatus = "success"
            }
        }
    }

    if (repairStatus == "installing" || repairStatus == "completing") {
        val animatedProgress by animateFloatAsState(
            targetValue = repairProgress,
            animationSpec = tween(durationMillis = 2000)
        )
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "正在修复核心组件",
            progress = animatedProgress,
            progressText = repairProgressText,
        )
    }

    if (repairStatus == "success") {
        LiquidDialog(
            onDismissRequest = { repairStatus = null },
            title = "修复成功",
            content = buildString {
                append("核心组件已成功修复，所有功能已恢复正常。")
                if (launcherNote.isNotBlank()) {
                    append("\n\n")
                    append(launcherNote)
                }
            },
            confirmText = "好的",
        )
    }

    if (repairStatus == "error") {
        val failure = repairFailure
        val detail = when {
            failure?.rolledBack == true ->
                "新组件装上后没能成功启动，已自动退回原厂组件，车机功能正常。\n\n" +
                    "这台车机可能与环境不匹配，请把日志发给我们排查，暂时不要重复尝试修复。"
            failure?.message?.isNotBlank() == true ->
                "核心组件修复失败：${failure.message}。"
            else ->
                "核心组件修复失败，请检查车机存储空间或尝试重启工具箱后重试。"
        }
        LiquidDialog(
            onDismissRequest = { repairStatus = null; repairFailure = null },
            title = "修复失败",
            content = detail,
            confirmText = "重试",
            onConfirm = {
                repairStatus = "installing"
                CoreComponent.install(
                    context,
                    trigger = "手动修复PSA工具箱（失败重试）",
                    force = true,
                ) { result ->
                    repairFailure = if (result.success) null else result
                    launcherNote = result.launcherNote
                    repairStatus = if (result.success) "completing" else "error"
                    corePatchedTick++
                    if (result.success) {
                        // 修复成功解除启动时的「暂不修复」静音（键在 MainActivity 里定义，
                        // 这里直接用同一个字符串，避免为一个常量跨模块暴露）。
                        context.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean("core_prompt_muted", false).apply()
                    }
                }
            },
            cancelText = "取消",
        )
    }

    if (appsAnalysisInfo != null) {
        LiquidDialog(
            onDismissRequest = { appsAnalysisInfo = null },
            title = "存储分析",
            content = appsAnalysisInfo!!,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "维护诊断")

        // 按用途分四组，组标题用 SectionTitle。
        SectionTitle(modifier = Modifier.padding(top = 28.dp), text = "车辆数据")

        Row {
            Column {
                SelectionText(text = "当前档位")

                Text(
                    text = uiState.currentGearMode,
                    color = when (uiState.currentGearMode) {
                        "P", "R" -> md_theme_dark_surfaceTint
                        "N", "D" -> success_500
                        else -> AppTheme.colors.textColorPrimary
                    },
                    style = AppTheme.typography.headlineLarge
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "剩余油量")

                Text(
                    text = uiState.remainOilPresent,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "预估行驶里程")

                Text(
                    text = uiState.remainDriveDistance,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "方向盘转角")

                Text(
                    text = uiState.steeringAngle,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "当前车速")

                Text(
                    text = uiState.currentSpeed,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "本次驾驶累计油耗")

                Text(
                    text = uiState.tripOilConsumption,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }
        }

        Row(modifier = Modifier.padding(top = 32.dp)) {
            Column {
                SelectionText(text = "原装车机检测")

                Text(
                    text = uiState.isVinCodeMatchSystem,
                    style = AppTheme.typography.headlineLarge,
                    color = when (uiState.isVinCodeMatchSystem) {
                        "异常" -> md_theme_dark_surfaceTint
                        "正常" -> success_500
                        else -> AppTheme.colors.textColorPrimary
                    }
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "实际总里程")

                Text(
                    text = uiState.totalMileage,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "真实车架号")

                Text(
                    text = uiState.vinCode,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "仅解锁后备箱",
            showInfoButton = true,
            infoClickListen = {
                appsAnalysisInfo = "使用车载钥匙上的后备箱按钮解锁后备箱时，不解锁车门仅解锁后备箱"
            }
        )

        SwitchGroup(
            check = uiState.isOnlyUnlockTrunk,
            title = "使用钥匙开启后备箱时，仅解锁后备箱不解锁车门",
            onCheckedChange = changeOnlyUnlockTrunk,
            isEnable = uiState.isOnlyUnlockTrunkUsable
        )

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "查找系统中占用大的文件夹",
            showInfoButton = true,
            infoClickListen = {
                appsAnalysisInfo = "分析 /sdcard/ 和 /data/ 中占用最多的前5个文件夹"
            }
        )
        TextButton(text = "查找异常占用") {
            Toast.makeText(context, "正在分析存储占用，请稍候...", Toast.LENGTH_SHORT).show()
            findLargeFolders { info ->
                appsAnalysisInfo = info
            }
        }

        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "清理与维护")

        // 缓存清理（含自动清理开关与白名单）已并入 StorageCleanSection 内部，
        // 与「存储清理」合成一段（2026-11-16 用户要求）。
        StorageCleanSection(topPad = 24.dp)

        MemGuardSection(topPad = 24.dp)

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "模拟插拔U盘，无法读取U盘时可尝试"
        )
        TextButton(text = "重载U盘") {
            fixUDisk()
        }

        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "运行与诊断")

        RuntimeStatusSection(topPad = 20.dp)

        DiagReportSection(topPad = 24.dp)

        BootWifiSection(topPad = 24.dp)

        // 「与风险操作」去掉了：原来这段里唯一的风险项是工程模式入口，
        // 2026-11-16 已搬到「专家模式」页最底下，这里只剩工具箱自己的修复。
        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "工具箱")

        SelectionText(text = "修复PSA工具箱异常，若工作正常请勿使用此功能")
        TextButton(text = "修复PSA工具箱") {
            preflightRunning = true
            scope.launch {
                val report = runCatching { PreflightCheck.run(context) }.getOrNull()
                preflightRunning = false
                if (report == null) {
                    // 预检自己出错不该拦住用户 —— 退回原来的「直接确认」流程。
                    showRepairConfirmDialog = true
                } else {
                    preflightReport = report
                }
            }
        }

        // 回滚出口（审计 M6）：核心组件是注入进 com.cusc.misc 的，它不在应用管理的
        // 列表里，所以「装上了、跑起来崩」以前在界面上根本没有撤掉的办法。
        // 只有当前确实是注入版时才显示 —— 已经是原厂件还点这一下没有意义。
        if (corePatched) {
            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "注入版异常时退回原厂组件（按键映射、360 等功能会一起失效）",
                showInfoButton = true,
                infoClickListen = {
                    appsAnalysisInfo = "会卸载 /data 里的注入版，系统自动回落到 " +
                        "/system 里的原厂组件。装好之后如果核心组件启动就崩、" +
                        "工具箱功能全部失效，用这个退回原厂，再用「修复PSA工具箱」重来。"
                }
            )
            TextButton(text = "恢复原厂核心组件") {
                showRestoreConfirmDialog = true
            }
        }
    }
}

@Composable
@CombinedThemePreviews
fun ToolsSettingsScreenPreview() {
    PSAHelperThemePreview {
        ToolsSettingsScreen(
            uiState = ToolsSettingsUIState(),
            changeOnlyUnlockTrunk = {},
            fixUDisk = {}
        )
    }
}

@SuppressLint("SdCardPath")
private fun findLargeFolders(onResult: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val folderSizesMap = mutableMapOf<String, Long>()

        fun scanDir(path: String) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { folder ->
                    if (folder.isDirectory) {
                        val size = getDirectorySize(folder)
                        if (size > 0) {
                            folderSizesMap[folder.absolutePath] = size
                        }
                    }
                }
            }
        }

        scanDir("/sdcard")
        scanDir("/data")

        if (folderSizesMap.isEmpty()) {
            launch(Dispatchers.Main) {
                onResult("未找到文件夹数据或权限不足")
            }
            return@launch
        }

        val topFolders = folderSizesMap.entries
            .sortedByDescending { it.value }
            .take(5)

        val content = StringBuilder()
        content.append("存储占用前5的文件夹：\n\n")

        topFolders.forEachIndexed { index, entry ->
            content.append("${index + 1}. ${entry.key}\n")
            content.append("   占用大小：${formatSize(entry.value)}\n\n")
        }

        launch(Dispatchers.Main) {
            onResult(content.toString())
        }
    }
}

private fun getDirectorySize(dir: File): Long {
    var size: Long = 0
    dir.walk().forEach { file ->
        if (file.isFile) {
            size += file.length()
        }
    }
    return size
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val k = bytes / 1024
    if (k < 1024) return "$k KB"
    val m = k / 1024
    if (m < 1024) return "$m MB"
    val g = m / 1024
    return "$g GB"
}
