package com.yukisoffd.lyracode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KimiDrawerContent(
    settings: AppSettings,
    pages: List<String>,
    selectedPage: Int,
    languageMode: String,
    controller: ChatController,
    nickname: String,
    avatarPath: String?,
    onProfileChanged: (String, String?) -> Unit,
    onSelectPage: (Int) -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
) {
    val context = LocalContext.current
    val conversationSnapshot = controller.conversations.toList()
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var selectedHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var actionConversation by remember { mutableStateOf<Conversation?>(null) }
    val filteredConversations = remember(conversationSnapshot, historyQuery) {
        val query = historyQuery.trim()
        if (query.isBlank()) {
            conversationSnapshot
        } else {
            conversationSnapshot.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.model.contains(query, ignoreCase = true) ||
                    it.status.contains(query, ignoreCase = true)
            }
        }
    }
    val historyLanguageKey = listOf(
        languageMode,
        context.getString(R.string.label_today),
        context.getString(R.string.date_format_year_month),
    ).joinToString("|")
    val groupedConversations = remember(filteredConversations, historyLanguageKey) {
        groupConversationsByTime(filteredConversations, context)
    }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    actionConversation?.let { conversation ->
        HistoryConversationActionsDialog(
            conversation = conversation,
            onDismiss = { actionConversation = null },
            onRename = { title ->
                controller.renameConversation(conversation.id, title)
                actionConversation = null
            },
            onPin = {
                controller.setConversationPinned(conversation.id, conversation.pinnedAt <= 0L)
                actionConversation = null
            },
            onArchive = {
                controller.archiveConversation(conversation.id)
                selectedHistoryIds = selectedHistoryIds - conversation.id
                actionConversation = null
            },
            onDelete = {
                controller.deleteConversation(conversation.id)
                selectedHistoryIds = selectedHistoryIds - conversation.id
                actionConversation = null
            },
            onMultiSelect = {
                selectedHistoryIds = selectedHistoryIds + conversation.id
                actionConversation = null
            },
        )
    }
    if (editingProfile) {
        ProfileEditDialog(
            settings = settings,
            nickname = nickname,
            avatarPath = avatarPath,
            onDismiss = { editingProfile = false },
            onSaved = { newNickname, newAvatarPath ->
                onProfileChanged(newNickname, newAvatarPath)
                editingProfile = false
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            KimiCardBox {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { editingProfile = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(avatarPath = avatarPath, fallback = nickname.take(1).ifBlank { "L" }, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.label_functions), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                KimiDivider()
                pages.forEachIndexed { index, page ->
                    KimiMenuRow(
                        icon = when (index) {
                            0 -> Icons.Default.Chat
                            1 -> Icons.Default.ReceiptLong
                            2 -> Icons.Default.Analytics
                            3 -> Icons.Default.TaskAlt
                            4 -> Icons.Default.Archive
                            5 -> Icons.Default.Settings
                            6 -> Icons.Default.School
                            7 -> Icons.Default.Description
                            else -> Icons.Default.Info
                        },
                        title = page,
                        value = if (selectedPage == index) context.getString(R.string.label_current) else "",
                        onClick = { onSelectPage(index) },
                    )
                    if (index != pages.lastIndex) KimiDivider()
                }
            }
        }
        item {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.label_history_sessions), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                CapsuleTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = context.getString(R.string.search_history_placeholder),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                )
                if (selectedHistoryIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(context.getString(R.string.label_selected_count, selectedHistoryIds.size), modifier = Modifier.weight(1f), color = KimiMuted)
                        KimiChip(context.getString(R.string.action_pin), onClick = {
                            controller.setConversationsPinned(selectedHistoryIds, true)
                            selectedHistoryIds = emptySet()
                        })
                        KimiChip(context.getString(R.string.action_delete), onClick = {
                            controller.deleteConversations(selectedHistoryIds)
                            selectedHistoryIds = emptySet()
                        })
                        IconButton(onClick = { selectedHistoryIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.action_cancel_select))
                        }
                    }
                }
                KimiDivider()
                if (filteredConversations.isEmpty()) {
                    Text(context.getString(R.string.notice_no_sessions), color = KimiMuted)
                }
            }
        }
        groupedConversations.forEach { (label, conversations) ->
            item(key = "history-group-$label") {
                Text(
                    label,
                    color = KimiMuted,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(top = 2.dp),
                )
            }
            items(conversations, key = { it.id }) { conversation ->
                KimiConversationRow(
                    conversation = conversation,
                    selected = controller.activeConversationId.value == conversation.id,
                    multiSelected = conversation.id in selectedHistoryIds,
                    selectionMode = selectedHistoryIds.isNotEmpty(),
                    onSelect = {
                        if (selectedHistoryIds.isEmpty()) {
                            onSelectConversation(conversation.id)
                        } else {
                            selectedHistoryIds = if (conversation.id in selectedHistoryIds) {
                                selectedHistoryIds - conversation.id
                            } else {
                                selectedHistoryIds + conversation.id
                            }
                        }
                    },
                    onLongPress = { actionConversation = conversation },
                )
            }
        }
    }
    }
}

