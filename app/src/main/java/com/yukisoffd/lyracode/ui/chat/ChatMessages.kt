package com.yukisoffd.lyracode

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.ai.ChatRecord
import com.yukisoffd.lyracode.ai.TodoItem
import com.yukisoffd.lyracode.data.AppSettings
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min
import kotlin.math.max


internal fun isInternalProcessMessage(message: ChatRecord): Boolean {
    return message.role == "tool" || (message.role == "assistant" && message.content.isBlank())
}

internal data class ChatRenderItem(
    val key: String,
    val message: ChatRecord? = null,
    val process: List<ChatRecord> = emptyList(),
    val processStartedAt: Long? = null,
    val processFinishedAt: Long? = null,
)

internal fun chatRenderItems(
    messages: List<ChatRecord>,
    isStreaming: Boolean = false,
): List<ChatRenderItem> {
    val result = mutableListOf<ChatRenderItem>()
    val assistantTurn = mutableListOf<ChatRecord>()

    fun flushAssistantTurn() {
        if (assistantTurn.isEmpty()) return
        val turn = assistantTurn.toList()
        assistantTurn.clear()
        if (isStreaming) {
            turn.forEach { message ->
                if (message.role == "assistant") {
                    if (message.thinking.isNotBlank() || message.content.isBlank()) {
                        val processMessage = if (message.content.isNotBlank()) {
                            message.copy(id = -message.id, content = "")
                        } else {
                            message
                        }
                        result += ChatRenderItem(
                            key = "process-${message.id}",
                            process = listOf(processMessage),
                            processStartedAt = message.createdAt,
                            processFinishedAt = message.createdAt,
                        )
                    }
                    if (message.content.isNotBlank()) {
                        result += ChatRenderItem(
                            key = "message-${message.id}",
                            message = message.copy(thinking = ""),
                        )
                    }
                } else {
                    result += ChatRenderItem(
                        key = "process-${message.id}",
                        process = listOf(message),
                        processStartedAt = message.createdAt,
                        processFinishedAt = message.createdAt,
                    )
                }
            }
            return
        }
        val finalAnswerIndex = turn.indexOfLast {
            it.role == "assistant" && it.content.isNotBlank()
        }
        val process = buildList {
            turn.forEachIndexed { index, message ->
                if (index == finalAnswerIndex) {
                    if (message.thinking.isNotBlank()) {
                        add(message.copy(id = -message.id, content = ""))
                    }
                } else {
                    add(message)
                }
            }
        }
        if (process.isNotEmpty()) {
            result += ChatRenderItem(
                key = "process-${turn.first().id}",
                process = process,
                processStartedAt = turn.minOfOrNull { it.createdAt },
                processFinishedAt = turn.maxOfOrNull { it.createdAt },
            )
        }
        if (finalAnswerIndex >= 0) {
            val finalAnswer = turn[finalAnswerIndex].copy(thinking = "")
            result += ChatRenderItem("message-${finalAnswer.id}", message = finalAnswer)
        }
    }

    messages.forEach { message ->
        if (message.role == "user") {
            flushAssistantTurn()
            result += ChatRenderItem("message-${message.id}", message = message)
        } else {
            assistantTurn += message
        }
    }
    flushAssistantTurn()
    return result
}

