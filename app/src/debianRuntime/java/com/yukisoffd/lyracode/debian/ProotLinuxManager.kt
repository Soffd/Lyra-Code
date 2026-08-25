package com.yukisoffd.lyracode.debian

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ProotLinuxInstance(
    val id: String,
    val name: String,
    val rootfsDir: File,
    val enabled: Boolean,
    val legacy: Boolean = false,
    val source: String = "import",
) {
    val shellPath: String
        get() = when {
            Files.exists(File(rootfsDir, "bin/bash").toPath(), LinkOption.NOFOLLOW_LINKS) -> "/bin/bash"
            Files.exists(File(rootfsDir, "bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS) -> "/bin/sh"
            else -> ""
        }
}

internal enum class ProotOperationPhase { IDLE, DOWNLOADING, IMPORTING, DELETING, ERROR }

internal data class ProotLinuxState(
    val instances: List<ProotLinuxInstance> = emptyList(),
    val phase: ProotOperationPhase = ProotOperationPhase.IDLE,
    val progressPercent: Int = 0,
    val message: String = "",
)

internal data class ProotCommandProcess(
    val process: Process,
    val completionFile: File,
    val supervisorPidFile: File,
)

/** Registry and lifecycle owner for multiple independent PRoot Linux installations. */
internal class ProotLinuxManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val instancesDir = File(appContext.filesDir, "proot-linux/instances")
    private val importStagingRoot = File(appContext.filesDir, "proot-linux/staging")
    private val legacyRuntime = DebianRuntimeManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val operationMutex = Mutex()
    private val beforeDeleteListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val backgroundCommands = ConcurrentHashMap<Long, BackgroundProotCommand>()
    private val backgroundReaper = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lyracode-proot-background-reaper").apply { isDaemon = true }
    }
    private val backgroundReaperScheduled = AtomicBoolean(false)
    private val _state = MutableStateFlow(ProotLinuxState(instances = scanInstances()))
    val state: StateFlow<ProotLinuxState> = _state.asStateFlow()

    fun isSupported(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } &&
        runCatching { prootFile().isFile && loaderFile().isFile }.getOrDefault(false)

    fun activeInstances(): List<ProotLinuxInstance> = state.value.instances.filter { it.enabled }

    fun hasActiveInstances(): Boolean = activeInstances().isNotEmpty()

    fun hasAllFilesAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        Environment.getExternalStorageDirectory().canRead()
    }

    fun sharedStorageRoot(): File = Environment.getExternalStorageDirectory().canonicalFile

    fun sharedStorageContainer(): File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.getStorageDirectory().canonicalFile
    } else {
        File("/storage").canonicalFile
    }

    fun instance(id: String): ProotLinuxInstance = state.value.instances.firstOrNull { it.id == id }
        ?: error("Unknown PRoot Linux ID: $id. Available IDs: ${state.value.instances.joinToString { it.id }}")

    fun inventoryForAgent(): String = activeInstances().joinToString("; ") { "${it.id} (${it.name})" }

    fun addBeforeDeleteListener(listener: (String) -> Unit): AutoCloseable {
        beforeDeleteListeners += listener
        return AutoCloseable { beforeDeleteListeners -= listener }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        instance(id)
        preferences.edit().putBoolean(enabledKey(id), enabled).apply()
        if (!enabled) {
            stopBackgroundCommands(id)
            beforeDeleteListeners.forEach { it(id) }
        }
        refresh()
    }

    fun refresh() {
        if (_state.value.phase == ProotOperationPhase.IDLE || _state.value.phase == ProotOperationPhase.ERROR) {
            _state.value = ProotLinuxState(instances = scanInstances())
        }
    }

    suspend fun downloadDebian(): String = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val existing = scanInstances().firstOrNull { it.id == LEGACY_DEBIAN_ID }
            if (existing != null) {
                setEnabled(existing.id, true)
                return@withLock existing.id
            }
            checkSupported()
            _state.value = ProotLinuxState(scanInstances(), ProotOperationPhase.DOWNLOADING, message = "Preparing Debian download")
            try {
                coroutineScope {
                    val progressJob: Job = launch {
                        legacyRuntime.state.collect { legacyState ->
                            val phase = when (legacyState.phase) {
                                DebianRuntimePhase.DOWNLOADING -> ProotOperationPhase.DOWNLOADING
                                DebianRuntimePhase.INSTALLING -> ProotOperationPhase.IMPORTING
                                else -> _state.value.phase
                            }
                            _state.value = ProotLinuxState(
                                instances = scanInstances(),
                                phase = phase,
                                progressPercent = legacyState.progressPercent,
                                message = legacyState.error,
                            )
                        }
                    }
                    try {
                        legacyRuntime.install()
                    } finally {
                        progressJob.cancel()
                    }
                }
                preferences.edit().putBoolean(enabledKey(LEGACY_DEBIAN_ID), true).apply()
                _state.value = ProotLinuxState(scanInstances())
                LEGACY_DEBIAN_ID
            } catch (error: Throwable) {
                publishError(error)
                throw error
            }
        }
    }

    suspend fun importRootfs(uri: Uri, displayName: String): String = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            checkSupported()
            val safeName = displayName.trim().ifBlank { "Imported Linux" }.take(80)
            val id = uniqueId(slug(safeName))
            val staging = File(importStagingRoot, UUID.randomUUID().toString())
            val stagingRootfs = File(staging, "rootfs")
            val archive = File(appContext.cacheDir, "proot-linux-import/$id.tgz")
            try {
                _state.value = ProotLinuxState(scanInstances(), ProotOperationPhase.IMPORTING, message = "Copying rootfs archive")
                archive.parentFile?.mkdirs()
                copyUri(uri, archive)
                stagingRootfs.mkdirs()
                openTarStream(archive).use { TarExtractor.extract(it, stagingRootfs) }
                normalizeSingleTopLevelDirectory(stagingRootfs)
                validateRootfs(stagingRootfs)
                prepareRootfs(stagingRootfs)
                staging.mkdirs()
                File(staging, METADATA_FILE).writeText(
                    JSONObject()
                        .put("id", id)
                        .put("name", safeName)
                        .put("source", "import")
                        .put("created_at", System.currentTimeMillis())
                        .toString(2),
                )
                instancesDir.mkdirs()
                val destination = File(instancesDir, id)
                check(!destination.exists()) { "Linux ID already exists: $id" }
                check(staging.renameTo(destination)) { "Unable to activate imported Linux rootfs." }
                preferences.edit().putBoolean(enabledKey(id), true).apply()
                _state.value = ProotLinuxState(scanInstances())
                id
            } catch (error: Throwable) {
                staging.deleteRecursively()
                publishError(error)
                throw error
            } finally {
                archive.delete()
            }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val target = instance(id)
            _state.value = ProotLinuxState(scanInstances(), ProotOperationPhase.DELETING, message = target.name)
            stopBackgroundCommands(id)
            beforeDeleteListeners.forEach { it(id) }
            try {
                val allowedRoot = if (target.legacy) {
                    File(appContext.filesDir, "debian-runtime").canonicalFile
                } else {
                    instancesDir.canonicalFile
                }
                val deleteTarget = if (target.legacy) {
                    target.rootfsDir.canonicalFile
                } else {
                    (target.rootfsDir.parentFile ?: error("Managed Linux rootfs has no parent directory.")).canonicalFile
                }
                check(deleteTarget.parentFile == allowedRoot || deleteTarget.absolutePath.startsWith(allowedRoot.absolutePath + File.separator)) {
                    "Refusing to delete a Linux rootfs outside the managed directory."
                }
                check(deleteTarget.deleteRecursively()) { "Unable to completely delete Linux environment: ${target.name}" }
                preferences.edit().remove(enabledKey(id)).apply()
                _state.value = ProotLinuxState(scanInstances())
            } catch (error: Throwable) {
                publishError(error)
                throw error
            }
        }
    }

    fun startInteractiveShell(id: String, workspaceRoot: String?, columns: Int, rows: Int): Process {
        val target = requireEnabled(id)
        prepareRootfs(target.rootfsDir)
        val workspace = workspaceRoot?.takeIf(String::isNotBlank)?.let(::File)?.canonicalFile?.takeIf(File::isDirectory)
        val command = "stty cols ${columns.coerceIn(2, 500)} rows ${rows.coerceIn(2, 300)} 2>/dev/null; exec ${target.shellPath} -l"
        val script = when {
            File(target.rootfsDir, "usr/bin/script").isFile -> "/usr/bin/script"
            File(target.rootfsDir, "bin/script").isFile -> "/bin/script"
            else -> null
        }
        val guestCommand = if (script != null) {
            listOf(script, "-qefc", command, "/dev/null")
        } else {
            // Very small rootfs images may omit util-linux/script. They still get an interactive
            // shell over the process streams; installing util-linux upgrades this to a real PTY.
            listOf(target.shellPath, "-l")
        }
        return startProcess(target, workspace, if (workspace == null) "/root" else "/workspace", guestCommand)
    }

    fun startCommand(
        id: String,
        workspaceRoot: File?,
        guestWorkDir: String,
        command: String,
        executionId: Long,
        background: Boolean,
    ): ProotCommandProcess {
        val target = requireEnabled(id)
        prepareRootfs(target.rootfsDir)
        val commandStateDir = File(target.rootfsDir, "root/.lyracode/commands").apply {
            check(isDirectory || mkdirs()) { "Unable to create the PRoot command state directory." }
        }
        val completionFile = File(commandStateDir, "$executionId.status")
        val supervisorPidFile = File(commandStateDir, "$executionId.pid")
        completionFile.delete()
        supervisorPidFile.delete()
        val commandToRun = if (background) {
            buildDetachedProotCommand(command, executionId, target.shellPath)
        } else {
            command
        }
        val trackedCommand = buildTrackedProotCommand(
            command = commandToRun,
            completionGuestPath = "/root/.lyracode/commands/$executionId.status",
            supervisorPidGuestPath = "/root/.lyracode/commands/$executionId.pid",
            shellPath = target.shellPath,
        )
        return ProotCommandProcess(
            process = startProcess(
                target,
                workspaceRoot,
                guestWorkDir,
                listOf(target.shellPath, "-lc", trackedCommand),
                mergeError = false,
                killOnExit = false,
            ),
            completionFile = completionFile,
            supervisorPidFile = supervisorPidFile,
        )
    }

    /**
     * Keeps the PRoot tracer alive after its command shell has returned. Detached guest
     * descendants still need that tracer for path translation, so allowing the Java Process to
     * be collected (or closing it as a timed-out foreground command) would terminate the service.
     */
    fun retainBackgroundCommand(executionId: Long, linuxId: String, process: Process, supervisorPid: Int?) {
        backgroundCommands[executionId] = BackgroundProotCommand(linuxId, process, supervisorPid)
        scheduleBackgroundReaper()
    }

    /** SIGQUIT is PRoot's supported shutdown path: it kills only this supervisor's tracees. */
    fun terminateCommandProcess(process: Process, supervisorPid: Int?) {
        if (!process.isAlive) return
        val signalled = supervisorPid != null && runCatching {
            Os.kill(supervisorPid, OsConstants.SIGQUIT)
        }.isSuccess
        if (!signalled) runCatching { process.destroy() }
    }

    private fun startProcess(
        target: ProotLinuxInstance,
        workspace: File?,
        guestWorkDir: String,
        guestCommand: List<String>,
        mergeError: Boolean = true,
        killOnExit: Boolean = true,
    ): Process {
        val args = mutableListOf(
            prootFile().absolutePath,
            "--root-id", "--link2symlink",
            "-r", target.rootfsDir.absolutePath,
            "-w", guestWorkDir,
        )
        if (killOnExit) args.add(2, "--kill-on-exit")
        if (workspace != null) args += listOf("-b", "${workspace.absolutePath}:/workspace")
        if (hasAllFilesAccess()) {
            args += listOf("-b", "${sharedStorageContainer().absolutePath}:/storage")
            args += listOf("-b", "${sharedStorageRoot().absolutePath}:/sdcard")
        }
        listOf("/dev", "/proc", "/sys", "/apex", "/system", "/system_ext", "/vendor").forEach { path ->
            if (File(path).exists()) args += listOf("-b", path)
        }
        args += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR=/tmp", "TERM=xterm-256color", "COLORTERM=truecolor",
            "LANG=C.UTF-8", "LC_ALL=C.UTF-8",
        )
        args += guestCommand
        val processTemp = File(appContext.cacheDir, "proot-linux-tmp/${target.id}").apply { mkdirs() }
        return ProcessBuilder(args)
            .directory(appContext.filesDir)
            .redirectErrorStream(mergeError)
            .apply {
                environment().remove("LD_PRELOAD")
                environment()["PROOT_LOADER"] = loaderFile().absolutePath
                environment()["PROOT_TMP_DIR"] = processTemp.absolutePath
                environment()["TMPDIR"] = processTemp.absolutePath
            }
            .start()
    }

    private fun requireEnabled(id: String): ProotLinuxInstance {
        checkSupported()
        val target = instance(id)
        check(target.enabled) { "PRoot Linux '$id' is disabled." }
        check(target.shellPath.isNotBlank()) { "PRoot Linux '$id' has no /bin/bash or /bin/sh." }
        return target
    }

    private fun stopBackgroundCommands(linuxId: String) {
        val commands = backgroundCommands.entries
            .filter { it.value.linuxId == linuxId }
            .mapNotNull { (executionId, command) ->
                command.takeIf { backgroundCommands.remove(executionId, command) }
            }
        commands.forEach { command ->
            terminateCommandProcess(command.process, command.supervisorPid)
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BACKGROUND_STOP_TIMEOUT_MILLIS)
        commands.forEach { command ->
            val process = command.process
            if (process.isAlive) {
                val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0L)
                runCatching { process.waitFor(remainingMillis, TimeUnit.MILLISECONDS) }
            }
            if (process.isAlive) runCatching { process.destroyForcibly() }
            closeBackgroundProcess(command)
        }
    }

    private fun scheduleBackgroundReaper() {
        if (!backgroundReaperScheduled.compareAndSet(false, true)) return
        backgroundReaper.schedule(
            {
                try {
                    backgroundCommands.entries.forEach { (executionId, command) ->
                        drainAvailable(command.process.inputStream)
                        drainAvailable(command.process.errorStream)
                        if (!command.process.isAlive && backgroundCommands.remove(executionId, command)) {
                            closeBackgroundProcess(command)
                        }
                    }
                } finally {
                    backgroundReaperScheduled.set(false)
                    if (backgroundCommands.isNotEmpty()) scheduleBackgroundReaper()
                }
            },
            BACKGROUND_REAP_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun drainAvailable(input: InputStream) {
        runCatching {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = MAX_BACKGROUND_DRAIN_BYTES_PER_PASS
            while (remaining > 0) {
                val available = input.available()
                if (available <= 0) break
                val count = input.read(buffer, 0, minOf(buffer.size, available, remaining))
                if (count <= 0) break
                remaining -= count
            }
        }
    }

    private fun closeBackgroundProcess(command: BackgroundProotCommand) {
        val process = command.process
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun scanInstances(): List<ProotLinuxInstance> {
        val result = mutableListOf<ProotLinuxInstance>()
        val legacyRootfs = File(appContext.filesDir, "debian-runtime/rootfs")
        if (hasShell(legacyRootfs)) {
            result += ProotLinuxInstance(
                id = LEGACY_DEBIAN_ID,
                name = "Debian",
                rootfsDir = legacyRootfs,
                enabled = preferences.getBoolean(enabledKey(LEGACY_DEBIAN_ID), true),
                legacy = true,
                source = "download",
            )
        }
        instancesDir.listFiles()?.filter(File::isDirectory)?.sortedBy(File::getName)?.forEach { directory ->
            val rootfs = File(directory, "rootfs")
            if (!hasShell(rootfs)) return@forEach
            val metadata = runCatching { JSONObject(File(directory, METADATA_FILE).readText()) }.getOrNull()
            val id = metadata?.optString("id")?.takeIf { it == directory.name } ?: directory.name
            val name = metadata?.optString("name")?.takeIf(String::isNotBlank) ?: id
            result += ProotLinuxInstance(
                id = id,
                name = name,
                rootfsDir = rootfs,
                enabled = preferences.getBoolean(enabledKey(id), true),
                source = metadata?.optString("source", "import") ?: "import",
            )
        }
        return result
    }

    private fun copyUri(uri: Uri, destination: File) {
        val total = querySize(uri)
        val input = appContext.contentResolver.openInputStream(uri) ?: error("Unable to open the selected rootfs archive.")
        input.use {
            FileOutputStream(destination).buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    val percent = if (total > 0) ((copied * 100L) / total).toInt().coerceIn(0, 100) else 0
                    _state.value = ProotLinuxState(scanInstances(), ProotOperationPhase.IMPORTING, percent, "Copying rootfs archive")
                }
            }
        }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun openTarStream(file: File): InputStream {
        val buffered = BufferedInputStream(file.inputStream())
        buffered.mark(4)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
    }

    private fun normalizeSingleTopLevelDirectory(rootfs: File) {
        if (hasShell(rootfs)) return
        val children = rootfs.listFiles().orEmpty().filterNot { it.name == "." || it.name == ".." }
        val nested = children.singleOrNull()?.takeIf(File::isDirectory) ?: return
        if (!hasShell(nested)) return
        nested.listFiles().orEmpty().forEach { child ->
            check(child.renameTo(File(rootfs, child.name))) { "Unable to normalize rootfs directory ${child.name}." }
        }
        nested.delete()
    }

    private fun validateRootfs(rootfs: File) {
        check(hasShell(rootfs)) { "The archive is not a compatible Linux rootfs: /bin/bash or /bin/sh is missing." }
        val shell = resolveInsideRootfs(
            rootfs,
            if (Files.exists(File(rootfs, "bin/bash").toPath(), LinkOption.NOFOLLOW_LINKS)) "bin/bash" else "bin/sh",
        )
        check(shell.isFile) { "The imported rootfs shell target is missing or invalid." }
        val machine = readElfMachine(shell)
        check(machine == null || machine == ELF_MACHINE_AARCH64) {
            "The selected rootfs is not arm64/aarch64 (ELF machine $machine)."
        }
    }

    private fun resolveInsideRootfs(rootfs: File, relative: String): File {
        // Do not mix canonical and lexical Android app-data paths here. On Android,
        // /data/user/0/<package> commonly resolves to the /data/data/<package> alias; comparing
        // one representation with the other falsely labels every legitimate rootfs link as an
        // escape. TarExtractor has already confined link creation to this managed rootfs, so
        // resolve guest-absolute links against one normalized lexical root and reject `..` that
        // leaves it.
        val rootPath = rootfs.absoluteFile.toPath().normalize()
        var current = rootPath.resolve(relative).normalize().toFile()
        repeat(8) {
            val normalized = current.absoluteFile.toPath().normalize()
            check(normalized.startsWith(rootPath)) { "Shell link escapes the imported rootfs." }
            current = normalized.toFile()
            if (!Files.isSymbolicLink(current.toPath())) return current
            val link = Os.readlink(current.absolutePath)
            current = if (link.startsWith('/')) {
                rootPath.resolve(link.trimStart('/')).normalize().toFile()
            } else {
                (current.parentFile ?: error("Imported shell link has no parent directory."))
                    .toPath()
                    .resolve(link)
                    .normalize()
                    .toFile()
            }
        }
        error("Too many symbolic links while validating /$relative")
    }

    private fun readElfMachine(file: File): Int? = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(20)
            if (input.read(header) != header.size) return@use null
            if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() || header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()) return@use null
            (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        }
    }.getOrNull()

    private fun prepareRootfs(rootfs: File) {
        File(rootfs, "root").mkdirs()
        listOf("tmp", "var/tmp", "dev", "proc", "sys", "workspace", "storage", "sdcard").forEach {
            val path = File(rootfs, it)
            if (!Files.isSymbolicLink(path.toPath())) path.mkdirs()
        }
        runCatching { Os.chmod(File(rootfs, "tmp").absolutePath, 0x3ff) }
        runCatching { Os.chmod(File(rootfs, "var/tmp").absolutePath, 0x3ff) }
    }

    private fun hasShell(rootfs: File): Boolean =
        Files.exists(File(rootfs, "bin/bash").toPath(), LinkOption.NOFOLLOW_LINKS) ||
            Files.exists(File(rootfs, "bin/sh").toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun uniqueId(base: String): String {
        val used = scanInstances().mapTo(mutableSetOf()) { it.id }
        if (base !in used) return base
        var suffix = 2
        while ("$base-$suffix" in used) suffix++
        return "$base-$suffix"
    }

    private fun slug(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
        .ifBlank { "linux" }
        .take(40)

    private fun checkSupported() {
        check(isSupported()) { "PRoot Linux currently supports arm64-v8a devices only." }
    }

    private fun publishError(error: Throwable) {
        _state.value = ProotLinuxState(
            instances = scanInstances(),
            phase = ProotOperationPhase.ERROR,
            message = error.message ?: error.javaClass.simpleName,
        )
    }

    private fun prootFile(): File = File(appContext.applicationInfo.nativeLibraryDir, PROOT_EXECUTABLE).also {
        check(it.isFile) { "Bundled PRoot executable is missing: ${it.absolutePath}" }
    }

    private fun loaderFile(): File = File(appContext.applicationInfo.nativeLibraryDir, PROOT_LOADER).also {
        check(it.isFile) { "Bundled PRoot loader is missing: ${it.absolutePath}" }
    }

    private fun enabledKey(id: String) = "enabled_$id"

    companion object {
        const val LEGACY_DEBIAN_ID = "debian"
        private const val METADATA_FILE = "metadata.json"
        private const val PREFERENCES = "proot_linux"
        private const val PROOT_EXECUTABLE = "libproot_exec.so"
        private const val PROOT_LOADER = "libproot_loader.so"
        private const val ELF_MACHINE_AARCH64 = 183
        private const val BACKGROUND_REAP_INTERVAL_MILLIS = 1_000L
        private const val BACKGROUND_STOP_TIMEOUT_MILLIS = 2_000L
        private const val MAX_BACKGROUND_DRAIN_BYTES_PER_PASS = 256 * 1024

        @Volatile private var instance: ProotLinuxManager? = null

        fun getInstance(context: Context): ProotLinuxManager = instance ?: synchronized(this) {
            instance ?: ProotLinuxManager(context).also { instance = it }
        }
    }

    private data class BackgroundProotCommand(
        val linuxId: String,
        val process: Process,
        val supervisorPid: Int?,
    )
}

internal fun buildTrackedProotCommand(
    command: String,
    completionGuestPath: String,
    supervisorPidGuestPath: String,
    shellPath: String,
): String {
    val quotedShell = shellSingleQuoteForProot(shellPath)
    val quotedCommand = shellSingleQuoteForProot(command)
    val quotedCompletionPath = shellSingleQuoteForProot(completionGuestPath)
    val quotedSupervisorPidPath = shellSingleQuoteForProot(supervisorPidGuestPath)
    return """
        printf '%s\n' "${'$'}PPID" > $quotedSupervisorPidPath
        $quotedShell -lc $quotedCommand
        lyra_exit_code=${'$'}?
        printf '%s\n' "${'$'}lyra_exit_code" > $quotedCompletionPath
        exit "${'$'}lyra_exit_code"
    """.trimIndent()
}

internal fun buildDetachedProotCommand(command: String, executionId: Long, shellPath: String): String {
    val quotedShell = shellSingleQuoteForProot(shellPath)
    val quotedCommand = shellSingleQuoteForProot(command)
    return """
        lyra_output_file="/tmp/lyracode-run-$executionId-${'$'}${'$'}.log"
        $quotedShell -lc $quotedCommand </dev/null >"${'$'}lyra_output_file" 2>&1 &
        lyra_launcher_pid=${'$'}!
        printf 'background_started: true\nlauncher_pid: %s\noutput_file: %s\nnote: Launch accepted; verify the process and log separately.\n' "${'$'}lyra_launcher_pid" "${'$'}lyra_output_file"
    """.trimIndent()
}

private fun shellSingleQuoteForProot(value: String): String =
    "'${value.replace("'", "'\"'\"'")}'"
