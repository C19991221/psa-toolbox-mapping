package com.bandwa.openadb.presentation.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.BuildConfig
import com.bandwa.openadb.R
import com.bandwa.openadb.ui.R as UiR
import com.bandwa.openadb.component.TabItem
import com.bandwa.openadb.component.VerticalTabLayout
import com.bandwa.openadb.presentation.main.about.AboutSettingsRoute
import com.bandwa.openadb.presentation.main.apps.AppsRoute
import com.bandwa.openadb.presentation.main.fuel.FuelLedgerRoute
import com.bandwa.openadb.presentation.main.expert.ExpertModeRoute
import com.bandwa.openadb.presentation.main.keymap.KeyMapRoute
import com.bandwa.openadb.presentation.main.music.AppsSettingsRoute
import com.bandwa.openadb.presentation.main.navi.NaviSettingsRoute
import com.bandwa.openadb.presentation.main.person.PersonSettingsRoute
import com.bandwa.openadb.presentation.main.projection.ProjectionSettingsRoute
import com.bandwa.openadb.presentation.main.system.SystemSettingsRoute
import com.bandwa.openadb.presentation.main.tools.ToolsSettingsRoute
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.GlassyContainer
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.preview.CombinedThemePreviews
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_PROXY_AIR_CONDITION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_PROXY_AIR_CONDITION
import com.bandwa.openadb.util.Utils.COMPAT_MODE_KEY
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.PsaExt
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.Utils.HVAC_HOOK_KEY
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.Utils.requestRequiredPermission
import dagger.hilt.android.AndroidEntryPoint
import com.umeng.commonsdk.UMConfigure
import javax.inject.Inject

/**
 * 「暂不修复」的静音标记。为 true 时启动不再弹强制修复框（审计 M6：退回原厂后
 * 每次开都会被堵住）。修复成功时清回 false。
 */
