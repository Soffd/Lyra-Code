package com.yukisoffd.lyracode

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.BackupOptions
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.data.FileTransferServerConfig
import com.yukisoffd.lyracode.data.WebDavServerConfig
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.webdav.TransferProgress
import com.yukisoffd.lyracode.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Locale
import kotlin.math.max



@Composable
internal fun WebDavSettings(settings: AppSettings, webDavClient: WebDavClient, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.webDavServers() }
    var editing by remember { mutableStateOf<WebDavServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<WebDavServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<WebDavServerConfig>) {
        settings.saveWebDavServers(updated)
        revision++
    }

    editing?.let { server ->
        WebDavServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = uiText("WebDAV 已保存")
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = uiText("删除 WebDAV 配置"),
            message = uiText("该操作会删除此 WebDAV 服务器配置和保存的认证信息。"),
            targetName = server.name.ifBlank { server.url },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = uiText("已删除 ${server.name}")
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WebDAV", style = MaterialTheme.typography.titleMedium)
                Text(uiText("用于远程文件搜索、上传下载和云端备份。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultWebDavServer() }, shape = KimiPillShape) { Text(uiText("添加")) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text(uiText("暂无 WebDAV 服务器"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("添加后，AI 可在用户确认后把 WebDAV 文件下载到工作区，或把工作区文件上传到 WebDAV。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (server.hideAddressInDrawer) uiText("地址已隐藏") else server.url, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Text(server.username.ifBlank { uiText("匿名") } + " · " + server.initialPath.ifBlank { "/" }, color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.url.startsWith("http://", ignoreCase = true)) {
                        Text(uiText("安全提示：HTTP 明文连接可能泄露账号、密码和文件内容。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = uiText("正在测试 ${server.name}...")
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                webDavClient.test(server).fold(
                                    onSuccess = { uiText("WebDAV 测试成功，当前目录 ${it.size} 项") },
                                    onFailure = { uiText("WebDAV 测试失败：${it.message}") },
                                )
                            }
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("测试连接")) }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = uiText("编辑 WebDAV"))
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("删除 WebDAV"))
                }
            }
        }
    }
}

