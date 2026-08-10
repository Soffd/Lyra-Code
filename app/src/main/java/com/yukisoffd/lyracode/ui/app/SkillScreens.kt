package com.yukisoffd.lyracode

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.SkillPack
import java.net.URL
import kotlin.math.min
import kotlin.math.max


@Composable
internal fun SkillDrawerRow(skill: SkillPack, onToggle: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(context.getString(R.string.label_files_count_enabled, skill.fileCount, if (skill.enabled) context.getString(R.string.skill_status_enabled) else context.getString(R.string.skill_status_disabled)), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
        }
        KimiChip(if (skill.enabled) context.getString(R.string.action_disable) else context.getString(R.string.action_enable), onClick = onToggle)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = context.getString(R.string.action_delete_skill))
        }
    }
}

@Composable
internal fun SkillsScreen(
    skills: List<SkillPack>,
    status: String,
    onImportSkillFile: () -> Unit,
    onImportSkillRepository: (String) -> Unit,
    onImportSkillMarkdown: (String) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onDeleteSkill: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<SkillPack?>(null) }
    var importModeVisible by remember { mutableStateOf(false) }
    var manualImportVisible by remember { mutableStateOf(false) }
    var repositoryImportVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    deleteTarget?.let { skill ->
        ConfirmDeleteDialog(
            title = context.getString(R.string.action_delete_skill),
            message = context.getString(R.string.confirm_delete_skill),
            targetName = skill.name,
            onDismiss = { deleteTarget = null },
            onConfirm = { onDeleteSkill(skill.id) },
        )
    }
    if (importModeVisible) {
        SkillImportModeDialog(
            onDismiss = { importModeVisible = false },
            onImportFile = {
                importModeVisible = false
                onImportSkillFile()
            },
            onImportRepository = {
                importModeVisible = false
                repositoryImportVisible = true
            },
            onImportMarkdown = {
                importModeVisible = false
                manualImportVisible = true
            },
        )
    }
    if (manualImportVisible) {
        SkillManualImportDialog(
            onDismiss = { manualImportVisible = false },
            onImportMarkdown = { text ->
                manualImportVisible = false
                onImportSkillMarkdown(text)
            },
        )
    }
    if (repositoryImportVisible) {
        SkillRepositoryImportDialog(
            onDismiss = { repositoryImportVisible = false },
            onImportRepository = { url ->
                repositoryImportVisible = false
                onImportSkillRepository(url)
            },
        )
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(context.getString(R.string.label_skills_capability), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        context.getString(R.string.skills_description),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiDivider()
            KimiMenuRow(
                icon = Icons.Default.UploadFile,
                title = context.getString(R.string.action_import_skills),
                value = context.getString(R.string.label_installed_count, skills.size),
                onClick = { importModeVisible = true },
            )
        }
        if (status.isNotBlank()) {
            Text(status, color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        }
        if (skills.isEmpty()) {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(context.getString(R.string.notice_no_skills), style = MaterialTheme.typography.titleMedium)
                            Text(context.getString(R.string.skills_empty_hint), color = KimiMuted)
                        }
                    }
                }
            } else {
            KimiSectionLabel(context.getString(R.string.label_installed))
        }
        if (skills.isNotEmpty()) {
            KimiCardBox {
                skills.forEachIndexed { index, skill ->
                    SkillSettingsRow(
                        skill = skill,
                        onToggle = { onToggleSkill(skill.id, !skill.enabled) },
                        onDelete = { deleteTarget = skill },
                    )
                    if (index != skills.lastIndex) KimiDivider()
                }
            }
        }
    }
}

@Composable
internal fun SkillImportModeDialog(
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportRepository: () -> Unit,
    onImportMarkdown: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SkillImportModeRow(
                    icon = Icons.Default.UploadFile,
                    title = context.getString(R.string.action_import_from_file),
                    subtitle = context.getString(R.string.label_import_file_hint),
                    onClick = onImportFile,
                )
                KimiDivider()
                SkillImportModeRow(
                    icon = Icons.Default.CloudDownload,
                    title = context.getString(R.string.action_import_from_repo),
                    subtitle = context.getString(R.string.label_import_repo_hint),
                    onClick = onImportRepository,
                )
                KimiDivider()
                SkillImportModeRow(
                    icon = Icons.Default.Add,
                    title = context.getString(R.string.action_manual_add),
                    subtitle = context.getString(R.string.label_manual_add_hint),
                    onClick = onImportMarkdown,
                )
            }
        }
    }
}

@Composable
internal fun SkillImportModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
    }
}

@Composable
internal fun SkillRepositoryImportDialog(
    onDismiss: () -> Unit,
    onImportRepository: (String) -> Unit,
) {
    val context = LocalContext.current
    var repoUrl by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(context.getString(R.string.title_import_from_repo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    context.getString(R.string.repo_import_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.label_repo_link)) },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onImportRepository(repoUrl.trim()) },
                        enabled = repoUrl.trim().isNotBlank(),
                        shape = KimiPillShape,
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.action_import))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillManualImportDialog(
    onDismiss: () -> Unit,
    onImportMarkdown: (String) -> Unit,
) {
    val context = LocalContext.current
    var manualText by rememberSaveable {
        mutableStateOf(
            if (AppStrings.isEnglish()) {
                """
                ---
                name: Custom Skill
                description: Briefly describe what this Skill is for
                ---

                # Custom Skill

                Write the capability instructions, applicable scenarios, usage steps, and constraints for the AI here.
                """.trimIndent()
            } else {
                """
                ---
                name: 自定义 Skill
                description: 简要说明这个 Skill 的用途
                ---

                # 自定义 Skill

                在这里写给 AI 的能力说明、适用场景、使用步骤和约束。
                """.trimIndent()
            },
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(context.getString(R.string.title_manual_add), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    context.getString(R.string.manual_add_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    label = { Text("SKILL.md") },
                    minLines = 8,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(context.getString(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onImportMarkdown(manualText) },
                        enabled = manualText.isNotBlank(),
                        shape = KimiPillShape,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.action_save_skill))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillSettingsRow(skill: SkillPack, onToggle: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            tint = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                    if (skill.enabled) context.getString(R.string.skill_status_enabled) else context.getString(R.string.skill_status_disabled),
                    color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(context.getString(R.string.label_files_and_id, skill.fileCount, skill.id), color = KimiMuted, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Switch(checked = skill.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = context.getString(R.string.action_delete_skill), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

