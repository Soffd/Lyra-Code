package com.yukisoffd.lyracode

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.LocalMcpServerConfig
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.mcp.LocalMcpServerManager
import com.yukisoffd.lyracode.mcp.McpClientManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.math.max



@Composable
internal fun McpSettings(
    settings: AppSettings,
    mcpClientManager: McpClientManager,
    externalRevision: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.mcpServers() }
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<McpServerConfig?>(null) }
    var status by remember { mutableStateOf("") }
    var expandedToolServerIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    editing?.let { server ->
        McpServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = {
                settings.upsertMcpServer(it)
                editing = null
                status = uiText("MCP 服务器已保存")
                revision++
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = uiText("删除 MCP 服务器"),
            message = uiText("该操作会删除此 MCP 服务器连接、认证信息和已拉取的工具列表。"),
            targetName = server.name.ifBlank { server.url },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                settings.deleteMcpServer(server.id)
                status = uiText("已删除 ${server.name}")
                revision++
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("MCP 服务器"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("支持 Streamable HTTP 与 SSE。Android 端不直接启动 stdio MCP Server。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                editing = defaultMcpServer()
            }, shape = KimiPillShape) { Text(uiText("添加")) }
        }
        if (status.isNotBlank()) {
            Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (servers.isEmpty()) {
        KimiCardBox {
            Text(uiText("暂无 MCP 服务器"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("请添加远程或局域网 MCP Server URL。若服务器使用 HTTP 明文连接，API Key 和工具参数可能被同一网络中的第三方截获。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.url, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(context.getString(R.string.label_mcp_tools_count, transportLabel(server.transport), server.timeoutSeconds, server.tools.size), color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.url.startsWith("http://", ignoreCase = true)) {
                        Text(uiText("安全提示：HTTP 明文连接可能泄露认证 key、工具参数和返回内容。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = {
                        settings.setMcpServerEnabled(server.id, it)
                        revision++
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = uiText("正在测试 ${server.name}...")
                        scope.launch {
                            mcpClientManager.testAndRefreshTools(server).fold(
                                onSuccess = {
                                    status = context.getString(R.string.mcp_connected, server.name, it.size)
                                    revision++
                                },
                                onFailure = { status = uiText("MCP 连接失败: ${it.message}") },
                            )
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("测试并拉取")) }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = uiText("编辑 MCP"))
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("删除 MCP"))
                }
            }
            if (server.tools.isNotEmpty()) {
                val toolsExpanded = server.id in expandedToolServerIds
                KimiDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            expandedToolServerIds = if (toolsExpanded) {
                                expandedToolServerIds - server.id
                            } else {
                                expandedToolServerIds + server.id
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(context.getString(R.string.label_fetched_tools, server.tools.size), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (toolsExpanded) uiText("点击收起工具名称和简介") else uiText("点击展开查看工具名称和简介"),
                            color = KimiMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(
                        if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = KimiMuted,
                    )
                }
                AnimatedVisibility(visible = toolsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        server.tools.forEachIndexed { index, tool ->
                            McpToolSummaryRow(tool)
                            if (index != server.tools.lastIndex) KimiDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LocalMcpServerSettings(
    settings: AppSettings,
    localMcpServerManager: LocalMcpServerManager,
    externalRevision: Int = 0,
) {
    var revision by remember { mutableIntStateOf(0) }
    val localConfig = remember(revision, externalRevision) { settings.localMcpServerConfig() }
    val localStatus = remember(revision, externalRevision) { localMcpServerManager.status() }
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        localMcpServerManager.syncWithSettings()
        revision++
    }
    val externalConnectionJson = remember(localConfig, localStatus.url, localStatus.lanUrls) {
        buildLocalMcpExternalConnectionJson(localConfig, localStatus.url, localStatus.lanUrls)
    }

    if (editing) {
        LocalMcpServerDialog(
            initial = localConfig,
            onDismiss = { editing = false },
            onSave = { config ->
                settings.saveLocalMcpServerConfig(config)
                if (config.enabled) {
                    localMcpServerManager.start(config)
                } else {
                    localMcpServerManager.stop()
                }
                editing = false
                status = uiText("本机 MCP 服务端配置已保存")
                revision++
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("本机作为 MCP 服务端"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("将 Lyra Code 已启用的 Agent 工具和已启用 MCP 工具暴露给其他 MCP Client。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = localConfig.enabled && localStatus.running,
                onCheckedChange = { enabled ->
                    val updated = localConfig.copy(enabled = enabled)
                    if (enabled) {
                        localMcpServerManager.start(updated)
                    } else {
                        settings.saveLocalMcpServerConfig(updated)
                        localMcpServerManager.stop()
                    }
                    revision++
                },
            )
        }
        KimiDivider()
        Text(
            uiText(
                stringResource(
                    R.string.label_server_status,
                    if (localStatus.running) uiText("运行中") else uiText("已停止"),
                    uiText(localStatus.message),
                ),
            ),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            uiText(stringResource(R.string.label_local_address_info, localStatus.url)),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (localStatus.lanUrls.isNotEmpty()) {
            Text(
                uiText("局域网地址：") + localStatus.lanUrls.joinToString("  "),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            if (localConfig.authKey.isBlank()) uiText("认证：未设置 key，局域网或公网暴露时不安全。") else uiText("认证：已启用 Authorization Bearer / X-Lyra-MCP-Key"),
            color = if (localConfig.authKey.isBlank()) MaterialTheme.colorScheme.error else KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = true }, shape = KimiPillShape) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(uiText("配置"))
            }
            OutlinedButton(
                onClick = {
                    if (localStatus.running) {
                        localMcpServerManager.stop()
                    }
                    localMcpServerManager.start(localConfig.copy(enabled = true))
                    status = uiText("本机 MCP 服务端已重启")
                    revision++
                },
                shape = KimiPillShape,
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(uiText("重启"))
            }
        }
    }

    KimiCardBox {
        Text(uiText("外部调用说明"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("外部 MCP Client 调用工具时，Lyra Code 默认不再弹出二次确认。请在外部 MCP Client 中配置是否需要用户确认，并避免把未设置认证 Key 的服务暴露到不可信网络。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(uiText("外部连接原始 JSON"), style = MaterialTheme.typography.titleSmall)
        CommandCopyCard(
            command = externalConnectionJson,
            buttonText = uiText("复制外部连接 JSON"),
            onCopy = { clipboard.setText(AnnotatedString(externalConnectionJson)) },
        )
        Text(
            uiText("复制配置默认只包含 Mcp-Protocol-Version 和 Authorization。X-Lyra-MCP-Key、X-API-Key、Api-Key 是兼容替代写法，不需要同时填写。请求地址必须是 /mcp。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun buildLocalMcpExternalConnectionJson(
    config: LocalMcpServerConfig,
    url: String,
    lanUrls: List<String>,
): String {
    val key = config.authKey.trim()
    val headers = JSONObject()
        .put("Mcp-Protocol-Version", "2025-06-18")
    if (key.isNotBlank()) {
        headers.put("Authorization", if (key.startsWith("Bearer ", ignoreCase = true)) key else "Bearer $key")
    }
    val server = JSONObject()
        .put("type", "streamableHttp")
        .put("transport", "streamable_http")
        .put("name", "Lyra Code")
        .put("url", url)
        .put("baseUrl", url)
        .put("headers", headers)
    val root = JSONObject()
        .put("protocolVersion", "2025-06-18")
        .put("mcpServers", JSONObject().put("lyra_code", server))
        .put(
            "direct",
            JSONObject()
                .put("method", "POST")
                .put("url", url)
                .put("headers", headers),
        )
    if (lanUrls.isNotEmpty()) root.put("alternativeUrls", JSONArray(lanUrls))
    return root.toString(2)
}

@Composable
internal fun McpToolSummaryRow(tool: McpToolDefinition) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(tool.name, style = MaterialTheme.typography.titleSmall)
        Text(
            tool.description.ifBlank { uiText("无描述") },
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LocalMcpServerDialog(
    initial: LocalMcpServerConfig,
    onDismiss: () -> Unit,
    onSave: (LocalMcpServerConfig) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initial.host.ifBlank { AppSettings.DEFAULT_LOCAL_MCP_SERVER_HOST }) }
    var port by rememberSaveable { mutableStateOf(initial.port.toString()) }
    var authKey by rememberSaveable { mutableStateOf(initial.authKey) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("本机 MCP 服务端")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    uiText("其他 MCP Client 可通过 http://主机:端口/mcp 连接。若监听局域网或公网，建议设置认证 Key。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("监听主机")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("端口")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = authKey,
                    onValueChange = { authKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("认证 Key，可空")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (authKey.isBlank()) {
                    Text(uiText("未设置认证 Key 时，同网络内能访问该端口的客户端都可请求工具调用。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("保存后立即启用"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        LocalMcpServerConfig(
                            host = host.trim().ifBlank { AppSettings.DEFAULT_LOCAL_MCP_SERVER_HOST },
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.DEFAULT_LOCAL_MCP_SERVER_PORT,
                            authKey = authKey.trim(),
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
    )
}

@Composable
internal fun McpServerDialog(
    initial: McpServerConfig,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var url by rememberSaveable(initial.id) { mutableStateOf(initial.url) }
    var authKey by rememberSaveable(initial.id) { mutableStateOf(initial.authKey) }
    var transport by rememberSaveable(initial.id) { mutableStateOf(initial.transport.ifBlank { AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP }) }
    var timeout by rememberSaveable(initial.id) { mutableStateOf(initial.timeoutSeconds.toString()) }
    var rawJson by rememberSaveable(initial.id) { mutableStateOf(initial.rawJson.ifBlank { "{}" }) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("MCP 服务器")) },
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
                    onValueChange = {
                        name = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("服务名")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    singleLine = true,
                )
                if (url.startsWith("http://", ignoreCase = true)) {
                    Text(uiText("HTTP 明文连接不安全，但不会阻止添加。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = authKey,
                    onValueChange = {
                        authKey = it
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(uiText("认证 Key，可空")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialChoiceButton("Streamable HTTP", transport == AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP) {
                        transport = AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    }
                    MaterialChoiceButton("SSE", transport == AppSettings.MCP_TRANSPORT_SSE) {
                        transport = AppSettings.MCP_TRANSPORT_SSE
                        rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport)
                    }
                }
                OutlinedTextField(value = timeout, onValueChange = { timeout = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("超时秒数 5-300")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("启用"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = {
                        rawJson = it
                        parseMcpRawJson(it)?.let { parsed ->
                            name = parsed.name.ifBlank { name }
                            url = parsed.url.ifBlank { url }
                            authKey = parsed.authKey.ifBlank { authKey }
                            transport = parsed.transport.ifBlank { transport }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text(uiText("原始 JSON：实际以此连接")) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "MCP Server" },
                            url = url.trim(),
                            authKey = authKey.trim(),
                            transport = transport,
                            timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                            enabled = enabled,
                            rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport),
                        ),
                    )
                },
            ) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
    )
}

internal data class ParsedMcpRawConfig(
    val name: String,
    val url: String,
    val authKey: String,
    val transport: String,
    val serverKey: String,
)

internal fun parseMcpRawJson(rawJson: String): ParsedMcpRawConfig? = runCatching {
    val root = JSONObject(rawJson)
    val servers = root.optJSONObject("mcpServers")
    val serverKey = servers?.keys()?.asSequence()?.firstOrNull().orEmpty()
    val node = if (serverKey.isNotBlank()) servers?.optJSONObject(serverKey) else root
    node ?: return@runCatching null
    val headers = node.optJSONObject("headers") ?: root.optJSONObject("headers")
    val auth = headers?.optString("Authorization").orEmpty().removePrefix("Bearer ").trim()
    val rawType = node.optString("type").ifBlank { node.optString("transport") }
    ParsedMcpRawConfig(
        name = node.optString("name").ifBlank { serverKey.ifBlank { root.optString("name") } },
        url = node.optString("baseUrl").ifBlank { node.optString("url").ifBlank { root.optString("baseUrl").ifBlank { root.optString("url") } } },
        authKey = auth,
        transport = when {
            rawType.equals("sse", ignoreCase = true) -> AppSettings.MCP_TRANSPORT_SSE
            else -> AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP
        },
        serverKey = serverKey.ifBlank { node.optString("id").ifBlank { "mcp_server" } },
    )
}.getOrNull()

internal fun buildMcpRawJson(rawJson: String, name: String, url: String, authKey: String, transport: String): String {
    val parsed = parseMcpRawJson(rawJson)
    val serverKey = parsed?.serverKey?.ifBlank { null } ?: name.ifBlank { "mcp_server" }
    val root = runCatching { JSONObject(rawJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    val servers = root.optJSONObject("mcpServers") ?: JSONObject()
    val node = servers.optJSONObject(serverKey) ?: JSONObject()
    node.put("type", if (transport == AppSettings.MCP_TRANSPORT_SSE) "sse" else "streamableHttp")
    node.put("name", name.ifBlank { parsed?.name ?: "MCP Server" })
    node.put("baseUrl", url)
    val headers = node.optJSONObject("headers") ?: JSONObject()
    if (authKey.isNotBlank()) {
        headers.put("Authorization", if (authKey.startsWith("Bearer ", ignoreCase = true)) authKey else "Bearer $authKey")
    }
    node.put("headers", headers)
    servers.put(serverKey, node)
    root.put("mcpServers", servers)
    if (!root.has("protocolVersion")) root.put("protocolVersion", "2025-06-18")
    return root.toString(2)
}

internal fun defaultMcpServer(): McpServerConfig = McpServerConfig(
    id = AppSettings.newId(),
    name = "MCP Server",
    url = "",
    authKey = "",
    transport = AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP,
    timeoutSeconds = 30,
    enabled = true,
    rawJson = """
        {
          "protocolVersion": "2025-06-18",
          "headers": {}
        }
    """.trimIndent(),
    tools = emptyList(),
)

internal fun transportLabel(transport: String): String = when (transport) {
    AppSettings.MCP_TRANSPORT_SSE -> "SSE"
    else -> "Streamable HTTP"
}