@Composable
internal fun WebDavServerDialog(
    initial: WebDavServerConfig,
    onDismiss: () -> Unit,
    onSave: (WebDavServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var url by rememberSaveable(initial.id) { mutableStateOf(initial.url) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var userAgent by rememberSaveable(initial.id) { mutableStateOf(initial.userAgent) }
    var initialPath by rememberSaveable(initial.id) { mutableStateOf(initial.initialPath) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var trustAll by rememberSaveable(initial.id) { mutableStateOf(initial.trustAllCertificates) }
    var multiThread by rememberSaveable(initial.id) { mutableStateOf(initial.multiThread) }
    var hideAddress by rememberSaveable(initial.id) { mutableStateOf(initial.hideAddressInDrawer) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("服务名")) }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("URL") }, singleLine = true)
                if (url.startsWith("http://", ignoreCase = true)) {
                    Text(uiText("HTTP 明文连接不安全，可能泄露账号密码和文件内容。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("用户名，可空")) }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("密码，可空")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(value = userAgent, onValueChange = { userAgent = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("自定义 UA，可空")) }, singleLine = true)
                OutlinedTextField(value = initialPath, onValueChange = { initialPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("初始路径")) }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("备注")) }, minLines = 2)
                WebDavSwitchRow(uiText("信任所有 HTTPS 证书"), uiText("仅用于自签名证书服务器；不建议在公网服务开启。"), trustAll) { trustAll = it }
                WebDavSwitchRow(uiText("启用多线程传输"), uiText("保存此偏好，后续大文件传输可按此策略扩展。"), multiThread) { multiThread = it }
                WebDavSwitchRow(uiText("在侧栏隐藏地址"), uiText("隐藏 URL 以避免旁人看到服务器地址。"), hideAddress) { hideAddress = it }
                WebDavSwitchRow(uiText("启用此服务器"), uiText("禁用后 AI 无法看到或调用该服务器。"), enabled) { enabled = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        WebDavServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { "WebDAV" },
                            url = url.trim(),
                            username = username.trim(),
                            password = password,
                            userAgent = userAgent.trim(),
                            initialPath = initialPath.ifBlank { "/" },
                            note = note,
                            trustAllCertificates = trustAll,
                            multiThread = multiThread,
                            hideAddressInDrawer = hideAddress,
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
internal fun WebDavSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun defaultWebDavServer(): WebDavServerConfig = WebDavServerConfig(
    id = AppSettings.newId(),
    name = "WebDAV",
    url = "",
    username = "",
    password = "",
    userAgent = "",
    initialPath = "/",
    note = "",
    trustAllCertificates = false,
    multiThread = true,
    hideAddressInDrawer = false,
    enabled = true,
)

@Composable
internal fun FileTransferSettings(settings: AppSettings, fileTransferClient: FileTransferClient, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.fileTransferServers() }
    var editing by remember { mutableStateOf<FileTransferServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<FileTransferServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<FileTransferServerConfig>) {
        settings.saveFileTransferServers(updated)
        revision++
    }

    editing?.let { server ->
        FileTransferServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = uiText("文件传输配置已保存")
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = uiText("删除文件传输配置"),
            message = uiText("该操作会删除此 ${server.protocol.uppercase(Locale.US)} 服务器配置和保存的认证信息。"),
            targetName = server.name.ifBlank { server.host },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = uiText("已删除 ${server.name}")
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FTP / FTPS / SFTP", style = MaterialTheme.typography.titleMedium)
                Text(uiText("用于远程文件搜索、上传和下载；AI 执行上传下载前仍需用户确认。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultFileTransferServer(AppSettings.FILE_TRANSFER_SFTP) }, shape = KimiPillShape) { Text(uiText("添加")) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text(uiText("暂无文件传输服务器"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("添加 FTP、FTPS 或 SFTP 后，AI 可列出远程目录、搜索文件，并在用户确认后下载到工作区或从工作区上传。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (server.hideAddressInDrawer) uiText("地址已隐藏") else "${server.protocol.uppercase(Locale.US)}://${server.host}:${server.port}",
                        color = KimiMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val auth = if (server.protocol == AppSettings.FILE_TRANSFER_SFTP && server.usePrivateKey) uiText("密钥登录") else server.username.ifBlank { uiText("匿名") }
                    Text("$auth · ${server.initialPath.ifBlank { "/" }}", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    if (server.protocol == AppSettings.FILE_TRANSFER_FTP) {
                        Text(uiText("安全提示：FTP 明文连接可能泄露账号、密码和文件内容。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = uiText("正在测试 ${server.name}...")
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                fileTransferClient.test(server).fold(
                                    onSuccess = { uiText("${server.protocol.uppercase(Locale.US)} 测试成功，当前目录 ${it.size} 项") },
                                    onFailure = { uiText("${server.protocol.uppercase(Locale.US)} 测试失败：${it.message}") },
                                )
                            }
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("测试连接")) }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = uiText("编辑文件传输"))
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("删除文件传输"))
                }
            }
        }
    }
}