private const val CORE_PROMPT_MUTED_KEY = "core_prompt_muted"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var toolBoxServiceWrapper: ToolBoxServiceWrapper

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var themeManager: ThemeManager

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (!sharedPreferences.getBoolean("first_launch", true)) {
            toolBoxServiceWrapper.startService(
                if (sharedPreferences.getBoolean(HVAC_HOOK_KEY, false)) {
                    ACTION_START_PROXY_AIR_CONDITION
                } else {
                    ACTION_STOP_PROXY_AIR_CONDITION
                }
            )
        }
        val activity = this
        setContent {
            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                CompositionLocalProvider(LocalFontScale provides themeManager.fontScale) {
                    PSAHelperTheme {
                        HomeScreen(activity)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestRequiredPermission()
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(activity: Activity) {
    val context = activity
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var showTermsDialog by remember {
        mutableStateOf(sharedPrefs.getBoolean("first_launch", true))
    }
    var showCoreInstallDialog by remember { mutableStateOf(false) }
    var coreInstallStatus by remember { mutableStateOf<String?>(null) }
    var coreInstallFailure by remember {
        mutableStateOf<com.bandwa.openadb.service.install.CoreComponent.Result?>(null)
    }
    var coreInstallProgress by remember { mutableStateOf(0f) }
    var coreInstallProgressText by remember { mutableStateOf("") }

    LaunchedEffect(showTermsDialog) {
        if (!showTermsDialog) {
            val compatMode = sharedPrefs.getBoolean(COMPAT_MODE_KEY, false)
            // 用户主动「暂不修复」过就不再每次启动都堵一次（审计 M6：以前这个框
            // 既不能取消也不能关，退回原厂之后每次开都弹）。手动修复成功后这个
            // 标记会被清掉，所以下次版本不匹配时照样会提示。
            val muted = sharedPrefs.getBoolean(CORE_PROMPT_MUTED_KEY, false)
            if (!compatMode && !muted && coreNeedsInstall(context)) {
                showCoreInstallDialog = true
            }
        }
    }

    if (showCoreInstallDialog) {
        LiquidDialog(
            onDismissRequest = {  },
            title = "车机环境需要修复",
            content = "检测到这台车机的运行环境不满足工具箱的使用条件" +
                "（核心组件未部署，或版本与本软件不一致）。\n\n" +
                "需要先修复核心组件，工具箱的按键映射、360 全景等功能才能正常使用。\n" +
                "修复过程大约几分钟，期间请勿操作车机。",
            confirmText = "开始修复",
            onConfirm = {
                coreInstallStatus = "installing"
                // 自动安装走与手动「修复」同一个入口（CoreComponent），只有 force 不同。
                com.bandwa.openadb.service.install.CoreComponent.install(
                    context,
                    trigger = "首次打开或版本不匹配",
                    force = false,
                ) { result ->
                    // result.success=false 且 result.rolledBack=true 表示装了但起不来、
                    // 已自动退回原厂（审计 H2 的兜底），界面上要说清楚。
                    coreInstallFailure = if (result.success) null else result
                    coreInstallStatus = if (result.success) "completing" else "error"
                    if (result.success) {
                        // 修复成功后解除「暂不修复」的静音，下次版本再变还会提示。
                        sharedPrefs.edit().putBoolean(CORE_PROMPT_MUTED_KEY, false).apply()
                    }
                }
            },
            cancelText = "暂不修复",
            onCancel = {
                // 记下「用户已知情并选择先不修复」，免得每次启动都被挡住。
                // 手动修复成功、或在维护诊断里点修复成功，都会把这个标记清掉。
                sharedPrefs.edit().putBoolean(CORE_PROMPT_MUTED_KEY, true).apply()
                showCoreInstallDialog = false
            },
        )
    }

    LaunchedEffect(coreInstallStatus) {
        when (coreInstallStatus) {
            "installing" -> {
                coreInstallProgress = 0f
                coreInstallProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    coreInstallProgress += increment
                }
            }
            "completing" -> {
                coreInstallProgressText = "即将完成..."
                val remaining = 1.0f - coreInstallProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    coreInstallProgress += increment
                }
                coreInstallProgress = 1.0f
                coreInstallStatus = "success"
            }
        }
    }

    when (coreInstallStatus) {
        "installing", "completing" -> {
            val animatedProgress by animateFloatAsState(
                targetValue = coreInstallProgress,
                animationSpec = tween(durationMillis = 2000)
            )
            LiquidProgressDialog(
                onDismissRequest = {},
                title = "正在修复车机环境",
                progress = animatedProgress,
                progressText = coreInstallProgressText,
            )
        }
        "success" -> {
            LiquidDialog(
                onDismissRequest = {
                    coreInstallStatus = null
                    coreInstallFailure = null
                    showCoreInstallDialog = false
                },
                title = "修复成功",
                content = "车机环境已修复，工具箱的全部功能可以正常使用了。",
                confirmText = "好的",
            )
        }
        "error" -> {
            // 三种失败要说清楚，尤其「装了但起不来、已自动退回原厂」——
            // 那种情况车机是好的，只是这个包在这台车上跑不起来。
            val failure = coreInstallFailure
            val detail = when {
                failure?.rolledBack == true ->
                    "新组件装上后没能成功启动，已自动退回原厂组件，车机功能正常。\n\n" +
                        "这台车机可能与环境不匹配，请把日志发给我们排查，暂时不要重复尝试修复。"
                failure?.message?.isNotBlank() == true ->
                    "车机环境修复失败：${failure.message}。\n\n" +
                        "建议：清理一点存储空间后重试；仍然失败可以重启车机再试，" +
                        "也可以到「维护诊断 → 修复PSA工具箱」手动重试。"
                else ->
                    "车机环境修复失败。常见原因是存储空间不足，或核心组件正被系统占用。\n\n" +
                        "建议：清理一点存储空间后重试；仍然失败可以重启车机再试，" +
                        "也可以到「维护诊断 → 修复PSA工具箱」手动重试。"
            }
            LiquidDialog(
                onDismissRequest = {
                    coreInstallStatus = null
                    coreInstallFailure = null
                },
                title = "修复失败",
                content = detail,
                confirmText = "重试",
                onConfirm = {
                    coreInstallFailure = null
                    showCoreInstallDialog = true
                },
                cancelText = "暂时跳过",
                onCancel = {
                    coreInstallStatus = null
                    coreInstallFailure = null
                    showCoreInstallDialog = false
                },
            )
        }
    }

    if (showTermsDialog) {
        LiquidDialog(
            onDismissRequest = {  },
            title = "使用协议",
            content = "请仔细阅读我们的使用协议，使用即代表接受此协议。\n\n" +
                    "1. 此应用所有功能均免费使用。\n" +
                    "2. 由于使用了诸多底层接口，请谨慎使用，使用造成的任何后果责任自负。\n" +
                    "3. 此应用不鼓励用户以任何方式破解车机，请遵循厂家的使用条款。\n" +
                    "4. 此应用与标致雪铁龙集团官方无任何关系。\n" +
                    "5. 禁止恶意篡改此应用，由此产生的风险和后果自负。",
            confirmText = "同意",
            onConfirm = {
                sharedPrefs.edit().putBoolean("first_launch", false).apply()

                val umengKey = BuildConfig.UMENG_APP_KEY
                if (umengKey.isNotEmpty()) {
                    UMConfigure.preInit(context, umengKey, "Umeng")
                    UMConfigure.init(context, umengKey, "Umeng", UMConfigure.DEVICE_TYPE_BOX, "")
                }

                val hvacHook = sharedPrefs.getBoolean(HVAC_HOOK_KEY, false)
                val action = if (hvacHook) ACTION_START_PROXY_AIR_CONDITION else ACTION_STOP_PROXY_AIR_CONDITION
                val serviceIntent = android.content.Intent(context, com.bandwa.openadb.service.PSAToolBoxService::class.java)
                serviceIntent.putExtra("ACTION_KEY", action)
                context.startService(serviceIntent)
                showTermsDialog = false
            },
            cancelText = "退出",
            onCancel = { activity.finishAffinity() },
            confirmEnabledSeconds = 10
        )
    }

    // group 只影响左侧菜单的分组分隔线（组号变化处画一条细线），与路由无关。
    val tabs = remember {
        listOf(
            TabItem(1, UiR.drawable.ic_tab_settings, "系统设置", 0),
            TabItem(3, UiR.drawable.ic_person_settings, "个性设置", 0),
            TabItem(2, UiR.drawable.ic_music_settings, "音乐助手", 0),
            TabItem(4, UiR.drawable.ic_navi_settings, "导航助手", 0),
            TabItem(10, UiR.drawable.ic_key_map, "按键映射", 0),
            TabItem(5, UiR.drawable.ic_projection_settings, "仪表投屏", 0),
            TabItem(6, UiR.drawable.ic_fuel_ledger, "油耗账本", 1),
            TabItem(11, UiR.drawable.ic_apps, "应用管理", 2),
            TabItem(7, UiR.drawable.ic_tool_settings, "维护诊断", 2),
            TabItem(8, UiR.drawable.ic_expert_mode, "专家模式", 2),
            TabItem(9, UiR.drawable.ic_tool_info, "关于软件", 3)
        )
    }
    var currentTab by remember {
        mutableStateOf(
            run {
                // 只在**进程冷启动**时算一次：开关关 = 和原版一样从「系统设置」开始。
                // 从桌面再点一次图标只是把已有界面提到前台（Activity 是 singleTask），
                // 走的不是这里，所以改完设置要「后台清掉再进」才生效 —— 这是用户要的语义。
                if (!PsaExt.startPageOn(context)) {
                    tabs[0]
                } else {
                    val fixedId = PsaExt.startPageId(context)
                    val wantId = if (fixedId > 0) fixedId else PsaExt.lastTab(context)
                    tabs.firstOrNull { it.id == wantId } ?: tabs[0]
                }
            }
        )
    }

    val themeManager = LocalThemeManager.current
    val backgroundBrush = themeManager.getBackgroundBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            GlassyContainer(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
            ) {
                // 这里不能再套 verticalScroll：里面本来就是 LazyColumn（VerticalTabLayout），
                // 两层滚动会互相抢手势；而且 verticalScroll 内 weight(1f) 失效，列表高度算不出来。
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        text = stringResource(id = R.string.app_name),
                        color = AppTheme.colors.textColorPrimary,
                        style = AppTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    VerticalTabLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        tabs = tabs,
                        selectedTabId = currentTab.id
                    ) { selectedTab ->
                        currentTab = selectedTab
                        // 记住最后停留的页面，「启动时自动打开上次的页面」靠它。
                        PsaExt.setLastTab(context, selectedTab.id)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            GlassyContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (currentTab.id) {
                        1 -> SystemSettingsRoute()
                        // 「音乐助手」2026-11-16 一度隐藏，用户当天又要求恢复显示 ⇒ 走原 Route。
                        // 「关于软件」(9) 按 GPL-3.0 §5 必须保留版权与许可证声明，也显示。
                        // HiddenPage() 及其注释保留着，下次要临时藏页面直接换回去即可。
                        2 -> AppsSettingsRoute()
                        3 -> PersonSettingsRoute()
                        4 -> NaviSettingsRoute()
                        5 -> ProjectionSettingsRoute()
                        6 -> FuelLedgerRoute()
                        7 -> ToolsSettingsRoute()
                        8 -> ExpertModeRoute()
                        9 -> AboutSettingsRoute()
                        10 -> KeyMapRoute()
                11 -> AppsRoute()
                        else -> SystemSettingsRoute()
                    }
                }
            }
        }
    }
}

