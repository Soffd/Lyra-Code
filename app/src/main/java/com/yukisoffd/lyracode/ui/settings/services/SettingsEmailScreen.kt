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
                status = uiText(R.string.ui_email_server_saved)
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(uiText(R.string.ui_delete_email_server)) },
            text = { Text(uiText(R.string.ui_this_will_delete_1_s_and_its_encrypted_login, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    settings.deleteEmailServer(target.id)
                    revision++
                    deleteTarget = null
                }) { Text(uiText(R.string.file_action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(uiText(R.string.action_cancel)) } },
        )
    }

    KimiCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(R.string.menu_email), style = MaterialTheme.typography.titleMedium)
                Text(uiText(R.string.ui_passwords_are_encrypted_on_this_device_reading_a_message), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { editing = defaultEmailServer() }, shape = KimiPillShape) { Text(uiText(R.string.action_add)) }
        }
        if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }

    if (accounts.isEmpty()) {
        KimiCardBox {
            Text(uiText(R.string.ui_no_email_servers_yet), style = MaterialTheme.typography.titleSmall)
            Text(uiText(R.string.ui_use_an_app_specific_password_from_your_email_provider), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
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
                        Text(uiText(R.string.ui_security_warning_this_connection_is_unencrypted_so_account_cr), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(
                    checked = account.enabled,
                    onCheckedChange = { enabled -> settings.setEmailServerEnabled(account.id, enabled); revision++ },
                )
                IconButton(onClick = { editing = account }) { Icon(Icons.Default.Edit, contentDescription = uiText(R.string.ui_edit)) }
                IconButton(onClick = { deleteTarget = account }) { Icon(Icons.Default.Delete, contentDescription = uiText(R.string.file_action_delete)) }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        status = uiText(R.string.ui_testing_imap_connection)
                        status = withContext(Dispatchers.IO) {
                            runCatching { client.listFolders(account); uiText(R.string.ui_imap_connection_successful) }
                                .getOrElse { uiText(R.string.ui_connection_failed_1_s, it.message) }
                        }
                    }
                },
                shape = KimiPillShape,
            ) { Text(uiText(R.string.ui_test_imap)) }
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
        title = { Text(uiText(R.string.detail_email)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(uiText(R.string.file_name_label)) }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(uiText(R.string.ui_email_address)) }, singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(uiText(R.string.ui_login_username_leave_blank_to_use_the_email_address)) }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(uiText(R.string.ui_password_app_specific_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(imapHost, { imapHost = it }, Modifier.fillMaxWidth(), label = { Text("IMAP Host") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(imapPort, { imapPort = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text(uiText(R.string.label_port)) }, singleLine = true)
                    SecurityField(imapSecurity, { imapSecurity = it }, Modifier.weight(1f))
                }
                OutlinedTextField(smtpHost, { smtpHost = it }, Modifier.fillMaxWidth(), label = { Text("SMTP Host") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(smtpPort, { smtpPort = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text(uiText(R.string.label_port)) }, singleLine = true)
                    SecurityField(smtpSecurity, { smtpSecurity = it }, Modifier.weight(1f))
                }
                Text(uiText(R.string.ui_enter_ssl_starttls_or_none_ssl_is_recommended_use), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalizedEmail = email.trim()
                val imap = imapPort.toIntOrNull()
                val smtp = smtpPort.toIntOrNull()
                if (!normalizedEmail.contains('@') || imap == null || imap !in 1..65535 || smtp == null || smtp !in 1..65535 || password.isBlank() || imapHost.isBlank() || smtpHost.isBlank()) {
                    error = uiText(R.string.ui_enter_a_valid_email_address_password_server_and_port)
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
            }) { Text(uiText(R.string.file_editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText(R.string.action_cancel)) } },
    )
}

@Composable
private fun SecurityField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier, label = { Text(uiText(R.string.ui_security_mode)) }, singleLine = true)
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
