package com.yukisoffd.lyracode.ssh

import android.content.Context
import android.util.Log
import com.yukisoffd.lyracode.debian.ProotLinuxManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** App-long local Debian shell session backed by the `script` utility's PTY. */
class LocalProotTerminalSessionManager(
    context: Context,
    private val workspaceRoot: () -> String?,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime = ProotLinuxManager.getInstance(context)
    private val connections = ConcurrentHashMap<String, LocalProotTerminalConnection>()
    private val deleteListener = runtime.addBeforeDeleteListener(::close)
    private val runtimeJob = scope.launch {
        runtime.state.collect { state ->
            val activeIds = state.instances.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
            connections.keys.filterNot { it in activeIds }.forEach(::close)
        }
    }

    fun connection(linuxId: String): LocalProotTerminalConnection = connections.getOrPut(linuxId) {
        LocalProotTerminalConnection(linuxId, runtime, workspaceRoot, scope)
    }

    fun close(linuxId: String) {
        connections.remove(linuxId)?.close()
    }

    override fun close() {
        connections.values.forEach(LocalProotTerminalConnection::close)
        connections.clear()
        runtimeJob.cancel()
        deleteListener.close()
        scope.cancel()
    }
}

class LocalProotTerminalConnection internal constructor(
    private val linuxId: String,
    private val runtime: ProotLinuxManager,
    private val workspaceRoot: () -> String?,
    private val scope: CoroutineScope,
) : TerminalSession {
    @Volatile private var process: Process? = null
    @Volatile private var requestedClose = false
    @Volatile private var cols = 80
    @Volatile private var rows = 24
    private val generation = AtomicLong(0L)
    private var processJob: Job? = null
    private val terminal = TerminalEmulator(cols, rows)
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(SshTerminalState())
    override val state: StateFlow<SshTerminalState> = _state.asStateFlow()
    private val _screen = MutableStateFlow(terminal.snapshot())
    override val screen: StateFlow<TerminalSnapshot> = _screen.asStateFlow()
    private val writerJob = scope.launch {
        for (bytes in writeQueue) {
            val active = process ?: continue
            runCatching {
                active.outputStream.write(bytes)
                active.outputStream.flush()
            }.onFailure { Log.e(TAG, "Local PTY write failed", it) }
        }
    }

    override fun connect() {
        if (process?.isAlive == true || processJob?.isActive == true) return
        requestedClose = false
        val myGeneration = generation.incrementAndGet()
        processJob = scope.launch {
            _state.value = SshTerminalState(SshTerminalStatus.CONNECTING, SshTerminalMessage.CONNECTING)
            try {
                val started = runtime.startInteractiveShell(linuxId, workspaceRoot(), cols, rows)
                if (generation.get() != myGeneration || requestedClose) {
                    started.destroyForcibly()
                    return@launch
                }
                process = started
                _state.value = SshTerminalState(
                    SshTerminalStatus.CONNECTED,
                    SshTerminalMessage.CONNECTED,
                    connectedAt = System.currentTimeMillis(),
                )
                val buffer = ByteArray(16 * 1024)
                started.inputStream.use { input ->
                    while (!requestedClose && started.isAlive) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) {
                            terminal.accept(buffer.copyOf(count))
                            _screen.value = terminal.snapshot()
                        }
                    }
                }
                if (!requestedClose && generation.get() == myGeneration) {
                    _state.value = SshTerminalState(SshTerminalStatus.DISCONNECTED, SshTerminalMessage.DISCONNECTED)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to start local Debian terminal", error)
                if (!requestedClose && generation.get() == myGeneration) {
                    _state.value = SshTerminalState(SshTerminalStatus.ERROR, SshTerminalMessage.CONNECTION_FAILED)
                }
            } finally {
                process = null
            }
        }
    }

    override fun write(bytes: ByteArray): Boolean {
        if (process?.isAlive != true) return false
        return writeQueue.trySend(bytes.copyOf()).isSuccess
    }

    override fun write(text: String): Boolean = write(text.toByteArray(Charsets.UTF_8))

    @Synchronized
    override fun resize(newCols: Int, newRows: Int, widthPx: Int, heightPx: Int) {
        // The local PTY size is selected before Bash starts, matching the SSH session policy.
        if (process?.isAlive == true) return
        cols = newCols.coerceIn(2, 500)
        rows = newRows.coerceIn(2, 300)
        terminal.resize(cols, rows)
        _screen.value = terminal.snapshot()
    }

    override fun disconnect(markRequested: Boolean) {
        requestedClose = markRequested
        generation.incrementAndGet()
        processJob?.cancel()
        processJob = null
        process?.let { active ->
            runCatching { active.outputStream.close() }
            active.destroy()
            if (active.isAlive) active.destroyForcibly()
        }
        process = null
        if (markRequested) {
            _state.value = SshTerminalState(SshTerminalStatus.DISCONNECTED, SshTerminalMessage.DISCONNECTED)
        }
    }

    override fun close() {
        writeQueue.close()
        writerJob.cancel()
        disconnect(true)
    }

    private companion object {
        const val TAG = "LyraTerminalDebian"
    }
}