private fun groupConversationsByTime(
    conversations: List<Conversation>,
    context: android.content.Context,
    nowMillis: Long = System.currentTimeMillis(),
): List<Pair<String, List<Conversation>>> {
    if (conversations.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance()
    fun startOfDay(time: Long): Long = calendar.run {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    val todayStart = startOfDay(nowMillis)
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = calendar.timeInMillis
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val weekStart = calendar.timeInMillis
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.MONTH, -1)
    val monthStart = calendar.timeInMillis

    val groups = linkedMapOf<String, MutableList<Conversation>>()
    conversations.forEach { conversation ->
        val label = when {
            conversation.pinnedAt > 0L -> context.getString(R.string.label_pinned)
            conversation.updatedAt >= todayStart -> context.getString(R.string.label_today)
            conversation.updatedAt >= yesterdayStart -> context.getString(R.string.label_yesterday)
            conversation.updatedAt >= weekStart -> context.getString(R.string.label_this_week)
            conversation.updatedAt >= monthStart -> context.getString(R.string.label_this_month)
            else -> SimpleDateFormat(context.getString(R.string.date_format_year_month), Locale.getDefault()).format(Date(conversation.updatedAt))
        }
        groups.getOrPut(label) { mutableListOf() }.add(conversation)
    }
    return groups.map { it.key to it.value }
}

@Composable
internal fun CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KimiPillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(placeholder, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
internal fun UserAvatar(avatarPath: String?, fallback: String, modifier: Modifier = Modifier) {
    val bitmap = remember(avatarPath) {
        avatarPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(Color(0xFFC6A990)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(fallback, color = Color(0xFF121212), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun KimiDrawerShortcut(icon: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(label, color = KimiMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun KimiConversationRow(
    conversation: Conversation,
    selected: Boolean,
    multiSelected: Boolean,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val borderWidth = if (selected) 2.dp else if (multiSelected) 1.5.dp else 0.dp
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        multiSelected -> KimiBlue
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(borderWidth, borderColor), rowShape)
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress),
        shape = rowShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected || multiSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = multiSelected, onCheckedChange = { onSelect() })
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.pinnedAt > 0L) {
                        val contextForPinned = LocalContext.current
                        Icon(Icons.Default.PushPin, contentDescription = contextForPinned.getString(R.string.label_pinned_icon), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    listOf(conversation.status, conversation.model).filter { it.isNotBlank() }.joinToString(" · "),
                    color = KimiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun HistoryConversationActionsDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(conversation.id) { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.title_session_actions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(context.getString(R.string.label_chat_title)) },
                )
                Button(onClick = { onRename(title.trim().ifBlank { conversation.title }) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_save_title))
                }
                OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PushPin, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (conversation.pinnedAt > 0L) context.getString(R.string.action_unpin) else context.getString(R.string.action_pin_chat))
                }
                OutlinedButton(onClick = onMultiSelect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_multi_select))
                }
                OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Archive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_archive_chat))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_delete_chat))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.action_close)) }
        },
    )
}



