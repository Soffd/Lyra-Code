package com.yukisoffd.lyracode

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.MiniServerConfig
import com.yukisoffd.lyracode.server.MiniServerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.net.URL
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.max



@Composable
internal fun MiniServerSettings(
    settings: AppSettings,
    miniServerManager: MiniServerManager,
    externalRevision: Int = 0,
    onOpenLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val savedConfig = remember(revision, externalRevision) { settings.miniServerConfig() }
    var protocol by remember(savedConfig) { mutableStateOf(savedConfig.protocol) }
    var host by remember(savedConfig) { mutableStateOf(savedConfig.host) }
    var portText by remember(savedConfig) { mutableStateOf(savedConfig.port.toString()) }
    var username by remember(savedConfig) { mutableStateOf(savedConfig.username.ifBlank { AppSettings.DEFAULT_MINI_SERVER_USERNAME }) }
    var password by remember(savedConfig) { mutableStateOf(savedConfig.password) }
    var customDomainsText by remember(savedConfig) { mutableStateOf(savedConfig.customDomains.joinToString("\n")) }
    var forceHttps by remember(savedConfig) { mutableStateOf(savedConfig.forceHttps) }
    var tlsKeyStoreBase64 by remember(savedConfig) { mutableStateOf(savedConfig.tlsKeyStoreBase64) }
    var tlsKeyStorePassword by remember(savedConfig) { mutableStateOf(savedConfig.tlsKeyStorePassword) }
    var tlsCertificateChain by remember(savedConfig) { mutableStateOf(savedConfig.tlsCertificateChain) }
    var tlsPrivateKey by remember(savedConfig) { mutableStateOf(savedConfig.tlsPrivateKey) }
    var spaFallback by remember(savedConfig) { mutableStateOf(savedConfig.spaFallback) }
    var directoryListing by remember(savedConfig) { mutableStateOf(savedConfig.directoryListing) }
    var mdnsEnabled by remember(savedConfig) { mutableStateOf(savedConfig.mdnsEnabled) }
    var mdnsName by remember(savedConfig) { mutableStateOf(savedConfig.mdnsName) }
    var statusText by remember { mutableStateOf("") }
    var statusRevision by remember { mutableIntStateOf(0) }
    val keyStoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsKeyStoreBase64 = Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
                } ?: error(uiText("无法读取证书库文件"))
            }.fold(
                { statusText = uiText("已读取证书库文件") },
                { statusText = uiText("读取证书库失败：${it.message}") },
            )
        }
    }
    val certChainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsCertificateChain = input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error(uiText("无法读取证书链文件"))
            }.fold(
                { statusText = uiText("已读取证书链文件") },
                { statusText = uiText("读取证书链失败：${it.message}") },
            )
        }
    }
    val privateKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tlsPrivateKey = input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error(uiText("无法读取私钥文件"))
            }.fold(
                { statusText = uiText("已读取私钥文件") },
                { statusText = uiText("读取私钥失败：${it.message}") },
            )
        }
    }
    val status = remember(statusRevision, revision, externalRevision) { miniServerManager.status() }
    val lanUrls = remember(statusRevision, revision, externalRevision) {
        miniServerManager.statusJson().optJSONArray("lanUrls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }.orEmpty()
    }
    val customUrls = remember(statusRevision, revision, externalRevision) {
        miniServerManager.statusJson().optJSONArray("customUrls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }.orEmpty()
    }

    fun currentConfig(enabled: Boolean = status.running): MiniServerConfig {
        return MiniServerConfig(
            protocol = if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) AppSettings.MINI_SERVER_PROTOCOL_HTTPS else AppSettings.MINI_SERVER_PROTOCOL_HTTP,
            host = host.trim().ifBlank { AppSettings.DEFAULT_MINI_SERVER_HOST },
            port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.DEFAULT_MINI_SERVER_PORT,
            username = username.trim().ifBlank { AppSettings.DEFAULT_MINI_SERVER_USERNAME },
            password = password,
            customDomains = customDomainsText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList(),
            forceHttps = forceHttps,
            tlsKeyStoreBase64 = tlsKeyStoreBase64,
            tlsKeyStorePassword = tlsKeyStorePassword,
            tlsCertificateChain = tlsCertificateChain,
            tlsPrivateKey = tlsPrivateKey,
            spaFallback = spaFallback,
            directoryListing = directoryListing,
            mdnsEnabled = mdnsEnabled,
            mdnsName = mdnsName.ifBlank { AppSettings.DEFAULT_MINI_SERVER_MDNS_NAME },
            enabled = enabled,
        )
    }

    fun refresh(message: String) {
        statusText = message
        statusRevision++
        revision++
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText("工作区微型服务器"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("以当前工作目录作为静态站点根目录，适合调试 Vue/Vite 文档站或普通 HTML/CSS/JS。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (status.running) uiText("运行中") else uiText("已停止"), color = if (status.running) MaterialTheme.colorScheme.primary else KimiMuted)
        }
        KimiDivider()
        Text(
            uiText(stringResource(R.string.label_local_address, status.url)),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (lanUrls.isNotEmpty()) {
            Text(uiText("局域网地址：") + lanUrls.joinToString("  "), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (customUrls.isNotEmpty()) {
            Text(uiText("绑定域名：") + customUrls.joinToString("  "), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (status.message.isNotBlank()) {
            Text(uiText(status.message), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (statusText.isNotBlank()) {
            Text(statusText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = onOpenLogs,
            shape = KimiPillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Article, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(uiText("查看终端日志"))
        }
    }

    KimiCardBox {
        Text(uiText("监听配置"), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { protocol = AppSettings.MINI_SERVER_PROTOCOL_HTTP },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTP) "HTTP ✓" else "HTTP") }
            OutlinedButton(
                onClick = { protocol = AppSettings.MINI_SERVER_PROTOCOL_HTTPS },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) "HTTPS ✓" else "HTTPS") }
        }
        if (protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) {
            Text(uiText("HTTPS 使用内置自签名证书，浏览器会提示不受信任；公网或正式分享建议使用内网穿透/反向代理提供可信 TLS。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("监听主机")) }, singleLine = true)
        Text(uiText("127.0.0.1 仅本机访问；0.0.0.0 可被局域网、内网穿透或公网映射访问。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("端口")) }, singleLine = true)
        OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("访问用户名")) }, singleLine = true)
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("访问密码，可空")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(
            value = customDomainsText,
            onValueChange = { customDomainsText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text(uiText("绑定域名，每行一个")) },
            placeholder = { Text("docs.example.com\nhttps://preview.example.com") },
        )
        WebDavSwitchRow(uiText("强制 HTTPS 连接"), uiText("HTTP 访问会返回 308 跳转到 HTTPS；适合反向代理或同端口 HTTPS 调试。"), forceHttps) { forceHttps = it }
        if (host.trim() == "0.0.0.0" || password.isBlank()) {
            Text(uiText("安全提示：面向局域网或公网映射时建议设置用户名和密码；HTTP 明文会暴露访问内容和账号密码。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    KimiCardBox {
        Text(uiText("HTTPS 证书"), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Text(uiText("未配置自定义证书时会使用内置自签名证书。证书库支持 PKCS12/JKS；PEM 私钥需为未加密 PKCS#8 格式。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { keyStoreLauncher.launch("*/*") },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (tlsKeyStoreBase64.isBlank()) uiText("上传证书库") else uiText("替换证书库")) }
            OutlinedButton(
                onClick = { tlsKeyStoreBase64 = "" },
                shape = KimiPillShape,
                enabled = tlsKeyStoreBase64.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(uiText("清除证书库")) }
        }
        OutlinedTextField(
            value = tlsKeyStorePassword,
            onValueChange = { tlsKeyStorePassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(uiText("证书库/私钥密码，可空")) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { certChainLauncher.launch("*/*") }, shape = KimiPillShape, modifier = Modifier.weight(1f)) { Text(uiText("上传证书链")) }
            OutlinedButton(onClick = { privateKeyLauncher.launch("*/*") }, shape = KimiPillShape, modifier = Modifier.weight(1f)) { Text(uiText("上传私钥")) }
        }
        OutlinedTextField(
            value = tlsCertificateChain,
            onValueChange = { tlsCertificateChain = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(uiText("证书链 PEM，可粘贴")) },
            placeholder = { Text("-----BEGIN CERTIFICATE-----") },
        )
        OutlinedTextField(
            value = tlsPrivateKey,
            onValueChange = { tlsPrivateKey = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(uiText("私钥 PEM，可粘贴")) },
            placeholder = { Text("-----BEGIN PRIVATE KEY-----") },
            visualTransformation = PasswordVisualTransformation(),
        )
    }

    KimiCardBox {
        Text(uiText("站点行为"), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        WebDavSwitchRow(uiText("SPA 回退到 index.html"), uiText("适合 Vue Router / VitePress / 单页应用刷新路径。"), spaFallback) { spaFallback = it }
        WebDavSwitchRow(uiText("允许目录列表"), uiText("没有 index.html 时显示目录文件；公网环境不建议开启。"), directoryListing) { directoryListing = it }
        WebDavSwitchRow(uiText("发布 mDNS"), uiText("在局域网内尝试发布 _http._tcp 服务，便于支持 mDNS 的设备发现。"), mdnsEnabled) { mdnsEnabled = it }
        if (mdnsEnabled) {
            OutlinedTextField(value = mdnsName, onValueChange = { mdnsName = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("mDNS 名称")) }, singleLine = true)
        }
    }

    KimiCardBox {
        Text(uiText("操作"), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    settings.saveMiniServerConfig(currentConfig())
                    refresh(uiText("微型服务器配置已保存"))
                },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(uiText("保存")) }
            Button(
                onClick = {
                    statusText = uiText("正在启动...")
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { miniServerManager.start(currentConfig(enabled = true)) }
                                .fold({ uiText("已启动：${it.url}") }, { uiText("启动失败：${it.message}") })
                        }
                        refresh(result)
                    }
                },
                shape = KimiPillShape,
                modifier = Modifier.weight(1f),
            ) { Text(if (status.running) uiText("重启") else uiText("启动")) }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { miniServerManager.stop() }
                            .fold({ uiText("已停止") }, { uiText("停止失败：${it.message}") })
                    }
                    refresh(result)
                }
            },
            shape = KimiPillShape,
            enabled = status.running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("停止服务")) }
    }
}

@Composable
internal fun MiniServerLogSettings(miniServerManager: MiniServerManager) {
    var revision by remember { mutableIntStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf("") }
    val payload = remember(revision, levelFilter) { miniServerManager.logsJson(200, levelFilter) }
    val logs = remember(payload) {
        payload.optJSONArray("logs")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(it) }
                }
            }
        }.orEmpty()
    }
    val levels = listOf("" to "ALL", "info" to "INFO", "warn" to "WARN", "error" to "ERROR")
    val terminalScroll = rememberScrollState()
    val terminalHorizontalScroll = rememberScrollState()
    val filterScroll = rememberScrollState()

    LaunchedEffect(levelFilter) {
        while (true) {
            delay(1_000)
            revision++
        }
    }

    LaunchedEffect(logs.size, levelFilter) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText("终端日志"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("自动跟随连接、资源加载、认证失败、404 和页面 JavaScript 报错。"),
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = {
                    miniServerManager.clearLogs()
                    revision++
                },
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = uiText("清空日志"), tint = MaterialTheme.colorScheme.primary)
            }
        }
        KimiDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(filterScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${if (payload.optBoolean("running")) "RUNNING" else "STOPPED"} · ${payload.optString("workspace")} · ${payload.optInt("count")} lines",
                color = KimiMuted,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            levels.forEach { (value, label) ->
                TextButton(
                    onClick = { levelFilter = value },
                    shape = KimiPillShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (levelFilter == value) "[$label]" else label,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 620.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101114))
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .horizontalScroll(terminalHorizontalScroll)
                    .verticalScroll(terminalScroll),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        uiText("$ lyra mini-server logs --follow\n# 暂无日志。启动微型服务器并访问站点后，这里会自动显示请求记录和客户端错误。"),
                        color = Color(0xFF8BE9FD),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    logs.forEach { log ->
                        MiniServerTerminalLine(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniServerTerminalLine(log: JSONObject) {
    val level = log.optString("level", "info")
    val color = when (level.lowercase(Locale.US)) {
        "error" -> Color(0xFFFF6B6B)
        "warn" -> Color(0xFFFFC857)
        else -> Color(0xFF7BD88F)
    }
    val time = remember(log.optLong("timestamp")) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.optLong("timestamp")))
    }
    val status = log.optInt("status").takeIf { it > 0 }?.toString().orEmpty()
    val method = log.optString("method")
    val path = log.optString("path")
    val durationMs = log.optLong("durationMs")
    val message = log.optString("message")
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$time ",
            color = Color(0xFF8D99AE),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            level.uppercase(Locale.US).padEnd(5),
            color = color,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                if (method.isNotBlank()) append(method).append(' ')
                if (status.isNotBlank()) append(status).append(' ')
                append(path.ifBlank { "-" })
                append(" (").append(durationMs).append("ms)")
                if (message.isNotBlank()) append(" - ").append(message)
            },
            color = Color(0xFFE8EAED),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
