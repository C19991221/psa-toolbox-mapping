package com.bandwa.openadb.presentation.main.tools

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.dialog.AppInfo
import com.bandwa.openadb.presentation.dialog.AppPickerTwoPane
import com.bandwa.openadb.presentation.dialog.CacheWhitelistDialog
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidInputDialog
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.util.CleanStorage.Companion.cleanStorage
import com.bandwa.openadb.util.CrashCollect
import com.bandwa.openadb.util.MemGuard
import com.bandwa.openadb.util.PsaExt
import com.bandwa.openadb.util.StorageClean
import com.bandwa.openadb.util.SysInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 「维护诊断」里的五段：运行状态 / 内存清理 / 存储清理 / 崩溃与诊断包 / 工程模式。
 *
 * 这些是原「按键映射软件」的诊断页搬过来的；界面风格与工具箱原有卡片一致
 * （`SelectionText` 当标题、`TextButton` 当动作、`SwitchGroup` 当开关）。
 * `topPad` 由调用方给：紧跟在 [SectionTitle] 后面时传小一点，免得空出一大截。
 */
@Composable
fun RuntimeStatusSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("读取中…") }

    // 只在这一段可见时轮询；离开页面 LaunchedEffect 会取消。
    LaunchedEffect(Unit) {
        while (true) {
            text = withContext(Dispatchers.IO) { SysInfo.snapshot(context) }
            delay(2000)
        }
    }

    SelectionText(modifier = Modifier.padding(top = topPad), text = "运行状态")
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )
}

/**
 * 内存清理名单的候选应用，转成两栏弹窗要的 [AppInfo]。
 *
 * `MemGuard.candidates()` 已经排掉了系统应用、常驻应用和本应用（那些永远不该被清），
 * 这里只补图标。
 */
private fun memCleanCandidates(context: Context): List<AppInfo> {
    val pm = context.packageManager
    return MemGuard.candidates(context).map { (pkg, label) ->
        val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        AppInfo(packageName = pkg, appName = label, icon = icon)
    }
}

@Composable
fun MemGuardSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MemGuard.enabled(context)) }
    var level2 by remember { mutableStateOf(MemGuard.level2(context)) }
    var resultText by remember { mutableStateOf(MemGuard.lastLine(context)) }
    var busy by remember { mutableStateOf(false) }
    var showWhitelist by remember { mutableStateOf(false) }
    var confirmClean by remember { mutableStateOf(false) }
    val sharedPrefs = remember { context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE) }

    if (showWhitelist) {
        CacheWhitelistDialog(
            themeManager = LocalThemeManager.current,
            onDismissRequest = { showWhitelist = false },
        )
    }

    if (confirmClean) {
        LiquidDialog(
            onDismissRequest = { confirmClean = false },
            title = "清理确认",
            content = "会结束后台应用进程（白名单里的应用、前台正在用的应用都会跳过）。是否继续？",
            cancelText = "取消",
            confirmText = "立即清理",
            onConfirm = {
                confirmClean = false
                busy = true
            },
        )
    }

    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { MemGuard.run(context, "手动", true) }
        resultText = r.detail.ifEmpty { MemGuard.lastLine(context) }
        busy = false
    }

    SelectionText(modifier = Modifier.padding(top = topPad), text = "内存清理")
    Text(
        text = MemGuard.levelLine(context),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )

    SwitchGroup(
        modifier = Modifier.padding(top = 16.dp),
        check = enabled,
        title = "可用内存过低时自动清理后台",
        description = MemGuard.triggerLine(context) + "。默认关闭，上电不会自动清理",
        onCheckedChange = {
            enabled = it
            MemGuard.setEnabled(context, it)
        },
    )

    if (enabled) {
    SwitchGroup(
        modifier = Modifier.padding(top = 12.dp),
        check = level2,
        title = "清理时改用「强制停止」（更彻底）",
        description = if (MemGuard.forceStopPermission(context)) {
            "需要 FORCE_STOP_PACKAGES 权限，本机已授予"
        } else {
            "需要 FORCE_STOP_PACKAGES 权限，本机未授予 ⇒ 只有 root 时才生效"
        },
        onCheckedChange = {
            level2 = it
            MemGuard.setLevel2(context, it)
        },
    )
    }

    Text(
        modifier = Modifier.padding(top = 16.dp),
        text = "触发线：${MemGuard.triggerLine(context)}　·　白名单：" +
            "${sharedPrefs.getStringSet("cacheWhitelist", emptySet())?.size ?: 0} 个应用（这些应用不会被清理）",
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )

    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(text = "白名单管理") { showWhitelist = true }
        TextButton(text = if (busy) "清理中…" else "立即清理") {
            if (!busy) confirmClean = true
        }
    }

    if (resultText.isNotEmpty()) {
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = resultText,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary,
        )
    }
}

