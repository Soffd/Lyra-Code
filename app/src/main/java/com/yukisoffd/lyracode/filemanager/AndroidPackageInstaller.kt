package com.yukisoffd.lyracode.filemanager

import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.system.SystemCommandResult
import com.yukisoffd.lyracode.termux.TermuxExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

internal enum class AndroidInstallMode(val displayName: String) {
    ROOT("Root"),
    SHELL("Shizuku Shell"),
    ADB("ADB"),
}

internal data class AndroidInstallAttempt(
    val mode: AndroidInstallMode?,
    val diagnostics: List<String>,
) {
    val installed: Boolean get() = mode != null
}

internal fun isAndroidPackageFile(file: File): Boolean =
    !file.isDirectory && file.extension.lowercase() in androidPackageExtensions

internal class AndroidPackageInstaller(
    context: Context,
    private val settings: AppSettings,
    private val systemExecutor: SystemCommandExecutor,
    private val termuxExecutor: TermuxExecutor,
) {
    private val appContext = context.applicationContext

    suspend fun install(file: File): AndroidInstallAttempt = withContext(Dispatchers.IO) {
        val diagnostics = mutableListOf<String>()
        val prepared = preparePackages(file).getOrElse { error ->
            diagnostics += "Bundle preparation: ${error.message.orEmpty()}"
            PreparedPackages(listOf(file), cleanupDirectory = null)
        }
        try {
            val command = buildPackageManagerInstallCommand(prepared.files)
            if (settings.requestRootAccess) {
                val probe = systemExecutor.probeRoot()
                if (probe.ok && probe.stdout.trim().lineSequence().lastOrNull() == "0") {
                    val result = systemExecutor.executeRoot(command, INSTALL_TIMEOUT_SECONDS, allowShellFallback = false)
                    if (result.isSuccessfulPackageInstall()) {
                        return@withContext AndroidInstallAttempt(AndroidInstallMode.ROOT, diagnostics)
                    }
                    diagnostics += result.diagnostic("Root")
                }
            }

            if (settings.requestShellAccess && systemExecutor.hasShellPermission()) {
                val result = systemExecutor.executeShell(command, INSTALL_TIMEOUT_SECONDS)
                if (result.isSuccessfulPackageInstall()) {
                    return@withContext AndroidInstallAttempt(AndroidInstallMode.SHELL, diagnostics)
                }
                diagnostics += result.diagnostic("Shell")
            }

            if (termuxExecutor.isTermuxInstalled() && termuxExecutor.hasRunCommandPermission()) {
                val adbCommand = buildAdbInstallCommand(prepared.files)
                val result = termuxExecutor.execute(
                    adbCommand,
                    timeoutSeconds = INSTALL_TIMEOUT_SECONDS,
                    allowApiFallback = false,
                )
                if (result.ok && Regex("(?m)^exit_code:\\s*0\\s*$").containsMatchIn(result.message) &&
                    !result.message.contains("Failure [", ignoreCase = true)
                ) {
                    return@withContext AndroidInstallAttempt(AndroidInstallMode.ADB, diagnostics)
                }
                diagnostics += "ADB: ${result.message.takeLast(DIAGNOSTIC_LIMIT)}"
            }

            AndroidInstallAttempt(null, diagnostics)
        } finally {
            prepared.cleanupDirectory?.deleteRecursively()
        }
    }

    private fun preparePackages(source: File): Result<PreparedPackages> = runCatching {
        require(source.isFile) { "Package file does not exist: ${source.path}" }
        if (source.extension.equals("apk", ignoreCase = true)) {
            return@runCatching PreparedPackages(listOf(source), cleanupDirectory = null)
        }

        val stagingRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ".lyracode-installer/${UUID.randomUUID()}",
        )
        require(stagingRoot.mkdirs()) { "Unable to create package staging directory" }
        val packages = mutableListOf<File>()
        try {
            ZipInputStream(source.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        require(packages.size < MAX_SPLIT_COUNT) { "Package contains too many APK splits" }
                        val output = File(stagingRoot, "${packages.size}-${File(entry.name).name}")
                        output.outputStream().buffered().use { stream -> zip.copyTo(stream) }
                        require(output.length() > 0L) { "Empty APK split: ${entry.name}" }
                        packages += output
                        require(packages.sumOf(File::length) <= MAX_EXTRACTED_PACKAGE_BYTES) {
                            "Extracted APK splits are too large"
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(packages.isNotEmpty()) { "No APK files were found in ${source.name}" }
            PreparedPackages(packages, stagingRoot)
        } catch (error: Throwable) {
            stagingRoot.deleteRecursively()
            throw error
        }
    }

    private data class PreparedPackages(
        val files: List<File>,
        val cleanupDirectory: File?,
    )

    private companion object {
        const val INSTALL_TIMEOUT_SECONDS = 600
        const val MAX_SPLIT_COUNT = 256
        const val MAX_EXTRACTED_PACKAGE_BYTES = 4L * 1024L * 1024L * 1024L
        const val DIAGNOSTIC_LIMIT = 1_000
    }
}

internal fun launchSystemPackageInstaller(context: Context, source: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Toast.makeText(context, R.string.apk_install_permission_hint, Toast.LENGTH_LONG).show()
        return
    }

    val stagingRoot = File(context.cacheDir, "system-package-installer/${UUID.randomUUID()}")
    val packageFiles = if (source.extension.equals("apk", ignoreCase = true)) {
        listOf(source)
    } else {
        extractPackageArchive(source, stagingRoot)
    }
    try {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(packageFiles.sumOf(File::length))
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            packageFiles.forEachIndexed { index, file ->
                file.inputStream().buffered().use { input ->
                    session.openWrite("$index-${file.name}", 0L, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }
            val callback = Intent(context, AndroidPackageInstallReceiver::class.java)
                .setAction(AndroidPackageInstallReceiver.ACTION_INSTALL_STATUS)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
        }
    } finally {
        stagingRoot.deleteRecursively()
    }
}

class AndroidPackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, R.string.apk_install_system_success, Toast.LENGTH_LONG).show()
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    .orEmpty()
                    .ifBlank { context.getString(R.string.apk_install_system_failed) }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.yukisoffd.lyracode.action.PACKAGE_INSTALL_STATUS"
    }
}

