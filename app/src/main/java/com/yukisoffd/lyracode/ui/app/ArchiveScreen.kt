package com.yukisoffd.lyracode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.ChatProject
import com.yukisoffd.lyracode.data.Conversation

@Composable
internal fun ArchivedConversationsScreen(controller: ChatController) {
    val context = LocalContext.current
    val archivedSnapshot = controller.archivedConversations.toList()
    val archivedProjectSnapshot = controller.archivedProjects.toList()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var pendingProjectDelete by remember { mutableStateOf<ChatProject?>(null) }
    val filteredConversations = remember(archivedSnapshot, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            archivedSnapshot
        } else {
            archivedSnapshot.filter { conversation ->
                conversation.title.contains(normalizedQuery, ignoreCase = true) ||
                    conversation.model.contains(normalizedQuery, ignoreCase = true) ||
                    conversation.status.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val filteredProjects = remember(archivedProjectSnapshot, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            archivedProjectSnapshot
        } else {
            archivedProjectSnapshot.filter { project ->
                project.name.contains(normalizedQuery, ignoreCase = true) ||
                    project.workspaceUri.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(context.getString(R.string.title_delete_archived_chat)) },
            text = { Text(context.getString(R.string.confirm_delete_archived_chat, conversation.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.permanentlyDeleteArchivedConversation(conversation.id)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        context.getString(R.string.action_delete_permanently),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }
    pendingProjectDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingProjectDelete = null },
            title = { Text(context.getString(R.string.title_delete_archived_project)) },
            text = { Text(context.getString(R.string.confirm_delete_archived_project, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.deleteProject(project.id)
                        pendingProjectDelete = null
                    },
                ) {
                    Text(
                        context.getString(R.string.action_delete_permanently),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingProjectDelete = null }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    context.getString(R.string.archive_page_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                CapsuleTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = context.getString(R.string.search_archived_placeholder),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
        if (filteredConversations.isEmpty() && filteredProjects.isEmpty()) {
            item {
                Text(
                    context.getString(R.string.notice_no_archived_chats),
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filteredProjects, key = { "archived-project-${it.id}" }) { project ->
                ArchivedProjectCard(
                    project = project,
                    onRestore = { controller.restoreArchivedProject(project.id) },
                    onDelete = { pendingProjectDelete = project },
                )
            }
            items(filteredConversations, key = { it.id }) { conversation ->
                ArchivedConversationCard(
                    conversation = conversation,
                    onRestore = { controller.restoreArchivedConversation(conversation.id) },
                    onDelete = { pendingDelete = conversation },
                )
            }
        }
    }
}

@Composable
private fun ArchivedProjectCard(
    project: ChatProject,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                context.getString(R.string.history_mode_projects),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Unarchive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_restore_project))
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        context.getString(R.string.action_delete_permanently),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedConversationCard(
    conversation: Conversation,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(conversation.status, conversation.model).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Unarchive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.action_restore_chat))
                }
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        context.getString(R.string.action_delete_permanently),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