/**
 * 存储清理：缓存清理 + 自动清理 + 白名单 + 日志/诊断包清理，合成一段。
 *
 * 原来「缓存清理」在「维护诊断」页顶上一段、`StorageClean` 在另一段，两段都在清同一个 `/sdcard`，
 * 用户分不清该点哪个（2026-11-16 用户要求合并）。
 */
@Composable
fun StorageCleanSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE) }
    var shouldAutoClearSD by remember { mutableStateOf(sharedPrefs.getBoolean("shouldAutoClearSD", false)) }
    var showCleanConfirm by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelistInfo by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(StorageClean.enabled(context)) }
    var javaDeep by remember { mutableStateOf(StorageClean.javaDeep(context)) }
    var kw by remember { mutableStateOf(StorageClean.kwPlayCache(context)) }
    var crashDiag by remember { mutableStateOf(StorageClean.crashDiag(context)) }
    var otherApp by remember { mutableStateOf(StorageClean.otherAppCache(context)) }
    var resultText by remember { mutableStateOf(StorageClean.lastLine(context)) }
    var busy by remember { mutableStateOf(false) }
    var confirmClean by remember { mutableStateOf(false) }

    // 工具箱自带的「清各应用缓存」：走 CleanStorage（/sdcard 与 Android/data 等），
    // 与下面的 StorageClean（日志目录、酷我缓存、诊断包）是两套，所以分成两个按钮。
    if (showCleanConfirm) {
        LiquidDialog(
            onDismissRequest = { showCleanConfirm = false },
            title = "清理缓存确认",
            content = "会删除 /sdcard 根目录下除「下载 / 文档 / 音乐 / 图片 / 相机 / 影片 / PSA / " +
                "psa_iptheme / netease / amapauto9 / launcherPic / mtklog / Android」以外的文件夹，" +
                "并清空各应用的 cache。是否继续？",
            cancelText = "取消",
            confirmText = "确认清理",
            onConfirm = {
                cleanStorage(context)
                Toast.makeText(context, "清理缓存成功", Toast.LENGTH_SHORT).show()
                showCleanConfirm = false
            },
        )
    }

    if (showWhitelistDialog) {
        CacheWhitelistDialog(
            themeManager = LocalThemeManager.current,
            onDismissRequest = { showWhitelistDialog = false },
        )
    }

    whitelistInfo?.let { info ->
        LiquidDialog(
            onDismissRequest = { whitelistInfo = null },
            title = "说明",
            content = info,
        )
    }

    if (confirmClean) {
        LiquidDialog(
            onDismissRequest = { confirmClean = false },
            title = "清理确认",
            content = "会把开机日志目录（/data/log_temp、/data/anr、/data/tombstones）清空，" +
                "并按下面的勾选清理酷我播放缓存与多余的诊断包。不会动你下载的歌和任何设置。是否继续？",
            cancelText = "取消",
            confirmText = "立即清理",
            onConfirm = {
                confirmClean = false
                busy = true
            },
        )
    }

    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { StorageClean.run(context, "手动", true) }
        resultText = r.detail.ifEmpty { StorageClean.lastLine(context) }
        busy = false
    }

    SelectionText(modifier = Modifier.padding(top = topPad), text = "存储清理")
    Text(
        text = StorageClean.levelLine(context),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )

    SwitchGroup(
        modifier = Modifier.padding(top = 16.dp),
        check = enabled,
        title = "存储不足时自动清理应用缓存",
        description = StorageClean.triggerLine(context) +
            "。默认关闭，上电不会自动清理；打开后只清下面勾选的目录",
        onCheckedChange = {
            enabled = it
            StorageClean.setEnabled(context, it)
            if (it) {
                // 打开总开关时给一套保守的默认范围（日志目录 + 酷我缓存 + 诊断包），
                // 下面每一项都能单独关。
                if (!javaDeep) { javaDeep = true; StorageClean.setJavaDeep(context, true) }
                if (!kw) { kw = true; StorageClean.setKwPlayCache(context, true) }
                if (!crashDiag) { crashDiag = true; StorageClean.setCrashDiag(context, true) }
            }
        },
    )

    if (enabled) {

    SwitchGroup(
        modifier = Modifier.padding(top = 12.dp),
        check = javaDeep,
        title = "清空开机日志目录（最有效）",
        description = "/data/log_temp 实测能攒到 3 GB；这几个目录属主是 system，本应用直接能删，不需要 root",
        onCheckedChange = {
            javaDeep = it
            StorageClean.setJavaDeep(context, it)
        },
    )

    SwitchGroup(
        modifier = Modifier.padding(top = 12.dp),
        check = kw,
        title = "清酷我播放缓存",
        description = "只清 playcache / xlog / log 这些子目录的内容；下载的歌（Song）与设置永远不动",
        onCheckedChange = {
            kw = it
            StorageClean.setKwPlayCache(context, it)
        },
    )

    SwitchGroup(
        modifier = Modifier.padding(top = 12.dp),
        check = crashDiag,
        title = "裁剪崩溃记录与诊断包",
        description = "各留最近 20 个，不是全删 —— 万一上次崩溃还没导出，现场还在",
        onCheckedChange = {
            crashDiag = it
            StorageClean.setCrashDiag(context, it)
        },
    )

    SwitchGroup(
        modifier = Modifier.padding(top = 12.dp),
        check = otherApp,
        title = "清其它应用的 cache",
        description = "只清各应用的 cache/ 目录，不碰 files/、databases/、shared_prefs/。会影响别的应用，默认关闭",
        onCheckedChange = {
            otherApp = it
            StorageClean.setOtherAppCache(context, it)
        },
    )
    }

    SwitchGroup(
        modifier = Modifier.padding(top = 20.dp),
        check = shouldAutoClearSD,
        title = "存储极低（< 1 GB）时清理 /sdcard 临时文件",
        description = "只清临时目录，下载 / 文档 / 音乐 / 图片 / 相机 / 影片 / PSA 日志都保留；默认关闭",
        onCheckedChange = {
            shouldAutoClearSD = it
            sharedPrefs.edit().apply {
                putBoolean("shouldAutoClearSD", it)
                commit()
            }
            Toast.makeText(
                context,
                if (it) "存储极低时自动清理已开启" else "存储极低时自动清理已关闭",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )

    Text(
        modifier = Modifier.padding(top = 12.dp),
        text = "触发线：${StorageClean.triggerLine(context)}　·　白名单：" +
            "${sharedPrefs.getStringSet("cacheWhitelist", emptySet())?.size ?: 0} 个应用（这些应用的缓存不会被清）",
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )

    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(text = "白名单管理") { showWhitelistDialog = true }
        TextButton(text = if (busy) "清理中…" else "立即清理") {
            if (!busy) confirmClean = true
        }
    }

    Text(
        modifier = Modifier.padding(top = 20.dp),
        text = "工具箱自带的深度清理（清 /sdcard 根目录临时文件夹与各应用 cache，白名单里的应用跳过）",
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        TextButton(text = "清理缓存") { showCleanConfirm = true }
    }

    if (resultText.isNotEmpty()) {
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = resultText,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary,
        )
    }
}