internal fun buildPackageManagerInstallCommand(files: List<File>): String {
    require(files.isNotEmpty()) { "At least one APK is required" }
    val action = if (files.size == 1) "install" else "install-multiple"
    return "pm $action -r ${files.joinToString(" ") { shellQuotePackagePath(it.absolutePath) }}"
}

internal fun buildAdbInstallCommand(files: List<File>): String {
    require(files.isNotEmpty()) { "At least one APK is required" }
    val action = if (files.size == 1) "install" else "install-multiple"
    return "command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1 && adb $action -r " +
        files.joinToString(" ") { shellQuotePackagePath(it.absolutePath) }
}

private fun SystemCommandResult.isSuccessfulPackageInstall(): Boolean {
    val output = "$stdout\n$stderr"
    return ok && output.lineSequence().any { it.trim().equals("Success", ignoreCase = true) }
}

private fun SystemCommandResult.diagnostic(label: String): String =
    "$label: ${stderr.ifBlank { stdout }.ifBlank { message }}".takeLast(1_000)

private fun shellQuotePackagePath(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun extractPackageArchive(source: File, stagingRoot: File): List<File> {
    require(stagingRoot.mkdirs()) { "Unable to create package staging directory" }
    val packages = mutableListOf<File>()
    ZipInputStream(source.inputStream().buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                require(packages.size < 256) { "Package contains too many APK splits" }
                val output = File(stagingRoot, "${packages.size}-${File(entry.name).name}")
                output.outputStream().buffered().use(zip::copyTo)
                require(output.length() > 0L) { "Empty APK split: ${entry.name}" }
                packages += output
                require(packages.sumOf(File::length) <= MAX_SYSTEM_EXTRACTED_PACKAGE_BYTES) {
                    "Extracted APK splits are too large"
                }
            }
            zip.closeEntry()
        }
    }
    require(packages.isNotEmpty()) { "No APK files were found in ${source.name}" }
    return packages
}

private val androidPackageExtensions = setOf("apk", "apks", "xapk", "apkm")

private const val MAX_SYSTEM_EXTRACTED_PACKAGE_BYTES = 4L * 1024L * 1024L * 1024L
