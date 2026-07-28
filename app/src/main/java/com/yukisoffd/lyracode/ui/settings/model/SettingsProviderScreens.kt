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
import androidx.compose.ui.platform.LocalUriHandler
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
    AiLogoBadge(
        logoRes = ProviderCatalog.logoRes(profile),
        fallback = profile.name.ifBlank { profile.baseUrl },
        modifier = modifier,
    )
}

@Composable
internal fun ProviderPresetPicker(
    onSelect: (ProviderPreset, ProviderPresetPlan) -> Unit,
    onCustom: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            uiText("选择预设后只需填写 API Key，接口地址和格式会自动配置，并且仍可在高级配置中修改。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        CustomProviderPresetRow(onClick = onCustom)
        ProviderCatalog.presets.forEach { preset ->
            preset.plans().forEach { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(preset, plan) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AiLogoBadge(
                            logoRes = preset.logoRes,
                            fallback = preset.displayName(),
                            modifier = Modifier,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                preset.displayName(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(apiFormatShortName(plan.apiFormat))
                                    if (plan.id != ProviderPresetPlan.DEFAULT_ID) {
                                        append(" · ")
                                        append(plan.displayName())
                                    }
                                },
                                color = KimiMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { runCatching { uriHandler.openUri(preset.websiteUrl) } },
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = uiText("打开服务商官网"),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomProviderPresetRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(uiText("自定义服务商"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("自行设置接口格式、基础 URL 和请求路径"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
        }
    }
}

@Composable
internal fun ModelProviderRow(
    profile: ApiProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProviderLogoBadge(profile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(profile.name.ifBlank { uiText("未命名平台") }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.baseUrl.isNotBlank()) {
                    Text(profile.baseUrl, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    uiText("${profile.savedModels.size} 个模型"),
                    modifier = Modifier
                        .clip(KimiPillShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
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

