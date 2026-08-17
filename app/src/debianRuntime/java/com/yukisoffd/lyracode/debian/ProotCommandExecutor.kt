package com.yukisoffd.lyracode.debian

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class ProotCommandExecutor(context: Context) {
    private val manager = ProotLinuxManager.getInstance(context)

    fun isAvailable(): Boolean = manager.hasActiveInstances()

    fun inventoryForAgent(): String = manager.inventoryForAgent()

    fun hasAllFilesAccess(): Boolean = manager.hasAllFilesAccess()

    suspend fun execute(
        linuxId: String,
        command: String,
        workspaceRoot: String?,
        workDir: String?,
        timeoutSeconds: Int,
    ): String = withContext(Dispatchers.IO) {
        require(linuxId.isNotBlank()) { "proot_command requires linux_id." }
        require(command.isNotBlank()) { "proot_command requires a non-empty command." }
        val workContext = resolveWorkContext(workspaceRoot, workDir)
        val process = manager.startCommand(linuxId, workContext.workspaceRoot, workContext.guestWorkDir, command)
        process.outputStream.close()

        val readers = Executors.newFixedThreadPool(2)
        val stdoutFuture = readers.submit<CapturedOutput> { capture(process.inputStream) }
        val stderrFuture = readers.submit<CapturedOutput> { capture(process.errorStream) }
        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        val stdout = runCatching { stdoutFuture.get(5, TimeUnit.SECONDS) }.getOrElse { CapturedOutput("", 0, false) }
        val stderr = runCatching { stderrFuture.get(5, TimeUnit.SECONDS) }.getOrElse { CapturedOutput("", 0, false) }
        readers.shutdownNow()
        if (!finished) {
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }

        JSONObject()
            .put("exit_code", if (finished) process.exitValue() else 124)
            .put("stdout", stdout.text)
            .put("stderr", stderr.text)
            .put("stdout_original_bytes", stdout.originalBytes)
            .put("stderr_original_bytes", stderr.originalBytes)
            .put("stdout_truncated", stdout.truncated)
            .put("stderr_truncated", stderr.truncated)
            .put("timed_out", !finished)
            .put("environment", "internal-proot-linux")
            .put("linux_id", linuxId)
            .put("work_dir", workContext.guestWorkDir)
            .put("shared_storage_mounted", manager.hasAllFilesAccess())
            .toString()
    }

    private fun resolveWorkContext(workspaceRoot: String?, rawWorkDir: String?): ProotWorkContext {
        val workspace = workspaceRoot?.trim()?.takeIf(String::isNotBlank)?.let(::File)?.canonicalFile
            ?.takeIf(File::isDirectory)
        val raw = rawWorkDir.orEmpty().trim().replace('\\', '/')
        if (raw.isBlank() || raw == "." || raw == "./") {
            return ProotWorkContext(workspace, if (workspace == null) "/root" else WORKSPACE_DIR)
        }

        if (raw == WORKSPACE_DIR || raw.startsWith("$WORKSPACE_DIR/")) {
            require(workspace != null) { "workDir uses /workspace but no directly accessible workspace is selected." }
            val relative = raw.removePrefix(WORKSPACE_DIR).trim('/')
            validateRelative(relative)
            val hostDirectory = File(workspace, relative).canonicalFile
            require(isInside(workspace, hostDirectory) && hostDirectory.isDirectory) {
                "proot_command workDir is not an accessible workspace directory: $raw"
            }
            return ProotWorkContext(workspace, if (relative.isBlank()) WORKSPACE_DIR else "$WORKSPACE_DIR/$relative")
        }

        val primaryStorage = manager.sharedStorageRoot()
        val storageContainer = manager.sharedStorageContainer()
        val primaryRelative = when {
            raw == "/sdcard" -> ""
            raw.startsWith("/sdcard/") -> raw.removePrefix("/sdcard/")
            else -> null
        }
        if (primaryRelative != null) {
            require(manager.hasAllFilesAccess()) {
                "Access to shared storage outside the workspace requires Android's All files access permission."
            }
            validateRelative(primaryRelative)
            val hostDirectory = File(primaryStorage, primaryRelative).canonicalFile
            require(isInside(primaryStorage, hostDirectory) && hostDirectory.isDirectory) {
                "proot_command shared-storage workDir is not an accessible directory: $raw"
            }
            val guest = if (primaryRelative.isBlank()) "/sdcard" else "/sdcard/$primaryRelative"
            return ProotWorkContext(workspace, guest)
        }

        val storagePath = storageContainer.absolutePath.replace('\\', '/')
        if (raw == storagePath || raw.startsWith("$storagePath/")) {
            require(manager.hasAllFilesAccess()) {
                "Access to shared storage outside the workspace requires Android's All files access permission."
            }
            val storageRelative = raw.removePrefix(storagePath).trim('/')
            validateRelative(storageRelative)
            val hostDirectory = File(storageContainer, storageRelative).canonicalFile
            require(isInside(storageContainer, hostDirectory) && hostDirectory.isDirectory) {
                "proot_command shared-storage workDir is not an accessible directory: $raw"
            }
            return ProotWorkContext(workspace, if (storageRelative.isBlank()) "/storage" else "/storage/$storageRelative")
        }

        if (raw.startsWith('/')) {
            val normalized = java.nio.file.Paths.get(raw).normalize().toString().replace('\\', '/')
            require(normalized.startsWith('/')) { "Invalid Linux workDir: $raw" }
            return ProotWorkContext(workspace, normalized)
        }

        validateRelative(raw)
        if (workspace != null) {
            val hostDirectory = File(workspace, raw).canonicalFile
            require(isInside(workspace, hostDirectory) && hostDirectory.isDirectory) {
                "proot_command workDir is not an accessible workspace directory: $raw"
            }
            return ProotWorkContext(workspace, "$WORKSPACE_DIR/${raw.trim('/')}")
        }
        return ProotWorkContext(null, "/root/${raw.trim('/')}")
    }

    private fun validateRelative(relative: String) {
        require(relative.split('/').none { it == ".." }) { "proot_command workDir contains a parent-directory escape." }
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate == root || candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    private fun capture(input: InputStream): CapturedOutput {
        val visible = ByteArrayOutputStream(MAX_CAPTURE_BYTES)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                val remaining = MAX_CAPTURE_BYTES - visible.size()
                if (remaining > 0) visible.write(buffer, 0, minOf(count, remaining))
                total += count
            }
        }
        return CapturedOutput(visible.toString(StandardCharsets.UTF_8.name()), total, total > MAX_CAPTURE_BYTES)
    }

    private data class ProotWorkContext(val workspaceRoot: File?, val guestWorkDir: String)
    private data class CapturedOutput(val text: String, val originalBytes: Long, val truncated: Boolean)

    private companion object {
        const val WORKSPACE_DIR = "/workspace"
        const val MAX_CAPTURE_BYTES = 256 * 1024
    }
}
