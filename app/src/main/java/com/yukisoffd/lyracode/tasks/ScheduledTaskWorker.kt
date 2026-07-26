package com.yukisoffd.lyracode.tasks

import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yukisoffd.lyracode.ai.AiResponseCache
import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.ai.ToolApprovalDecision
import com.yukisoffd.lyracode.ai.WebViewWebAgent
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.AuditLogStore
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.server.MiniServerManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ScheduledTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        val manager = ScheduledTaskManager.getInstance(applicationContext)
        val task = manager.task(taskId) ?: return Result.success()
        if (!task.enabled || task.nextRunAt > System.currentTimeMillis() + EARLY_TOLERANCE_MS) {
            return Result.success()
        }
        manager.markRunning(taskId)

        val settings = AppSettings(applicationContext)
        val profile = settings.profiles().firstOrNull { it.id == task.profileId } ?: settings.selectedProfile()
        val model = task.model.ifBlank { profile.selectedModel }
        if (!awaitModelNetworkReady(manager, taskId, profile)) {
            return Result.retry()
        }
        val conversationStore = ConversationStore(applicationContext)
        var conversationId = 0L
        return try {
            conversationId = conversationStore.createConversation(
                profileId = profile.id,
                model = model,
                title = task.title,
                mode = ConversationStore.MODE_TASK,
            )
            val agent = createAgent(settings, conversationStore, manager)
            agent.approvalHandler = {
                ToolApprovalDecision(
                    approved = false,
                    feedback = "Background scheduled tasks may use read-only Agent tools. This call requires real-time approval, but the background worker cannot show an approval dialog. State which file mutation, command, SSH, Root, MCP mutation, upload, or download step the user must run from the foreground.",
                )
            }
            agent.chat(
                conversationId = conversationId,
                userInput = buildString {
                    appendLine("LYRA_SCHEDULED_TASK_V1")
                    appendLine("Task name: ${task.title}")
                    appendLine("This is a background scheduled task. You may use available read-only Agent tools, including conversation search/read, web search/read, configuration inspection, and remote directory listing.")
                    appendLine("Tools requiring real-time approval will be rejected. If that prevents completion, state exactly which steps the user must approve from the foreground.")
                    append(task.prompt)
                },
                profile = profile,
                model = model,
                propagateErrors = true,
            ) { }
            val conversation = conversationStore.conversation(conversationId)
            val finalText = conversationStore.messages(conversationId)
                .asReversed()
                .firstOrNull { it.role == "assistant" && it.content.isNotBlank() }
                ?.content
                .orEmpty()
            val failure = conversation?.status == ConversationStore.STATUS_INTERRUPTED
            val message = finalText.ifBlank { if (failure) "任务执行中断" else "任务已完成" }
            manager.markFinished(taskId, result = if (failure) "" else message, error = if (failure) message else "")
            TaskCompletionNotifier.notify(
                context = applicationContext,
                settings = settings,
                title = if (failure) "任务失败" else "任务完成",
                message = "${task.title}：${message.take(180)}",
                notificationId = task.id.hashCode(),
                success = !failure,
            )
            Result.success()
        } catch (error: Throwable) {
            val message = error.message.orEmpty().ifBlank { error::class.java.simpleName }
            if (error.isRetryableNetworkFailure() && runAttemptCount < MAX_NETWORK_RETRY_ATTEMPTS) {
                manager.markWaitingForNetwork(
                    taskId,
                    "网络暂不可用，将自动重试（${runAttemptCount + 1}/$MAX_NETWORK_RETRY_ATTEMPTS）：$message",
                )
                return Result.retry()
            }
            manager.markFinished(taskId, result = "", error = message)
            TaskCompletionNotifier.notify(
                context = applicationContext,
                settings = settings,
                title = "任务失败",
                message = "${task.title}：${message.take(180)}",
                notificationId = task.id.hashCode(),
                success = false,
            )
            Result.failure()
        } finally {
            if (conversationId > 0L) {
                conversationStore.deleteConversation(conversationId)
            }
            conversationStore.close()
        }
    }

    private suspend fun awaitModelNetworkReady(
        manager: ScheduledTaskManager,
        taskId: String,
        profile: com.yukisoffd.lyracode.data.ApiProfile,
    ): Boolean {
        val host = runCatching { URI(profile.baseUrl).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return true
        var lastError = ""
        repeat(NETWORK_PREFLIGHT_ATTEMPTS) { attempt ->
            if (!hasUsableNetwork()) {
                lastError = "系统暂未报告可用网络"
            } else {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { InetAddress.getAllByName(host).isNotEmpty() }
                        .onFailure { lastError = it.message.orEmpty().ifBlank { it::class.java.simpleName } }
                        .getOrDefault(false)
                }
                if (resolved) return true
            }
            manager.markWaitingForNetwork(
                taskId,
                "等待网络和 DNS 可用（${attempt + 1}/$NETWORK_PREFLIGHT_ATTEMPTS）：$host ${lastError.ifBlank { "暂不可解析" }}",
            )
            delay(NETWORK_PREFLIGHT_DELAY_MS)
        }
        manager.markWaitingForNetwork(
            taskId,
            "网络或 DNS 仍不可用，将交给系统稍后重试：$host ${lastError.ifBlank { "暂不可解析" }}",
        )
        return false
    }

    private fun hasUsableNetwork(): Boolean {
        val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                )
    }

    private fun createAgent(
        settings: AppSettings,
        conversationStore: ConversationStore,
        scheduledTaskManager: ScheduledTaskManager,
    ): OpenAiAgent {
        val auditLogStore = AuditLogStore(applicationContext)
        val workspaceManager = WorkspaceManager(applicationContext, settings)
        val nativeFileManager = NativeFileManager(applicationContext, workspaceManager)
        val globalFileManager = GlobalFileManager()
        val miniServerManager = MiniServerManager(applicationContext, settings, workspaceManager)
        val downloadTaskManager = DownloadTaskManager.getInstance(
            applicationContext,
            settings,
            nativeFileManager,
            globalFileManager,
        )
        return OpenAiAgent(
            context = applicationContext,
            settings = settings,
            conversationStore = conversationStore,
            nativeFileManager = nativeFileManager,
            globalFileManager = globalFileManager,
            termuxExecutor = TermuxExecutor(applicationContext, auditLogStore),
            workspaceManager = workspaceManager,
            webAgent = WebViewWebAgent(applicationContext, settings),
            mcpClientManager = McpClientManager(applicationContext, settings),
            sshExecutor = SshExecutor(settings),
            systemCommandExecutor = SystemCommandExecutor(applicationContext, settings),
            webDavClient = WebDavClient(),
            fileTransferClient = FileTransferClient(applicationContext),
            backupManager = BackupManager(applicationContext, settings, conversationStore),
            miniServerManager = miniServerManager,
            downloadTaskManager = downloadTaskManager,
            scheduledTaskManager = scheduledTaskManager,
            responseCache = AiResponseCache(applicationContext.cacheDir),
        )
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val EARLY_TOLERANCE_MS = 60_000L
        private const val MAX_NETWORK_RETRY_ATTEMPTS = 24
        private const val NETWORK_PREFLIGHT_ATTEMPTS = 36
        private const val NETWORK_PREFLIGHT_DELAY_MS = 5_000L
    }
}

internal fun Throwable.isRetryableNetworkFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (
            current is UnknownHostException ||
            current is ConnectException ||
            current is SocketTimeoutException ||
            current is InterruptedIOException
        ) {
            return true
        }
        current = current.cause
    }
    val normalized = buildString {
        current = this@isRetryableNetworkFailure
        while (current != null) {
            append(' ')
            append(current?.message.orEmpty())
            current = current?.cause
        }
    }.lowercase()
    return normalized.contains("unable to resolve host") ||
        normalized.contains("no address associated with hostname") ||
        normalized.contains("temporary failure in name resolution")
}
