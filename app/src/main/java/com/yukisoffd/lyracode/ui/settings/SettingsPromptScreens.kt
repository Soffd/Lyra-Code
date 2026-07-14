package com.yukisoffd.lyracode

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.SystemPromptPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max



@Composable
internal fun PromptSettingsScreen(settings: AppSettings) {
    fun visiblePresets() = settings.systemPromptPresets()
    var presets by remember { mutableStateOf(visiblePresets()) }
    var selectedId by remember { mutableStateOf(settings.selectedSystemPromptId) }
    var editing by remember { mutableStateOf<SystemPromptPreset?>(null) }
    var notice by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KimiCardBox {
                Text(uiText("系统提示词"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("选择不同用途的系统提示词。修改后会保存到当前预设；恢复预设只影响当前选中的提示词。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            KimiCardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("提示词配置"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            editing = SystemPromptPreset(
                                id = AppSettings.newId(),
                                name = uiText("自定义提示词"),
                                prompt = "",
                                builtIn = false,
                            )
                        },
                        shape = KimiPillShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(uiText("新增"))
                    }
                }
                presets.forEach { preset ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selectedId = preset.id
                                settings.selectedSystemPromptId = preset.id
                                notice = uiText("已切换到 ${preset.name}")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            val desc = preset.prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                            Text(
                                if (preset.exampleConversation.isBlank()) desc else uiText("$desc · 含示例对话"),
                                color = KimiMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (preset.id == selectedId) Icon(Icons.Default.Check, contentDescription = uiText("已选择"))
                        IconButton(onClick = { editing = preset }) {
                            Icon(Icons.Default.Edit, contentDescription = uiText("编辑"))
                        }
                    }
                    if (preset != presets.last()) KimiDivider()
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        editing?.let { preset ->
            PromptEditDialog(
                preset = preset,
                onDismiss = { editing = null },
                onSave = { updated ->
                    settings.saveSystemPromptConfig(updated)
                    presets = visiblePresets()
                    selectedId = settings.selectedSystemPromptId
                    editing = null
                    notice = uiText("提示词已保存")
                },
                onRestore = {
                    settings.restoreSystemPrompt(preset.id)
                    presets = visiblePresets()
                    editing = null
                    notice = uiText("已恢复预设")
                },
                onDelete = {
                    settings.deleteSystemPromptConfig(preset.id)
                    presets = visiblePresets()
                    selectedId = settings.selectedSystemPromptId
                    editing = null
                    notice = uiText("提示词已删除")
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
internal fun PromptEditDialog(
    preset: SystemPromptPreset,
    onDismiss: () -> Unit,
    onSave: (SystemPromptPreset) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var prompt by remember(preset.id) { mutableStateOf(preset.prompt) }
    var example by remember(preset.id) { mutableStateOf(preset.exampleConversation) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset.builtIn) uiText("编辑内置提示词") else uiText("编辑自定义提示词")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("提示词名称")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("提示词内容")) },
                    minLines = 8,
                    maxLines = 16,
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("示例对话（可选）")) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = prompt.isNotBlank(),
                onClick = {
                    onSave(
                        preset.copy(
                            name = name.trim().ifBlank { uiText("自定义提示词") },
                            prompt = prompt,
                            exampleConversation = example,
                        ),
                    )
                },
                shape = KimiPillShape,
            ) { Text(uiText("保存")) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preset.builtIn) {
                    TextButton(onClick = onRestore) { Text(uiText("恢复预设")) }
                } else {
                    TextButton(onClick = onDelete) { Text(uiText("删除")) }
                }
                TextButton(onClick = onDismiss) { Text(uiText("取消")) }
            }
        },
    )
}

internal fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))












