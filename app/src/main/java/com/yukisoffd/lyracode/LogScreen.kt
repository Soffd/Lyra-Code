package com.yukisoffd.lyracode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yukisoffd.lyracode.data.AuditEntry
import com.yukisoffd.lyracode.data.AuditLogStore

@Composable
internal fun LogScreen(auditLogStore: AuditLogStore) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(auditLogStore.recent()) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedLog by remember { mutableStateOf<AuditEntry?>(null) }
    LaunchedEffect(refresh) { logs = auditLogStore.recent() }
    selectedLog?.let { entry ->
        LogDetailDialog(entry = entry, onDismiss = { selectedLog = null })
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.title_audit_log), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.cd_refresh_log))
            }
            IconButton(onClick = {
                auditLogStore.clear()
                refresh++
            }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = context.getString(R.string.cd_clear_log))
            }
        }
        if (logs.isEmpty()) {
            KimiCardBox {
                Text(context.getString(R.string.notice_no_log), style = MaterialTheme.typography.titleSmall)
                Text(context.getString(R.string.log_empty_hint), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(logs) { entry ->
                    LogCard(entry = entry, onClick = { selectedLog = entry })
                }
            }
        }
    }
}

@Composable
internal fun LogCard(entry: AuditEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 138.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${entry.kind} · ${formatTime(entry.createdAt)}", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.detail, color = KimiMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun LogDetailDialog(entry: AuditEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(context.getString(R.string.title_log_detail), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${entry.kind} · ${formatTime(entry.createdAt)}", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cd_close))
                }
            }
            KimiCardBox {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer {
                    Text(
                        entry.detail,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}