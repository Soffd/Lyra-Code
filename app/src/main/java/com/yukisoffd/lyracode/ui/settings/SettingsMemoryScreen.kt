package com.yukisoffd.lyracode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.MemoryEntry

@Composable
internal fun MemorySettingsScreen(settings: AppSettings) {
    val context = LocalContext.current
    fun loadMemories() = settings.memories().sortedByDescending { it.updatedAt }

    var memories by remember { mutableStateOf(loadMemories()) }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var notice by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        KimiCardBox {
            Text(context.getString(R.string.memory_page_title), style = MaterialTheme.typography.titleMedium)
            Text(
                context.getString(R.string.memory_page_description),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                context.getString(R.string.memory_page_privacy),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    editing = MemoryEntry(
                        id = "",
                        content = "",
                        category = MemoryEntry.CATEGORY_OTHER,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.memory_add))
            }
        }

        if (memories.isEmpty()) {
            KimiCardBox {
                Text(context.getString(R.string.memory_empty_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    context.getString(R.string.memory_empty_description),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            memories.forEach { memory ->
                KimiCardBox {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = memory }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                memory.content,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                context.getString(
                                    R.string.memory_metadata,
                                    memoryCategoryLabel(memory.category),
                                    formatTime(memory.updatedAt),
                                ),
                                color = KimiMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = memory.enabled,
                                onCheckedChange = { enabled ->
                                    settings.updateMemory(memory.id, enabled = enabled)
                                    memories = loadMemories()
                                },
                            )
                            IconButton(onClick = { editing = memory }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = context.getString(R.string.memory_edit),
                                )
                            }
                        }
                    }
                }
            }
        }
            Spacer(Modifier.height(72.dp))
        }

        editing?.let { memory ->
            MemoryEditDialog(
                memory = memory,
                onDismiss = { editing = null },
                onSave = { content, category, enabled ->
                    if (memory.id.isBlank()) {
                        settings.createMemory(content, category, enabled)
                    } else {
                        settings.updateMemory(memory.id, content, category, enabled)
                    }
                    memories = loadMemories()
                    editing = null
                    notice = context.getString(R.string.memory_saved)
                },
                onDelete = {
                    if (memory.id.isNotBlank()) settings.deleteMemory(memory.id)
                    memories = loadMemories()
                    editing = null
                    notice = context.getString(R.string.memory_deleted)
                },
            )
        }

        TransientNotice(
            message = notice,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            onDismiss = { notice = "" },
        )
    }
}

@Composable
private fun MemoryEditDialog(
    memory: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var content by remember(memory.id) { mutableStateOf(memory.content) }
    var category by remember(memory.id) { mutableStateOf(memory.category) }
    var enabled by remember(memory.id) { mutableStateOf(memory.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                context.getString(
                    if (memory.id.isBlank()) R.string.memory_add else R.string.memory_edit,
                ),
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.memory_content_label)) },
                    supportingText = { Text(context.getString(R.string.memory_content_help)) },
                    minLines = 4,
                    maxLines = 10,
                )
                Text(context.getString(R.string.memory_category_label), style = MaterialTheme.typography.titleSmall)
                MemoryEntry.categories.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { category = option }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = category == option, onClick = { category = option })
                        Spacer(Modifier.width(8.dp))
                        Text(memoryCategoryLabel(option), modifier = Modifier.weight(1f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(context.getString(R.string.memory_enabled_label), style = MaterialTheme.typography.titleSmall)
                        Text(
                            context.getString(R.string.memory_enabled_description),
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.isNotBlank(),
                onClick = { onSave(content, category, enabled) },
                shape = KimiPillShape,
            ) {
                Text(context.getString(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (memory.id.isNotBlank()) {
                    TextButton(onClick = onDelete) {
                        Text(context.getString(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(context.getString(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun memoryCategoryLabel(category: String): String {
    val context = LocalContext.current
    val resource = when (category) {
        MemoryEntry.CATEGORY_PREFERENCE -> R.string.memory_category_preference
        MemoryEntry.CATEGORY_WORK_STYLE -> R.string.memory_category_work_style
        MemoryEntry.CATEGORY_COMMUNICATION -> R.string.memory_category_communication
        MemoryEntry.CATEGORY_PERSONAL -> R.string.memory_category_personal
        else -> R.string.memory_category_other
    }
    return context.getString(resource)
}
