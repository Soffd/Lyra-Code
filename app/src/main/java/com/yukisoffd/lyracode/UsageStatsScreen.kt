package com.yukisoffd.lyracode

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.UsageStatisticsRepository
import com.yukisoffd.lyracode.data.UsageStatsPeriod
import com.yukisoffd.lyracode.data.UsageStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageStatsScreen(controller: ChatController) {
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(context.getString(R.string.title_usage_stats), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        context.getString(R.string.stats_description),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.action_refresh))
                }
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
                    onClick = { selectedPeriodName = period.name },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = compactNumbers,
                onClick = { compactNumbers = true },
                label = { Text(context.getString(R.string.stats_display_compact)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            FilterChip(
                selected = !compactNumbers,
                onClick = { compactNumbers = false },
                label = { Text(context.getString(R.string.stats_display_exact)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }

        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(context.getString(R.string.stats_time_range), style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary?.let { formatStatsRange(context, it) } ?: if (selectedPeriod == UsageStatsPeriod.TOTAL) context.getString(R.string.stats_all_history) else formatAnchorDate(anchorAt),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (selectedPeriod != UsageStatsPeriod.TOTAL) {
                    IconButton(onClick = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, -1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = context.getString(R.string.cd_previous_period))
                    }
                    IconButton(onClick = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, 1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = context.getString(R.string.cd_next_period))
                    }
                }
            }
            if (selectedPeriod != UsageStatsPeriod.TOTAL) {
                KimiDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(context.getString(R.string.action_select_date))
                    }
                    TextButton(onClick = { anchorAt = System.currentTimeMillis() }) {
                        Text(context.getString(R.string.action_back_to_today))
                    }
                    Text(formatAnchorDate(anchorAt), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        when {
            loading -> KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(context.getString(R.string.stats_loading), color = KimiMuted)
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
private fun UsageStatsContent(summary: UsageStatsSummary, compactNumbers: Boolean) {
    val context = LocalContext.current
    KimiSectionLabel(context.getString(R.string.stats_period_format, context.getString(summary.period.labelResId)))
    UsageMetricCard(
        icon = Icons.Default.Forum,
        title = context.getString(R.string.stats_conversation_count),
        value = formatStatsNumber(summary.conversationCount.toLong(), compactNumbers),
        description = context.getString(R.string.stats_conversation_desc),
    )
    UsageMetricCard(
        icon = Icons.AutoMirrored.Filled.Input,
        title = context.getString(R.string.stats_input_tokens),
        value = formatStatsNumber(summary.userInputTokens, compactNumbers),
        description = context.getString(R.string.stats_input_tokens_desc),
    )
    UsageMetricCard(
        icon = Icons.Default.Output,
        title = context.getString(R.string.stats_output_tokens),
        value = formatStatsNumber(summary.aiOutputTokens, compactNumbers),
        description = context.getString(R.string.stats_output_tokens_desc),
    )

    KimiCardBox {
        Text(context.getString(R.string.stats_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        KimiDivider()
        UsageDetailRow(Icons.Default.Analytics, context.getString(R.string.stats_model_requests), context.getString(R.string.stats_model_requests_format, formatStatsNumber(summary.modelRequestCount.toLong(), compactNumbers)))
        KimiDivider()
        UsageDetailRow(Icons.Default.Forum, context.getString(R.string.stats_user_messages), context.getString(R.string.stats_message_format, formatStatsNumber(summary.userMessageCount.toLong(), compactNumbers)))
        KimiDivider()
        UsageDetailRow(Icons.Default.SmartToy, context.getString(R.string.stats_ai_messages), context.getString(R.string.stats_message_format, formatStatsNumber(summary.assistantMessageCount.toLong(), compactNumbers)))
        KimiDivider()
        UsageDetailRow(Icons.Default.Build, context.getString(R.string.stats_tool_results), context.getString(R.string.stats_message_format, formatStatsNumber(summary.toolMessageCount.toLong(), compactNumbers)))
    }

    Text(
        context.getString(R.string.stats_disclaimer),
        color = KimiMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun UsageMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    description: String,
) {
    KimiCardBox {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
            Column(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UsageDetailRow(icon: ImageVector, title: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    value,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
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
            context.getString(R.string.stats_month_format, cal.get(Calendar.YEAR).toString(), (cal.get(Calendar.MONTH) + 1).toString())
        }
        UsageStatsPeriod.YEAR -> context.getString(R.string.stats_year_format, Calendar.getInstance().apply { time = start }.get(Calendar.YEAR).toString())
        UsageStatsPeriod.TOTAL -> context.getString(R.string.stats_all_history)
    }
}