@Composable
fun BootWifiSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    var bootWifi by remember { mutableStateOf(PsaExt.bootWifiOn()) }

    SelectionText(modifier = Modifier.padding(top = topPad), text = "开机行为")

    SwitchGroup(
        modifier = Modifier.padding(top = 8.dp),
        check = bootWifi,
        title = "开机自动打开 WiFi",
        description = "由公共服务的开机动作执行；关掉后开机不再动 WiFi 开关",
        onCheckedChange = { on ->
            if (PsaExt.setBootWifi(on)) {
                bootWifi = on
                Toast.makeText(context, if (on) "已开启" else "已关闭", Toast.LENGTH_SHORT).show()
            } else {
                bootWifi = PsaExt.bootWifiOn()
                Toast.makeText(context, "这台车机不允许写这个属性", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
fun DiagReportSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    var summary by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        summary = withContext(Dispatchers.IO) { CrashCollect.summary(context) }
    }

    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { CrashCollect.collect(context, force = true) }
        lastResult = r
        summary = withContext(Dispatchers.IO) { CrashCollect.summary(context) }
        busy = false
    }

    SelectionText(modifier = Modifier.padding(top = topPad), text = "崩溃与诊断包")
    Text(
        text = summary,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorSecondary,
    )
    Row(modifier = Modifier.padding(top = 16.dp)) {
        TextButton(text = if (busy) "收集中…" else "收集诊断包") {
            if (!busy) busy = true
        }
    }
    lastResult?.let {
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = it,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary,
        )
    }
}

@Composable
fun EngineerSection(topPad: Dp = 32.dp) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(PsaExt.engineerUnlocked(context)) }

    // 未解锁时整段不渲染：标题、说明一律不出现（用户 2026-11-16 要求）。
    // 提示只放在「关于软件」页连点版本号的过程里，这里藏着才叫"隐藏入口"。
    if (!unlocked) return

    SelectionText(modifier = Modifier.padding(top = topPad), text = "工程模式")

    Text(
        text = "⚠ 工程模式是车机自带的高风险界面，改错设置可能导致车机异常。不确定的项不要动。",
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textColorPrimary,
    )
    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(text = "打开工程模式") {
            val ok = PsaExt.openEngineer(context)
            if (!ok) Toast.makeText(context, "这台车机没有工程模式界面", Toast.LENGTH_SHORT).show()
        }
        TextButton(text = "重新隐藏入口") {
            PsaExt.lockEngineer(context)
            unlocked = false
            Toast.makeText(context, "已重新隐藏", Toast.LENGTH_SHORT).show()
        }
    }
}
