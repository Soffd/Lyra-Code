package com.yukisoffd.lyracode.interaction.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.yukisoffd.lyracode.interaction.model.ManualDeviceAction
import com.yukisoffd.lyracode.interaction.service.ManualControlCommandBridge
import com.yukisoffd.lyracode.interaction.session.ManualControlController

/** Receives explicit, app-internal commands from the isolated overlay process. */
class ManualControlActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sentAt = intent.getLongExtra(ManualControlOverlayProtocol.EXTRA_SENT_AT_ELAPSED, 0L)
        val transportMillis = if (sentAt > 0L) SystemClock.elapsedRealtime() - sentAt else -1L
        Log.i(LOG_TAG, "main_command_received action=${intent.action} transport_ms=$transportMillis pid=${Process.myPid()}")
        when (intent.action) {
            ManualControlOverlayProtocol.COMMAND_SELECT -> {
                val handle = intent.getStringExtra(ManualControlOverlayProtocol.EXTRA_HANDLE) ?: return
                val action = intent.getStringExtra(ManualControlOverlayProtocol.EXTRA_ACTION)
                    ?.let { name -> ManualDeviceAction.entries.firstOrNull { it.name == name } }
                    ?: return
                ManualControlController.select(handle, action)
            }
            ManualControlOverlayProtocol.COMMAND_CONFIRM -> ManualControlCommandBridge.requestConfirm()
            ManualControlOverlayProtocol.COMMAND_CLEAR_SELECTION -> ManualControlController.clearSelection()
            ManualControlOverlayProtocol.COMMAND_STOP -> ManualControlController.stop()
            ManualControlOverlayProtocol.SERVICE_STATE -> {
                if (intent.getBooleanExtra(ManualControlOverlayProtocol.EXTRA_RUNNING, false)) {
                    ManualControlForegroundConnection.markRunning()
                } else {
                    ManualControlForegroundConnection.markStopped()
                    if (ManualControlController.state.value.isActive()) ManualControlController.stop()
                }
            }
        }
    }

    private companion object {
        const val LOG_TAG = "LyraManualControl"
    }
}
