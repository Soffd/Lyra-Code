package com.yukisoffd.lyracode.tasks

import android.content.Context
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class DownloadTaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class DownloadTask(
    val id: String,
    val url: String,
    val destination: String,
    val path: String,
    val status: DownloadTaskStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L,
    val contentType: String = "",
    val sha256: String = "",
    val error: String = "",
)

data class DownloadTaskRequest(
    val url: String,
    val destination: String,
    val path: String,
    val headers: List<Pair<String, String>>,
    val expectedSha256: String,
    val timeoutSeconds: Int,
)

data class DownloadTaskResult(
    val finalUrl: String,
    val destination: String,
    val path: String,
    val bytes: Long,
    val contentType: String,
    val sha256: String,
)

class DownloadTaskManager private constructor(
    context: Context,
    private val settings: AppSettings,
    private val nativeFileManager: NativeFileManager,
    private val globalFileManager: GlobalFileManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskLock = Any()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val _tasks = MutableStateFlow(loadTasks())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun download(request: DownloadTaskRequest): Deferred<DownloadTaskResult> {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = request.url,
            destination = request.destination,
            path = request.path,
            status = DownloadTaskStatus.QUEUED,
        )
        updateTask(task)
        return scope.async { performDownload(task, request) }
    }

    fun clearFinished() {
        synchronized(taskLock) {
            _tasks.value = _tasks.value.filter { it.status == DownloadTaskStatus.RUNNING || it.status == DownloadTaskStatus.QUEUED }
            persistTasks()
        }
    }

    private fun performDownload(initial: DownloadTask, request: DownloadTaskRequest): DownloadTaskResult {
        val requestBuilder = Request.Builder().url(request.url).get()
        request.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        val tempFile = File.createTempFile("agent_download_", ".part", appContext.cacheDir)
        var current = initial.copy(status = DownloadTaskStatus.RUNNING)
        updateTask(current)
        try {
            val downloadClient = client.newBuilder()
                .readTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .callTimeout(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
            return downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body ?: error("下载响应为空")
                if (!response.isSuccessful) {
                    error("下载失败 HTTP ${response.code}: ${body.string().take(500)}")
                }
                val totalBytes = body.contentLength()
                val contentType = body.contentType()?.toString().orEmpty()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastBytes = 0L
                var lastUpdateAt = System.currentTimeMillis()
                DigestInputStream(body.byteStream(), digest).use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= PROGRESS_INTERVAL_MS) {
                                val elapsed = (now - lastUpdateAt).coerceAtLeast(1L)
                                val speed = ((downloaded - lastBytes) * 1000L / elapsed).coerceAtLeast(0L)
                                current = current.copy(
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speed,
                                    contentType = contentType,
                                )
                                updateTask(current)
                                lastBytes = downloaded
                                lastUpdateAt = now
                            }
                        }
                    }
                }
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (request.expectedSha256.isNotBlank() && actualSha256 != request.expectedSha256) {
                    error("SHA-256 校验失败: expected=${request.expectedSha256} actual=$actualSha256")
                }
                val effectiveDestination = if (request.destination == "workspace" && !nativeFileManager.hasWorkspaceRoot()) {
                    "global"
                } else {
                    request.destination
                }
                val effectivePath = if (effectiveDestination == "global" && request.destination == "workspace") {
                    fallbackDownloadPath(request.path)
                } else {
                    request.path
                }
                val written = tempFile.inputStream().buffered().use { input ->
                    when (effectiveDestination) {
                        "workspace" -> nativeFileManager.writeStream(effectivePath, input).getOrThrow()
                        else -> globalFileManager.writeStream(effectivePath, input).getOrThrow()
                    }
                }
                val result = DownloadTaskResult(
                    finalUrl = response.request.url.toString(),
                    destination = effectiveDestination,
                    path = effectivePath,
                    bytes = written,
                    contentType = contentType,
                    sha256 = actualSha256,
                )
                updateTask(
                    current.copy(
                        status = DownloadTaskStatus.COMPLETED,
                        downloadedBytes = downloaded,
                        totalBytes = if (totalBytes >= 0L) totalBytes else downloaded,
                        bytesPerSecond = 0L,
                        finishedAt = System.currentTimeMillis(),
                        contentType = contentType,
                        sha256 = actualSha256,
                        destination = result.destination,
                        path = result.path,
                    ),
                )
                notifyCompleted(result.path)
                result
            }
        } catch (error: Throwable) {
            val errorMessage = error.message.orEmpty().ifBlank { error::class.java.simpleName }
            updateTask(
                current.copy(
                    status = DownloadTaskStatus.FAILED,
                    finishedAt = System.currentTimeMillis(),
                    bytesPerSecond = 0L,
                    error = errorMessage,
                ),
            )
            notifyFailed(request.path, errorMessage)
            throw error
        } finally {
            tempFile.delete()
        }
    }

    private fun fallbackDownloadPath(path: String): String {
        val clean = path.replace('\\', '/').trim().trimStart('/').ifBlank { "download.bin" }
        return "Download/LyraCode/$clean"
    }

    private fun updateTask(task: DownloadTask) {
        synchronized(taskLock) {
            val updated = _tasks.value.toMutableList()
            val index = updated.indexOfFirst { it.id == task.id }
            if (index >= 0) updated[index] = task else updated.add(0, task)
            _tasks.value = updated.take(MAX_STORED_TASKS)
            persistTasks()
        }
    }

    private fun notifyCompleted(path: String) {
        val fileName = path.replace('\\', '/').substringAfterLast('/').ifBlank { path }
        TaskCompletionNotifier.notify(
            context = appContext,
            settings = settings,
            title = "任务完成",
            message = "文件下载完成：$fileName",
            notificationId = path.hashCode(),
        )
    }

    private fun notifyFailed(path: String, error: String) {
        val fileName = path.replace('\\', '/').substringAfterLast('/').ifBlank { path }
        TaskCompletionNotifier.notify(
            context = appContext,
            settings = settings,
            title = "任务失败",
            message = "文件下载失败：$fileName\n${error.take(180)}",
            notificationId = path.hashCode(),
            success = false,
        )
    }

    private fun persistTasks() {
        val array = JSONArray()
        _tasks.value.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("url", task.url)
                    .put("destination", task.destination)
                    .put("path", task.path)
                    .put("status", task.status.name)
                    .put("downloadedBytes", task.downloadedBytes)
                    .put("totalBytes", task.totalBytes)
                    .put("bytesPerSecond", task.bytesPerSecond)
                    .put("startedAt", task.startedAt)
                    .put("finishedAt", task.finishedAt)
                    .put("contentType", task.contentType)
                    .put("sha256", task.sha256)
                    .put("error", task.error),
            )
        }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    private fun loadTasks(): List<DownloadTask> {
        val raw = prefs.getString(KEY_TASKS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val storedStatus = runCatching {
                        DownloadTaskStatus.valueOf(item.optString("status"))
                    }.getOrDefault(DownloadTaskStatus.FAILED)
                    val interrupted = storedStatus == DownloadTaskStatus.RUNNING || storedStatus == DownloadTaskStatus.QUEUED
                    add(
                        DownloadTask(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            url = item.optString("url"),
                            destination = item.optString("destination"),
                            path = item.optString("path"),
                            status = if (interrupted) DownloadTaskStatus.FAILED else storedStatus,
                            downloadedBytes = item.optLong("downloadedBytes"),
                            totalBytes = item.optLong("totalBytes", -1L),
                            bytesPerSecond = 0L,
                            startedAt = item.optLong("startedAt"),
                            finishedAt = if (interrupted) System.currentTimeMillis() else item.optLong("finishedAt"),
                            contentType = item.optString("contentType"),
                            sha256 = item.optString("sha256").lowercase(Locale.US),
                            error = if (interrupted) "应用进程结束，任务已中断" else item.optString("error"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "download_tasks"
        private const val KEY_TASKS = "tasks"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val MAX_STORED_TASKS = 100

        @Volatile
        private var instance: DownloadTaskManager? = null

        fun getInstance(
            context: Context,
            settings: AppSettings,
            nativeFileManager: NativeFileManager,
            globalFileManager: GlobalFileManager,
        ): DownloadTaskManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadTaskManager(
                    context,
                    settings,
                    nativeFileManager,
                    globalFileManager,
                ).also { instance = it }
            }
        }
    }
}
