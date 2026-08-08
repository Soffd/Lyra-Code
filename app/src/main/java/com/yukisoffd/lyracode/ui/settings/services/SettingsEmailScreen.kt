package com.yukisoffd.lyracode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.EmailServerConfig
import com.yukisoffd.lyracode.email.EmailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EmailSettings(settings: AppSettings, externalRevision: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { EmailClient(context) }
    var revision by remember { mutableIntStateOf(0) }
    val accounts = remember(revision, externalRevision) { settings.emailServers() }
    var editing by remember { mutableStateOf<EmailServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<EmailServerConfig?>(null) }
    var status by remember { mutableStateOf("") }

    editing?.let { initial ->
        EmailServerDialog(
            initial = initial,
            onDismiss = { editing = null },
            onSave = { saved ->
                settings.upsertEmailServer(saved)
                revision++
                editing = null
                status = uiText("邮件服务器已保存")
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(uiText("删除邮件服务器")) },
            text = { Text(uiText("将删除 ${target.name} 及其加密保存的登录密码。")) },
            confirmButton = {
                TextButton(onClick = {
                    settings.deleteEmailServer(target.id)
                    revision++
                    deleteTarget = null
                }) { Text(uiText("删除"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(uiText("取消")) } },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("SMTP / IMAP 邮件"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("密码加密保存在本机。读取正文不会改变已读状态；SMTP 发送每次都要求确认。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultEmailServer() }, shape = KimiPillShape) { Text(uiText("添加")) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (accounts.isEmpty()) {
        KimiCardBox {
            Text(uiText("暂无邮件服务器"), style = MaterialTheme.typography.titleSmall)
            Text(uiText("建议使用邮箱服务商生成的应用专用密码，并启用 SSL/TLS。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    accounts.forEach { account ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    Text(account.emailAddress, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                    Text("IMAP ${account.imapHost}:${account.imapPort} · SMTP ${account.smtpHost}:${account.smtpPort}", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
                    if (account.imapSecurity == AppSettings.EMAIL_SECURITY_NONE || account.smtpSecurity == AppSettings.EMAIL_SECURITY_NONE) {
                        Text(uiText("安全警告：连接未加密，账号、密码和邮件内容可能被窃听。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = account.enabled,
                    onCheckedChange = { enabled -> settings.setEmailServerEnabled(account.id, enabled); revision++ },
                )
                IconButton(onClick = { editing = account }) { Icon(Icons.Default.Edit, contentDescription = uiText("编辑")) }
                IconButton(onClick = { deleteTarget = account }) { Icon(Icons.Default.Delete, contentDescription = uiText("删除")) }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        status = uiText("正在测试 IMAP 连接…")
                        status = withContext(Dispatchers.IO) {
                            runCatching { client.listFolders(account); uiText("IMAP 连接成功") }
                                .getOrElse { uiText("连接失败：${it.message}") }
                        }
                    }
                },
                shape = KimiPillShape,
            ) { Text(uiText("测试 IMAP")) }
        }
    }
}

@Composable
private fun EmailServerDialog(
    initial: EmailServerConfig,
    onDismiss: () -> Unit,
    onSave: (EmailServerConfig) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var email by remember(initial.id) { mutableStateOf(initial.emailAddress) }
    var username by remember(initial.id) { mutableStateOf(initial.username) }
    var password by remember(initial.id) { mutableStateOf(initial.password) }
    var imapHost by remember(initial.id) { mutableStateOf(initial.imapHost) }
    var imapPort by remember(initial.id) { mutableStateOf(initial.imapPort.toString()) }
    var imapSecurity by remember(initial.id) { mutableStateOf(initial.imapSecurity) }
    var smtpHost by remember(initial.id) { mutableStateOf(initial.smtpHost) }
    var smtpPort by remember(initial.id) { mutableStateOf(initial.smtpPort.toString()) }
    var smtpSecurity by remember(initial.id) { mutableStateOf(initial.smtpSecurity) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("邮件服务器")) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(uiText("名称")) }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(uiText("邮箱地址")) }, singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(uiText("登录用户名（留空则使用邮箱地址）")) }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(uiText("密码 / 应用专用密码")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(imapHost, { imapHost = it }, Modifier.fillMaxWidth(), label = { Text("IMAP Host") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(imapPort, { imapPort = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text(uiText("端口")) }, singleLine = true)
                    SecurityField(imapSecurity, { imapSecurity = it }, Modifier.weight(1f))
                }
                OutlinedTextField(smtpHost, { smtpHost = it }, Modifier.fillMaxWidth(), label = { Text("SMTP Host") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(smtpPort, { smtpPort = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text(uiText("端口")) }, singleLine = true)
                    SecurityField(smtpSecurity, { smtpSecurity = it }, Modifier.weight(1f))
                }
                Text(uiText("安全模式填写 ssl、starttls 或 none。推荐 ssl；none 仅用于受信任的本地测试服务器。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalizedEmail = email.trim()
                val imap = imapPort.toIntOrNull()
                val smtp = smtpPort.toIntOrNull()
                if (!normalizedEmail.contains('@') || imap == null || imap !in 1..65535 || smtp == null || smtp !in 1..65535 || password.isBlank() || imapHost.isBlank() || smtpHost.isBlank()) {
                    error = uiText("请填写有效邮箱、密码、服务器和端口。")
                    return@TextButton
                }
                onSave(
                    initial.copy(
                        name = name.trim().ifBlank { normalizedEmail },
                        emailAddress = normalizedEmail,
                        username = username.trim().ifBlank { normalizedEmail },
                        password = password,
                        imapHost = imapHost.trim(),
                        imapPort = imap!!,
                        imapSecurity = AppSettings.normalizeEmailSecurity(imapSecurity),
                        smtpHost = smtpHost.trim(),
                        smtpPort = smtp!!,
                        smtpSecurity = AppSettings.normalizeEmailSecurity(smtpSecurity),
                    ),
                )
            }) { Text(uiText("保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("取消")) } },
    )
}

@Composable
private fun SecurityField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier, label = { Text(uiText("安全模式")) }, singleLine = true)
}

private fun defaultEmailServer() = EmailServerConfig(
    id = AppSettings.newId(),
    name = "",
    emailAddress = "",
    username = "",
    password = "",
    imapHost = "",
    imapPort = 993,
    imapSecurity = AppSettings.EMAIL_SECURITY_SSL,
    smtpHost = "",
    smtpPort = 465,
    smtpSecurity = AppSettings.EMAIL_SECURITY_SSL,
)