@Composable
internal fun FileTransferServerDialog(
    initial: FileTransferServerConfig,
    onDismiss: () -> Unit,
    onSave: (FileTransferServerConfig) -> Unit,
) {
    var protocol by rememberSaveable(initial.id) { mutableStateOf(AppSettings.normalizeFileTransferProtocol(initial.protocol)) }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var host by rememberSaveable(initial.id) { mutableStateOf(initial.host) }
    var portText by rememberSaveable(initial.id) { mutableStateOf(initial.port.toString()) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var usePrivateKey by rememberSaveable(initial.id) { mutableStateOf(initial.usePrivateKey) }
    var privateKey by rememberSaveable(initial.id) { mutableStateOf(initial.privateKey) }
    var passphrase by rememberSaveable(initial.id) { mutableStateOf(initial.passphrase) }
    var initialPath by rememberSaveable(initial.id) { mutableStateOf(initial.initialPath) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var encoding by rememberSaveable(initial.id) { mutableStateOf(initial.encoding) }
    var passiveMode by rememberSaveable(initial.id) { mutableStateOf(initial.passiveMode) }
    var explicitFtps by rememberSaveable(initial.id) { mutableStateOf(initial.explicitFtps) }
    var multiThread by rememberSaveable(initial.id) { mutableStateOf(initial.multiThread) }
    var syncPermissions by rememberSaveable(initial.id) { mutableStateOf(initial.syncPermissions) }
    var hideAddress by rememberSaveable(initial.id) { mutableStateOf(initial.hideAddressInDrawer) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    var protocolMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("文件传输")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { protocolMenu = true }, shape = KimiPillShape) {
                        Text(protocol.uppercase(Locale.US))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = protocolMenu, onDismissRequest = { protocolMenu = false }) {
                        listOf(AppSettings.FILE_TRANSFER_SFTP, AppSettings.FILE_TRANSFER_FTP, AppSettings.FILE_TRANSFER_FTPS).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.uppercase(Locale.US)) },
                                onClick = {
                                    protocol = item
                                    portText = AppSettings.defaultFileTransferPort(item).toString()
                                    if (item == AppSettings.FILE_TRANSFER_SFTP && username == "anonymous") username = ""
                                    if (item != AppSettings.FILE_TRANSFER_SFTP && username.isBlank()) username = "anonymous"
                                    protocolMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("服务名")) }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("主机")) }, singleLine = true)
                OutlinedTextField(value = portText, onValueChange = { portText = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("端口")) }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (protocol == AppSettings.FILE_TRANSFER_SFTP) uiText("用户名") else uiText("用户名，可空")) }, singleLine = true)
                if (protocol == AppSettings.FILE_TRANSFER_SFTP) {
                    WebDavSwitchRow(uiText("使用密钥登录"), uiText("开启后使用私钥和可选口令登录 SFTP。"), usePrivateKey) { usePrivateKey = it }
                }
                if (protocol == AppSettings.FILE_TRANSFER_SFTP && usePrivateKey) {
                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("私钥内容")) }, minLines = 4)
                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("私钥口令，可空")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else {
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("密码，可空")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                if (protocol == AppSettings.FILE_TRANSFER_FTP) {
                    Text(uiText("FTP 是明文协议，建议只在可信局域网使用；公网或敏感文件请优先使用 SFTP/FTPS。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = initialPath, onValueChange = { initialPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("初始路径")) }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("备注")) }, minLines = 2)
                OutlinedTextField(value = encoding, onValueChange = { encoding = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("编码")) }, singleLine = true)
                if (protocol != AppSettings.FILE_TRANSFER_SFTP) {
                    WebDavSwitchRow(uiText("被动模式"), uiText("FTP/FTPS 推荐开启被动模式，兼容 NAT 和多数服务器。"), passiveMode) { passiveMode = it }
                }
                if (protocol == AppSettings.FILE_TRANSFER_FTPS) {
                    WebDavSwitchRow(uiText("显式 FTPS"), uiText("使用 AUTH TLS 升级连接；关闭后尝试隐式 FTPS。"), explicitFtps) { explicitFtps = it }
                }
                WebDavSwitchRow(uiText("启用多线程传输"), uiText("保存此偏好，后续大文件传输可按此策略扩展。"), multiThread) { multiThread = it }
                WebDavSwitchRow(uiText("传输时同步文件权限"), uiText("仅部分 SFTP 服务器支持。"), syncPermissions) { syncPermissions = it }
                WebDavSwitchRow(uiText("在侧栏隐藏地址"), uiText("隐藏主机地址以避免旁人看到服务器信息。"), hideAddress) { hideAddress = it }
                WebDavSwitchRow(uiText("启用此服务器"), uiText("禁用后 AI 无法看到或调用该服务器。"), enabled) { enabled = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedProtocol = AppSettings.normalizeFileTransferProtocol(protocol)
                    onSave(
                        FileTransferServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { normalizedProtocol.uppercase(Locale.US) },
                            protocol = normalizedProtocol,
                            host = host.trim(),
                            port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: AppSettings.defaultFileTransferPort(normalizedProtocol),
                            username = username.trim().ifBlank { if (normalizedProtocol == AppSettings.FILE_TRANSFER_SFTP) "" else "anonymous" },
                            password = password,
                            usePrivateKey = normalizedProtocol == AppSettings.FILE_TRANSFER_SFTP && usePrivateKey,
                            privateKey = privateKey,
                            passphrase = passphrase,
                            initialPath = initialPath.ifBlank { "/" },
                            note = note,
                            encoding = encoding.ifBlank { "UTF-8" },
                            passiveMode = passiveMode,
                            explicitFtps = explicitFtps,
                            multiThread = multiThread,
                            syncPermissions = syncPermissions,
                            hideAddressInDrawer = hideAddress,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
    )
}

internal fun defaultFileTransferServer(protocol: String): FileTransferServerConfig {
    val normalized = AppSettings.normalizeFileTransferProtocol(protocol)
    return FileTransferServerConfig(
        id = AppSettings.newId(),
        name = normalized.uppercase(Locale.US),
        protocol = normalized,
        host = "",
        port = AppSettings.defaultFileTransferPort(normalized),
        username = if (normalized == AppSettings.FILE_TRANSFER_SFTP) "" else "anonymous",
        password = "",
        usePrivateKey = false,
        privateKey = "",
        passphrase = "",
        initialPath = "/",
        note = "",
        encoding = "UTF-8",
        passiveMode = true,
        explicitFtps = true,
        multiThread = true,
        syncPermissions = false,
        hideAddressInDrawer = false,
        enabled = true,
    )
}

@Composable
internal fun BackupSettings(
    settings: AppSettings,
    webDavClient: WebDavClient,
    backupManager: BackupManager,
    status: String,
    onStatusChange: (String) -> Unit,
    onImportBackup: (String) -> Unit,
    onConfigChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val webDavServers = settings.webDavServers().filter { it.enabled }
    var includeProfile by rememberSaveable { mutableStateOf(true) }
    var includeConversations by rememberSaveable { mutableStateOf(true) }

    var includeModelProfiles by rememberSaveable { mutableStateOf(true) }
    var includeMcp by rememberSaveable { mutableStateOf(true) }
    var includeSsh by rememberSaveable { mutableStateOf(true) }
    var includePrompts by rememberSaveable { mutableStateOf(true) }
    var includeMemories by rememberSaveable { mutableStateOf(true) }
    var includeSkills by rememberSaveable { mutableStateOf(true) }
    var includeWebDav by rememberSaveable { mutableStateOf(true) }
    var includeFileTransfer by rememberSaveable { mutableStateOf(true) }
    var includeSecrets by rememberSaveable { mutableStateOf(false) }
    var selectedServerId by rememberSaveable { mutableStateOf(webDavServers.firstOrNull()?.id.orEmpty()) }
    var remotePath by rememberSaveable { mutableStateOf(DEFAULT_WEBDAV_BACKUP_PATH) }
    var transferStatus by remember { mutableStateOf("") }
    val selectedServer = webDavServers.firstOrNull { it.id == selectedServerId } ?: webDavServers.firstOrNull()

    fun options() = BackupOptions(
        includeProfile = includeProfile,
        includeConversations = includeConversations,

        includeModelProfiles = includeModelProfiles,
        includeMcp = includeMcp,
        includeSsh = includeSsh,
        includePrompts = includePrompts,
        includeMemories = includeMemories,
        includeSkills = includeSkills,
        includeWebDav = includeWebDav,
        includeFileTransfer = includeFileTransfer,
        includeSecrets = includeSecrets,
    )

    KimiCardBox {
        Text(uiText("导出内容"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("可单独选择导出范围；跨版本导入时会跳过不兼容结构并导入可兼容部分。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        BackupIncludeRow(uiText("个人资料"), includeProfile) { includeProfile = it }
        BackupIncludeRow(uiText("对话历史"), includeConversations) { includeConversations = it }

        BackupIncludeRow(uiText("模型服务配置"), includeModelProfiles) { includeModelProfiles = it }
        BackupIncludeRow(uiText("MCP 服务器配置"), includeMcp) { includeMcp = it }
        BackupIncludeRow(uiText("SSH 连接配置"), includeSsh) { includeSsh = it }
        BackupIncludeRow(uiText("系统提示词"), includePrompts) { includePrompts = it }
        BackupIncludeRow(uiText("个性化记忆"), includeMemories) { includeMemories = it }
        BackupIncludeRow("Skills", includeSkills) { includeSkills = it }
        BackupIncludeRow(uiText("WebDAV 配置"), includeWebDav) { includeWebDav = it }
        BackupIncludeRow(uiText("文件传输配置"), includeFileTransfer) { includeFileTransfer = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("包含 API Key / 密码 / 私钥"), style = MaterialTheme.typography.titleSmall)
                Text(uiText("包含密钥的备份可直接导入使用，但必须妥善保管，不要分享给他人。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = includeSecrets, onCheckedChange = { includeSecrets = it })
        }
        Button(
            onClick = {
                scope.launch {
                    onStatusChange(uiText("正在导出到 Download/LyraCode..."))
                    onStatusChange(withContext(Dispatchers.IO) {
                        runCatching { backupManager.exportToDownloads(options()) }
                            .fold({ it }, { uiText("导出失败：${it.message}") })
                    })
                }
            },
            shape = KimiPillShape,
        ) { Text(uiText("导出到本地")) }
    }

    KimiCardBox {
        Text(uiText("导入备份"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("补充模式会在现有数据上新增并去重，推荐使用。覆盖模式会替换已有兼容配置，存在数据丢失风险。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onImportBackup("supplement") }, shape = KimiPillShape) { Text(uiText("补充导入")) }
            OutlinedButton(onClick = { onImportBackup("replace") }, shape = KimiPillShape) { Text(uiText("覆盖导入")) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    KimiCardBox {
        Text(uiText("WebDAV 云备份"), style = MaterialTheme.typography.titleMedium)
        if (webDavServers.isEmpty()) {
            Text(uiText("暂无启用的 WebDAV 服务器。先在 WebDAV 设置中添加服务器后，可直接上传或下载备份。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            WebDavServerPicker(webDavServers, selectedServerId) { selectedServerId = it }
            OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("远程备份路径")) }, singleLine = true)
            if (transferStatus.isNotBlank()) Text(transferStatus, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val server = selectedServer ?: return@OutlinedButton
                        scope.launch {
                            onStatusChange(uiText("正在导出并上传 WebDAV..."))
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val bytes = backupManager.exportZip(options())
                                    val targetPath = remotePath.ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
                                    webDavClient.upload(server, targetPath, bytes) { progress ->
                                        scope.launch { transferStatus = formatTransferProgress(progress) }
                                    }
                                    uiText("已上传到 ${server.name}:$targetPath")
                                }.fold({ it }, { uiText("上传失败：${it.message}") })
                            }
                            transferStatus = ""
                            onStatusChange(result)
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("上传备份")) }
                OutlinedButton(
                    onClick = {
                        val server = selectedServer ?: return@OutlinedButton
                        scope.launch {
                            onStatusChange(uiText("正在从 WebDAV 下载并补充导入..."))
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val requested = remotePath.trim().ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
                                    var usedPath = requested
                                    val bytes = runCatching {
                                        webDavClient.download(server, requested) { progress ->
                                            scope.launch { transferStatus = formatTransferProgress(progress) }
                                        }
                                    }.getOrElse {
                                        usedPath = resolveLatestWebDavBackupPath(webDavClient, server, requested)
                                        webDavClient.download(server, usedPath) { progress ->
                                            scope.launch { transferStatus = formatTransferProgress(progress) }
                                        }
                                    }
                                    uiText("从 ") + usedPath + uiText(" 补充导入：") + backupManager.importZip(bytes, "supplement")
                                }.fold({ uiText("导入完成：$it") }, { uiText("导入失败：${it.message}") })
                            }
                            transferStatus = ""
                            onStatusChange(result)
                            onConfigChanged()
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("从云端导入")) }
            }
        }
    }
}

private const val DEFAULT_WEBDAV_BACKUP_PATH = "/LyraCode/lyra_backup_latest.zip"

private fun resolveLatestWebDavBackupPath(client: WebDavClient, server: WebDavServerConfig, rawPath: String): String {
    val requested = rawPath.trim().ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
    val directory = requested.substringBeforeLast('/', "/").ifBlank { "/" }.let { if (it.endsWith("/")) it else "$it/" }
    val candidates = client.list(server, directory, depth = 1)
        .filter {
            val name = it.path.substringAfterLast('/')
            name.endsWith(".zip", ignoreCase = true) && name.contains("lyra_backup", ignoreCase = true)
        }
        .sortedWith(compareByDescending<com.yukisoffd.lyracode.webdav.WebDavFile> { it.modified }.thenByDescending { it.path.substringAfterLast('/') })
    return candidates.firstOrNull()?.path ?: requested
}

@Composable
internal fun BackupIncludeRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun WebDavServerPicker(servers: List<WebDavServerConfig>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = KimiPillShape) {
            Text(selected?.name ?: uiText("选择 WebDAV"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.name) },
                    onClick = {
                        onSelect(server.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun formatTransferProgress(progress: TransferProgress): String {
    val total = if (progress.totalBytes > 0) formatBytes(progress.totalBytes) else uiText("未知大小")
    val percent = if (progress.totalBytes > 0) " · ${(progress.doneBytes * 100 / progress.totalBytes).coerceIn(0, 100)}%" else ""
    return "${progress.title}: ${formatBytes(progress.doneBytes)} / $total$percent · ${formatBytes(progress.bytesPerSecond)}/s"
}

@Composable
internal fun SshSettings(settings: AppSettings, sshExecutor: SshExecutor, externalRevision: Int = 0) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val servers = remember(revision, externalRevision) { settings.sshServers() }
    var editing by remember { mutableStateOf<SshServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<SshServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    fun saveServers(updated: List<SshServerConfig>) {
        settings.saveSshServers(updated)
        revision++
    }

    editing?.let { server ->
        SshServerDialog(
            initial = server,
            onDismiss = { editing = null },
            onSave = { saved ->
                val updated = servers.toMutableList()
                val index = updated.indexOfFirst { it.id == saved.id }
                if (index >= 0) updated[index] = saved else updated += saved
                saveServers(updated)
                editing = null
                status = uiText("SSH 连接已保存")
            },
        )
    }
    deleteTarget?.let { server ->
        ConfirmDeleteDialog(
            title = uiText("删除 SSH 连接"),
            message = uiText("该操作会删除服务器地址、用户名、密码或私钥配置。"),
            targetName = "${server.name} · ${server.stableId}",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                saveServers(servers.filterNot { it.id == server.id })
                status = uiText("已删除 ${server.name}")
            },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("SSH 连接"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("用于连接 Git 服务器或公网 Linux/Windows 服务器。命令执行前会弹出确认。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultSshServer() }, shape = KimiPillShape) { Text(uiText("添加")) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (servers.isEmpty()) {
        KimiCardBox {
            Text(uiText("暂无 SSH 连接"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("可使用密码或私钥连接 GitHub/GitLab 服务器、VPS、云主机或局域网主机。配置会加密保存。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    servers.forEach { server ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.stableId, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    Text("${server.username} · ${sshAuthLabel(server.authType)} · ${server.timeoutSeconds}s", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { enabled ->
                        saveServers(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        status = uiText("正在测试 ${server.name}...")
                        scope.launch {
                            val result = sshExecutor.execute(
                                server = server,
                                command = "printf 'lyra_ssh_ok\\n' && uname -a 2>/dev/null || ver",
                                cwd = "",
                                inputLines = emptyList(),
                                timeoutSeconds = 15,
                            )
                            status = if (result.ok) uiText("SSH 测试成功: ${server.stableId}") else result.message.take(200)
                        }
                    },
                    shape = KimiPillShape,
                ) { Text(uiText("测试连接")) }
                IconButton(onClick = { editing = server }) {
                    Icon(Icons.Default.Edit, contentDescription = uiText("编辑 SSH"))
                }
                IconButton(onClick = { deleteTarget = server }) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("删除 SSH"))
                }
            }
        }
    }

    KimiCardBox {
        Text(uiText("使用约束"), style = MaterialTheme.typography.titleSmall)
        Text(
            uiText("AI 使用 SSH 执行命令会像文件修改一样请求确认。安装软件或修改服务器前，AI 应先检查系统、CPU/GPU、内存、磁盘和权限。复杂交互式 shell（如 vim/top/交互 ssh）不适合由内置 SSH 工具执行。"),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun SshServerDialog(
    initial: SshServerConfig,
    onDismiss: () -> Unit,
    onSave: (SshServerConfig) -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var host by rememberSaveable(initial.id) { mutableStateOf(initial.host) }
    var port by rememberSaveable(initial.id) { mutableStateOf(initial.port.toString()) }
    var username by rememberSaveable(initial.id) { mutableStateOf(initial.username) }
    var authType by rememberSaveable(initial.id) { mutableStateOf(initial.authType.ifBlank { AppSettings.SSH_AUTH_PASSWORD }) }
    var password by rememberSaveable(initial.id) { mutableStateOf(initial.password) }
    var privateKey by rememberSaveable(initial.id) { mutableStateOf(initial.privateKey) }
    var passphrase by rememberSaveable(initial.id) { mutableStateOf(initial.passphrase) }
    var timeout by rememberSaveable(initial.id) { mutableStateOf(initial.timeoutSeconds.toString()) }
    var enabled by rememberSaveable(initial.id) { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("SSH 连接")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("显示名称")) }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("主机/IP")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.weight(1f), label = { Text(uiText("端口")) }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.weight(2f), label = { Text(uiText("用户名")) }, singleLine = true)
                }
                Text(uiText("认证方式"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaterialChoiceButton(uiText("密码"), authType == AppSettings.SSH_AUTH_PASSWORD) { authType = AppSettings.SSH_AUTH_PASSWORD }
                    MaterialChoiceButton(uiText("私钥"), authType == AppSettings.SSH_AUTH_KEY) { authType = AppSettings.SSH_AUTH_KEY }
                }
                if (authType == AppSettings.SSH_AUTH_PASSWORD) {
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("密码")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                } else {
                    OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("私钥内容")) }, minLines = 5, maxLines = 10)
                    OutlinedTextField(value = passphrase, onValueChange = { passphrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("私钥口令（可空）")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                OutlinedTextField(value = timeout, onValueChange = { timeout = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text(uiText("默认超时秒数")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("启用此连接"), style = MaterialTheme.typography.titleSmall)
                        Text(uiText("禁用后 AI 无法看到或调用该服务器。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(uiText("固定标识将使用 ") + host.ifBlank { "host" } + ":" + port.ifBlank { "22" } + uiText("，AI 调用 SSH 工具时会使用这个标识。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SshServerConfig(
                            id = initial.id.ifBlank { AppSettings.newId() },
                            name = name.ifBlank { host.ifBlank { "SSH Server" } },
                            host = host.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                            username = username.trim(),
                            authType = authType,
                            password = password,
                            privateKey = privateKey,
                            passphrase = passphrase,
                            timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 600) ?: 60,
                            enabled = enabled,
                        ),
                    )
                },
            ) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
    )
}

internal fun defaultSshServer(): SshServerConfig = SshServerConfig(
    id = AppSettings.newId(),
    name = "SSH Server",
    host = "",
    port = 22,
    username = "",
    authType = AppSettings.SSH_AUTH_PASSWORD,
    password = "",
    privateKey = "",
    passphrase = "",
    timeoutSeconds = 60,
    enabled = true,
)

internal fun sshAuthLabel(authType: String): String = when (authType) {
    AppSettings.SSH_AUTH_KEY -> uiText("私钥")
    else -> uiText("密码")
}

