package com.yukisoffd.lyracode.interaction.perception

import com.yukisoffd.lyracode.interaction.model.ScreenSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ScreenProbeState(
    val activeUntilEpochMillis: Long = 0L,
    val latestSnapshot: ScreenSnapshot? = null,
    val lastFailure: ScreenProbeFailureCode? = null,
) {
    fun isActive(nowEpochMillis: Long): Boolean = activeUntilEpochMillis > nowEpochMillis
}

internal enum class ScreenProbeFailureCode {
    SERVICE_DISCONNECTED,
    SERVICE_INTERRUPTED,
    NO_WINDOWS,
    NO_ROOT_NODES,
    SECURITY_RESTRICTION,
    CAPTURE_FAILED,
}

internal object ScreenProbeController {
    const val DEFAULT_DURATION_MILLIS = 30_000L

    private val _state = MutableStateFlow(ScreenProbeState())
    val state: StateFlow<ScreenProbeState> = _state.asStateFlow()

    fun start(
        nowEpochMillis: Long = System.currentTimeMillis(),
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
    ) {
        _state.value = ScreenProbeState(
            activeUntilEpochMillis = nowEpochMillis + durationMillis.coerceIn(1_000L, 60_000L),
        )
    }

    fun stop() {
        _state.value = _state.value.copy(activeUntilEpochMillis = 0L)
    }

    fun clear() {
        _state.value = ScreenProbeState()
    }

    fun expire(nowEpochMillis: Long = System.currentTimeMillis()) {
        if (_state.value.activeUntilEpochMillis in 1..nowEpochMillis) stop()
    }

    fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        expire(nowEpochMillis)
        return _state.value.isActive(nowEpochMillis)
    }

    fun publish(snapshot: ScreenSnapshot, nowEpochMillis: Long = System.currentTimeMillis()) {
        if (!isActive(nowEpochMillis)) return
        _state.value = _state.value.copy(latestSnapshot = snapshot, lastFailure = null)
    }

    fun fail(code: ScreenProbeFailureCode, stop: Boolean = false) {
        _state.value = _state.value.copy(
            activeUntilEpochMillis = if (stop) 0L else _state.value.activeUntilEpochMillis,
            lastFailure = code,
        )
    }
}