/**
 * 被「暂时隐藏」的页面所渲染的空内容。
 *
 * 用户 2026-11-16 要求：隐藏「音乐助手」「关于软件」的**页面所有内容**，
 * 但**不删除入口和组件**。所以左侧菜单项还在、还能点，只是右侧什么都不画。
 * 恢复时把 `when (currentTab.id)` 里对应的分支换回原 Route 即可，
 * 原 Route 文件（AppsSettingsRoute / AboutSettingsRoute）一行都没改。
 */
@Composable
private fun HiddenPage() {
    Box(modifier = Modifier.fillMaxSize())
}

private suspend fun coreNeedsInstall(context: Context): Boolean {
    val packageName = "com.cusc.misc"
    val stockApkPath = "/system/app/PSA_Misc/PSA_Misc.apk"
    val packageInfo = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        ?: return java.io.File(stockApkPath).exists()

    if (packageInfo.applicationInfo.sourceDir == stockApkPath) return true

    HookState.versionOf(packageName)?.let { return it != Hook.VERSION }

    runCatching {
        context.startService(Intent().setComponent(ComponentName(packageName, "$packageName.MiscService")))
    }

    return withTimeoutOrNull(12000L) {
        while (HookState.versionOf(packageName) == null) {
            delay(200)
        }
        HookState.versionOf(packageName) != Hook.VERSION
    } ?: true
}

@Composable
@CombinedThemePreviews
fun HomeScreenPreview() {
    PSAHelperThemePreview {
        HomeScreen(Activity())
    }
}