@Composable
internal fun AgentProcessSummary(
    messages: List<ChatRecord>,
    selectionResetKey: Int,
    active: Boolean = false,
    startedAtOverride: Long? = null,
    finishedAtOverride: Long? = null,
) {
    var expanded by rememberSaveable(messages.firstOrNull()?.id ?: 0L) { mutableStateOf(false) }
    val toolCount = messages.count { it.role == "tool" }
    val thinkingCount = messages.count { it.thinking.isNotBlank() || it.role == "assistant" }
    val processKey = messages.firstOrNull()?.id ?: 0L
    val fallbackNow = remember(processKey) { System.currentTimeMillis() }
    var wasActive by rememberSaveable(processKey) { mutableStateOf(false) }
    var completedAt by rememberSaveable(processKey) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, processKey) {
        if (active) {
            wasActive = true
            completedAt = null
        } else if (wasActive && completedAt == null) {
            completedAt = System.currentTimeMillis()
        }
    }
    val startedAt = startedAtOverride ?: messages.minOfOrNull { it.createdAt } ?: fallbackNow
    val finishedAt = completedAt ?: finishedAtOverride ?: messages.maxOfOrNull { it.createdAt } ?: fallbackNow
    val collapsedText = if (expanded) {
        uiText("过程记录已展开")
    } else {
        uiText("过程记录已收起 · thinking $thinkingCount / 工具 $toolCount")
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProcessDurationHeader(
                startedAt = startedAt,
                finishedAt = finishedAt,
                active = active,
            )
            CollapsedStatusLine(
                text = collapsedText,
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    messages.forEach { MessageCard(it, selectionResetKey, inProcessRecord = true) }
                }
            }
        }
    }
}

@Composable
internal fun ProcessDurationHeader(
    startedAt: Long,
    finishedAt: Long?,
    active: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (active) uiText("任务处理中 · ") else uiText("任务耗时 · "),
            color = KimiMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        ProcessDurationText(
            startedAt = startedAt,
            finishedAt = finishedAt,
            active = active,
        )
    }
}

