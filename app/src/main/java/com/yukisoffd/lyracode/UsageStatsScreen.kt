package com.yukisoffd.lyracode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.ModelUsageStat
import com.yukisoffd.lyracode.data.UsageStatisticsRepository
import com.yukisoffd.lyracode.data.UsageStatsPeriod
import com.yukisoffd.lyracode.data.UsageStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageStatsScreen(
    controller: ChatController,
    showAboutDialog: Boolean,
    onDismissAboutDialog: () -> Unit,
) {
    val context = LocalContext.current
    var selectedPeriodName by rememberSaveable { mutableStateOf(UsageStatsPeriod.DAY.name) }
    var anchorAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<UsageStatsSummary?>(null) }
    var compactNumbers by rememberSaveable { mutableStateOf(true) }
    val selectedPeriod = UsageStatsPeriod.valueOf(selectedPeriodName)
    val conversationRevision = controller.conversations.size
    val currentMessageRevision = controller.messages.value.size

    LaunchedEffect(selectedPeriodName, anchorAt, refreshKey, conversationRevision, currentMessageRevision) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                UsageStatisticsRepository(context, controller.usageStore()).calculate(selectedPeriod, anchorAt)
            }
        }
        result.fold(
            onSuccess = { summary = it },
            onFailure = {
                summary = null
                error = it.message.orEmpty().ifBlank { context.getString(R.string.stats_error) }
            },
        )
        loading = false
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = anchorAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        anchorAt = datePickerState.selectedDateMillis ?: anchorAt
                        showDatePicker = false
                    },
                ) {
                    Text(context.getString(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = onDismissAboutDialog,
            icon = {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
            },
            title = {
                Text(context.getString(R.string.stats_about_title))
            },
            text = {
                Text(context.getString(R.string.stats_disclaimer))
            },
            confirmButton = {
                TextButton(onClick = onDismissAboutDialog) {
                    Text(context.getString(R.string.action_close))
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatsRangeCard(
            selectedPeriod = selectedPeriod,
            summary = summary,
            anchorAt = anchorAt,
            compactNumbers = compactNumbers,
            onPeriodChange = { selectedPeriodName = it.name },
            onCompactNumbersChange = { compactNumbers = it },
            onPrevious = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, -1) },
            onNext = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, 1) },
            onSelectDate = { showDatePicker = true },
            onRefresh = { refreshKey++ },
        )

        when {
            loading -> KimiCardBox {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(context.getString(R.string.stats_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error.isNotBlank() -> KimiCardBox {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            summary != null -> UsageStatsContent(summary!!, compactNumbers)
        }
    }
}

@Composable
private fun StatsRangeCard(
    selectedPeriod: UsageStatsPeriod,
    summary: UsageStatsSummary?,
    anchorAt: Long,
    compactNumbers: Boolean,
    onPeriodChange: (UsageStatsPeriod) -> Unit,
    onCompactNumbersChange: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    context.getString(selectedPeriod.labelResId),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    summary?.let { formatStatsRange(context, it) }
                        ?: if (selectedPeriod == UsageStatsPeriod.TOTAL) {
                            context.getString(R.string.stats_all_history)
                        } else {
                            formatAnchorDate(anchorAt)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.action_refresh))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsageStatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == selectedPeriod,
                    onClick = { onPeriodChange(period) },
                    label = { Text(context.getString(period.labelResId)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedPeriod != UsageStatsPeriod.TOTAL) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = context.getString(R.string.cd_previous_period))
                }
                TextButton(onClick = onSelectDate, modifier = Modifier.weight(1f)) {
                    Text(formatAnchorDate(anchorAt), maxLines = 1)
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.ChevronRight, contentDescription = context.getString(R.string.cd_next_period))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            FilterChip(
                selected = compactNumbers,
                onClick = { onCompactNumbersChange(!compactNumbers) },
                label = {
                    Text(
                        context.getString(
                            if (compactNumbers) R.string.stats_display_compact else R.string.stats_display_exact,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun UsageStatsContent(summary: UsageStatsSummary, compactNumbers: Boolean) {
    val context = LocalContext.current
    val totalTokens = summary.userInputTokens + summary.aiOutputTokens

    KimiCardBox {
        Text(
            context.getString(R.string.stats_total_tokens),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            formatStatsNumber(totalTokens, compactNumbers),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            formatStatsRange(context, summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    KimiSectionLabel(context.getString(R.string.stats_quick_overview))
    KimiCardBox {
        StatsValueRow(
            Icons.Default.Forum,
            context.getString(R.string.stats_conversation_count),
            formatStatsNumber(summary.conversationCount.toLong(), compactNumbers),
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.SmartToy,
            context.getString(R.string.stats_total_requests),
            formatStatsNumber(summary.modelRequestCount.toLong(), compactNumbers),
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.DataUsage,
            context.getString(R.string.stats_total_tokens),
            formatStatsNumber(totalTokens, compactNumbers),
        )
    }

    StatsExpandableCard(
        icon = Icons.Default.DataUsage,
        title = context.getString(R.string.stats_token_usage),
        initiallyExpanded = true,
    ) {
        TokenUsageRow(
            icon = Icons.AutoMirrored.Filled.Input,
            title = context.getString(R.string.stats_input_tokens),
            value = summary.userInputTokens,
            total = totalTokens,
            compactNumbers = compactNumbers,
        )
        TokenUsageRow(
            icon = Icons.Default.Output,
            title = context.getString(R.string.stats_output_tokens),
            value = summary.aiOutputTokens,
            total = totalTokens,
            compactNumbers = compactNumbers,
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.DataUsage,
            context.getString(R.string.stats_total_tokens),
            formatStatsNumber(totalTokens, compactNumbers),
        )
    }

    StatsExpandableCard(
        icon = Icons.Default.SmartToy,
        title = context.getString(R.string.stats_request_breakdown),
        initiallyExpanded = false,
    ) {
        StatsValueRow(
            Icons.Default.SmartToy,
            context.getString(R.string.stats_model_requests),
            formatStatsNumber(summary.modelRequestCount.toLong(), compactNumbers),
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.Forum,
            context.getString(R.string.stats_user_messages),
            formatStatsNumber(summary.userMessageCount.toLong(), compactNumbers),
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.SmartToy,
            context.getString(R.string.stats_ai_messages),
            formatStatsNumber(summary.assistantMessageCount.toLong(), compactNumbers),
        )
        KimiDivider()
        StatsValueRow(
            Icons.Default.Build,
            context.getString(R.string.stats_tool_results),
            formatStatsNumber(summary.toolMessageCount.toLong(), compactNumbers),
        )
    }

    TopModelsCard(summary.modelUsage, compactNumbers)

}

@Composable
private fun StatsExpandableCard(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    KimiCardBox(
        modifier = Modifier.animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun StatsValueRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TokenUsageRow(
    icon: ImageVector,
    title: String,
    value: Long,
    total: Long,
    compactNumbers: Boolean,
) {
    val context = LocalContext.current
    val fraction = if (total > 0L) (value.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(formatStatsNumber(value, compactNumbers), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                context.getString(R.string.stats_share_format, (fraction * 100f).roundToInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        StatsProgressBar(fraction)
    }
}

@Composable
private fun TopModelsCard(models: List<ModelUsageStat>, compactNumbers: Boolean) {
    val context = LocalContext.current
    var showAll by rememberSaveable(models.size) { mutableStateOf(false) }
    val visibleModels = if (showAll) models else models.take(3)
    val totalTokens = models.sumOf { it.totalTokens }
    val totalRequests = models.sumOf { it.requestCount }

    StatsExpandableCard(
        icon = Icons.Default.SmartToy,
        title = context.getString(R.string.stats_top_models),
        initiallyExpanded = true,
    ) {
        if (models.isEmpty()) {
            Text(
                context.getString(R.string.stats_no_model_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            visibleModels.forEachIndexed { index, model ->
                ModelUsageRow(
                    model = model,
                    totalTokens = totalTokens,
                    totalRequests = totalRequests,
                    compactNumbers = compactNumbers,
                )
                if (index != visibleModels.lastIndex) KimiDivider()
            }
            if (models.size > 3) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        context.getString(
                            if (showAll) R.string.stats_show_less_models else R.string.stats_show_all_models,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelUsageRow(
    model: ModelUsageStat,
    totalTokens: Long,
    totalRequests: Int,
    compactNumbers: Boolean,
) {
    val context = LocalContext.current
    val fraction = when {
        totalTokens > 0L -> (model.totalTokens.toFloat() / totalTokens.toFloat()).coerceIn(0f, 1f)
        totalRequests > 0 -> (model.requestCount.toFloat() / totalRequests.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiLogoBadge(
                logoRes = modelLogoRes(model.modelName),
                fallback = model.modelName.ifBlank { context.getString(R.string.stats_unknown_model) },
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                model.modelName.ifBlank { context.getString(R.string.stats_unknown_model) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                context.getString(R.string.stats_share_format, (fraction * 100f).roundToInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        StatsProgressBar(fraction)
        Text(
            context.getString(
                R.string.stats_model_usage_format,
                formatStatsNumber(model.totalTokens, compactNumbers),
                formatStatsNumber(model.requestCount.toLong(), compactNumbers),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatsProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun formatStatsNumber(value: Long, compact: Boolean): String {
    if (!compact) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    return if (UiTextBridge.isEnglish()) formatCompactEnglishNumber(value) else formatCompactChineseNumber(value)
}

private fun formatCompactEnglishNumber(value: Long): String {
    val absValue = kotlin.math.abs(value)
    if (absValue < 1_000L) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    val units = listOf(
        1_000.0 to "K",
        1_000_000.0 to "M",
        1_000_000_000.0 to "B",
        1_000_000_000_000.0 to "T",
        1_000_000_000_000_000.0 to "P",
    )
    val (divisor, unit) = units.lastOrNull { absValue >= it.first } ?: units.first()
    val scaled = absValue / divisor
    val rounded = if (scaled < 100.0) kotlin.math.round(scaled * 10.0) / 10.0 else kotlin.math.round(scaled)
    val numberText = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
    return "${if (value < 0) "-" else ""}$numberText$unit"
}

private fun formatCompactChineseNumber(value: Long): String {
    val absValue = kotlin.math.abs(value)
    if (absValue < 10_000L) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    val units = listOf(
        10_000.0 to uiText("万"),
        100_000_000.0 to uiText("亿"),
        1_000_000_000_000.0 to uiText("万亿"),
        10_000_000_000_000_000.0 to uiText("亿亿"),
    )
    val (divisor, unit) = units.lastOrNull { absValue >= it.first } ?: units.first()
    val scaled = absValue / divisor
    val rounded = if (scaled < 100.0) kotlin.math.round(scaled * 10.0) / 10.0 else kotlin.math.round(scaled)
    val numberText = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", rounded)
    }
    return "${if (value < 0) "-" else ""}$numberText$unit"
}

private fun shiftAnchor(anchorAt: Long, period: UsageStatsPeriod, amount: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = anchorAt
        when (period) {
            UsageStatsPeriod.DAY -> add(Calendar.DAY_OF_YEAR, amount)
            UsageStatsPeriod.WEEK -> add(Calendar.WEEK_OF_YEAR, amount)
            UsageStatsPeriod.MONTH -> add(Calendar.MONTH, amount)
            UsageStatsPeriod.YEAR -> add(Calendar.YEAR, amount)
            UsageStatsPeriod.TOTAL -> Unit
        }
    }.timeInMillis
}

private fun formatAnchorDate(anchorAt: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(anchorAt))
}

private fun formatStatsRange(context: android.content.Context, summary: UsageStatsSummary): String {
    if (summary.period == UsageStatsPeriod.TOTAL) return context.getString(R.string.stats_all_history)
    val start = Date(summary.startAt)
    val endInclusive = Date((summary.endAt - 1L).coerceAtLeast(summary.startAt))
    return when (summary.period) {
        UsageStatsPeriod.DAY -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(start)
        UsageStatsPeriod.WEEK -> {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            context.getString(R.string.stats_date_range_format, formatter.format(start), formatter.format(endInclusive))
        }
        UsageStatsPeriod.MONTH -> {
            val cal = Calendar.getInstance().apply { time = start }
            context.getString(
                R.string.stats_month_format,
                cal.get(Calendar.YEAR).toString(),
                (cal.get(Calendar.MONTH) + 1).toString(),
            )
        }
        UsageStatsPeriod.YEAR -> context.getString(
            R.string.stats_year_format,
            Calendar.getInstance().apply { time = start }.get(Calendar.YEAR).toString(),
        )
        UsageStatsPeriod.TOTAL -> context.getString(R.string.stats_all_history)
    }
}
