package com.bandwa.openadb.presentation.main.fuel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.DigitWheelRow
import com.bandwa.openadb.component.FuelAmountInput
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.util.FuelLedger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelLedgerRoute(
    viewModel: FuelLedgerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    FuelLedgerScreen(
        uiState = uiState,
        onPromptEnabledChange = viewModel::setPromptEnabled,
        onTankLitersChange = viewModel::setTankLiters,
        onDeleteEntry = viewModel::deleteEntry,
        onUpdateEntryAmount = viewModel::updateEntryAmount,
        onAddManualEntry = viewModel::addManualEntry,
    )
}

@Composable
fun FuelLedgerScreen(
    uiState: FuelLedgerUIState,
    onPromptEnabledChange: (Boolean) -> Unit,
    onTankLitersChange: (Int) -> Unit,
    onDeleteEntry: (FuelLedger.Entry) -> Unit,
    onUpdateEntryAmount: (FuelLedger.Entry, Int) -> Unit,
    onAddManualEntry: (Int) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<FuelLedger.Entry?>(null) }
    var editingEntry by remember { mutableStateOf<FuelLedger.Entry?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showTankDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 28.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "油耗账本")

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "加油记录",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "油箱容量",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
            )
            Text(
                text = "  ${uiState.tankLiters} L",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary,
            )
            TextButton(
                modifier = Modifier.padding(start = 12.dp),
                text = "修改",
                onclickListener = { showTankDialog = true },
            )
            Spacer(Modifier.width(20.dp))
            TextButton(text = "手动补录") { showManualEntry = true }
        }

        SwitchGroup(
            modifier = Modifier.padding(bottom = 32.dp),
            check = uiState.promptEnabled,
            title = "加油后弹窗提醒记录加油金额",
            onCheckedChange = onPromptEnabledChange
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(
                modifier = Modifier.weight(1f),
                label = "累计加油",
                value = "¥${uiState.totalAmount}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "加油次数",
                value = "${uiState.entries.size}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "平均每次",
                value = if (uiState.entries.isEmpty()) "—" else "¥${uiState.averageAmount}",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "百公里油耗",
                value = uiState.averagePer100Km?.let {
                    String.format(Locale.getDefault(), "%.1f L", it)
                } ?: "—",
            )
            StatItem(
                modifier = Modifier.weight(1f),
                label = "每公里成本",
                value = uiState.averageCostPerKm?.let {
                    String.format(Locale.getDefault(), "¥%.2f", it)
                } ?: "—",
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 趋势卡片（v2026-11-23 m05704 原本只在「能算出百公里油耗」时才画，
        // 用户 m07289 报「没看到趋势图」—— 算不出来时整块消失，什么提示都没有）。
        // 现在永远出一张卡片：有油耗就画油耗柱，没有就退化成加油金额柱 + 写明缺什么。
        val trendPoints = remember(uiState.entries, uiState.segments) {
            uiState.entries
                .mapNotNull { entry -> uiState.segments[entry.id]?.let { entry to it } }
                .take(8)
                .reversed()
        }
        FuelTrendCard(
            points = trendPoints,
            entries = uiState.entries,
            dateFormat = dateFormat,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // 空状态文案已经在上面趋势卡片里讲清楚了（「还没有加油记录…」），
        // 这里不再重复一遍，免得同一屏出现两句「暂无」。
        if (uiState.entries.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = "单击记录修改金额，长按删除",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
            )
            uiState.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = AppTheme.colors.glassBorderColor
                    )
                }
                FuelEntryRow(
                    entry = entry,
                    date = dateFormat.format(Date(entry.timestamp)),
                    segment = uiState.segments[entry.id],
                    onClick = { editingEntry = entry },
                    onLongPress = { pendingDelete = entry }
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        LiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = "删除记录",
            content = "确定删除 ${dateFormat.format(Date(entry.timestamp))} 的${if (entry.skipped) "跳过" else " ¥${entry.amount} "}记录吗？",
            confirmText = "删除",
            onConfirm = {
                onDeleteEntry(entry)
                pendingDelete = null
            },
            cancelText = "取消",
            onCancel = { pendingDelete = null }
        )
    }

    editingEntry?.let { entry ->
        val known = entry.amount > 0 && !entry.skipped
        FuelAmountDialog(
            title = if (known) "修改加油金额" else "补充加油金额",
            subtitle = if (entry.manual) {
                dateFormat.format(Date(entry.timestamp))
            } else {
                "${dateFormat.format(Date(entry.timestamp))} · 油量 ${entry.percentBefore}% → ${entry.percentAfter}%"
            },
            initialAmount = if (known) entry.amount else 200,
            onDismiss = { editingEntry = null },
            onConfirm = { amount ->
                onUpdateEntryAmount(entry, amount)
                editingEntry = null
            },
        )
    }

    if (showTankDialog) {
        TankCapacityDialog(
            initialLiters = uiState.tankLiters,
            onDismiss = { showTankDialog = false },
            onConfirm = { liters ->
                onTankLitersChange(liters)
                showTankDialog = false
            },
        )
    }

    if (showManualEntry) {
        FuelAmountDialog(
            title = "补录加油金额",
            subtitle = "用于没有弹窗提醒或误触取消的情况",
            initialAmount = 200,
            onDismiss = { showManualEntry = false },
            onConfirm = { amount ->
                onAddManualEntry(amount)
                showManualEntry = false
            }
        )
    }
}