@Composable
internal fun ProcessDurationText(
    startedAt: Long,
    finishedAt: Long?,
    active: Boolean,
) {
    var now by remember(startedAt, finishedAt, active) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, finishedAt, active) {
        if (active) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }
    val endAt = if (active) now else (finishedAt ?: now)
    Text(
        text = formatProcessDuration((endAt - startedAt).coerceAtLeast(0L)),
        color = KimiMuted,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun ToolApprovalDialog(
    pending: PendingToolApproval,
    onApprove: (rememberConversation: Boolean) -> Unit,
    onReject: (feedback: String) -> Unit,
) {
    var feedback by rememberSaveable(pending.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(uiText("确认工具调用")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText(pending.request.summary), style = MaterialTheme.typography.titleSmall)
                Text(uiText(pending.request.risk), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        pending.request.arguments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(uiText("拒绝时给 AI 的修改要求")) },
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pending.request.toolName != "send_email") {
                    TextButton(
                        onClick = { onApprove(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(uiText("本会话无需确认"))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onReject(feedback) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(uiText("不同意"))
                    }
                    Button(
                        onClick = { onApprove(false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(uiText("同意"))
                    }
                }
            }
        },
    )
}

@Composable
internal fun UserQuestionDialog(
    pending: PendingUserQuestion,
    onActivity: () -> Unit,
    onSubmit: (selectedOptions: List<String>, freeText: String) -> Unit,
) {
    var selectedOptions by remember(pending.id) { mutableStateOf(emptyList<String>()) }
    var freeText by rememberSaveable(pending.id) { mutableStateOf("") }
    var confirming by rememberSaveable(pending.id) { mutableStateOf(false) }
    val canSubmit = selectedOptions.isNotEmpty() || freeText.isNotBlank()
    AlertDialog(
        onDismissRequest = { onActivity() },
        title = { Text(pending.request.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(pending.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed }) onActivity()
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(pending.request.question, style = MaterialTheme.typography.bodyLarge)
                if (pending.request.options.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ask_user_multi_select_hint),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pending.request.options.forEach { option ->
                        val selected = option in selectedOptions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                                )
                                .clickable {
                                    onActivity()
                                    selectedOptions = if (selected) {
                                        selectedOptions - option
                                    } else {
                                        selectedOptions + option
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Spacer(Modifier.width(6.dp))
                            Text(option, modifier = Modifier.weight(1f))
                        }
                    }
                }
                OutlinedTextField(
                    value = freeText,
                    onValueChange = {
                        onActivity()
                        freeText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text(stringResource(R.string.ask_user_free_text_label)) },
                    supportingText = { Text(stringResource(R.string.ask_user_free_text_support)) },
                )
                Text(
                    stringResource(R.string.ask_user_idle_timeout_hint),
                    color = KimiMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    onActivity()
                    confirming = true
                },
            ) {
                Text(stringResource(R.string.ask_user_submit))
            }
        },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = {
                onActivity()
                confirming = false
            },
            title = { Text(stringResource(R.string.ask_user_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ask_user_confirm_body))
                    if (selectedOptions.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.ask_user_confirm_selected,
                                selectedOptions.joinToString("、"),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (freeText.isNotBlank()) {
                        Text(
                            stringResource(R.string.ask_user_confirm_extra, freeText.trim()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onActivity()
                        confirming = false
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onActivity()
                        onSubmit(selectedOptions, freeText)
                    },
                ) {
                    Text(stringResource(R.string.ask_user_confirm_submit))
                }
            },
        )
    }
}

@Composable
internal fun TodoProgressPanel(settings: AppSettings, conversationId: Long, items: List<TodoItem>) {
    if (items.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val signature = remember(items) { items.joinToString("|") { "${it.id}:${it.status}:${it.text}:${it.note}" } }
    var hiddenSignature by remember(conversationId) { mutableStateOf(settings.hiddenTodoSignature(conversationId)) }
    var dragX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedDragX by animateFloatAsState(targetValue = dragX, label = "todo-panel-drag")
    val panelDragX = if (isDragging) dragX else animatedDragX
    val completed = items.count { it.status == "completed" }
    AnimatedVisibility(visible = hiddenSignature != signature, enter = fadeIn(), exit = fadeOut()) {
        Card(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = panelDragX.coerceAtMost(0f)
                    alpha = 1f - ((-translationX) / 420f).coerceIn(0f, 0.45f)
                }
                .pointerInput(signature) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (dragX < -120f) {
                                hiddenSignature = signature
                                settings.setHiddenTodoSignature(conversationId, signature)
                            }
                            dragX = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragX = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragX = (dragX + dragAmount.x).coerceIn(-420f, 0f)
                    }
                },
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TODO $completed/${items.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) uiText("收纳") else uiText("展开"))
                    }
                }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(todoStatusMark(item.status), color = todoStatusColor(item.status), style = MaterialTheme.typography.bodyMedium)
                                Column(Modifier.weight(1f)) {
                                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                    if (item.note.isNotBlank()) {
                                        Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

internal fun todoStatusMark(status: String): String = when (status) {
    "running" -> "..."
    "completed" -> "✓"
    "blocked" -> "!"
    else -> "○"
}

@Composable
internal fun todoStatusColor(status: String): Color = when (status) {
    "running" -> MaterialTheme.colorScheme.primary
    "completed" -> Color(0xFF188038)
    "blocked" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal data class ConversationFileChange(
    val messageId: Long,
    val index: Int,
    val change: FileChangeView,
) {
    val key: String = "$messageId:$index:${change.path}"
}

@Composable
internal fun ConversationChangesPanel(settings: AppSettings, conversationId: Long, messages: List<ChatRecord>) {
    val events = remember(messages) {
        messages.flatMap { message ->
            parseFileChanges(message.content).mapIndexed { index, change ->
                ConversationFileChange(message.id, index, change)
            }
        }.takeLast(20)
      }
      if (events.isEmpty()) return
      val signature = remember(events) { events.joinToString("|") { it.key } }
      var hiddenSignature by remember(conversationId) { mutableStateOf(settings.hiddenFileChangesSignature(conversationId)) }
      var dragX by remember { mutableStateOf(0f) }
      var isDragging by remember { mutableStateOf(false) }
      val animatedDragX by animateFloatAsState(targetValue = dragX, label = "changes-panel-drag")
      val panelDragX = if (isDragging) dragX else animatedDragX

      var expanded by rememberSaveable { mutableStateOf(true) }
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val totalAdded = events.sumOf { it.change.added }
    val totalRemoved = events.sumOf { it.change.removed }

      AnimatedVisibility(visible = hiddenSignature != signature, enter = fadeIn(), exit = fadeOut()) {
      Card(
          Modifier
              .fillMaxWidth()
              .graphicsLayer {
                  translationX = panelDragX.coerceAtMost(0f)
                  alpha = 1f - ((-translationX) / 420f).coerceIn(0f, 0.45f)
              }
              .pointerInput(signature) {
                  detectDragGestures(
                      onDragStart = { isDragging = true },
                      onDragEnd = {
                          isDragging = false
                          if (dragX < -120f) {
                              hiddenSignature = signature
                              settings.setHiddenFileChangesSignature(conversationId, signature)
                          }
                          dragX = 0f
                      },
                      onDragCancel = {
                          isDragging = false
                          dragX = 0f
                      },
                  ) { change, dragAmount ->
                      change.consume()
                      dragX = (dragX + dragAmount.x).coerceIn(-420f, 0f)
                  }
              },
      ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${uiText("文件变更")} ${events.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("+$totalAdded", color = Color(0xFF188038), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text("-$totalRemoved", color = Color(0xFFD93025), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) uiText("收纳") else uiText("展开"))
                }
            }
            AnimatedVisibility(expanded) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(events.asReversed(), key = { it.key }) { event ->
                        val change = event.change
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openedKey = if (openedKey == event.key) null else event.key
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(fileNameForDisplay(change.path), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(change.path, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                }
                                Text("+${change.added}", color = Color(0xFF188038), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                                Text("-${change.removed}", color = Color(0xFFD93025), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                if (openedKey == event.key) uiText("收起变更详情") else uiText("点击审视变更前后代码"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            AnimatedVisibility(openedKey == event.key) {
                                FileChangeDetail(change)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun ModelToolbar(controller: ChatController) {
    var profileExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val profiles = controller.profiles.toList()
    val profile = profiles.firstOrNull { it.id == controller.activeProfileId.value } ?: profiles.firstOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.9f)) {
            OutlinedButton(onClick = { profileExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(profile?.name ?: uiText("平台"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                profiles.forEach {
                    DropdownMenuItem(text = { Text(it.name) }, onClick = {
                        profileExpanded = false
                        controller.selectProfile(it.id)
                    })
                }
            }
        }
        Box(Modifier.weight(1.1f)) {
            OutlinedButton(onClick = { modelExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(controller.activeModel.value.ifBlank { uiText("模型") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                profile?.enabledModels.orEmpty().forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = {
                        modelExpanded = false
                        controller.selectModel(model)
                    })
                }
            }
        }
    }
}


internal fun formatTokensPerSecond(value: Double): String {
    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return ""
    val text = if (value >= 10.0) "%.0f".format(Locale.US, value) else "%.1f".format(Locale.US, value)
    return "$text tok/s"
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageCard(
    message: ChatRecord,
    selectionResetKey: Int = 0,
    inProcessRecord: Boolean = false,
    streamingAnimationMode: String = AppSettings.STREAMING_ANIMATION_TYPEWRITER,
    isStreaming: Boolean = false,
    onEditAndRegenerate: ((Long, String) -> Unit)? = null,
    onCreateBranch: ((Long) -> Unit)? = null,
) {
    val visibleContent = displayMessageContent(message)
    val mediaPreviews = remember(message.content) { uploadedMediaPreviews(message.content) }
    val filePreviews = remember(message.content) { uploadedFilePreviews(message.content) }
    val workspaceReferences = remember(message.content) { workspaceReferencePreviews(message.content) }
    val container = when (message.role) {
        "user" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        "tool" -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        else -> Color.Transparent
    }
    val contentColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val clipboard = LocalClipboardManager.current
    var showThinking by rememberSaveable(message.id) { mutableStateOf(false) }
    var showToolResult by rememberSaveable(message.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var localSelectionResetKey by rememberSaveable(message.id) { mutableStateOf(0) }
    var editDialogOpen by rememberSaveable(message.id) { mutableStateOf(false) }
    var editText by rememberSaveable(message.id) { mutableStateOf(message.content) }
    val isUser = message.role == "user"
    val shouldRenderBubble = !isUser ||
        visibleContent.isNotBlank() ||
        message.thinking.isNotBlank()
    if (editDialogOpen) {
        AlertDialog(
            onDismissRequest = { editDialogOpen = false },
            title = { Text(uiText("编辑并重新生成")) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    minLines = 5,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editDialogOpen = false
                        onEditAndRegenerate?.invoke(message.id, editText)
                    },
                ) {
                    Text(uiText("重新生成"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogOpen = false }) {
                    Text(uiText("取消"))
                }
            },
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isUser && workspaceReferences.isNotEmpty()) {
                WorkspaceReferenceCardColumn(workspaceReferences)
            }
            if (isUser && mediaPreviews.isNotEmpty()) {
                UploadedMediaGrid(mediaPreviews)
            }
            if (isUser && filePreviews.isNotEmpty()) {
                UploadedFileCardColumn(filePreviews)
            }
            if (shouldRenderBubble) {
                Box {
                    val cardModifier = if (isUser) {
                        Modifier
                            .widthIn(max = 320.dp)
                            .combinedClickable(
                                onClick = { localSelectionResetKey++ },
                                onLongClick = {
                                    editText = message.content
                                    menuExpanded = true
                                },
                            )
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .clickable { localSelectionResetKey++ }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = container),
                        shape = if (isUser) RoundedCornerShape(22.dp) else RoundedCornerShape(18.dp),
                        border = if (message.role == "assistant") null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                        modifier = cardModifier,
                    ) {
                        val compactToolResult = inProcessRecord && message.role == "tool"
                        Column(
                            Modifier.padding(
                                horizontal = when {
                                    isUser -> 16.dp
                                    compactToolResult -> 2.dp
                                    else -> 6.dp
                                },
                                vertical = when {
                                    isUser -> 9.dp
                                    compactToolResult -> 2.dp
                                    else -> 6.dp
                                },
                            ),
                            verticalArrangement = Arrangement.spacedBy(
                                when {
                                    compactToolResult -> 2.dp
                                    isUser || message.role == "assistant" -> 4.dp
                                    else -> 8.dp
                                },
                            ),
                        ) {
                            if (!isUser && message.role != "assistant" && !compactToolResult) {
                                Text(uiText("工具结果"), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            if (message.thinking.isNotBlank()) {
                                CollapsedStatusLine(
                                    text = if (showThinking) uiText("思考详情已展开") else if (message.content.isBlank()) "thinking..." else uiText("思考完毕"),
                                    expanded = showThinking,
                                    onClick = { showThinking = !showThinking },
                                )
                                AnimatedVisibility(showThinking) {
                                    key(selectionResetKey) {
                                        SelectionContainer {
                                            Text(
                                                message.thinking,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = contentColor,
                                            )
                                        }
                                    }
                                }
                            }
                            if (message.role == "tool") {
                                ToolResultContent(
                                    content = message.content,
                                    toolName = message.toolName,
                                    toolInput = message.toolInput,
                                    expanded = showToolResult,
                                    onToggle = { showToolResult = !showToolResult },
                                    compact = compactToolResult,
                                )
                            } else {
                                if (visibleContent.isNotBlank()) {
                                    key(selectionResetKey) {
                                        if (isUser) {
                                            Text(visibleContent, color = contentColor, style = MaterialTheme.typography.bodyLarge)
                                        } else {
                                            key(localSelectionResetKey) {
                                                SelectionContainer {
                                                    StreamingAssistantContent(visibleContent, isStreaming, streamingAnimationMode)
                                                }
                                            }
                                        }
                                    }
                                    if (message.role == "assistant" && !inProcessRecord) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                formatTokensPerSecond(message.tokensPerSecond),
                                                color = KimiMuted,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Spacer(Modifier.weight(1f))
                                            if (!isStreaming) {
                                                Box {
                                                    IconButton(
                                                        onClick = { menuExpanded = true },
                                                        modifier = Modifier.size(32.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Default.MoreVert,
                                                            contentDescription = uiText("更多操作"),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                    MessageActionsDropdown(
                                                        expanded = menuExpanded,
                                                        onDismiss = { menuExpanded = false },
                                                        onCopy = {
                                                            clipboard.setText(AnnotatedString(message.content))
                                                            menuExpanded = false
                                                        },
                                                        onCreateBranch = onCreateBranch?.let { createBranch ->
                                                            {
                                                                menuExpanded = false
                                                                createBranch(message.id)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (message.role == "assistant" && !inProcessRecord) {
                                    Text(uiText("正在组织输出..."), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (isUser && !inProcessRecord) {
                        MessageActionsDropdown(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                menuExpanded = false
                            },
                            onCreateBranch = onCreateBranch?.let { createBranch ->
                                {
                                    menuExpanded = false
                                    createBranch(message.id)
                                }
                            },
                            onEditAndRegenerate = onEditAndRegenerate?.let {
                                {
                                    editText = message.content
                                    menuExpanded = false
                                    editDialogOpen = true
                                }
                            },
                            onRegenerate = onEditAndRegenerate?.let { regenerate ->
                                {
                                    menuExpanded = false
                                    regenerate(message.id, message.content)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onCreateBranch: (() -> Unit)? = null,
    onEditAndRegenerate: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(uiText("复制")) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = onCopy,
        )
        onCreateBranch?.let { createBranch ->
            DropdownMenuItem(
                text = { Text(uiText("从此处创建分支")) },
                leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                onClick = createBranch,
            )
        }
        onEditAndRegenerate?.let { editAndRegenerate ->
            DropdownMenuItem(
                text = { Text(uiText("修改并重新生成")) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = editAndRegenerate,
            )
        }
        onRegenerate?.let { regenerate ->
            DropdownMenuItem(
                text = { Text(uiText("重新生成")) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = regenerate,
            )
        }
    }
}

@Composable
internal fun StreamingAssistantContent(content: String, isStreaming: Boolean, mode: String) {
    val normalizedMode = AppSettings.normalizeStreamingAnimationMode(mode)
    val latestContent by rememberUpdatedState(content)
    val fadeMode = normalizedMode == AppSettings.STREAMING_ANIMATION_FADE
    var renderedContent by remember {
        mutableStateOf(if (isStreaming && !fadeMode) "" else content)
    }
    val opaquePosition = remember {
        Animatable(if (isStreaming && fadeMode) 0f else content.length.toFloat())
    }

    LaunchedEffect(content, isStreaming, normalizedMode) {
        if (!fadeMode) return@LaunchedEffect

        val targetPosition = content.length.toFloat()
        val contentWasReplaced = !content.startsWith(renderedContent)
        renderedContent = content
        when {
            contentWasReplaced -> opaquePosition.snapTo((targetPosition - 72f).coerceAtLeast(0f))
            opaquePosition.value > targetPosition -> opaquePosition.snapTo(targetPosition)
        }
        // Render every received character immediately; only the newest tail fades in.
        opaquePosition.animateTo(
            targetValue = targetPosition,
            animationSpec = tween(durationMillis = 500),
        )
    }

    LaunchedEffect(isStreaming, normalizedMode) {
        if (fadeMode) return@LaunchedEffect
        if (!isStreaming) {
            renderedContent = latestContent
            return@LaunchedEffect
        }

        while (true) {
            val target = latestContent
            if (!target.startsWith(renderedContent)) {
                renderedContent = ""
            }
            val pending = target.length - renderedContent.length
            if (pending > 0) {
                renderedContent = target.take(renderedContent.length + 1)
            }
            delay(12L)
        }
    }
    val streamingFade = if (
        fadeMode && opaquePosition.value < renderedContent.length
    ) {
        StreamingTextFade(renderedContent.length, opaquePosition.value)
    } else {
        null
    }
    RichMarkdownContent(
        renderedContent,
        streamingFade = streamingFade,
    )
}
internal fun formatProcessDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> uiText("${hours}小时${minutes}分${seconds}秒")
        minutes > 0L -> uiText("${minutes}分${seconds}秒")
        else -> uiText("${seconds}秒")
    }
}

@Composable
internal fun CollapsedStatusLine(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KimiPillShape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) uiText("收起") else uiText("展开"),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ContinueInterruptedRow(onContinue: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
        KimiChip(uiText("继续对话"), onClick = onContinue)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
    }
}

