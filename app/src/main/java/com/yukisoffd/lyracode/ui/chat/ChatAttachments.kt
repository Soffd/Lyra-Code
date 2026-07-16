package com.yukisoffd.lyracode

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.yukisoffd.lyracode.ai.ChatRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.min
import kotlin.math.max


internal data class UploadedMediaPreview(
    val name: String,
    val kind: String,
    val mimeType: String,
    val uri: String,
)

internal data class UploadedFilePreview(
    val name: String,
    val sizeBytes: Long?,
    val type: String,
)

@Composable
internal fun UploadedFileCardColumn(files: List<UploadedFilePreview>) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEach { file ->
            UploadedFileCard(file)
        }
    }
}

@Composable
internal fun UploadedFileCard(file: UploadedFilePreview) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(Modifier.widthIn(min = 150.dp, max = 220.dp)) {
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(file.type, formatUploadedFileSize(file.sizeBytes))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    meta,
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun UploadedMediaGrid(media: List<UploadedMediaPreview>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        media.forEach { item ->
            if (item.kind == "image") {
                MediaThumb(item.name, item.uri, item.kind)
            } else {
                MediaPlaceholder(item.name, item.kind, source = item.uri)
            }
        }
    }
}

internal fun uploadedMediaPreviews(content: String): List<UploadedMediaPreview> {
    val markerPreviews = uploadedAttachmentPayloads(content)
        .filter { payload -> payload.optString("kind") in setOf("image", "video", "audio") }
        .map { payload ->
            UploadedMediaPreview(
                name = payload.optString("name").ifBlank { uiText("未命名文件") },
                kind = payload.optString("kind"),
                mimeType = payload.optString("mime_type"),
                uri = payload.optString("data_url").ifBlank { payload.optString("uri") },
            )
        }
    val legacyRegex = Regex("用户上传媒体：([^\\n]+)\\n类型：([^\\n]+)\\nMIME：([^\\n]*)\\n(?:DATA_URL：([^\\n]*)\\n)?URI：([^\\n]*)", RegexOption.MULTILINE)
    val legacyPreviews = legacyRegex.findAll(content).map {
        UploadedMediaPreview(
            name = it.groupValues[1].trim(),
            kind = it.groupValues[2].trim(),
            mimeType = it.groupValues[3].trim(),
            uri = it.groupValues[4].trim().ifBlank { it.groupValues[5].trim() },
        )
    }.toList()
    return markerPreviews + legacyPreviews
}

internal fun uploadedFilePreviews(content: String): List<UploadedFilePreview> {
    val markerPreviews = uploadedAttachmentPayloads(content)
        .filter { payload -> payload.optString("kind").ifBlank { "text" } == "text" }
        .map { payload ->
            val name = payload.optString("name").ifBlank { uiText("未命名文件") }
            UploadedFilePreview(
                name = name,
                sizeBytes = payload.optLong("size", -1L).takeIf { it >= 0L },
                type = uploadedFileTypeLabel(name),
            )
        }
    val legacyRegex = Regex("用户上传文件：([^\\n]+)\\n大小：(\\d+) bytes", RegexOption.MULTILINE)
    val legacyPreviews = legacyRegex.findAll(content).map {
        val name = it.groupValues[1].trim().ifBlank { uiText("未命名文件") }
        UploadedFilePreview(
            name = name,
            sizeBytes = it.groupValues[2].toLongOrNull(),
            type = uploadedFileTypeLabel(name),
        )
    }.toList()
    return markerPreviews + legacyPreviews
}

internal data class WorkspaceReferencePreview(val name: String, val path: String)

internal fun workspaceReferencePreviews(content: String): List<WorkspaceReferencePreview> {
    val regex = Regex("<lyra_workspace_refs_v1>([\\s\\S]*?)</lyra_workspace_refs_v1>")
    return regex.findAll(content).flatMap { match ->
        val files = runCatching { JSONObject(match.groupValues[1]).optJSONArray("files") }.getOrNull() ?: JSONArray()
        buildList {
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) add(WorkspaceReferencePreview(item.optString("name").ifBlank { fileNameForDisplay(path) }, path))
            }
        }.asSequence()
    }.toList()
}

@Composable
internal fun WorkspaceReferenceCardColumn(files: List<WorkspaceReferencePreview>) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEach { file ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            ) {
                Row(
                    Modifier.widthIn(max = 320.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis, color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal fun stripWorkspaceReferenceBlocks(content: String): String = content.replace(
    Regex("\\n*<lyra_workspace_refs_v1>[\\s\\S]*?</lyra_workspace_refs_v1>\\n*"),
    "\n",
).trim()

internal fun stripEditorContextBlocks(content: String): String = content.replace(
    Regex("\\n*<lyra_editor_context_v1>[\\s\\S]*?</lyra_editor_context_v1>\\n*"),
    "\n",
).trim()
internal fun uploadedAttachmentPayloads(content: String): List<JSONObject> {
    val regex = Regex("<lyra_attachment_v1>([\\s\\S]*?)</lyra_attachment_v1>")
    return regex.findAll(content).mapNotNull { match ->
        runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
    }.toList()
}

internal fun displayMessageContent(message: ChatRecord): String {
    if (message.role != "user") return message.content
    return stripEditorContextBlocks(stripWorkspaceReferenceBlocks(stripUploadedFileBlocks(stripUploadedMediaBlocks(stripUploadedAttachmentBlocks(message.content))))).trim()
}

internal fun stripUploadedAttachmentBlocks(content: String): String {
    return content.replace(
        Regex("\\n*<lyra_attachment_v1>[\\s\\S]*?</lyra_attachment_v1>\\n*"),
        "\n",
    ).trim()
}

internal fun stripUploadedMediaBlocks(content: String): String {
    return content.replace(
        Regex("\\n*用户上传媒体：[^\\n]+\\n类型：[^\\n]+\\nMIME：[^\\n]*\\n(?:DATA_URL：[^\\n]*\\n)?URI：[^\\n]*\\n大小：[^\\n]*(\\n)?"),
        "\n",
    ).trim()
}