@Composable
private fun FuelAmountDialog(
    title: String,
    subtitle: String,
    initialAmount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var amount by remember { mutableIntStateOf(initialAmount) }
    val colors = AppTheme.colors
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.backgroundColor)
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.height(24.dp))

            FuelAmountInput(
                initialAmount = initialAmount,
                onAmountChange = { amount = it },
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "取消",
                    onclickListener = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "确认",
                    onclickListener = {
                        if (amount <= 0) {
                            Toast.makeText(context, "请输入加油金额", Toast.LENGTH_SHORT).show()
                        } else {
                            onConfirm(amount)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FuelEntryRow(
    entry: FuelLedger.Entry,
    date: String,
    segment: FuelLedger.Segment?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.skipped) "未记录金额" else "¥${entry.amount}",
                style = AppTheme.typography.headlineLarge,
                color = if (entry.skipped) AppTheme.colors.textColorSecondary else AppTheme.colors.textColorPrimary
            )
            Text(
                text = date,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary
            )
            segment?.let {
                val cost = it.costPerKm?.let { cost -> String.format(Locale.getDefault(), "¥%.2f/km", cost) } ?: "成本未知"
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "行驶 %.0f km · %.1f L/100km · %s",
                        it.km,
                        it.per100Km,
                        cost,
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorAccent,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    entry.manual -> "手动补录"
                    entry.skipped -> "已跳过 · ${entry.percentBefore}% → ${entry.percentAfter}%"
                    else -> "油量 ${entry.percentBefore}% → ${entry.percentAfter}%"
                },
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorSecondary
            )
            entry.totalMileageKm?.let { mileage ->
                Text(
                    text = "总里程 ${mileage.toInt()} km",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorSecondary
                )
            }
        }
    }
}

/**
 * 最近油耗趋势卡片。
 *
 * 为什么画柱子而不是折线：柱状图在车机上手指不用对点，一眼能比高低；
 * 折线图还得给用户解释「线往上走＝更费油」。柱子高度直接就是油耗值，
 * 柱底再标日期（月/日），柱顶标数值。
 *
 * 油耗柱只取「相邻两条加油记录能算出油耗」的那些段（[FuelLedger.Segment]）——
 * 手动补录的、中途跳过的都算不出真实油耗，画上去会误导。
 *
 * 但卡片本身**永远显示**（用户 m07289 反馈「没看到趋势图」）：算不出油耗时
 * 退化成「最近加油金额」柱，并写明还差什么，而不是整块消失让人以为功能没做。
 */
@Composable
private fun FuelTrendCard(
    points: List<Pair<FuelLedger.Entry, FuelLedger.Segment>>,
    entries: List<FuelLedger.Entry>,
    dateFormat: SimpleDateFormat,
) {
    val colors = AppTheme.colors
    val dayFormat = remember(dateFormat) { SimpleDateFormat("M/d", Locale.getDefault()) }
    val hasFuelTrend = points.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近油耗趋势",
                style = AppTheme.typography.titleMedium,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (hasFuelTrend) {
                val average = points.map { it.second.per100Km }.average().toFloat()
                Text(
                    text = String.format(Locale.getDefault(), "这几段平均 %.1f L/100km", average),
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textColorSecondary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            hasFuelTrend -> {
                val values = points.map { it.second.per100Km }
                val average = values.average().toFloat()
                TrendBars(
                    values = values,
                    labels = points.map { dayFormat.format(Date(it.first.timestamp)) },
                    valueText = { String.format(Locale.getDefault(), "%.1f", it) },
                    highlightOverAverage = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "柱高＝这一段路的百公里油耗，红柱表示比这几段平均更费油。",
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textColorSecondary,
                )
            }

            entries.isEmpty() -> {
                TrendBars(
                    values = emptyList(),
                    labels = emptyList(),
                    valueText = { "" },
                    highlightOverAverage = false,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "还没有加油记录。加油后会自动提醒记录金额，记满两次就能算出百公里油耗，这里会出现油耗柱。",
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textColorSecondary,
                )
            }

            else -> {
                // 有记录但算不出油耗：先把「每次加油的金额」画出来，这张卡片不至于空着。
                val recent = entries.take(8).reversed()
                TrendBars(
                    values = recent.map { it.amount.toFloat() },
                    labels = recent.map { dayFormat.format(Date(it.timestamp)) },
                    valueText = { String.format(Locale.getDefault(), "¥%.0f", it) },
                    highlightOverAverage = false,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = fuelTrendMissingHint(entries),
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textColorAccent,
                )
            }
        }
    }
}

