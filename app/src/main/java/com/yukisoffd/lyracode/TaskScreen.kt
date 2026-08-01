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
            KimiSectionLabel(uiText("任务通知"))
            KimiCardBox {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(uiText("任务完成通知"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiText("下载和定时任务完成或失败后推送系统通知"),
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
                KimiSectionLabel(uiText("定时任务"))
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        editingTask = null
                        showTaskEditor = true
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = uiText("添加定时任务"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (scheduledTasks.isEmpty()) {
            item {
                KimiCardBox {
                    Text(uiText("暂无定时任务"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiText("可在这里手动添加，或在对话中用自然语言让 AI 创建。系统会通过 WorkManager 延期唤醒执行，受省电策略影响时不保证秒级准时。"),
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
                KimiSectionLabel(uiText("下载任务"))
                Spacer(Modifier.weight(1f))
                if (downloads.any { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.FAILED }) {
                    IconButton(onClick = downloadTaskManager::clearFinished) {
                        Icon(Icons.Default.ClearAll, contentDescription = uiText("清除已完成下载"))
                    }
                }
            }
        }
        if (downloads.isEmpty()) {
            item {
                KimiCardBox {
                    Text(uiText("暂无下载任务"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiText("AI 使用下载文件工具后，可在这里查看进度、速度和结果。"),
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
            title = { Text(uiText("删除定时任务")) },
            text = { Text(uiText("确定删除“${task.title}”吗？")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(uiText("删除")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(uiText("取消")) } },
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
                    Text(uiText("下次执行：${formatTaskTime(task.nextRunAt)}"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (task.lastRunAt > 0L) {
                    Text(
                        when (task.status) {
                            ScheduledTaskStatus.FAILED -> uiText("最近失败：") + task.error.ifBlank { uiText("未知错误") }
                            ScheduledTaskStatus.RUNNING -> task.error.ifBlank { uiText("正在执行") }
                            else -> uiText("最近结果：") + task.result.ifBlank { uiText("已完成") }
                        },
                        color = if (task.status == ScheduledTaskStatus.FAILED) MaterialTheme.colorScheme.error else KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.length > 160) {
                        Text(
                            uiText("点击查看完整结果"),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = task.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = uiText("编辑")) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = uiText("删除")) }
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
                        uiText("状态：${taskStatusLabel(task.status)}\n") +
                            uiText("频率：${scheduleLabel(task)}\n") +
                            uiText("模型：${task.model}\n") +
                            if (task.lastRunAt > 0L) uiText("最近执行：${formatTaskTime(task.lastRunAt)}") else uiText("尚未执行"),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (task.result.isNotBlank()) {
                    item {
                        Text(uiText("输出结果"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(task.result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (task.error.isNotBlank()) {
                    item {
                        Text(uiText("错误信息"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(task.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(uiText("关闭")) }
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
        title = { Text(if (initial == null) uiText("添加定时任务") else uiText("编辑定时任务")) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text(uiText("任务名称")) }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(
                        prompt,
                        { prompt = it },
                        label = { Text(uiText("任务说明")) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Column {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(uiText("执行频率：${typeLabel(type)}"))
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
                            label = { Text(uiText("执行时间")) },
                            supportingText = { Text(uiText("格式：yyyy-MM-dd HH:mm")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        OutlinedTextField(
                            time,
                            { time = it },
                            label = { Text(uiText("执行时间")) },
                            supportingText = { Text(uiText("格式：HH:mm")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (type == ScheduledTaskType.WEEKLY || type == ScheduledTaskType.MONTHLY) {
                        item {
                            OutlinedTextField(
                                dayValue,
                                { dayValue = it.filter(Char::isDigit) },
                                label = { Text(if (type == ScheduledTaskType.WEEKLY) uiText("星期（1=周一，7=周日）") else uiText("每月日期（1-31）")) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    Column {
                        OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(uiText("服务商：${selectedProfile.name}"), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        OutlinedTextField(model, { model = it }, label = { Text(uiText("执行模型")) }, modifier = Modifier.fillMaxWidth())
                        if (selectedProfile.enabledModels.isNotEmpty()) {
                            TextButton(onClick = { modelMenu = true }) { Text(uiText("从预保存模型选择")) }
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
                        require(prompt.isNotBlank()) { uiText("任务说明不能为空") }
                        val timeParts = time.split(":")
                        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 9
                        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                        val runAtMillis = if (type == ScheduledTaskType.ONCE) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }
                                .parse(runAt)?.time ?: kotlin.error(uiText("执行时间格式不正确"))
                        } else {
                            initial?.runAtMillis ?: 0L
                        }
                        if (type == ScheduledTaskType.ONCE) {
                            require(runAtMillis > System.currentTimeMillis()) { uiText("一次性任务时间必须晚于当前时间") }
                        }
                        require(hour in 0..23 && minute in 0..59) { uiText("执行时间格式不正确") }
                        if (type == ScheduledTaskType.WEEKLY) {
                            require((dayValue.toIntOrNull() ?: 0) in 1..7) { uiText("星期必须为 1 到 7") }
                        }
                        if (type == ScheduledTaskType.MONTHLY) {
                            require((dayValue.toIntOrNull() ?: 0) in 1..31) { uiText("每月日期必须为 1 到 31") }
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
                    }.onFailure { validationError = it.message.orEmpty().ifBlank { uiText("保存失败") } }
                },
            ) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
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
                            DownloadTaskStatus.QUEUED -> uiText("等待下载")
                            DownloadTaskStatus.COMPLETED -> uiText("已完成 · ${formatTaskBytes(task.downloadedBytes)}")
                            DownloadTaskStatus.FAILED -> uiText("失败：") + task.error.ifBlank { uiText("未知错误") }
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
    ScheduledTaskType.ONCE -> uiText("一次性")
    ScheduledTaskType.DAILY -> uiText("每天")
    ScheduledTaskType.WEEKLY -> uiText("每周")
    ScheduledTaskType.MONTHLY -> uiText("每月")
}

private fun scheduleLabel(task: ScheduledTask): String = when (task.type) {
    ScheduledTaskType.ONCE -> uiText("一次性 · ${formatTaskTime(task.runAtMillis)}")
    ScheduledTaskType.DAILY -> String.format(Locale.getDefault(), uiText("每天 %02d:%02d"), task.hour, task.minute)
    ScheduledTaskType.WEEKLY -> String.format(Locale.getDefault(), uiText("每周%s %02d:%02d"), listOf(uiText("一"), uiText("二"), uiText("三"), uiText("四"), uiText("五"), uiText("六"), uiText("日"))[task.dayOfWeek.coerceIn(1, 7) - 1], task.hour, task.minute)
    ScheduledTaskType.MONTHLY -> String.format(Locale.getDefault(), uiText("每月%d日 %02d:%02d"), task.dayOfMonth, task.hour, task.minute)
}

private fun taskStatusLabel(status: ScheduledTaskStatus): String = when (status) {
    ScheduledTaskStatus.IDLE -> uiText("等待执行")
    ScheduledTaskStatus.RUNNING -> uiText("正在执行")
    ScheduledTaskStatus.COMPLETED -> uiText("已完成")
    ScheduledTaskStatus.FAILED -> uiText("失败")
}

private fun ScheduledTask.detailText(): String = error.ifBlank { result }

private fun formatTaskTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(time))

private fun formatTaskTotal(bytes: Long): String = if (bytes < 0L) uiText("未知大小") else formatTaskBytes(bytes)

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


