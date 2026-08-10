package com.yukisoffd.lyracode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.tasks.DownloadTask
import com.yukisoffd.lyracode.tasks.DownloadTaskManager
import com.yukisoffd.lyracode.tasks.DownloadTaskStatus
import com.yukisoffd.lyracode.tasks.ScheduledTask
import com.yukisoffd.lyracode.tasks.ScheduledTaskManager
import com.yukisoffd.lyracode.tasks.ScheduledTaskStatus
import com.yukisoffd.lyracode.tasks.ScheduledTaskType
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
internal fun TaskScreen(
    settings: AppSettings,
    downloadTaskManager: DownloadTaskManager,
    scheduledTaskManager: ScheduledTaskManager,
) {
    val context = LocalContext.current
    val downloads by downloadTaskManager.tasks.collectAsState()
    val scheduledTasks by scheduledTaskManager.tasks.collectAsState()
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var detailTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(
            settings.taskCompletionNotificationsEnabled &&
                (
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ),
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsEnabled = granted
        settings.taskCompletionNotificationsEnabled = granted
    }
    if (showTaskEditor) {
        ScheduledTaskEditorDialog(
            initial = editingTask,
            profiles = settings.profiles(),
            onDismiss = { showTaskEditor = false },
            onSave = {
                scheduledTaskManager.save(it)
                showTaskEditor = false
            },
        )
    }
    detailTask?.let { task ->
        ScheduledTaskDetailDialog(
            task = task,
            onDismiss = { detailTask = null },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            KimiSectionLabel(uiText(R.string.ui_task_notifications))
            KimiCardBox {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(uiText(R.string.notification_channel_name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiText(R.string.ui_send_system_notifications_when_downloads_or_scheduled_tasks_complete),
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                notificationsEnabled = false
                                settings.taskCompletionNotificationsEnabled = false
                            } else if (
                                Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationsEnabled = true
                                settings.taskCompletionNotificationsEnabled = true
                            }
                        },
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KimiSectionLabel(uiText(R.string.tool_manage_tasks))
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        editingTask = null
                        showTaskEditor = true
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = uiText(R.string.ui_add_scheduled_task),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (scheduledTasks.isEmpty()) {
            item {
                KimiCardBox {
                    Text(uiText(R.string.ui_no_scheduled_tasks), style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiText(R.string.ui_add_tasks_manually_here_or_ask_the_ai_to),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(scheduledTasks, key = { it.id }) { task ->
                ScheduledTaskCard(
                    task = task,
                    onToggle = { scheduledTaskManager.setEnabled(task.id, it) },
                    onOpenDetails = { detailTask = task },
                    onEdit = {
                        editingTask = task
                        showTaskEditor = true
                    },
                    onDelete = { scheduledTaskManager.delete(task.id) },
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KimiSectionLabel(uiText(R.string.ui_download_tasks))
                Spacer(Modifier.weight(1f))
                if (downloads.any { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.FAILED }) {
                    IconButton(onClick = downloadTaskManager::clearFinished) {
                        Icon(Icons.Default.ClearAll, contentDescription = uiText(R.string.ui_clear_finished_downloads))
                    }
                }
            }
        }
        if (downloads.isEmpty()) {
            item {
                KimiCardBox {
                    Text(uiText(R.string.ui_no_download_tasks), style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiText(R.string.ui_when_the_ai_uses_the_download_tool_progress_speed),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(downloads, key = { it.id }) { task -> DownloadTaskCard(task) }
        }
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    onToggle: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(uiText(R.string.ui_delete_scheduled_task)) },
            text = { Text(uiText(R.string.ui_delete_1_s, task.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(uiText(R.string.file_action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(uiText(R.string.action_cancel)) } },
        )
    }
    val detail = task.detailText()
    KimiCardBox(
        modifier = Modifier.clickable(enabled = detail.isNotBlank(), onClick = onOpenDetails),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (task.status) {
                    ScheduledTaskStatus.RUNNING -> Icons.Default.HourglassTop
                    ScheduledTaskStatus.FAILED -> Icons.Default.Error
                    ScheduledTaskStatus.COMPLETED -> Icons.Default.CheckCircle
                    ScheduledTaskStatus.IDLE -> Icons.Default.Schedule
                },
                contentDescription = null,
                tint = if (task.status == ScheduledTaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(task.prompt, color = KimiMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    "${scheduleLabel(task)} · ${task.model}",
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (task.nextRunAt > 0L) {
                    Text(uiText(R.string.ui_next_run_1_s, formatTaskTime(task.nextRunAt)), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (task.lastRunAt > 0L) {
                    Text(
                        when (task.status) {
                            ScheduledTaskStatus.FAILED -> uiText(R.string.ui_last_failure) + task.error.ifBlank { uiText(R.string.label_unknown_error) }
                            ScheduledTaskStatus.RUNNING -> task.error.ifBlank { uiText(R.string.ui_running) }
                            else -> uiText(R.string.ui_last_result) + task.result.ifBlank { uiText(R.string.ui_completed) }
                        },
                        color = if (task.status == ScheduledTaskStatus.FAILED) MaterialTheme.colorScheme.error else KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.length > 160) {
                        Text(
                            uiText(R.string.ui_tap_to_view_full_result),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = task.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = uiText(R.string.ui_edit)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = uiText(R.string.file_action_delete)) }
            }
        }
    }
}

@Composable
private fun ScheduledTaskDetailDialog(
    task: ScheduledTask,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        uiText(R.string.ui_status_1_s, taskStatusLabel(task.status)) +
                            uiText(R.string.task_schedule_format, scheduleLabel(task)) +
                            uiText(R.string.task_model_format, task.model) +
                            if (task.lastRunAt > 0L) uiText(R.string.task_last_run_format, formatTaskTime(task.lastRunAt)) else uiText(R.string.ui_not_run_yet),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (task.result.isNotBlank()) {
                    item {
                        Text(uiText(R.string.ui_output), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(task.result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (task.error.isNotBlank()) {
                    item {
                        Text(uiText(R.string.ui_error), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(task.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(uiText(R.string.cd_close)) }
        },
    )
}

@Composable
private fun ScheduledTaskEditorDialog(
    initial: ScheduledTask?,
    profiles: List<ApiProfile>,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit,
) {
    val defaultProfile = profiles.first()
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var prompt by remember { mutableStateOf(initial?.prompt.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: ScheduledTaskType.ONCE) }
    var runAt by remember {
        mutableStateOf(
            initial?.runAtMillis?.takeIf { it > 0L }?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            }.orEmpty(),
        )
    }
    var time by remember { mutableStateOf(String.format(Locale.US, "%02d:%02d", initial?.hour ?: 9, initial?.minute ?: 0)) }
    var dayValue by remember {
        mutableStateOf(
            when (type) {
                ScheduledTaskType.WEEKLY -> (initial?.dayOfWeek ?: 1).toString()
                ScheduledTaskType.MONTHLY -> (initial?.dayOfMonth ?: 1).toString()
                else -> "1"
            },
        )
    }
    var selectedProfileId by remember { mutableStateOf(initial?.profileId ?: defaultProfile.id) }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: defaultProfile
    var model by remember { mutableStateOf(initial?.model.orEmpty().ifBlank { selectedProfile.selectedModel }) }
    var typeMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) uiText(R.string.ui_add_scheduled_task) else uiText(R.string.ui_edit_scheduled_task)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text(uiText(R.string.ui_task_name)) }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(
                        prompt,
                        { prompt = it },
                        label = { Text(uiText(R.string.ui_task_description)) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Column {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(uiText(R.string.ui_frequency_1_s, typeLabel(type)))
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            ScheduledTaskType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(typeLabel(option)) },
                                    onClick = {
                                        type = option
                                        dayValue = "1"
                                        typeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (type == ScheduledTaskType.ONCE) {
                    item {
                        OutlinedTextField(
                            runAt,
                            { runAt = it },
                            label = { Text(uiText(R.string.ui_run_time)) },
                            supportingText = { Text(uiText(R.string.ui_format_yyyy_mm_dd_hh_mm)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            time,
                            { time = it },
                            label = { Text(uiText(R.string.ui_run_time)) },
                            supportingText = { Text(uiText(R.string.ui_format_hh_mm)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (type == ScheduledTaskType.WEEKLY || type == ScheduledTaskType.MONTHLY) {
                        item {
                            OutlinedTextField(
                                dayValue,
                                { dayValue = it.filter(Char::isDigit) },
                                label = { Text(if (type == ScheduledTaskType.WEEKLY) uiText(R.string.ui_weekday_1_mon_7_sun) else uiText(R.string.ui_day_of_month_1_31)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Column {
                        OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(uiText(R.string.ui_provider_1_s, selectedProfile.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        selectedProfileId = profile.id
                                        if (model.isBlank() || model == selectedProfile.selectedModel) model = profile.selectedModel
                                        profileMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        OutlinedTextField(model, { model = it }, label = { Text(uiText(R.string.ui_execution_model)) }, modifier = Modifier.fillMaxWidth())
                        if (selectedProfile.enabledModels.isNotEmpty()) {
                            TextButton(onClick = { modelMenu = true }) { Text(uiText(R.string.ui_choose_from_saved_models)) }
                            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                selectedProfile.enabledModels.forEach { savedModel ->
                                    DropdownMenuItem(
                                        text = { Text(savedModel) },
                                        onClick = {
                                            model = savedModel
                                            modelMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (validationError.isNotBlank()) {
                    item { Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runCatching {
                        require(prompt.isNotBlank()) { uiText(R.string.ui_task_description_cannot_be_empty) }
                        val timeParts = time.split(":")
                        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 9
                        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                        val runAtMillis = if (type == ScheduledTaskType.ONCE) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }
                                .parse(runAt)?.time ?: kotlin.error(uiText(R.string.ui_invalid_run_time_format))
                        } else {
                            initial?.runAtMillis ?: 0L
                        }
                        if (type == ScheduledTaskType.ONCE) {
                            require(runAtMillis > System.currentTimeMillis()) { uiText(R.string.ui_one_time_task_must_be_scheduled_in_the_future) }
                        }
                        require(hour in 0..23 && minute in 0..59) { uiText(R.string.ui_invalid_run_time_format) }
                        if (type == ScheduledTaskType.WEEKLY) {
                            require((dayValue.toIntOrNull() ?: 0) in 1..7) { uiText(R.string.ui_weekday_must_be_1_to_7) }
                        }
                        if (type == ScheduledTaskType.MONTHLY) {
                            require((dayValue.toIntOrNull() ?: 0) in 1..31) { uiText(R.string.ui_day_of_month_must_be_1_to_31) }
                        }
                        onSave(
                            ScheduledTask(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                title = title,
                                prompt = prompt,
                                type = type,
                                hour = hour,
                                minute = minute,
                                runAtMillis = runAtMillis,
                                dayOfWeek = if (type == ScheduledTaskType.WEEKLY) dayValue.toIntOrNull() ?: 1 else initial?.dayOfWeek ?: 1,
                                dayOfMonth = if (type == ScheduledTaskType.MONTHLY) dayValue.toIntOrNull() ?: 1 else initial?.dayOfMonth ?: 1,
                                profileId = selectedProfile.id,
                                model = model.ifBlank { selectedProfile.selectedModel },
                                enabled = initial?.enabled ?: true,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                lastRunAt = initial?.lastRunAt ?: 0L,
                                finishedAt = initial?.finishedAt ?: 0L,
                                status = initial?.status ?: ScheduledTaskStatus.IDLE,
                                result = initial?.result.orEmpty(),
                                error = initial?.error.orEmpty(),
                            ),
                        )
                    }.onFailure { validationError = it.message.orEmpty().ifBlank { uiText(R.string.notice_save_failed) } }
                },
            ) { Text(uiText(R.string.file_editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText(R.string.action_cancel)) } },
    )
}

@Composable
private fun DownloadTaskCard(task: DownloadTask) {
    val progress = if (task.totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    KimiCardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (task.status) {
                    DownloadTaskStatus.COMPLETED -> Icons.Default.CheckCircle
                    DownloadTaskStatus.FAILED -> Icons.Default.Error
                    DownloadTaskStatus.QUEUED -> Icons.Default.HourglassTop
                    DownloadTaskStatus.RUNNING -> Icons.Default.CloudDownload
                },
                contentDescription = null,
                tint = if (task.status == DownloadTaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    task.path.replace('\\', '/').substringAfterLast('/').ifBlank { task.path },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(task.path, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (task.status == DownloadTaskStatus.RUNNING) {
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${formatTaskBytes(task.downloadedBytes)} / ${formatTaskTotal(task.totalBytes)}",
                            color = KimiMuted,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${formatTaskBytes(task.bytesPerSecond)}/s", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        when (task.status) {
                            DownloadTaskStatus.QUEUED -> uiText(R.string.ui_waiting_to_download)
                            DownloadTaskStatus.COMPLETED -> uiText(R.string.download_completed_size, formatTaskBytes(task.downloadedBytes))
                            DownloadTaskStatus.FAILED -> uiText(R.string.ui_failed) + task.error.ifBlank { uiText(R.string.label_unknown_error) }
                            DownloadTaskStatus.RUNNING -> ""
                        },
                        color = if (task.status == DownloadTaskStatus.FAILED) MaterialTheme.colorScheme.error else KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(formatTaskTime(task.startedAt), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun typeLabel(type: ScheduledTaskType): String = when (type) {
    ScheduledTaskType.ONCE -> uiText(R.string.ui_once)
    ScheduledTaskType.DAILY -> uiText(R.string.ui_daily)
    ScheduledTaskType.WEEKLY -> uiText(R.string.ui_weekly)
    ScheduledTaskType.MONTHLY -> uiText(R.string.ui_monthly)
}

private fun scheduleLabel(task: ScheduledTask): String = when (task.type) {
    ScheduledTaskType.ONCE -> uiText(R.string.task_one_time_at, formatTaskTime(task.runAtMillis))
    ScheduledTaskType.DAILY -> String.format(Locale.getDefault(), uiText(R.string.ui_daily_02d_02d), task.hour, task.minute)
    ScheduledTaskType.WEEKLY -> String.format(Locale.getDefault(), uiText(R.string.ui_every_s_02d_02d), listOf(uiText(R.string.ui_mon), uiText(R.string.ui_tue), uiText(R.string.ui_wed), uiText(R.string.ui_thu), uiText(R.string.ui_fri), uiText(R.string.ui_sat), uiText(R.string.stat_period_day))[task.dayOfWeek.coerceIn(1, 7) - 1], task.hour, task.minute)
    ScheduledTaskType.MONTHLY -> String.format(Locale.getDefault(), uiText(R.string.ui_monthly_on_day_d_02d_02d), task.dayOfMonth, task.hour, task.minute)
}

private fun taskStatusLabel(status: ScheduledTaskStatus): String = when (status) {
    ScheduledTaskStatus.IDLE -> uiText(R.string.ui_waiting)
    ScheduledTaskStatus.RUNNING -> uiText(R.string.ui_running)
    ScheduledTaskStatus.COMPLETED -> uiText(R.string.ui_completed)
    ScheduledTaskStatus.FAILED -> uiText(R.string.ui_failed_2)
}

private fun ScheduledTask.detailText(): String = error.ifBlank { result }

private fun formatTaskTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(time))

private fun formatTaskTotal(bytes: Long): String = if (bytes < 0L) uiText(R.string.label_unknown_size) else formatTaskBytes(bytes)

private fun formatTaskBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unitIndex])
}