/**
 * 柱区。values 为空时画一排占位空框，让「还没有数据」也有个形状，
 * 不会让卡片缩成一行字。
 */
@Composable
private fun TrendBars(
    values: List<Float>,
    labels: List<String>,
    valueText: (Float) -> String,
    highlightOverAverage: Boolean,
) {
    val colors = AppTheme.colors
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    // 纵向留 15% 余量，柱子不至于顶到标题。
    val scaleMax = max * 1.15f
    val average = if (values.isEmpty()) 0f else values.average().toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (values.isEmpty()) {
            repeat(4) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(colors.glassBorderColor),
                    )
                }
            }
            return@Row
        }

        values.forEachIndexed { index, value ->
            val ratio = (value / scaleMax).coerceIn(0.02f, 1f)
            val overAverage = highlightOverAverage && value > average
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = valueText(value),
                    style = AppTheme.typography.bodyMedium,
                    color = when {
                        !highlightOverAverage -> colors.textColorSecondary
                        overAverage -> colors.textColorError
                        else -> colors.textColorSuccess
                    },
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // 用 fillMaxHeight(ratio) 撑在底部对齐的行里，柱高＝比例。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(ratio)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (overAverage) {
                                colors.textColorError.copy(alpha = 0.75f)
                            } else {
                                colors.colorBrand.copy(alpha = 0.75f)
                            }
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textColorSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 算不出百公里油耗时，告诉用户到底缺什么 —— 不要只说「暂无数据」。
 *
 * 算油耗需要相邻两条记录都带「总里程」且油量确实涨了（见 FuelLedger.consumptionByEntry）。
 * 实测这台车 0x2160f338（总里程）不一定推事件，所以先点明「几条记录里有几条带总里程」，
 * 用户一眼能看出是不是里程没读上来。
 */
private fun fuelTrendMissingHint(entries: List<FuelLedger.Entry>): String {
    val withMileage = entries.count { it.totalMileageKm != null }
    val usable = entries.count {
        !it.manual &&
            it.percentBefore in 0..100 &&
            it.percentAfter in 0..100 &&
            it.totalMileageKm != null
    }
    return when {
        withMileage < 2 ->
            "还差 ${2 - withMileage} 条带总里程的记录才能算出油耗（现在 ${entries.size} 条里有 $withMileage 条带总里程）。" +
                "上面画的是每次加油的金额。若一直不出现总里程，说明这台车没把里程报给工具箱。"
        usable < 2 ->
            "还需要再记 ${2 - usable} 次加油才能算出百公里油耗（手动补录的只记金额、不参与油耗计算）。" +
                "上面画的是每次加油的金额。"
        else ->
            "这几次之间没能算出油耗：要么油量没真正上涨，要么两次之间跑的里程不足 1 km。" +
                "上面先画每次加油的金额，再多记几次就会切成油耗柱。"
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SelectionText(text = label)
        Text(
            text = value,
            style = AppTheme.typography.headlineLarge,
            color = AppTheme.colors.textColorPrimary,
        )
    }
}

@Composable
private fun TankCapacityDialog(
    initialLiters: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var liters by remember { mutableIntStateOf(initialLiters) }
    val colors = AppTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.backgroundColor)
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "油箱容量",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "用于按油量百分比换算实际油耗",
                fontSize = 16.sp,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.height(24.dp))

            DigitWheelRow(initialValue = initialLiters, onValueChange = { liters = it })

            Spacer(Modifier.height(12.dp))
            Text(
                text = "$liters L",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorAccent,
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "取消",
                    onclickListener = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "确认",
                    onclickListener = { onConfirm(liters) },
                )
            }
        }
    }
}

@Composable
@CombinedThemePreviews
fun FuelLedgerScreenPreview() {
    PSAHelperThemePreview {
        FuelLedgerScreen(
            uiState = FuelLedgerUIState(),
            onPromptEnabledChange = {},
            onTankLitersChange = {},
            onDeleteEntry = {},
            onUpdateEntryAmount = { _, _ -> },
            onAddManualEntry = {},
        )
    }
}
