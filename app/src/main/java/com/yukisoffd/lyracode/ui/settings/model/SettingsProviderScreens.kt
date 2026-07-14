package com.yukisoffd.lyracode

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import java.net.URL



@Composable
internal fun ProviderLogoBadge(profile: ApiProfile, modifier: Modifier = Modifier) {
    val name = profile.name.ifBlank { profile.baseUrl }.trim()
    val icon = when {
        name.contains("gemini", ignoreCase = true) || profile.apiFormat == ApiProfile.API_FORMAT_GEMINI -> Icons.Default.AutoAwesome
        name.contains("anthropic", ignoreCase = true) || name.contains("claude", ignoreCase = true) || profile.apiFormat == ApiProfile.API_FORMAT_ANTHROPIC -> Icons.Default.Psychology
        name.contains("deepseek", ignoreCase = true) -> Icons.Default.WaterDrop
        name.contains("openrouter", ignoreCase = true) -> Icons.Default.Route
        name.contains("vercel", ignoreCase = true) -> Icons.Default.ChangeCircle
        name.contains("openai", ignoreCase = true) -> Icons.Default.Hub
        else -> Icons.Default.Cloud
    }
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
internal fun ModelProviderRow(
    profile: ApiProfile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.name.ifBlank { uiText("未命名平台") }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.baseUrl.isNotBlank()) {
                    Text(profile.baseUrl, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        uiText("启用"),
                        modifier = Modifier
                            .clip(KimiPillShape)
                            .background(KimiGreen.copy(alpha = 0.28f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        color = KimiGreen,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        uiText("${profile.savedModels.size} 个模型"),
                        modifier = Modifier
                            .clip(KimiPillShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        apiFormatShortName(profile.apiFormat),
                        color = KimiMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = uiText("当前"), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = uiText("删除"), tint = KimiMuted)
            }
        }
    }
}

internal fun apiFormatShortName(format: String): String = when (format) {
    ApiProfile.API_FORMAT_ANTHROPIC -> "Anthropic"
    ApiProfile.API_FORMAT_GEMINI -> "Gemini"
    else -> "OpenAI"
}

@Composable
internal fun WebSearchSettings(
    settings: AppSettings,
    externalRevision: Int = 0,
    onChanged: () -> Unit,
) {
    var blacklist by rememberSaveable(externalRevision) { mutableStateOf(settings.webSearchBlacklistText) }
    var notice by remember { mutableStateOf("") }
    val blockedCount = remember(blacklist, externalRevision) {
        blacklist.lineSequence()
            .map { raw ->
                val clean = raw.trim().trimEnd('/').trim()
                if (clean.isBlank() || clean.startsWith("#")) "" else {
                    val withoutScheme = clean.substringAfter("://", clean)
                    val authority = withoutScheme
                        .substringBefore('/')
                        .substringBefore('?')
                        .substringBefore('#')
                        .substringAfterLast('@')
                    val hostPart = authority
                        .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
                        .lowercase()
                        .trim('.')
                    val host = hostPart.removePrefix("*.").trim('.')
                    when {
                        host.isBlank() -> ""
                        hostPart.startsWith("*.") && !host.contains('.') -> ""
                        hostPart.startsWith("*.") -> "*.$host"
                        else -> host
                    }
                }
            }
            .filter { it.isNotBlank() }
            .distinct()
            .count()
    }
    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(uiText("网站黑名单"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    uiText("AI 联网搜索和网页读取会跳过这些域名。普通域名精确匹配，* 通配符匹配子域名。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        KimiDivider()
        OutlinedTextField(
            value = blacklist,
            onValueChange = {
                blacklist = it
                notice = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("每行一个域名或 URL")) },
            placeholder = { Text("x.com\nwww.x.com\n*.example.com\nhttps://baijiahao.baidu.com/") },
            minLines = 8,
            maxLines = 14,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(
            uiText("保存后会自动归一化：移除协议、路径和尾部斜杠，但保留 www.。例如 x.com 只匹配 x.com；*.x.com 匹配 www.x.com、news.x.com 等子域名；如需同时拦截根域名和全部子域名，请同时填写 x.com 与 *.x.com。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    settings.webSearchBlacklistText = blacklist
                    blacklist = settings.webSearchBlacklistText
                    notice = uiText("已保存 ${settings.webSearchBlockedHosts().size} 个黑名单域名")
                    onChanged()
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(uiText("保存"))
            }
            OutlinedButton(
                onClick = {
                    blacklist = ""
                    settings.webSearchBlacklistText = ""
                    notice = uiText("已清空联网搜索黑名单")
                    onChanged()
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(uiText("清空"))
            }
        }
        val summary = if (notice.isNotBlank()) notice else uiText("当前将保存 $blockedCount 个域名")
        Text(summary, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

