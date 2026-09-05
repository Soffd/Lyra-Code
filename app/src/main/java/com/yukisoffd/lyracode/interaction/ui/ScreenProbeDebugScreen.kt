package com.yukisoffd.lyracode.interaction.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.KimiCardBox
import com.yukisoffd.lyracode.KimiMuted
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.interaction.model.toDebugText
import com.yukisoffd.lyracode.interaction.perception.ScreenProbeController
import com.yukisoffd.lyracode.interaction.service.AccessibilityConnection
import com.yukisoffd.lyracode.interaction.session.ManualControlController
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
internal fun ScreenProbeDebugScreen(settings: AppSettings) {
    val context = LocalContext.current
    val probeState by ScreenProbeController.state.collectAsState()
    val connected by AccessibilityConnection.connected.collectAsState()
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(probeState.activeUntilEpochMillis) {
        while (probeState.activeUntilEpochMillis > nowEpochMillis) {
            delay(1_000L)
            nowEpochMillis = System.currentTimeMillis()
            ScreenProbeController.expire(nowEpochMillis)
        }
    }

    val active = probeState.isActive(nowEpochMillis)
    val remainingSeconds = ceil(
        (probeState.activeUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L) / 1_000.0,
    ).toInt()
    val canStart = connected && settings.deviceInteractionExperimentalEnabled

    KimiCardBox {
        Text(
            context.getString(R.string.screen_probe_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            context.getString(R.string.screen_probe_instructions),
            color = KimiMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            when {
                !connected -> context.getString(R.string.screen_probe_service_required)
                active -> context.resources.getQuantityString(
                    R.plurals.screen_probe_active,
                    remainingSeconds,
                    remainingSeconds,
                )
                else -> context.getString(R.string.screen_probe_idle)
            },
            color = if (active) MaterialTheme.colorScheme.primary else KimiMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    nowEpochMillis = System.currentTimeMillis()
                    ManualControlController.stop()
                    ScreenProbeController.start(nowEpochMillis)
                },
                modifier = Modifier.weight(1f),
                enabled = canStart && !active,
            ) {
                Text(context.getString(R.string.screen_probe_start))
            }
            OutlinedButton(
                onClick = { ScreenProbeController.stop() },
                modifier = Modifier.weight(1f),
                enabled = active,
            ) {
                Text(context.getString(R.string.screen_probe_stop))
            }
        }
        OutlinedButton(
            onClick = { ScreenProbeController.clear() },
            modifier = Modifier.fillMaxWidth(),
            enabled = probeState.latestSnapshot != null || probeState.lastFailure != null,
        ) {
            Text(context.getString(R.string.screen_probe_clear))
        }
    }

    probeState.lastFailure?.let { failure ->
        Text(
            context.getString(R.string.screen_probe_failure, failure.name),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    val snapshot = probeState.latestSnapshot
    if (snapshot == null) {
        KimiCardBox {
            Text(
                context.getString(R.string.screen_probe_no_snapshot),
                color = KimiMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        KimiCardBox {
            Text(
                context.getString(R.string.screen_probe_snapshot_summary),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                context.getString(
                    R.string.screen_probe_snapshot_stats,
                    snapshot.activePackage ?: context.getString(R.string.screen_probe_unknown_package),
                    snapshot.windows.size,
                    snapshot.nodes.size,
                    snapshot.uiFingerprint,
                ),
                color = KimiMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        KimiCardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    context.getString(R.string.screen_probe_semantic_tree),
                    style = MaterialTheme.typography.titleSmall,
                )
                SelectionContainer {
                    Text(
                        snapshot.toDebugText(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }

    Text(
        context.getString(R.string.screen_probe_memory_notice),
        color = KimiMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}
