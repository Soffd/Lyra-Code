package com.yukisoffd.lyracode.interaction.session

import com.yukisoffd.lyracode.interaction.model.DeviceActionResult
import com.yukisoffd.lyracode.interaction.model.DeviceActionStatus
import com.yukisoffd.lyracode.interaction.model.ManualActionSelection
import com.yukisoffd.lyracode.interaction.model.ManualDeviceAction
import com.yukisoffd.lyracode.interaction.model.ScreenSnapshot
import com.yukisoffd.lyracode.interaction.policy.DeviceActionPolicy
import com.yukisoffd.lyracode.interaction.policy.DevicePolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class ManualControlStatus {
    IDLE,
    OBSERVING,
    READY,
    TARGET_SELECTED,
    EXECUTING,
    VERIFYING,
    PAUSED_PACKAGE_CHANGED,
    FAILED,
    CANCELLED,
}

internal data class ManualControlState(
    val activeUntilEpochMillis: Long = 0L,
    val status: ManualControlStatus = ManualControlStatus.IDLE,
    val targetPackage: String? = null,
    val latestSnapshot: ScreenSnapshot? = null,
    val selection: ManualActionSelection? = null,
    val lastResult: DeviceActionResult? = null,
) {
    fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        activeUntilEpochMillis > nowEpochMillis
}

internal object ManualControlController {
    const val DEFAULT_DURATION_MILLIS = 120_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ManualControlState())
    val state: StateFlow<ManualControlState> = _state.asStateFlow()
    private var expiryJob: Job? = null

    @Synchronized
    fun start(
        nowEpochMillis: Long = System.currentTimeMillis(),
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
    ) {
        expiryJob?.cancel()
        val boundedDuration = durationMillis.coerceIn(10_000L, 180_000L)
        _state.value = ManualControlState(
            activeUntilEpochMillis = nowEpochMillis + boundedDuration,
            status = ManualControlStatus.OBSERVING,
        )
        expiryJob = scope.launch {
            delay(boundedDuration)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        expiryJob?.cancel()
        expiryJob = null
        _state.value = ManualControlState(status = ManualControlStatus.CANCELLED)
    }

    @Synchronized
    fun publish(snapshot: ScreenSnapshot) {
        val current = _state.value
        if (!current.isActive()) return
        val packageName = snapshot.activePackage.orEmpty()
        if (!DeviceActionPolicy.isPackageAllowed(packageName)) {
            _state.value = current.copy(
                status = ManualControlStatus.PAUSED_PACKAGE_CHANGED,
                targetPackage = null,
                latestSnapshot = null,
                selection = null,
            )
            return
        }

        if (
            current.targetPackage == packageName &&
            current.latestSnapshot?.uiFingerprint == snapshot.uiFingerprint
        ) {
            return
        }

        val packageChanged = current.targetPackage != null && packageName != current.targetPackage
        val keepSelection = !packageChanged && current.selection?.snapshotId == snapshot.snapshotId
        _state.value = current.copy(
            status = if (keepSelection) ManualControlStatus.TARGET_SELECTED else ManualControlStatus.READY,
            targetPackage = packageName,
            latestSnapshot = snapshot,
            selection = current.selection.takeIf { keepSelection },
        )
    }

    @Synchronized
    fun select(handle: String, action: ManualDeviceAction): Boolean {
        val current = _state.value
        val snapshot = current.latestSnapshot ?: return false
        val targetPackage = current.targetPackage ?: return false
        val node = snapshot.nodes.firstOrNull { it.handle == handle } ?: return false
        if (DeviceActionPolicy.evaluate(targetPackage, node, action) !is DevicePolicyDecision.Allowed) {
            return false
        }
        _state.value = current.copy(
            status = ManualControlStatus.TARGET_SELECTED,
            selection = ManualActionSelection(snapshot.snapshotId, handle, action, targetPackage),
        )
        return true
    }

    @Synchronized
    fun clearSelection() {
        val current = _state.value
        _state.value = current.copy(
            status = if (current.latestSnapshot == null) ManualControlStatus.OBSERVING else ManualControlStatus.READY,
            selection = null,
        )
    }

    @Synchronized
    fun beginExecution(): Pair<ManualActionSelection, ScreenSnapshot>? {
        val current = _state.value
        val selection = current.selection ?: return null
        val snapshot = current.latestSnapshot ?: return null
        if (selection.snapshotId != snapshot.snapshotId) return null
        _state.value = current.copy(status = ManualControlStatus.EXECUTING)
        return selection to snapshot
    }

    @Synchronized
    fun markVerifying() {
        _state.value = _state.value.copy(status = ManualControlStatus.VERIFYING)
    }

    @Synchronized
    fun finish(result: DeviceActionResult, snapshot: ScreenSnapshot? = null) {
        val current = _state.value
        val canContinue = current.isActive() && result.status != DeviceActionStatus.PACKAGE_CHANGED
        _state.value = current.copy(
            status = if (canContinue) ManualControlStatus.READY else ManualControlStatus.FAILED,
            latestSnapshot = snapshot ?: current.latestSnapshot,
            selection = null,
            lastResult = result,
        )
    }
}
