package com.yukisoffd.lyracode.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.yukisoffd.lyracode.DeviceInfoCollector
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.BackupOptions
import com.yukisoffd.lyracode.data.ChatMessage
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.data.DeepSeekV3Tokenizer
import com.yukisoffd.lyracode.data.FileTransferServerConfig
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.data.MemoryEntry
import com.yukisoffd.lyracode.data.MiniServerConfig
import com.yukisoffd.lyracode.data.SkillPack
import com.yukisoffd.lyracode.data.SubAgentConfig
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.data.WebDavServerConfig
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.server.MiniServerManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.system.InstalledAppCollector
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.tasks.DownloadTaskManager
import com.yukisoffd.lyracode.tasks.DownloadTaskRequest
import com.yukisoffd.lyracode.tasks.ScheduledTask
import com.yukisoffd.lyracode.tasks.ScheduledTaskManager
import com.yukisoffd.lyracode.tasks.ScheduledTaskType
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.uiText
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceFile
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.TimeZone
import java.util.concurrent.TimeUnit


private data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
    val rawArguments: String,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("arguments", rawArguments),
            )
    }
}

private data class StreamingResult(
    val content: String,
    val thinking: String,
    val rawMessage: JSONObject,
    val toolCalls: List<ToolCall>,
    val tokensPerSecond: Double = 0.0,
    val fromCache: Boolean = false,
)

private class ToolCallBuilder {
    var id: String = ""
    var name: String = ""
    val arguments = StringBuilder()

    fun toToolCall(index: Int): ToolCall? {
        if (name.isBlank()) return null
        val raw = arguments.toString().ifBlank { "{}" }
        val parsed = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return ToolCall(id.ifBlank { "tool_$index" }, name, parsed, raw)
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("arguments", arguments.toString()),
            )
    }
}

private class AnthropicBlockBuilder {
    var type: String = ""
    var id: String = ""
    var name: String = ""
    val text = StringBuilder()
    val thinking = StringBuilder()
    val input = StringBuilder()
}

private data class ToolExecution(
    val content: String,
    val fileChanges: List<FileDiff> = emptyList(),
)

class OpenAiAgent(
    private val context: Context,
    private val settings: AppSettings,
    private val conversationStore: ConversationStore,
    private val nativeFileManager: NativeFileManager,
    private val globalFileManager: GlobalFileManager,
    private val termuxExecutor: TermuxExecutor,
    private val workspaceManager: WorkspaceManager,
    private val webAgent: WebViewWebAgent,
    private val mcpClientManager: McpClientManager,
    private val sshExecutor: SshExecutor,
    private val systemCommandExecutor: SystemCommandExecutor,
    private val webDavClient: WebDavClient,
    private val fileTransferClient: FileTransferClient,
    private val backupManager: BackupManager,
    private val miniServerManager: MiniServerManager,
    private val downloadTaskManager: DownloadTaskManager,
    private val scheduledTaskManager: ScheduledTaskManager,
    private val responseCache: AiResponseCache? = null,
) {
    var approvalHandler: suspend (ToolApprovalRequest) -> ToolApprovalDecision = { ToolApprovalDecision.Approved }
    var todoSetHandler: suspend (Long, List<TodoItem>) -> String = { _, _ -> "TODO 列表已记录" }
    var todoUpdateHandler: suspend (Long, String, String, String) -> String = { _, _, _, _ -> "TODO 状态已更新" }
    var configChangedHandler: suspend () -> Unit = {}
    var fileEditHandler: suspend (AgentFileMutation) -> AgentFileEditResult = { AgentFileEditResult.NotHandled }
    var fileMutationHandler: suspend (AgentFileMutation) -> Unit = {}
    var fileActivityHandler: suspend (AgentFileActivity?) -> Unit = {}

    fun localMcpToolDefinitions(): JSONArray = toolDefinitions()

    suspend fun executeLocalMcpTool(name: String, arguments: JSONObject): String {
        val rawArguments = arguments.toString()
        return executeTool(
            conversationId = LOCAL_MCP_CONVERSATION_ID,
            call = ToolCall(
                id = "local_mcp_${System.currentTimeMillis()}_${name.take(32)}",
                name = name,
                arguments = arguments,
                rawArguments = rawArguments,
            ),
            skipApproval = true,
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val reachabilityClient = client.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val tokenizer by lazy { DeepSeekV3Tokenizer.get(context) }
    private val forcedSkillsByConversation = ConcurrentHashMap<Long, List<String>>()

    suspend fun chat(
        conversationId: Long,
        userInput: String,
        profile: ApiProfile,
        model: String,
        userMessagePersisted: Boolean = false,
        propagateErrors: Boolean = false,
        forcedSkillIds: List<String> = emptyList(),
        onUpdate: suspend (ChatUpdate) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val normalizedForcedSkillIds = forcedSkillIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedForcedSkillIds.isNotEmpty()) {
            forcedSkillsByConversation[conversationId] = normalizedForcedSkillIds
        }
        try {
            conversationStore.setConversationMeta(
                conversationId,
                title = titleFor(conversationId, userInput),
                status = ConversationStore.STATUS_RUNNING,
                profileId = profile.id,
                model = model,
            )
            if (!userMessagePersisted) {
                conversationStore.addMessage(conversationId, "user", userInput, profileId = profile.id, model = model)
            }
            onUpdate(ChatUpdate("", "", uiText("已发送")))
            runLoop(conversationId, profile, model, onUpdate, propagateErrors)
        } finally {
            if (normalizedForcedSkillIds.isNotEmpty()) forcedSkillsByConversation.remove(conversationId)
        }
    }

    suspend fun summarizeConversationTopic(
        profile: ApiProfile,
        model: String,
        firstUserMessage: String,
    ): String = withContext(Dispatchers.IO) {
        require(profile.apiKey.isNotBlank()) { "请先配置 ${profile.name} 的 API Key" }
        val input = firstUserMessage.trim().take(4000)
        require(input.isNotBlank()) { "首条消息不能为空" }
        val instruction = "为下面的新对话生成一个简短标题。中文用4到12个汉字，英文用2到6个词。只输出标题，不要引号、前缀、标点或解释。"
        val rawTitle = when (profile.apiFormat) {
            ApiProfile.API_FORMAT_ANTHROPIC -> {
                val payload = JSONObject().put("model", model).put("max_tokens", 48).put("temperature", 0.2)
                    .put("system", instruction).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", input)))
                val request = Request.Builder().url(profile.chatEndpoint).addHeader("x-api-key", profile.apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION).addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("话题总结请求失败 ${response.code}: ${body.take(300)}")
                    val blocks = JSONObject(body).optJSONArray("content") ?: JSONArray()
                    buildString { for (index in 0 until blocks.length()) blocks.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text")?.let(::append) }
                }
            }
            ApiProfile.API_FORMAT_GEMINI -> {
                val payload = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", input)))))
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
                    .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 48))
                val request = Request.Builder().url(profile.geminiGenerateContentEndpoint(model)).addHeader("x-goog-api-key", profile.apiKey)
                    .addHeader("Content-Type", "application/json").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("话题总结请求失败 ${response.code}: ${body.take(300)}")
                    val parts = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                    buildString { for (index in 0 until parts.length()) parts.optJSONObject(index)?.optString("text")?.let(::append) }
                }
            }
            else -> {
                val payload = JSONObject().put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", instruction)).put(JSONObject().put("role", "user").put("content", input)))
                    .put("temperature", 0.2).put("max_tokens", 48).put("stream", false)
                val request = Request.Builder().url(profile.chatEndpoint).addHeader("Authorization", "Bearer ${profile.apiKey}")
                    .addHeader("Content-Type", "application/json").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("话题总结请求失败 ${response.code}: ${body.take(300)}")
                    JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                }
            }
        }
        sanitizeConversationTopic(rawTitle)
    }

    fun estimatedConversationContextTokens(conversationId: Long): Long {
        return REQUEST_STATIC_INPUT_TOKENS + contextHistory(conversationId, -1L).sumOf { it.promptInputCost() }
    }

    suspend fun compressConversationHistory(
        conversationId: Long,
        profile: ApiProfile,
        model: String,
        customInstruction: String,
    ): String = withContext(Dispatchers.IO) {
        require(profile.apiKey.isNotBlank()) { "请先配置 ${profile.name} 的 API Key" }
        require(model.isNotBlank()) { "未配置会话历史压缩模型" }
        val history = contextHistory(conversationId, -1L)
        require(history.isNotEmpty()) { "当前会话没有可压缩的历史" }
        val transcript = buildCompressionTranscript(history)
        val instruction = buildString {
            append("你负责压缩会话历史，输出将直接替代旧消息并作为后续对话的唯一历史依据。")
            append("完整保留用户目标、明确要求、关键事实、已完成工作、未完成事项、重要决定、约束、文件路径、代码符号、命令结果、错误与下一步。")
            append("去除寒暄、重复和无助于继续任务的过程噪音。不要虚构信息。使用结构清晰、信息密集的纯文本，不要添加关于本指令的解释。")
            customInstruction.trim().takeIf { it.isNotBlank() }?.let {
                append("\n\n用户的额外压缩要求（优先遵循）：\n")
                append(it)
            }
        }
        val rawSummary = when (profile.apiFormat) {
            ApiProfile.API_FORMAT_ANTHROPIC -> {
                val payload = JSONObject().put("model", model).put("max_tokens", HISTORY_COMPRESSION_MAX_OUTPUT_TOKENS)
                    .put("temperature", 0.1).put("system", instruction)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", transcript)))
                val request = Request.Builder().url(profile.chatEndpoint).addHeader("x-api-key", profile.apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION).addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(historyCompressionHttpError(response.code, body))
                    val blocks = JSONObject(body).optJSONArray("content") ?: JSONArray()
                    buildString {
                        for (index in 0 until blocks.length()) {
                            blocks.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text")?.let(::append)
                        }
                    }
                }
            }
            ApiProfile.API_FORMAT_GEMINI -> {
                val payload = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", transcript)))))
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
                    .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", HISTORY_COMPRESSION_MAX_OUTPUT_TOKENS))
                val request = Request.Builder().url(profile.geminiGenerateContentEndpoint(model)).addHeader("x-goog-api-key", profile.apiKey)
                    .addHeader("Content-Type", "application/json").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(historyCompressionHttpError(response.code, body))
                    val parts = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                    buildString { for (index in 0 until parts.length()) parts.optJSONObject(index)?.optString("text")?.let(::append) }
                }
            }
            else -> {
                val payload = JSONObject().put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", instruction)).put(JSONObject().put("role", "user").put("content", transcript)))
                    .put("temperature", 0.1).put("max_tokens", HISTORY_COMPRESSION_MAX_OUTPUT_TOKENS).put("stream", false)
                val request = Request.Builder().url(profile.chatEndpoint).addHeader("Authorization", "Bearer ${profile.apiKey}")
                    .addHeader("Content-Type", "application/json").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(historyCompressionHttpError(response.code, body))
                    JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                }
            }
        }
        cleanGeneratedText(rawSummary).trim().also {
            require(it.isNotBlank()) { "会话历史压缩模型未返回有效摘要，原上下文已保留" }
        }
    }

    private fun buildCompressionTranscript(history: List<ChatMessage>): String = buildString {
        append("LYRA_HISTORY_TO_COMPRESS_V1\n")
        history.forEachIndexed { index, message ->
            append("\n--- message ").append(index + 1).append(" role=").append(message.role).append(" ---\n")
            if (message.thinking.isNotBlank()) append("thinking:\n").append(message.thinking).append('\n')
            append("content:\n").append(message.content)
            toolCallsOutputText(message.rawJson).takeIf { it.isNotBlank() }?.let { append("\ntool_calls:\n").append(it) }
            append('\n')
        }
    }

    private fun historyCompressionHttpError(code: Int, body: String): String {
        val detail = body.take(500)
        return uiText(if (code == 400 || code == 413) {
            "会话历史压缩失败：历史可能超过所选压缩模型的上下文窗口（HTTP $code）。原上下文已保留。$detail"
        } else {
            "会话历史压缩请求失败（HTTP $code）。原上下文已保留。$detail"
        })
    }

    suspend fun continueConversation(
        conversationId: Long,
        profile: ApiProfile,
        model: String,
        onUpdate: suspend (ChatUpdate) -> Unit,
    ) = withContext(Dispatchers.IO) {
        conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_RUNNING, profileId = profile.id, model = model)
        runLoop(conversationId, profile, model, onUpdate)
    }

    private val reachabilityService = AgentReachabilityService(client, reachabilityClient)

    fun fetchModels(profile: ApiProfile): Result<List<String>> = reachabilityService.fetchModels(profile)

    fun checkReachability(profile: ApiProfile, models: List<String>): ProviderReachabilityReport =
        reachabilityService.checkReachability(profile, models)

    fun checkProviderReachability(profile: ApiProfile): ProviderReachabilityResult =
        reachabilityService.checkProviderReachability(profile)

    fun checkModelReachability(profile: ApiProfile, model: String): ModelReachabilityResult =
        reachabilityService.checkModelReachability(profile, model)

    private suspend fun runLoop(
        conversationId: Long,
        profile: ApiProfile,
        model: String,
        onUpdate: suspend (ChatUpdate) -> Unit,
        propagateErrors: Boolean = false,
    ) {
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val assistantId = conversationStore.addMessage(conversationId, "assistant", "", profileId = profile.id, model = model)
                val result = streamModel(
                    conversationId = conversationId,
                    excludeMessageId = assistantId,
                    profile = profile,
                    model = model,
                    onDelta = { content, thinking ->
                        conversationStore.updateMessage(assistantId, content = content, thinking = thinking)
                        onUpdate(ChatUpdate(content, thinking, uiText("输出中"), assistantId))
                    },
                    onRetry = { retryNumber, maxRetries, error ->
                        conversationStore.updateMessage(assistantId, content = "", thinking = "")
                        val retryStatus = uiText(context.getString(R.string.status_request_retry, retryNumber, maxRetries))
                        Log.w(
                            AGENT_TAG,
                            "model_request_retry conversation=$conversationId model=$model retry=$retryNumber/$maxRetries error=${error.message}",
                        )
                        onUpdate(ChatUpdate("", "", retryStatus, assistantId))
                    },
                )
                conversationStore.updateMessage(
                    assistantId,
                    content = result.content,
                    thinking = result.thinking,
                    rawJson = result.rawMessage.toString(),
                    tokensPerSecond = result.tokensPerSecond,
                )
                conversationStore.recordUsageModelRequest(
                    assistantId,
                    estimatedPromptInputTokens(conversationId, assistantId),
                    estimatedAssistantOutputTokens(result.content, result.thinking, result.rawMessage.toString()),
                )
                onUpdate(ChatUpdate(result.content, result.thinking, if (result.fromCache) uiText("缓存命中") else uiText("模型完成"), assistantId, result.tokensPerSecond))
                if (result.toolCalls.isEmpty()) {
                    conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_IDLE, profileId = profile.id, model = model)
                    return
                }
                result.toolCalls.forEach { call ->
                    onUpdate(ChatUpdate(result.content, result.thinking, uiText("调用工具：") + call.name, assistantId))
                    val toolResult = executeTool(conversationId, call) { toolStatus ->
                        onUpdate(ChatUpdate(result.content, result.thinking, toolStatus, assistantId))
                    }
                    conversationStore.addMessage(
                        conversationId,
                        "tool",
                        toolResult.take(MAX_TOOL_RESULT_CHARS),
                        profileId = profile.id,
                        model = model,
                        toolCallId = call.id,
                    )
                    onUpdate(ChatUpdate(result.content, result.thinking, uiText("工具完成：") + call.name, assistantId))
                }
            }
        } catch (error: CancellationException) {
            conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_INTERRUPTED, profileId = profile.id, model = model)
            throw error
        } catch (error: Throwable) {
            conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_INTERRUPTED, profileId = profile.id, model = model)
            val finalError = uiText("请求中断：") + error.message.orEmpty()
            conversationStore.addMessage(conversationId, "assistant", finalError, profileId = profile.id, model = model)
            onUpdate(ChatUpdate("", "", finalError))
            if (propagateErrors) throw error
        }
    }

    private suspend fun streamModel(
        conversationId: Long,
        excludeMessageId: Long,
        profile: ApiProfile,
        model: String,
        onDelta: suspend (String, String) -> Unit,
        onRetry: suspend (retryNumber: Int, maxRetries: Int, error: Throwable) -> Unit,
    ): StreamingResult {
        return executeModelRequestWithRetry(onRetry = onRetry) {
            when (profile.apiFormat) {
                ApiProfile.API_FORMAT_ANTHROPIC -> requestAnthropicModel(conversationId, excludeMessageId, profile, model, onDelta)
                ApiProfile.API_FORMAT_GEMINI -> requestGeminiModel(conversationId, excludeMessageId, profile, model, onDelta)
                else -> streamOpenAiModel(conversationId, excludeMessageId, profile, model, onDelta)
            }
        }
    }

    private suspend fun streamOpenAiModel(
        conversationId: Long,
        excludeMessageId: Long,
        profile: ApiProfile,
        model: String,
        onDelta: suspend (String, String) -> Unit,
    ): StreamingResult {
        require(profile.apiKey.isNotBlank()) { "请先配置 ${profile.name} 的 API Key" }
        val requestJson = JSONObject()
            .put("model", model)
            .put("tools", toolDefinitions(allowSubAgentsFor(conversationId)))
            .put("tool_choice", "auto")
            .put("messages", promptMessages(conversationId, excludeMessageId))
            .put("temperature", 0.2)
            .put("stream", true)
        applyProviderCacheHints(requestJson, profile, model, conversationId)
        applyReasoningDepthHint(requestJson, profile, model)

        val allowLocalResponseCache = !isFreshSingleUserTurn(conversationId, excludeMessageId)
        if (allowLocalResponseCache) responseCache?.get(profile, requestJson)?.let { cached ->
            val result = cached.toStreamingResult()
            Log.d(
                AGENT_TAG,
                "stream_cache_hit conversation=$conversationId model=$model toolCalls=${result.toolCalls.map { it.name }} contentChars=${result.content.length}",
            )
            if (result.content.isNotBlank() || result.thinking.isNotBlank()) {
                onDelta(result.content, result.thinking)
            }
            return result
        }

        val body = requestJson
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(profile.chatEndpoint)
            .addHeader("Authorization", "Bearer ${profile.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val content = StringBuilder()
        val thinking = StringBuilder()
        val startedAtNanos = System.nanoTime()
        var promptTokens = 0L
        var completionTokens = 0L
        var cachedPromptTokens = 0L
        val toolBuilders = linkedMapOf<Int, ToolCallBuilder>()
        client.newCall(request).execute().use { response ->
            val source = response.body ?: throw IOException("响应为空")
            if (!response.isSuccessful) {
                val text = source.string()
                throwModelRequestHttpError(response.code, text)
            }
            source.byteStream().bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") return@forEach
                    val root = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                    root.optJSONObject("usage")?.let { usage ->
                        promptTokens = usage.optLong("prompt_tokens", promptTokens)
                        completionTokens = usage.optLong("completion_tokens", completionTokens)
                        cachedPromptTokens = usage.optJSONObject("prompt_tokens_details")
                            ?.optLong("cached_tokens", cachedPromptTokens)
                            ?: cachedPromptTokens
                    }
                    val delta = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return@forEach
                    val thinkDelta = delta.stringFieldOrNull("reasoning_content")
                        ?: delta.stringFieldOrNull("thinking_content")
                        ?: delta.stringFieldOrNull("reasoning")
                    if (thinkDelta != null) thinking.append(thinkDelta)
                    val contentDelta = delta.stringFieldOrNull("content")
                    if (contentDelta != null) content.append(contentDelta)
                    parseToolDelta(delta, toolBuilders)
                    if (contentDelta != null || thinkDelta != null) onDelta(content.toString(), thinking.toString())
                }
            }
        }
        val calls = toolBuilders.mapNotNull { (index, builder) -> builder.toToolCall(index) }
        Log.d(
            AGENT_TAG,
            "stream_done conversation=$conversationId model=$model toolCalls=${calls.map { it.name }} contentChars=${content.length} thinkingChars=${thinking.length} promptTokens=$promptTokens cachedPromptTokens=$cachedPromptTokens",
        )
        val split = splitInlineThink(content.toString(), thinking.toString())
        val cleanContent = cleanGeneratedText(split.first)
        val cleanThinking = cleanGeneratedText(split.second)
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", cleanContent)
        if (cleanThinking.isNotBlank()) {
            message.put("reasoning_content", cleanThinking)
        }
        if (calls.isNotEmpty()) {
            message.put("tool_calls", JSONArray().apply { calls.forEach { put(it.toJson()) } })
        }
        if (allowLocalResponseCache) {
            responseCache?.put(
                profile,
                requestJson,
                AiCachedResponse(
                    content = cleanContent,
                    thinking = cleanThinking,
                    rawMessage = message.toString(),
                ),
            )
        }
        return StreamingResult(cleanContent, cleanThinking, message, calls, outputTokensPerSecond(cleanContent, completionTokens, startedAtNanos))
    }

    private fun isFreshSingleUserTurn(conversationId: Long, excludeMessageId: Long): Boolean {
        val history = conversationStore.messages(conversationId).filter { it.id != excludeMessageId }
        return history.count { it.role == "user" } == 1 &&
            history.none { it.role == "assistant" || it.role == "tool" }
    }

    private fun applyReasoningDepthHint(requestJson: JSONObject, profile: ApiProfile, model: String) {
        val depth = settings.reasoningDepth
        if (depth == AppSettings.REASONING_AUTO) return
        if (profile.apiFormat != ApiProfile.API_FORMAT_OPENAI) return
        if (!modelLooksReasoningCapable(model)) return
        val effort = when (depth) {
            AppSettings.REASONING_LOW -> "low"
            AppSettings.REASONING_MEDIUM -> "medium"
            AppSettings.REASONING_HIGH, AppSettings.REASONING_ULTRA -> "high"
            else -> return
        }
        requestJson.put("reasoning_effort", effort)
    }

    private fun modelLooksReasoningCapable(model: String): Boolean {
        val clean = model.lowercase(Locale.US)
        return listOf("o1", "o3", "o4", "gpt-5", "reason", "reasoner", "r1", "qwen3", "glm-4.5", "glm-5")
            .any { clean.contains(it) }
    }

    private suspend fun requestAnthropicModel(
        conversationId: Long,
        excludeMessageId: Long,
        profile: ApiProfile,
        model: String,
        onDelta: suspend (String, String) -> Unit,
    ): StreamingResult {
        require(profile.apiKey.isNotBlank()) { "请先配置 ${profile.name} 的 API Key" }
        val requestJson = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("temperature", 0.2)
            .put("system", providerSystemText(conversationId))
            .put("messages", anthropicMessages(conversationId, excludeMessageId))
            .put("tools", anthropicTools(allowSubAgentsFor(conversationId)))
            .put("stream", true)
        val request = Request.Builder()
            .url(profile.chatEndpoint)
            .addHeader("x-api-key", profile.apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val content = StringBuilder()
        val thinking = StringBuilder()
        val startedAtNanos = System.nanoTime()
        var outputTokens = 0L
        val blockBuilders = linkedMapOf<Int, AnthropicBlockBuilder>()
        val nonStreamingBody = StringBuilder()
        var sawStreamingData = false
        client.newCall(request).execute().use { response ->
            val source = response.body ?: throw IOException("响应为空")
            if (!response.isSuccessful) {
                val body = source.string()
                throwModelRequestHttpError(response.code, body)
            }
            source.byteStream().bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) {
                        if (line.isNotBlank() && !line.startsWith("event:")) nonStreamingBody.appendLine(line)
                        return@forEach
                    }
                    sawStreamingData = true
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") return@forEach
                    val root = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                    root.optJSONObject("usage")?.let { usage ->
                        outputTokens = usage.optLong("output_tokens", outputTokens)
                    }
                    when (root.optString("type")) {
                        "content_block_start" -> {
                            val blockIndex = root.optInt("index")
                            val block = root.optJSONObject("content_block") ?: JSONObject()
                            val builder = blockBuilders.getOrPut(blockIndex) { AnthropicBlockBuilder() }
                            builder.type = block.optString("type")
                            builder.id = block.optString("id")
                            builder.name = block.optString("name")
                            block.stringFieldOrNull("text")?.let {
                                builder.text.append(it)
                                content.append(it)
                                onDelta(content.toString(), thinking.toString())
                            }
                            block.stringFieldOrNull("thinking")?.let {
                                builder.thinking.append(it)
                                thinking.append(it)
                                onDelta(content.toString(), thinking.toString())
                            }
                            block.optJSONObject("input")?.takeIf { it.length() > 0 }?.let { builder.input.append(it.toString()) }
                        }
                        "content_block_delta" -> {
                            val blockIndex = root.optInt("index")
                            val builder = blockBuilders.getOrPut(blockIndex) { AnthropicBlockBuilder() }
                            val delta = root.optJSONObject("delta") ?: JSONObject()
                            delta.stringFieldOrNull("text")?.let {
                                builder.text.append(it)
                                content.append(it)
                                onDelta(content.toString(), thinking.toString())
                            }
                            delta.stringFieldOrNull("thinking")?.let {
                                builder.thinking.append(it)
                                thinking.append(it)
                                onDelta(content.toString(), thinking.toString())
                            }
                            delta.stringFieldOrNull("partial_json")?.let { builder.input.append(it) }
                        }
                    }
                }
            }
        }
        if (!sawStreamingData && nonStreamingBody.isNotBlank()) {
            val root = JSONObject(nonStreamingBody.toString())
            outputTokens = root.optJSONObject("usage")?.optLong("output_tokens", outputTokens) ?: outputTokens
            val contentBlocks = root.optJSONArray("content") ?: JSONArray()
            for (index in 0 until contentBlocks.length()) {
                val block = contentBlocks.optJSONObject(index) ?: continue
                when (block.optString("type")) {
                    "text" -> content.append(block.optString("text"))
                    "thinking" -> thinking.append(block.optString("thinking"))
                    "tool_use" -> {
                        val builder = blockBuilders.getOrPut(index) { AnthropicBlockBuilder() }
                        builder.type = "tool_use"
                        builder.id = block.optString("id")
                        builder.name = block.optString("name")
                        block.optJSONObject("input")?.let { builder.input.append(it.toString()) }
                    }
                }
            }
            onDelta(content.toString(), thinking.toString())
        }
        val calls = blockBuilders.mapNotNull { (index, builder) ->
            if (builder.type != "tool_use" || builder.name.isBlank()) {
                null
            } else {
                val raw = builder.input.toString().ifBlank { "{}" }
                ToolCall(
                    id = builder.id.ifBlank { "tool_$index" },
                    name = builder.name,
                    arguments = runCatching { JSONObject(raw) }.getOrElse { JSONObject() },
                    rawArguments = raw,
                )
            }
        }
        val cleanContent = cleanGeneratedText(content.toString())
        val cleanThinking = cleanGeneratedText(thinking.toString())
        val raw = assistantRawMessage(cleanContent, cleanThinking, calls)
        return StreamingResult(cleanContent, cleanThinking, raw, calls, outputTokensPerSecond(cleanContent, outputTokens, startedAtNanos))
    }

    private suspend fun requestGeminiModel(
        conversationId: Long,
        excludeMessageId: Long,
        profile: ApiProfile,
        model: String,
        onDelta: suspend (String, String) -> Unit,
    ): StreamingResult {
        require(profile.apiKey.isNotBlank()) { "请先配置 ${profile.name} 的 API Key" }
        val requestJson = JSONObject()
            .put("contents", geminiContents(conversationId, excludeMessageId))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", providerSystemText(conversationId)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", geminiFunctionDeclarations(allowSubAgentsFor(conversationId)))))
        val request = Request.Builder()
            .url(profile.geminiGenerateContentEndpoint(model))
            .addHeader("x-goog-api-key", profile.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val startedAtNanos = System.nanoTime()
        client.newCall(request).execute().use { response ->
            val source = response.body ?: throw IOException("响应为空")
            val body = source.string()
            if (!response.isSuccessful) throwModelRequestHttpError(response.code, body)
            val root = JSONObject(body)
            val outputTokens = root.optJSONObject("usageMetadata")?.optLong("candidatesTokenCount", 0L) ?: 0L
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: JSONArray()
            val content = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                part.stringFieldOrNull("text")?.let { content.append(it) }
                part.optJSONObject("functionCall")?.let { functionCall ->
                    val args = functionCall.optJSONObject("args") ?: JSONObject()
                    calls += ToolCall(
                        id = "gemini_${index}_${sha256(functionCall.toString()).take(10)}",
                        name = functionCall.optString("name"),
                        arguments = args,
                        rawArguments = args.toString(),
                    )
                }
            }
            val cleanContent = cleanGeneratedText(content.toString())
            onDelta(cleanContent, "")
            val raw = assistantRawMessage(cleanContent, "", calls)
            return StreamingResult(cleanContent, "", raw, calls, outputTokensPerSecond(cleanContent, outputTokens, startedAtNanos))
        }
    }

    private fun throwModelRequestHttpError(statusCode: Int, body: String): Nothing {
        val message = uiText("AI 请求失败 ") + "$statusCode: ${body.take(600)}"
        if (isRetryableModelHttpStatus(statusCode)) {
            throw RetryableModelHttpException(statusCode, message)
        }
        error(message)
    }

    private fun assistantRawMessage(content: String, thinking: String, calls: List<ToolCall>): JSONObject {
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", content)
        if (thinking.isNotBlank()) message.put("reasoning_content", thinking)
        if (calls.isNotEmpty()) message.put("tool_calls", JSONArray().apply { calls.forEach { put(it.toJson()) } })
        return message
    }

    private fun promptMessages(conversationId: Long, excludeMessageId: Long): JSONArray {
        val messages = JSONArray()
            .put(staticSystemMessage())
            .put(activeSystemPromptMessage())
            .put(memorySystemMessage())
            .put(activeSkillsMessage(conversationId))
            .put(sessionContextMessage())
        val history = openAiHistoryGroups(conversationId, excludeMessageId)
        history.forEach { group ->
            group.forEach { messages.put(it) }
        }
        return sanitizePromptMessageSequence(messages)
    }

    private fun providerSystemText(conversationId: Long): String {
        return listOf(
            staticSystemMessage(),
            activeSystemPromptMessage(),
            memorySystemMessage(),
            activeSkillsMessage(conversationId),
            sessionContextMessage(),
        ).joinToString("\n\n") { it.optString("content") }
    }

    private fun providerHistory(conversationId: Long, excludeMessageId: Long): List<ChatMessage> {
        return contextHistory(conversationId, excludeMessageId)
    }

    private fun contextHistory(conversationId: Long, excludeMessageId: Long): List<ChatMessage> {
        val conversation = conversationStore.conversation(conversationId)
        val source = conversationStore.messages(conversationId)
            .filter { it.id != excludeMessageId && it.id > (conversation?.compressedThroughMessageId ?: 0L) }
        val summary = conversation?.compressedContext.orEmpty().trim()
        if (summary.isBlank()) return source
        return listOf(
            ChatMessage(
                id = conversation?.compressedThroughMessageId ?: 0L,
                conversationId = conversationId,
                role = "user",
                content = "LYRA_COMPRESSED_CONVERSATION_CONTEXT_V1\n$summary\n\n以上内容是此前会话历史的压缩摘要，请将其视为已发生的对话事实并继续当前任务。",
                thinking = "",
                profileId = conversation?.profileId.orEmpty(),
                model = conversation?.model.orEmpty(),
                toolCallId = null,
                rawJson = null,
                createdAt = conversation?.updatedAt ?: System.currentTimeMillis(),
            ),
        ) + source
    }

    private fun anthropicMessages(conversationId: Long, excludeMessageId: Long): JSONArray {
        val output = JSONArray()
        val source = providerHistory(conversationId, excludeMessageId)
        var index = 0
        while (index < source.size) {
            val message = source[index]
            when (message.role) {
                "user" -> output.put(JSONObject().put("role", "user").put("content", anthropicUserContent(message.content)))
                "assistant" -> {
                    val toolUseIds = message.anthropicToolUseIds()
                    if (toolUseIds.isEmpty()) {
                        output.put(JSONObject().put("role", "assistant").put("content", anthropicAssistantContent(message)))
                    } else {
                        val toolResults = JSONArray()
                        val returned = mutableSetOf<String>()
                        var next = index + 1
                        while (next < source.size && source[next].role == "tool") {
                            val tool = source[next]
                            val id = tool.toolCallId.orEmpty()
                            if (id in toolUseIds) {
                                toolResults.put(anthropicToolResult(tool))
                                returned += id
                            }
                            next++
                        }
                        if (returned.containsAll(toolUseIds)) {
                            output.put(JSONObject().put("role", "assistant").put("content", anthropicAssistantContent(message)))
                            output.put(JSONObject().put("role", "user").put("content", toolResults))
                        }
                        index = next
                        continue
                    }
                }
                "tool" -> Unit
            }
            index++
        }
        return output
    }

    private fun anthropicUserContent(content: String): JSONArray {
        if (!hasUploadedAttachments(content)) {
            return JSONArray().put(JSONObject().put("type", "text").put("text", content.ifBlank { " " }))
        }
        val openAi = userPromptWithAttachments(content)
        val parts = openAi.optJSONArray("content") ?: JSONArray()
        return JSONArray().also { output ->
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                when (part.optString("type")) {
                    "text" -> output.put(JSONObject().put("type", "text").put("text", part.optString("text").ifBlank { " " }))
                    "image_url" -> {
                        val dataUrl = part.optJSONObject("image_url")?.optString("url").orEmpty()
                        parseDataUrlForProvider(dataUrl)?.let { parsed ->
                            output.put(
                                JSONObject()
                                    .put("type", "image")
                                    .put(
                                        "source",
                                        JSONObject()
                                            .put("type", "base64")
                                            .put("media_type", parsed.first)
                                            .put("data", parsed.second),
                                    ),
                            )
                        } ?: output.put(JSONObject().put("type", "text").put("text", "图片无法转换为 Claude 可读取的 base64 image block。"))
                    }
                    else -> output.put(JSONObject().put("type", "text").put("text", "该媒体类型无法直接转换为 Anthropic Messages API 输入块：${part.optString("type")}"))
                }
            }
        }
    }

    private fun anthropicAssistantContent(message: ChatMessage): JSONArray {
        val raw = message.rawJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        return JSONArray().also { output ->
            val text = cleanGeneratedText(message.content)
            if (text.isNotBlank()) output.put(JSONObject().put("type", "text").put("text", text))
            val calls = raw?.optJSONArray("tool_calls") ?: JSONArray()
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                val function = call.optJSONObject("function") ?: JSONObject()
                val args = runCatching { JSONObject(function.optString("arguments").ifBlank { "{}" }) }.getOrElse { JSONObject() }
                output.put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", call.optString("id").ifBlank { "tool_$index" })
                        .put("name", function.optString("name"))
                        .put("input", args),
                )
            }
            if (output.length() == 0) output.put(JSONObject().put("type", "text").put("text", " "))
        }
    }

    private fun anthropicToolResult(message: ChatMessage): JSONObject {
        return JSONObject()
            .put("type", "tool_result")
            .put("tool_use_id", message.toolCallId.orEmpty())
            .put("content", message.content)
    }

    private fun ChatMessage.anthropicToolUseIds(): Set<String> {
        val raw = rawJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptySet()
        val calls = raw.optJSONArray("tool_calls") ?: return emptySet()
        return buildSet {
            for (index in 0 until calls.length()) {
                calls.optJSONObject(index)?.optString("id").orEmpty().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun geminiContents(conversationId: Long, excludeMessageId: Long): JSONArray {
        val output = JSONArray()
        providerHistory(conversationId, excludeMessageId).forEach { message ->
            when (message.role) {
                "user" -> output.put(JSONObject().put("role", "user").put("parts", geminiUserParts(message.content)))
                "assistant" -> output.put(JSONObject().put("role", "model").put("parts", geminiAssistantParts(message)))
                "tool" -> output.put(JSONObject().put("role", "user").put("parts", JSONArray().put(geminiFunctionResponse(message))))
            }
        }
        return output
    }

    private fun geminiUserParts(content: String): JSONArray {
        if (!hasUploadedAttachments(content)) return JSONArray().put(JSONObject().put("text", content.ifBlank { " " }))
        val openAi = userPromptWithAttachments(content)
        val parts = openAi.optJSONArray("content") ?: JSONArray()
        return JSONArray().also { output ->
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                when (part.optString("type")) {
                    "text" -> output.put(JSONObject().put("text", part.optString("text").ifBlank { " " }))
                    "image_url" -> {
                        val dataUrl = part.optJSONObject("image_url")?.optString("url").orEmpty()
                        parseDataUrlForProvider(dataUrl)?.let { parsed ->
                            output.put(JSONObject().put("inlineData", JSONObject().put("mimeType", parsed.first).put("data", parsed.second)))
                        }
                    }
                    "input_audio" -> {
                        val audio = part.optJSONObject("input_audio") ?: JSONObject()
                        output.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "audio/${audio.optString("format", "mp3")}").put("data", audio.optString("data"))))
                    }
                    "video_url" -> {
                        val dataUrl = part.optJSONObject("video_url")?.optString("url").orEmpty()
                        parseDataUrlForProvider(dataUrl)?.let { parsed ->
                            output.put(JSONObject().put("inlineData", JSONObject().put("mimeType", parsed.first).put("data", parsed.second)))
                        }
                    }
                }
            }
        }
    }

    private fun geminiAssistantParts(message: ChatMessage): JSONArray {
        val raw = message.rawJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        return JSONArray().also { output ->
            val text = cleanGeneratedText(message.content)
            if (text.isNotBlank()) output.put(JSONObject().put("text", text))
            val calls = raw?.optJSONArray("tool_calls") ?: JSONArray()
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                val function = call.optJSONObject("function") ?: JSONObject()
                val args = runCatching { JSONObject(function.optString("arguments").ifBlank { "{}" }) }.getOrElse { JSONObject() }
                output.put(JSONObject().put("functionCall", JSONObject().put("name", function.optString("name")).put("args", args)))
            }
            if (output.length() == 0) output.put(JSONObject().put("text", " "))
        }
    }

    private fun geminiFunctionResponse(message: ChatMessage): JSONObject {
        return JSONObject()
            .put(
                "functionResponse",
                JSONObject()
                    .put("name", toolNameForToolResult(message))
                    .put("response", JSONObject().put("content", message.content)),
            )
    }

    private fun toolNameForToolResult(message: ChatMessage): String {
        val previous = conversationStore.messages(message.conversationId)
            .takeWhile { it.id < message.id }
            .asReversed()
            .firstOrNull { it.role == "assistant" && it.rawJson?.contains(message.toolCallId.orEmpty()) == true }
        val raw = previous?.rawJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val calls = raw?.optJSONArray("tool_calls") ?: return "tool_result"
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index) ?: continue
            if (call.optString("id") == message.toolCallId) {
                return call.optJSONObject("function")?.optString("name").orEmpty().ifBlank { "tool_result" }
            }
        }
        return "tool_result"
    }

    private fun parseDataUrlForProvider(dataUrl: String): Pair<String, String>? {
        val match = Regex("""^data:([^;,]+);base64,(.+)$""", RegexOption.IGNORE_CASE).matchEntire(dataUrl.trim()) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun sanitizePromptMessageSequence(messages: JSONArray): JSONArray {
        val output = mutableListOf<JSONObject>()
        val pendingToolIds = linkedSetOf<String>()
        var pendingAssistantIndex = -1

        fun dropPendingAssistant() {
            if (pendingAssistantIndex >= 0 && pendingAssistantIndex < output.size) {
                while (output.size > pendingAssistantIndex) output.removeAt(output.lastIndex)
            }
            pendingAssistantIndex = -1
            pendingToolIds.clear()
        }

        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            when (message.optString("role")) {
                "tool" -> {
                    val id = message.optString("tool_call_id")
                    if (id.isNotBlank() && id in pendingToolIds) {
                        output += message
                        pendingToolIds -= id
                    }
                }
                "assistant" -> {
                    if (pendingToolIds.isNotEmpty()) dropPendingAssistant()
                    output += message
                    if (message.hasToolCalls()) {
                        pendingAssistantIndex = output.lastIndex
                        pendingToolIds += message.toolCallIds()
                    } else {
                        pendingAssistantIndex = -1
                        pendingToolIds.clear()
                    }
                }
                else -> {
                    if (pendingToolIds.isNotEmpty()) dropPendingAssistant()
                    output += message
                    pendingAssistantIndex = -1
                    pendingToolIds.clear()
                }
            }
        }
        if (pendingToolIds.isNotEmpty()) dropPendingAssistant()
        return JSONArray().also { array -> output.forEach { array.put(it) } }
    }

    private fun openAiHistoryGroups(conversationId: Long, excludeMessageId: Long): List<List<JSONObject>> {
        val source = contextHistory(conversationId, excludeMessageId)
        val groups = mutableListOf<List<JSONObject>>()
        var index = 0
        while (index < source.size) {
            val message = source[index]
            if (message.role == "tool") {
                index++
                continue
            }
            val json = message.toPromptJson()
            if (json.hasToolCalls()) {
                val requiredToolIds = json.toolCallIds()
                val toolMessages = mutableListOf<JSONObject>()
                var next = index + 1
                while (next < source.size && source[next].role == "tool") {
                    val tool = source[next]
                    val toolCallId = tool.toolCallId.orEmpty()
                    if (toolCallId in requiredToolIds) {
                        toolMessages.add(tool.toToolPromptJson())
                    }
                    next++
                }
                val returnedToolIds = toolMessages.map { it.optString("tool_call_id") }.toSet()
                if (requiredToolIds.isNotEmpty() && returnedToolIds.containsAll(requiredToolIds)) {
                    groups.add(listOf(json) + toolMessages)
                }
                index = next
            } else {
                groups.add(listOf(json))
                index++
            }
        }
        return groups
    }

    private fun applyProviderCacheHints(requestJson: JSONObject, profile: ApiProfile, model: String, conversationId: Long) {
        val host = runCatching { URI(profile.chatEndpoint).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        if (!isOfficialOpenAiHost(host)) return
        requestJson.put("prompt_cache_key", openAiPromptCacheKey(profile, model, conversationId))
        if (supportsOpenAiExtendedPromptCache(model)) {
            requestJson.put("prompt_cache_retention", "24h")
        }
    }

    private fun isOfficialOpenAiHost(host: String): Boolean {
        return host == "api.openai.com" || host.endsWith(".api.openai.com")
    }

    private fun supportsOpenAiExtendedPromptCache(model: String): Boolean {
        val normalized = model.lowercase(Locale.US)
        return normalized.startsWith("gpt-5") ||
            normalized.startsWith("gpt-4.1")
    }

    private fun openAiPromptCacheKey(profile: ApiProfile, model: String, conversationId: Long): String {
        val stable = listOf(
            "lyra_code_cache_v2",
            model.trim().lowercase(Locale.US),
            settings.activeSystemPromptText().trim(),
            settings.memoryPrompt(),
            settings.activeSkillsPrompt(forcedSkillIdsFor(conversationId)).trim(),
            workspaceManager.termuxRootPath().orEmpty(),
            workspaceManager.displayName(),
            staticToolFingerprint(),
            normalizeEndpointForCacheKey(profile.chatEndpoint),
        ).joinToString("\n")
        return "lyra-${sha256(stable).take(PROMPT_CACHE_KEY_HASH_CHARS)}"
    }

    private fun staticToolFingerprint(): String {
        return sha256(toolDefinitions(false).toString()).take(PROMPT_CACHE_KEY_HASH_CHARS)
    }

    private fun AiCachedResponse.toStreamingResult(): StreamingResult {
        val raw = runCatching { JSONObject(rawMessage) }.getOrElse {
            JSONObject().put("role", "assistant").put("content", content)
        }
        val calls = raw.optJSONArray("tool_calls")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val call = array.optJSONObject(index) ?: continue
                    val function = call.optJSONObject("function") ?: JSONObject()
                    val rawArguments = function.optString("arguments").ifBlank { "{}" }
                    add(
                        ToolCall(
                            id = call.optString("id").ifBlank { "tool_$index" },
                            name = function.optString("name"),
                            arguments = runCatching { JSONObject(rawArguments) }.getOrElse { JSONObject() },
                            rawArguments = rawArguments,
                        ),
                    )
                }
            }
        }.orEmpty()
        return StreamingResult(
            content = cleanGeneratedText(raw.optString("content").ifBlank { content }),
            thinking = cleanGeneratedText(
                raw.cleanString("reasoning_content")
                    .ifBlank { raw.cleanString("thinking_content") }
                    .ifBlank { thinking },
            ),
            rawMessage = raw,
            toolCalls = calls,
            fromCache = true,
        )
    }

    private fun parseToolDelta(delta: JSONObject, builders: MutableMap<Int, ToolCallBuilder>) {
        val calls = delta.optJSONArray("tool_calls") ?: return
        for (index in 0 until calls.length()) {
            val call = calls.getJSONObject(index)
            val callIndex = call.optInt("index", index)
            val builder = builders.getOrPut(callIndex) { ToolCallBuilder() }
            call.cleanString("id").takeIf { it.isNotBlank() }?.let { builder.id = it }
            val function = call.optJSONObject("function") ?: continue
            function.cleanString("name").takeIf { it.isNotBlank() }?.let { builder.name = it }
            function.stringFieldOrNull("arguments")?.let { builder.arguments.append(it) }
        }
    }

    private suspend fun executeTool(
        conversationId: Long,
        call: ToolCall,
        skipApproval: Boolean = false,
        onStatus: suspend (String) -> Unit = {},
    ): String {
        val args = call.arguments
        val startedAt = System.currentTimeMillis()
        Log.d(
            AGENT_TAG,
            "tool_start conversation=$conversationId name=${call.name} args=${call.rawArguments.take(LOG_ARGUMENT_CHARS)}",
        )
        if (call.name in settings.disabledTools()) {
            val output = ToolExecution("ERROR: TOOL_DISABLED\n工具 ${call.name} 已在 AI Agent 管理中被用户禁用。请改用其他可用工具，或请用户重新启用。")
                .toToolOutputJson(call.name, ok = false)
            Log.w(AGENT_TAG, "tool_end conversation=$conversationId name=${call.name} ok=false disabled=true")
            return output
        }
        return runCatching {
            val approval = if (skipApproval) null else approvalFor(conversationId, call)
            if (approval != null) {
                val decision = approvalHandler(approval)
                if (!decision.approved) {
                    return@runCatching ToolExecution(
                        content = buildString {
                            append("USER_REJECTED_TOOL_CALL: 用户拒绝执行 ${call.name}。")
                            if (decision.feedback.isNotBlank()) append("\n用户要求: ${decision.feedback}")
                            append("\n请根据用户要求调整计划，不要重复提交相同工具调用。")
                        },
                    )
                }
            }
            when (call.name) {
                "list_directory" -> nativeFileManager.listDirectory(args.optString("path"))
                    .fold({ ToolExecution(it.toAgentText()) }, { throw it })
                "read_file" -> readFileWithActivity(args.getString("path"), globalStorage = false)
                "read_file_lines" -> ToolExecution(readFileLines(args, globalStorage = false))
                "write_file" -> writeFileWithDiff(args.getString("path"), args.toolTextArgument("content"))
                "edit_file" -> editFileWithDiff(args, globalStorage = false)
                "append_file" -> appendFileWithDiff(args.getString("path"), args.toolTextArgument("content"))
                "create_folder" -> ToolExecution(nativeFileManager.createFolder(args.getString("path")).getOrThrow())
                "delete_file_or_folder" -> deleteWithDiff(args.getString("path"))
                "rename_move" -> renameMoveWithDiff(args.getString("from"), args.getString("to"))
                "global_list_directory" -> globalFileManager.listDirectory(args.optString("path"))
                    .fold({ ToolExecution(it.toAgentText()) }, { throw it })
                "global_read_file" -> readFileWithActivity(args.getString("path"), globalStorage = true)
                "global_read_file_lines" -> ToolExecution(readFileLines(args, globalStorage = true))
                "global_write_file" -> globalWriteFileWithDiff(args.getString("path"), args.toolTextArgument("content"))
                "global_edit_file" -> editFileWithDiff(args, globalStorage = true)
                "global_append_file" -> globalAppendFileWithDiff(args.getString("path"), args.toolTextArgument("content"))
                "global_create_folder" -> ToolExecution(globalFileManager.createFolder(args.getString("path")).getOrThrow())
                "global_delete_file_or_folder" -> ToolExecution(globalFileManager.delete(args.getString("path")).getOrThrow())
                "global_rename_move" -> ToolExecution(globalFileManager.renameMove(args.getString("from"), args.getString("to")).getOrThrow())
                "download_file" -> downloadFile(args)
                "manage_scheduled_tasks" -> ToolExecution(manageScheduledTasks(args))
                "search_conversation_history" -> ToolExecution(searchConversationHistory(args))
                "read_conversation_history" -> ToolExecution(readConversationHistory(args))
                "read_memories" -> ToolExecution(readMemories(args))
                "save_memory" -> ToolExecution(saveMemory(args))
                "update_memory" -> ToolExecution(updateMemory(args))
                "delete_memory" -> ToolExecution(deleteMemory(args))
                "search_files" -> {
                    val query = args.getString("query")
                    val path = args.optString("path")
                    nativeFileManager.searchFiles(query, path)
                        .fold({ ToolExecution(it.toSearchAgentText(query, path)) }, { throw it })
                }
                "global_search_files" -> globalSearchFiles(args.getString("query"))
                "get_file_info" -> ToolExecution(nativeFileManager.fileInfo(args.getString("path")).getOrThrow())
                "list_skill_files" -> ToolExecution(settings.listSkillFiles(args.getString("skill_id")).getOrThrow())
                "read_skill_file" -> ToolExecution(settings.readSkillFile(args.getString("skill_id"), args.getString("path")).getOrThrow())
                "get_current_time" -> ToolExecution(currentTimeInfo())
                "get_current_location" -> ToolExecution(currentLocationInfo())
                "get_device_hardware_info" -> ToolExecution(DeviceInfoCollector.collectJson(context))
                "list_installed_apps" -> ToolExecution(
                    InstalledAppCollector.collect(
                        context = context,
                        scope = args.optString("scope", "all"),
                        query = args.optString("query"),
                        offset = args.optInt("offset", 0),
                        limit = args.optInt("limit", 100),
                    ),
                )
                "execute_shell_command" -> ToolExecution(
                    systemCommandExecutor.executeShell(
                        command = args.toolCommandArgument(),
                        timeoutSeconds = args.optInt("timeout_seconds", 60),
                    ).toJson(),
                )
                "execute_root_command" -> ToolExecution(
                    systemCommandExecutor.executeRoot(
                        command = args.toolCommandArgument(),
                        timeoutSeconds = args.optInt("timeout_seconds", 60),
                        allowShellFallback = true,
                    ).toJson(),
                )
                "list_ssh_servers" -> ToolExecution(sshExecutor.availableServers())
                "ssh_exec" -> {
                    val server = settings.resolveSshServer(args.getString("server_id"))
                        ?: error("SSH 服务器不存在或已禁用: ${args.optString("server_id")}。请先调用 list_ssh_servers 获取可用 id。")
                    val timeoutSeconds = args.optInt("timeout_seconds", server.timeoutSeconds).coerceIn(5, 600)
                    val result = sshExecutor.execute(
                        server = server,
                        command = args.toolCommandArgument(),
                        cwd = args.optString("cwd"),
                        inputLines = args.optJSONArray("input_lines")?.let { array ->
                            buildList {
                                for (index in 0 until array.length()) add(array.optString(index))
                            }
                        }.orEmpty(),
                        timeoutSeconds = timeoutSeconds,
                    )
                    if (result.ok) ToolExecution(result.message) else error(result.message)
                }
                "list_webdav_servers" -> ToolExecution(webDavClient.serversJson(settings.webDavServers().filter { it.enabled }))
                "webdav_list" -> {
                    val server = settings.resolveWebDavServer(args.getString("server_id"))
                        ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}。请先调用 list_webdav_servers 获取可用 id。")
                    val files = webDavClient.list(
                        server = server,
                        path = args.optString("path").ifBlank { server.initialPath.ifBlank { "/" } },
                        depth = args.optInt("depth", 1).coerceIn(0, 2),
                    )
                    ToolExecution(webDavFilesJson(server, files).put("path", args.optString("path").ifBlank { server.initialPath.ifBlank { "/" } }).toString())
                }
                "webdav_search" -> {
                    val server = settings.resolveWebDavServer(args.getString("server_id"))
                        ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}。请先调用 list_webdav_servers 获取可用 id。")
                    val files = webDavClient.search(
                        server = server,
                        query = args.getString("query"),
                        basePath = args.optString("path").ifBlank { server.initialPath },
                        limit = args.optInt("limit", 80).coerceIn(1, 200),
                    )
                    ToolExecution(webDavFilesJson(server, files).toString())
                }
                "webdav_download_to_workspace" -> {
                    val server = settings.resolveWebDavServer(args.getString("server_id"))
                        ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}")
                    val bytes = webDavClient.download(server, args.getString("remote_path"))
                    val message = nativeFileManager.writeBytes(args.getString("local_path"), bytes).getOrThrow()
                    ToolExecution("$message\n已从 WebDAV 下载 ${bytes.size} bytes。")
                }
                "webdav_upload_from_workspace" -> {
                    val server = settings.resolveWebDavServer(args.getString("server_id"))
                        ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}")
                    val bytes = nativeFileManager.readBytes(args.getString("local_path")).getOrThrow()
                    webDavClient.upload(server, args.getString("remote_path"), bytes)
                    ToolExecution("已上传到 WebDAV: ${server.name}:${args.getString("remote_path")}，大小 ${bytes.size} bytes。")
                }
                "list_file_transfer_servers" -> ToolExecution(fileTransferClient.serversJson(settings.fileTransferServers().filter { it.enabled }))
                "file_transfer_list" -> {
                    val server = settings.resolveFileTransferServer(args.getString("server_id"))
                        ?: error("文件传输服务器不存在或已禁用: ${args.optString("server_id")}。请先调用 list_file_transfer_servers 获取可用 id。")
                    val path = args.optString("path").ifBlank { server.initialPath.ifBlank { "/" } }
                    val files = fileTransferClient.list(server, path)
                    ToolExecution(fileTransferFilesJson(server, files).put("path", path).toString())
                }
                "file_transfer_search" -> {
                    val server = settings.resolveFileTransferServer(args.getString("server_id"))
                        ?: error("文件传输服务器不存在或已禁用: ${args.optString("server_id")}。请先调用 list_file_transfer_servers 获取可用 id。")
                    val files = fileTransferClient.search(
                        server = server,
                        query = args.getString("query"),
                        basePath = args.optString("path").ifBlank { server.initialPath.ifBlank { "/" } },
                        limit = args.optInt("limit", 80).coerceIn(1, 200),
                    )
                    ToolExecution(fileTransferFilesJson(server, files).toString())
                }
                "file_transfer_download_to_workspace" -> {
                    val server = settings.resolveFileTransferServer(args.getString("server_id"))
                        ?: error("文件传输服务器不存在或已禁用: ${args.optString("server_id")}")
                    val bytes = fileTransferClient.download(server, args.getString("remote_path"))
                    val message = nativeFileManager.writeBytes(args.getString("local_path"), bytes).getOrThrow()
                    ToolExecution("$message\n已从 ${server.protocol.uppercase(Locale.US)} 下载 ${bytes.size} bytes。")
                }
                "file_transfer_upload_from_workspace" -> {
                    val server = settings.resolveFileTransferServer(args.getString("server_id"))
                        ?: error("文件传输服务器不存在或已禁用: ${args.optString("server_id")}")
                    val bytes = nativeFileManager.readBytes(args.getString("local_path")).getOrThrow()
                    fileTransferClient.upload(server, args.getString("remote_path"), bytes)
                    ToolExecution("已上传到 ${server.protocol.uppercase(Locale.US)}: ${server.name}:${args.getString("remote_path")}，大小 ${bytes.size} bytes。")
                }
                "export_backup" -> {
                    val options = parseBackupOptions(args)
                    val destination = args.optString("destination", "local").lowercase(Locale.US)
                    if (destination == "webdav") {
                        val server = settings.resolveWebDavServer(args.getString("server_id"))
                            ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}")
                        val remotePath = args.optString("remote_path").ifBlank { DEFAULT_WEBDAV_BACKUP_PATH }
                        val bytes = backupManager.exportZip(options)
                        webDavClient.upload(server, remotePath, bytes)
                        ToolExecution(
                            "已导出备份并上传 WebDAV: ${server.name}:$remotePath，大小 ${bytes.size} bytes。\n" +
                                "未指定 remote_path 时会覆盖固定 latest 备份路径，之后可直接从 WebDAV 导入，无需手动查找时间戳文件名。",
                        )
                    } else {
                        ToolExecution(backupManager.exportToDownloads(options))
                    }
                }
                "import_backup" -> {
                    val source = args.optString("source", "local").lowercase(Locale.US)
                    val result = if (source == "webdav") {
                        val server = settings.resolveWebDavServer(args.getString("server_id"))
                            ?: error("WebDAV 服务器不存在或已禁用: ${args.optString("server_id")}")
                        val remotePath = resolveWebDavBackupPath(server, args.optString("remote_path"))
                        val bytes = webDavClient.download(server, remotePath)
                        backupManager.importZip(bytes, "supplement")
                    } else if (source == "download" || source == "global") {
                        val path = args.optString("global_path").ifBlank { args.optString("local_path") }
                        val bytes = globalFileManager.readBytes(path).getOrThrow()
                        backupManager.importZip(bytes, "supplement")
                    } else {
                        val bytes = nativeFileManager.readBytes(args.getString("local_path")).getOrThrow()
                        backupManager.importZip(bytes, "supplement")
                    }
                    configChangedHandler()
                    ToolExecution("已用补充模式导入备份: $result")
                }
                "get_mini_server_status" -> ToolExecution(miniServerManager.statusJson().toString())
                "read_mini_server_logs" -> ToolExecution(readMiniServerLogs(args))
                "manage_mini_server" -> ToolExecution(manageMiniServer(args))
                "run_command" -> {
                    val command = args.toolCommandArgument()
                    if (isFileSearchCommand(command)) {
                        return@runCatching ToolExecution(
                            "ERROR: FILE_SEARCH_COMMAND_BLOCKED\n" +
                                "需要按文件名查找路径时必须先调用 search_files，而不是用 run_command 执行 find/fd/locate。\n" +
                                "请改用 search_files，参数示例: {\"query\":\"AvatarSkin.json\",\"path\":\".\"}。\n" +
                                "只有 search_files 返回空且用户明确要求扩大到工作区外时，才考虑 shell 搜索。",
                        )
                    }
                    val workDir = normalizeCommandWorkDir(args.cleanString("workDir"))
                    val timeoutSeconds = args.optInt("timeout_seconds", 60).coerceIn(5, 600)
                    val result = termuxExecutor.execute(command, workDir, timeoutSeconds)
                    if (result.ok) ToolExecution(result.message) else error(result.message)
                }
                "web_search" -> ToolExecution(webAgent.search(args.getString("query"), args.optInt("limit", 6)))
                "read_web_page" -> ToolExecution(webAgent.readPage(args.getString("url")))
                "mark_web_sources" -> ToolExecution(webSourceMarkResult(args))
                "manage_app_config" -> ToolExecution(manageAppConfig(args))
                "run_sub_agents" -> ToolExecution(runSubAgents(conversationId, args, onStatus))
                "set_todo_list" -> ToolExecution(todoSetHandler(conversationId, parseTodoItems(args)))
                "update_todo_item" -> ToolExecution(
                    todoUpdateHandler(
                        conversationId,
                        args.getString("id"),
                        args.optString("status", "completed"),
                        args.optString("note"),
                    ),
                )
                else -> {
                    val mcpTool = settings.resolveMcpTool(call.name) ?: error("未知工具: ${call.name}")
                    executeMcpTool(mcpTool.first, mcpTool.second, args)
                }
            }
        }.fold(
            onSuccess = {
                val output = it.toToolOutputJson(call.name, ok = true)
                Log.d(
                    AGENT_TAG,
                    "tool_end conversation=$conversationId name=${call.name} ok=true durationMs=${System.currentTimeMillis() - startedAt} outputChars=${output.length}",
                )
                output
            },
            onFailure = {
                val correctionHint = if (call.name in FILE_TEXT_ARGUMENT_TOOLS) {
                    """

                    请修正参数后重试。content_lines、old_content_lines、new_content_lines 必须是实际 JSON 字符串数组。
                    正确：{"content_lines":["first line","second line",""]}
                    错误：{"content_lines":"\"first line\", \"second line\", \"\""}
                    content 与 content_lines 二选一；不要把数组整体序列化成字符串。修改现有文件优先使用 edit_file/global_edit_file。
                    """.trimIndent()
                } else {
                    ""
                }
                val output = ToolExecution(
                    "ERROR: ${it.message}\narguments: ${call.rawArguments}" +
                        correctionHint.takeIf { hint -> hint.isNotBlank() }?.let { hint -> "\n$hint" }.orEmpty(),
                ).toToolOutputJson(call.name, ok = false)
                Log.w(
                    AGENT_TAG,
                    "tool_end conversation=$conversationId name=${call.name} ok=false durationMs=${System.currentTimeMillis() - startedAt} error=${it.message}",
                    it,
                )
                output
            },
        )
    }

    private fun resolveWebDavBackupPath(server: WebDavServerConfig, requestedPath: String): String {
        val explicit = requestedPath.trim()
        if (explicit.isNotBlank()) return explicit
        val files = runCatching { webDavClient.list(server, "/LyraCode", depth = 1) }.getOrDefault(emptyList())
        val latest = files
            .filter { !it.directory && it.path.endsWith(".zip", ignoreCase = true) }
            .filter {
                val name = it.path.substringAfterLast('/').lowercase(Locale.US)
                "backup" in name || "lyra" in name
            }
        latest.firstOrNull { it.path.equals(DEFAULT_WEBDAV_BACKUP_PATH, ignoreCase = true) }?.let { return it.path }
        return latest.maxWithOrNull(
            compareBy<com.yukisoffd.lyracode.webdav.WebDavFile> { parseWebDavModifiedMillis(it.modified) }
                .thenBy { it.path },
        )?.path ?: DEFAULT_WEBDAV_BACKUP_PATH
    }

    private fun parseWebDavModifiedMillis(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(value)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun runSubAgents(parentConversationId: Long, args: JSONObject, onStatus: suspend (String) -> Unit = {}): String {
        if (!settings.subAgentOrchestrationEnabled) return "ERROR: SUB_AGENT_DISABLED\n用户未开启子代理编排。"
        val candidates = settings.enabledSubAgents()
        if (candidates.isEmpty()) return "ERROR: NO_SUB_AGENT_MODELS\n请先在设置 > 子代理编排中添加并启用子代理模型。"
        val tasks = parseSubAgentTasks(args).take(MAX_SUB_AGENT_TASKS)
        if (tasks.isEmpty()) return "ERROR: NO_SUB_AGENT_TASKS\ntasks 不能为空。"
        val results = JSONArray()
        val assignmentCounts = mutableMapOf<String, Int>()
        tasks.forEachIndexed { index, task ->
            currentCoroutineContext().ensureActive()
            val agentConfig = chooseSubAgent(candidates, task, index, assignmentCounts)
            assignmentCounts[agentConfig.id] = (assignmentCounts[agentConfig.id] ?: 0) + 1
            onStatus(uiText("正在执行子代理任务") + " ${index + 1}/${tasks.size}: ${agentConfig.name}")
            val profile = settings.profiles().firstOrNull { it.id == agentConfig.profileId }
            if (profile == null) {
                results.put(subAgentError(index, agentConfig, task, "模型服务不存在: ${agentConfig.profileId}"))
                return@forEachIndexed
            }
            val model = agentConfig.model.ifBlank { profile.selectedModel }
            val childConversationId = conversationStore.createConversation(
                profileId = profile.id,
                model = model,
                title = "子代理 ${index + 1}: ${task.task.take(32)}",
                mode = ConversationStore.MODE_SUBAGENT,
            )
            val prompt = buildSubAgentPrompt(parentConversationId, task, agentConfig)
            conversationStore.addMessage(childConversationId, "user", prompt, profileId = profile.id, model = model)
            runLoop(
                childConversationId,
                profile,
                model,
                onUpdate = { update ->
                    if (update.status.isNotBlank()) {
                        onStatus(uiText("正在执行子代理任务") + " ${index + 1}/${tasks.size}: ${agentConfig.name} · ${update.status}")
                    }
                },
                propagateErrors = false,
            )
            val assistant = conversationStore.messages(childConversationId).lastOrNull { it.role == "assistant" }
            results.put(
                JSONObject()
                    .put("index", index + 1)
                    .put("agent", agentConfig.name)
                    .put("profile_id", profile.id)
                    .put("model", model)
                    .put("task", task.task)
                    .put("capability_hint", task.capabilityHint)
                    .put("expected_output", task.expectedOutput)
                    .put("result", assistant?.content.orEmpty())
                    .put("status", conversationStore.conversation(childConversationId)?.status ?: "unknown"),
            )
        }
        onStatus(uiText("子代理任务完成"))
        return JSONObject()
            .put("schema", "lyra_sub_agent_results_v1")
            .put("parent_conversation_id", parentConversationId)
            .put("results", results)
            .toString()
    }

    private fun parseSubAgentTasks(args: JSONObject): List<SubAgentTask> {
        val array = args.optJSONArray("tasks") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                when (item) {
                    is JSONObject -> {
                        val task = item.optString("task").ifBlank { item.optString("description") }
                        if (task.isNotBlank()) {
                            add(
                                SubAgentTask(
                                    task = task,
                                    capabilityHint = item.optString("capability_hint").ifBlank { item.optString("capability") },
                                    expectedOutput = item.optString("expected_output"),
                                    preferredAgent = item.optString("sub_agent_id")
                                        .ifBlank { item.optString("agent_id") }
                                        .ifBlank { item.optString("agent") }
                                        .ifBlank { item.optString("model") },
                                ),
                            )
                        }
                    }
                    is String -> if (item.isNotBlank()) add(SubAgentTask(item, "", "", ""))
                }
            }
        }
    }

    private fun chooseSubAgent(
        candidates: List<SubAgentConfig>,
        task: SubAgentTask,
        taskIndex: Int,
        assignmentCounts: Map<String, Int>,
    ): SubAgentConfig {
        val preferred = task.preferredAgent.trim().lowercase(Locale.US)
        if (preferred.isNotBlank()) {
            candidates.firstOrNull { agent ->
                listOf(agent.id, agent.name, agent.model, agent.profileId).any { it.lowercase(Locale.US) == preferred }
            }?.let { return it }
        }
        val hint = listOf(task.capabilityHint, task.task).joinToString(" ")
        val tokens = hint.lowercase(Locale.US).split(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"))
            .filter { it.length >= 2 }
            .toSet()
        fun usage(agent: SubAgentConfig): Int = assignmentCounts[agent.id] ?: 0
        if (tokens.isNotEmpty()) {
            val scored = candidates.map { agent ->
                val text = "${agent.name} ${agent.model} ${agent.description}".lowercase(Locale.US)
                agent to tokens.count { token -> token in text }
            }
            val bestScore = scored.maxOf { it.second }
            if (bestScore > 0) {
                return scored
                    .filter { it.second == bestScore }
                    .minWith(compareBy<Pair<SubAgentConfig, Int>> { usage(it.first) }.thenBy { candidates.indexOf(it.first) })
                    .first
            }
        }
        val leastUsed = candidates.minOf { usage(it) }
        val leastUsedAgents = candidates.filter { usage(it) == leastUsed }
        return leastUsedAgents[taskIndex % leastUsedAgents.size]
    }

    private fun buildSubAgentPrompt(parentConversationId: Long, task: SubAgentTask, agent: SubAgentConfig): String {
        return """
        LYRA_SUB_AGENT_TASK_V1
        你是主对话临时委派的子代理。只完成下面的独立子任务，不要向用户寒暄，不要输出你的 thinking。
        主会话 ID: $parentConversationId
        子代理说明: ${agent.description.ifBlank { "无" }}
        子任务: ${task.task}
        能力提示: ${task.capabilityHint.ifBlank { "自动判断" }}
        期望输出: ${task.expectedOutput.ifBlank { "给出可供主模型复核和整合的结论、证据、风险与必要文件/命令结果。" }}

        工作规则：
        - 可独立调用当前可用工具；需要用户确认的工具照常申请确认。
        - 只返回最终可见结果，不要包含隐藏思考过程。
        - 如果信息不足或工具被拒绝，明确说明缺口和已完成的检查。
        - 不要尝试再次调用子代理编排。
        """.trimIndent()
    }

    private fun subAgentError(index: Int, agent: SubAgentConfig, task: SubAgentTask, error: String): JSONObject {
        return JSONObject()
            .put("index", index + 1)
            .put("agent", agent.name)
            .put("task", task.task)
            .put("error", error)
    }

    private fun allowSubAgentsFor(conversationId: Long): Boolean {
        return settings.subAgentOrchestrationEnabled && conversationStore.conversation(conversationId)?.mode != ConversationStore.MODE_SUBAGENT
    }

    private fun subAgentPromptJson(): JSONArray {
        val array = JSONArray()
        settings.enabledSubAgents().forEach { agent ->
            array.put(
                JSONObject()
                    .put("id", agent.id)
                    .put("name", agent.name)
                    .put("profile_id", agent.profileId)
                    .put("model", agent.model)
                    .put("description", agent.description),
            )
        }
        return array
    }

    private data class SubAgentTask(
        val task: String,
        val capabilityHint: String,
        val expectedOutput: String,
        val preferredAgent: String,
    )

    private fun parseTodoItems(args: JSONObject): List<TodoItem> {
        val array = args.optJSONArray("items")
        if (array != null) {
            return buildList {
                for (index in 0 until array.length()) {
                    val raw = array.opt(index)
                    when (raw) {
                        is JSONObject -> add(
                            TodoItem(
                                id = raw.optString("id").ifBlank { (index + 1).toString() },
                                text = raw.optString("text").ifBlank { raw.optString("title") },
                                status = raw.optString("status").ifBlank { "pending" },
                                note = raw.optString("note"),
                            ),
                        )
                        is String -> add(TodoItem((index + 1).toString(), raw, "pending"))
                    }
                }
            }.filter { it.text.isNotBlank() }
        }
        return args.optString("items")
            .lineSequence()
            .map { it.trim().trimStart('-', '*').trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, text -> TodoItem((index + 1).toString(), text, "pending") }
            .toList()
    }

    private fun sanitizeConversationTopic(rawTitle: String): String {
        return rawTitle.lineSequence().firstOrNull().orEmpty()
            .replace(Regex("""^(标题|话题|主题|title|topic)\s*[:：]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\r\n\t]+"""), " ").replace(Regex("""\s+"""), " ")
            .trim().trim('"', '\'', '“', '”', '‘', '’', '。', '.', ':', '：', '#', '*').take(24).trim()
            .also { require(it.isNotBlank()) { "话题总结模型未返回有效标题" } }
    }
    private suspend fun executeMcpTool(
        server: McpServerConfig,
        tool: McpToolDefinition,
        args: JSONObject,
    ): ToolExecution {
        val result = mcpClientManager.callTool(server, tool, args)
        return ToolExecution(
            JSONObject()
                .put("schema", "lyra_mcp_tool_result_v1")
                .put("server", result.serverName)
                .put("tool", result.toolName)
                .put("content", result.content)
                .toString(),
        )
    }

    private fun currentTimeInfo(): String {
        val now = Date()
        val zone = TimeZone.getDefault()
        return JSONObject()
            .put("schema", "lyra_time_context_v1")
            .put("timestamp_ms", now.time)
            .put("timezone", zone.id)
            .put("local_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now))
            .put("utc_offset", SimpleDateFormat("Z", Locale.US).format(now))
            .toString()
    }

    private fun currentLocationInfo(): String {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            return JSONObject()
                .put("schema", "lyra_location_context_v1")
                .put("permission_granted", false)
                .put("message", "未授予位置信息权限。需要用户在设置的应用权限中开启位置权限。")
                .toString()
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return JSONObject()
                .put("schema", "lyra_location_context_v1")
                .put("permission_granted", true)
                .put("available", false)
                .put("message", "系统 LocationManager 不可用。")
                .toString()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val location = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        if (location == null) {
            return JSONObject()
                .put("schema", "lyra_location_context_v1")
                .put("permission_granted", true)
                .put("available", false)
                .put("message", "没有可用的最近位置。请确认系统定位已开启，并允许 Lyra Code 访问位置。")
                .toString()
        }
        return JSONObject()
            .put("schema", "lyra_location_context_v1")
            .put("permission_granted", true)
            .put("available", true)
            .put("provider", location.provider.orEmpty())
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("accuracy_meters", location.accuracy.toDouble())
            .put("timestamp_ms", location.time)
            .toString()
    }

    private fun webSourceMarkResult(args: JSONObject): String {
        val sources = args.optJSONArray("sources") ?: JSONArray()
        return JSONObject()
            .put("schema", "lyra_web_source_marks_v1")
            .put("sources", sources)
            .put("instruction", "最终回答中，对依赖网页内容的关键句就近添加 Markdown 来源链接；只标注已读取并在 sources 中声明的网页。")
            .toString()
    }

    private suspend fun manageAppConfig(args: JSONObject): String {
        val target = args.optString("target").trim().lowercase(Locale.US).replace("-", "_")
        val action = args.optString("action").trim().lowercase(Locale.US).replace("-", "_")
        require(target.isNotBlank()) { "target 不能为空，可用 all、mcp_server、ssh_server、webdav_server、file_transfer_server、skill、agent_tool" }
        require(action.isNotBlank()) { "action 不能为空，可用 list、add、update、enable、disable、delete" }
        val result = when (target) {
            "all", "config", "configs", "inventory" -> {
                require(action == "list") { "target=$target 仅支持 action=list" }
                configInventoryJson().toString()
            }
            "mcp", "mcp_server", "mcp_servers" -> manageMcpConfig(action, args)
            "ssh", "ssh_server", "ssh_servers" -> manageSshConfig(action, args)
            "webdav", "webdav_server", "webdav_servers" -> manageWebDavConfig(action, args)
            "file_transfer", "file_transfer_server", "file_transfer_servers", "ftp", "ftps", "sftp" -> manageFileTransferConfig(target, action, args)
            "skill", "skills" -> manageSkillConfig(action, args)
            "agent", "agent_tool", "tool", "tools" -> manageAgentToolConfig(action, args)
            else -> error("未知配置目标: $target")
        }
        if (action != "list") {
            configChangedHandler()
        }
        return result
    }

    private suspend fun manageMcpConfig(action: String, args: JSONObject): String {
        if (action == "list") return configResult("mcp_servers", mcpServersJson()).toString()
        val existing = resolveMcpServerForConfig(args.optString("id").ifBlank { args.optString("name") }.ifBlank { args.optString("url") })
        when (action) {
            "delete", "remove" -> {
                val target = existing ?: error("未找到要删除的 MCP 服务器")
                settings.deleteMcpServer(target.id)
                return configResult("mcp_server_deleted", JSONObject().put("id", target.id).put("name", target.name)).toString()
            }
            "enable", "disable" -> {
                val target = existing ?: error("未找到要${if (action == "enable") "启用" else "禁用"}的 MCP 服务器")
                settings.setMcpServerEnabled(target.id, action == "enable")
                return configResult("mcp_server_${action}d", mcpServerJson(target.copy(enabled = action == "enable"))).toString()
            }
        }

        require(action in setOf("add", "create", "update", "modify", "upsert")) { "MCP 不支持 action=$action" }
        val rawJson = args.optString("raw_json").ifBlank { existing?.rawJson.orEmpty() }
        val parsed = parseMcpRawJson(rawJson)
        val url = args.optString("url")
            .ifBlank { args.optString("base_url") }
            .ifBlank { parsed?.url.orEmpty() }
            .ifBlank { existing?.url.orEmpty() }
            .trim()
        require(url.isNotBlank()) { "MCP URL 不能为空；如果网页需要认证，请先让用户提供 key 或完整 raw_json。" }
        val name = args.optString("name")
            .ifBlank { parsed?.name.orEmpty() }
            .ifBlank { existing?.name.orEmpty() }
            .ifBlank { "MCP Server" }
        val authKey = args.optString("auth_key")
            .ifBlank { args.optString("api_key") }
            .ifBlank { args.optString("key") }
            .ifBlank { parsed?.authKey.orEmpty() }
            .ifBlank { existing?.authKey.orEmpty() }
        val transport = normalizeMcpTransport(
            args.optString("transport")
                .ifBlank { parsed?.transport.orEmpty() }
                .ifBlank { existing?.transport.orEmpty() },
        )
        val timeout = args.optInt("timeout_seconds", existing?.timeoutSeconds ?: 30).coerceIn(5, 300)
        val enabled = if (args.has("enabled")) args.optBoolean("enabled") else existing?.enabled ?: true
        val server = McpServerConfig(
            id = existing?.id ?: args.optString("id").ifBlank { AppSettings.newId() },
            name = name,
            url = url,
            authKey = authKey,
            transport = transport,
            timeoutSeconds = timeout,
            enabled = enabled,
            rawJson = buildMcpRawJson(rawJson, name, url, authKey, transport),
            tools = existing?.tools.orEmpty(),
        )
        settings.upsertMcpServer(server)
        val refresh = if (enabled) {
            runCatching { mcpClientManager.testAndRefreshTools(server).getOrThrow() }
        } else {
            Result.success(server.tools)
        }
        val saved = settings.mcpServers().firstOrNull { it.id == server.id } ?: server
        return configResult(
            "mcp_server_saved",
            JSONObject()
                .put("server", mcpServerJson(saved))
                .put("tools_count", saved.tools.size)
                .put("refresh_ok", refresh.isSuccess)
                .put("message", refresh.exceptionOrNull()?.message.orEmpty().ifBlank { "MCP 已保存并刷新 tools" }),
        ).toString()
    }

    private fun manageSshConfig(action: String, args: JSONObject): String {
        if (action == "list") return configResult("ssh_servers", sshServersJson()).toString()
        val existing = resolveSshServerForConfig(args.optString("id").ifBlank { args.optString("host") }.ifBlank { args.optString("name") })
        when (action) {
            "delete", "remove" -> {
                val target = existing ?: error("未找到要删除的 SSH 服务器")
                settings.deleteSshServer(target.id)
                return configResult("ssh_server_deleted", JSONObject().put("id", target.id).put("host", target.host)).toString()
            }
            "enable", "disable" -> {
                val target = existing ?: error("未找到要${if (action == "enable") "启用" else "禁用"}的 SSH 服务器")
                settings.setSshServerEnabled(target.id, action == "enable")
                return configResult("ssh_server_${action}d", sshServerJson(target.copy(enabled = action == "enable"))).toString()
            }
        }
        require(action in setOf("add", "create", "update", "modify", "upsert")) { "SSH 不支持 action=$action" }
        val host = args.optString("host").ifBlank { existing?.host.orEmpty() }.trim()
        val username = args.optString("username").ifBlank { args.optString("user") }.ifBlank { existing?.username.orEmpty() }.trim()
        require(host.isNotBlank()) { "SSH host 不能为空" }
        require(username.isNotBlank()) { "SSH username 不能为空" }
        val authType = when (args.optString("auth_type").ifBlank { existing?.authType.orEmpty() }.lowercase(Locale.US)) {
            "key", "private_key", "ssh_key" -> AppSettings.SSH_AUTH_KEY
            else -> AppSettings.SSH_AUTH_PASSWORD
        }
        val server = SshServerConfig(
            id = existing?.id ?: args.optString("id").ifBlank { AppSettings.newId() },
            name = args.optString("name").ifBlank { existing?.name.orEmpty() }.ifBlank { host },
            host = host,
            port = args.optInt("port", existing?.port ?: 22).coerceIn(1, 65535),
            username = username,
            authType = authType,
            password = args.optString("password").ifBlank { existing?.password.orEmpty() },
            privateKey = args.optString("private_key").ifBlank { existing?.privateKey.orEmpty() },
            passphrase = args.optString("passphrase").ifBlank { existing?.passphrase.orEmpty() },
            timeoutSeconds = args.optInt("timeout_seconds", existing?.timeoutSeconds ?: 60).coerceIn(5, 600),
            enabled = if (args.has("enabled")) args.optBoolean("enabled") else existing?.enabled ?: true,
        )
        require(server.authType != AppSettings.SSH_AUTH_PASSWORD || server.password.isNotBlank()) { "密码登录需要 password；如果用户未提供，请先向用户索取。" }
        require(server.authType != AppSettings.SSH_AUTH_KEY || server.privateKey.isNotBlank()) { "密钥登录需要 private_key；如果用户未提供，请先向用户索取。" }
        settings.upsertSshServer(server)
        return configResult("ssh_server_saved", sshServerJson(server)).toString()
    }

    private fun manageWebDavConfig(action: String, args: JSONObject): String {
        if (action == "list") return configResult("webdav_servers", webDavServersJson()).toString()
        val existing = resolveWebDavServerForConfig(
            args.optString("id")
                .ifBlank { args.optString("url") }
                .ifBlank { args.optString("name") },
        )
        when (action) {
            "delete", "remove" -> {
                val target = existing ?: error("未找到要删除的 WebDAV 服务器")
                settings.deleteWebDavServer(target.id)
                return configResult("webdav_server_deleted", JSONObject().put("id", target.id).put("name", target.name)).toString()
            }
            "enable", "disable" -> {
                val target = existing ?: error("未找到要${if (action == "enable") "启用" else "禁用"}的 WebDAV 服务器")
                settings.setWebDavServerEnabled(target.id, action == "enable")
                return configResult("webdav_server_${action}d", webDavServerJson(target.copy(enabled = action == "enable"))).toString()
            }
        }
        require(action in setOf("add", "create", "update", "modify", "upsert")) { "WebDAV 不支持 action=$action" }
        val url = args.optString("url").ifBlank { args.optString("base_url") }.ifBlank { existing?.url.orEmpty() }.trim()
        require(url.isNotBlank()) { "WebDAV URL 不能为空" }
        require(url.startsWith("http://", true) || url.startsWith("https://", true)) { "WebDAV URL 必须是 http:// 或 https://" }
        val server = WebDavServerConfig(
            id = existing?.id ?: args.optString("id").ifBlank { AppSettings.newId() },
            name = args.optString("name").ifBlank { existing?.name.orEmpty() }.ifBlank { runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { "WebDAV" } },
            url = url,
            username = args.optString("username").ifBlank { args.optString("user") }.ifBlank { existing?.username.orEmpty() },
            password = args.optString("password").ifBlank { existing?.password.orEmpty() },
            userAgent = args.optString("user_agent").ifBlank { existing?.userAgent.orEmpty() },
            initialPath = args.optString("initial_path").ifBlank { args.optString("path") }.ifBlank { existing?.initialPath.orEmpty() }.ifBlank { "/" },
            note = args.optString("note").ifBlank { existing?.note.orEmpty() },
            trustAllCertificates = if (args.has("trust_all_certificates")) args.optBoolean("trust_all_certificates") else existing?.trustAllCertificates ?: false,
            multiThread = if (args.has("multi_thread")) args.optBoolean("multi_thread") else existing?.multiThread ?: true,
            hideAddressInDrawer = if (args.has("hide_address")) args.optBoolean("hide_address") else existing?.hideAddressInDrawer ?: false,
            enabled = if (args.has("enabled")) args.optBoolean("enabled") else existing?.enabled ?: true,
        )
        settings.upsertWebDavServer(server)
        val test = if (server.enabled) webDavClient.test(server) else Result.success(emptyList())
        return configResult(
            "webdav_server_saved",
            JSONObject()
                .put("server", webDavServerJson(server))
                .put("test_ok", test.isSuccess)
                .put("message", test.exceptionOrNull()?.message.orEmpty().ifBlank { if (server.url.startsWith("http://", true)) "已保存。注意 HTTP 明文连接不安全。" else "WebDAV 已保存并测试通过。" }),
        ).toString()
    }

    private fun manageFileTransferConfig(target: String, action: String, args: JSONObject): String {
        if (action == "list") return configResult("file_transfer_servers", fileTransferServersJson()).toString()
        val protocolHint = when (target) {
            "ftp", "ftps", "sftp" -> target
            else -> ""
        }
        val existing = resolveFileTransferServerForConfig(
            args.optString("id")
                .ifBlank { args.optString("host") }
                .ifBlank { args.optString("name") },
        )
        when (action) {
            "delete", "remove" -> {
                val targetServer = existing ?: error("未找到要删除的文件传输服务器")
                settings.deleteFileTransferServer(targetServer.id)
                return configResult("file_transfer_server_deleted", JSONObject().put("id", targetServer.id).put("name", targetServer.name)).toString()
            }
            "enable", "disable" -> {
                val targetServer = existing ?: error("未找到要${if (action == "enable") "启用" else "禁用"}的文件传输服务器")
                settings.setFileTransferServerEnabled(targetServer.id, action == "enable")
                return configResult("file_transfer_server_${action}d", fileTransferServerJson(targetServer.copy(enabled = action == "enable"))).toString()
            }
        }
        require(action in setOf("add", "create", "update", "modify", "upsert")) { "文件传输服务器不支持 action=$action" }
        val protocol = AppSettings.normalizeFileTransferProtocol(
            args.optString("protocol")
                .ifBlank { protocolHint }
                .ifBlank { existing?.protocol.orEmpty() }
                .ifBlank { AppSettings.FILE_TRANSFER_SFTP },
        )
        val host = args.optString("host").ifBlank { args.optString("url") }.ifBlank { existing?.host.orEmpty() }.trim()
        require(host.isNotBlank()) { "文件传输服务器 host 不能为空" }
        val username = args.optString("username").ifBlank { args.optString("user") }.ifBlank { existing?.username.orEmpty() }.trim()
        if (protocol == AppSettings.FILE_TRANSFER_SFTP) require(username.isNotBlank()) { "SFTP 需要 username；如果用户未提供，请先向用户索取。" }
        val usePrivateKey = if (args.has("use_private_key")) args.optBoolean("use_private_key") else existing?.usePrivateKey ?: false
        val server = FileTransferServerConfig(
            id = existing?.id ?: args.optString("id").ifBlank { AppSettings.newId() },
            name = args.optString("name").ifBlank { existing?.name.orEmpty() }.ifBlank { "${protocol.uppercase(Locale.US)} $host" },
            protocol = protocol,
            host = host,
            port = args.optInt("port", existing?.port ?: AppSettings.defaultFileTransferPort(protocol)).coerceIn(1, 65535),
            username = username.ifBlank { if (protocol == AppSettings.FILE_TRANSFER_SFTP) "" else "anonymous" },
            password = args.optString("password").ifBlank { existing?.password.orEmpty() },
            usePrivateKey = usePrivateKey,
            privateKey = args.optString("private_key").ifBlank { existing?.privateKey.orEmpty() },
            passphrase = args.optString("passphrase").ifBlank { existing?.passphrase.orEmpty() },
            initialPath = args.optString("initial_path").ifBlank { args.optString("path") }.ifBlank { existing?.initialPath.orEmpty() }.ifBlank { "/" },
            note = args.optString("note").ifBlank { existing?.note.orEmpty() },
            encoding = args.optString("encoding").ifBlank { existing?.encoding.orEmpty() }.ifBlank { "UTF-8" },
            passiveMode = if (args.has("passive_mode")) args.optBoolean("passive_mode") else existing?.passiveMode ?: true,
            explicitFtps = if (args.has("explicit_ftps")) args.optBoolean("explicit_ftps") else existing?.explicitFtps ?: true,
            multiThread = if (args.has("multi_thread")) args.optBoolean("multi_thread") else existing?.multiThread ?: true,
            syncPermissions = if (args.has("sync_permissions")) args.optBoolean("sync_permissions") else existing?.syncPermissions ?: false,
            hideAddressInDrawer = if (args.has("hide_address")) args.optBoolean("hide_address") else existing?.hideAddressInDrawer ?: false,
            enabled = if (args.has("enabled")) args.optBoolean("enabled") else existing?.enabled ?: true,
        )
        require(!server.usePrivateKey || server.privateKey.isNotBlank()) { "密钥登录需要 private_key；如果用户未提供，请先向用户索取。" }
        settings.upsertFileTransferServer(server)
        val test = if (server.enabled) fileTransferClient.test(server) else Result.success(emptyList())
        return configResult(
            "file_transfer_server_saved",
            JSONObject()
                .put("server", fileTransferServerJson(server))
                .put("test_ok", test.isSuccess)
                .put("message", test.exceptionOrNull()?.message.orEmpty().ifBlank {
                    if (server.protocol == AppSettings.FILE_TRANSFER_FTP) "已保存。注意 FTP 明文连接不安全，建议优先使用 SFTP 或 FTPS。" else "文件传输服务器已保存并测试通过。"
                }),
        ).toString()
    }

    private fun manageSkillConfig(action: String, args: JSONObject): String {
        if (action == "list") return configResult("skills", skillsJson()).toString()
        val existing = resolveSkillForConfig(args.optString("id").ifBlank { args.optString("name") })
        when (action) {
            "add", "create", "install", "import" -> {
                val url = args.optString("zip_url").ifBlank { args.optString("url") }.trim()
                require(url.isNotBlank()) { "安装 Skill 需要 zip_url；如果用户给的是网页，请先读取网页找出 zip 下载链接。" }
                val download = downloadBytes(url)
                val skill = settings.importSkillZipBytes(args.optString("name").ifBlank { download.first }, download.second).getOrThrow()
                args.optString("description").takeIf { it.isNotBlank() }?.let { settings.updateSkillMeta(skill.id, description = it) }
                return configResult("skill_installed", skillJson(settings.installedSkills().firstOrNull { it.id == skill.id } ?: skill)).toString()
            }
            "delete", "remove", "uninstall" -> {
                val target = existing ?: error("未找到要删除的 Skill")
                settings.deleteSkill(target.id)
                return configResult("skill_deleted", JSONObject().put("id", target.id).put("name", target.name)).toString()
            }
            "enable", "disable" -> {
                val target = existing ?: error("未找到要${if (action == "enable") "启用" else "禁用"}的 Skill")
                settings.setSkillEnabled(target.id, action == "enable")
                return configResult("skill_${action}d", skillJson(target.copy(enabled = action == "enable"))).toString()
            }
            "update", "modify", "rename" -> {
                val target = existing ?: error("未找到要修改的 Skill")
                settings.updateSkillMeta(target.id, args.optString("name").ifBlank { null }, args.optString("description").ifBlank { null })
                if (args.has("enabled")) settings.setSkillEnabled(target.id, args.optBoolean("enabled"))
                val updated = settings.installedSkills().firstOrNull { it.id == target.id } ?: target
                return configResult("skill_updated", skillJson(updated)).toString()
            }
            else -> error("Skill 不支持 action=$action")
        }
    }

    private fun manageAgentToolConfig(action: String, args: JSONObject): String {
        if (action == "list") return configResult("agent_tools", agentToolsJson()).toString()
        val toolName = args.optString("tool_name").ifBlank { args.optString("name") }.ifBlank { args.optString("id") }.trim()
        require(toolName.isNotBlank()) { "管理 Agent 工具需要 tool_name" }
        require(toolName != "manage_app_config") { "manage_app_config 是配置管理保护工具，不能被禁用或删除。" }
        return when (action) {
            "enable" -> {
                settings.setToolEnabled(toolName, true)
                configResult("agent_tool_enabled", JSONObject().put("tool_name", toolName)).toString()
            }
            "disable" -> {
                settings.setToolEnabled(toolName, false)
                configResult("agent_tool_disabled", JSONObject().put("tool_name", toolName)).toString()
            }
            "update", "modify" -> {
                require(args.has("enabled")) { "Agent 工具只能通过 enabled=true/false 修改启用状态" }
                settings.setToolEnabled(toolName, args.optBoolean("enabled"))
                configResult("agent_tool_updated", JSONObject().put("tool_name", toolName).put("enabled", args.optBoolean("enabled"))).toString()
            }
            "delete", "remove" -> error("Agent 工具由系统代码提供，不能删除，只能启用或禁用。")
            else -> error("Agent 工具不支持 action=$action")
        }
    }

    private fun configResult(type: String, payload: Any): JSONObject {
        return JSONObject()
            .put("schema", "lyra_config_management_result_v1")
            .put("type", type)
            .put("payload", payload)
    }

    private fun configInventoryJson(): JSONObject {
        return configResult(
            "config_inventory",
            JSONObject()
                .put("agent_tools", agentToolsJson())
                .put("mcp_servers", mcpServersJson())
                .put("ssh_servers", sshServersJson())
                .put("webdav_servers", webDavServersJson())
                .put("file_transfer_servers", fileTransferServersJson())
                .put("skills", skillsJson())
                .put("disabled_summary", disabledConfigSummaryJson())
                .put("instruction", "启用前先从 disabled_summary 或对应列表里确认 id/name/tool_name。Agent 工具用 target=agent_tool；MCP/SSH/WebDAV/文件传输/Skill 配置用对应 target。"),
        )
    }

    private fun disabledConfigSummaryJson(): JSONObject {
        val disabledTools = settings.disabledTools()
        val mcpServers = settings.mcpServers()
        return JSONObject()
            .put("agent_tools", JSONArray().also { array ->
                agentToolNamesForConfig().filter { it != "manage_app_config" && it in disabledTools }.sorted().forEach { array.put(it) }
            })
            .put("mcp_servers", JSONArray().also { array ->
                mcpServers.filterNot { it.enabled }.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
            })
            .put("mcp_tools_unavailable", JSONArray().also { array ->
                mcpServers.forEach { server ->
                    server.tools.forEach { tool ->
                        val functionName = settings.mcpToolFunctionName(server, tool)
                        if (!server.enabled || functionName in disabledTools) {
                            array.put(
                                JSONObject()
                                    .put("tool_name", functionName)
                                    .put("server_id", server.id)
                                    .put("server_name", server.name)
                                    .put("mcp_tool", tool.name)
                                    .put("server_enabled", server.enabled)
                                    .put("tool_enabled", functionName !in disabledTools),
                            )
                        }
                    }
                }
            })
            .put("ssh_servers", JSONArray().also { array ->
                settings.sshServers().filterNot { it.enabled }.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)) }
            })
            .put("webdav_servers", JSONArray().also { array ->
                settings.webDavServers().filterNot { it.enabled }.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
            })
            .put("file_transfer_servers", JSONArray().also { array ->
                settings.fileTransferServers().filterNot { it.enabled }.forEach {
                    array.put(JSONObject().put("id", it.id).put("name", it.name).put("protocol", it.protocol).put("host", it.host))
                }
            })
            .put("skills", JSONArray().also { array ->
                settings.installedSkills().filterNot { it.enabled }.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description)) }
            })
    }

    private data class ParsedMcpRawConfig(
        val name: String,
        val url: String,
        val authKey: String,
        val transport: String,
        val serverKey: String,
    )

    private fun parseMcpRawJson(rawJson: String): ParsedMcpRawConfig? = runCatching {
        if (rawJson.isBlank()) return@runCatching null
        val root = JSONObject(rawJson)
        val servers = root.optJSONObject("mcpServers")
        val serverKey = servers?.keys()?.asSequence()?.firstOrNull().orEmpty()
        val node = if (serverKey.isNotBlank()) servers?.optJSONObject(serverKey) else root
        node ?: return@runCatching null
        val headers = node.optJSONObject("headers") ?: root.optJSONObject("headers")
        val auth = headers?.optString("Authorization").orEmpty().removePrefix("Bearer ").trim()
        val rawType = node.optString("type").ifBlank { node.optString("transport") }
        ParsedMcpRawConfig(
            name = node.optString("name").ifBlank { serverKey.ifBlank { root.optString("name") } },
            url = node.optString("baseUrl").ifBlank { node.optString("url").ifBlank { root.optString("baseUrl").ifBlank { root.optString("url") } } },
            authKey = auth,
            transport = normalizeMcpTransport(rawType),
            serverKey = serverKey.ifBlank { node.optString("id").ifBlank { "mcp_server" } },
        )
    }.getOrNull()

    private fun buildMcpRawJson(rawJson: String, name: String, url: String, authKey: String, transport: String): String {
        val parsed = parseMcpRawJson(rawJson)
        val serverKey = parsed?.serverKey?.takeIf { it.isNotBlank() } ?: configKeyPart(name).ifBlank { "mcp_server" }
        val root = runCatching { JSONObject(rawJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val servers = root.optJSONObject("mcpServers") ?: JSONObject()
        val node = servers.optJSONObject(serverKey) ?: JSONObject()
        node.put("type", if (transport == AppSettings.MCP_TRANSPORT_SSE) "sse" else "streamableHttp")
        node.put("name", name)
        node.put("baseUrl", url)
        val headers = node.optJSONObject("headers") ?: JSONObject()
        if (authKey.isNotBlank()) {
            headers.put("Authorization", if (authKey.startsWith("Bearer ", ignoreCase = true)) authKey else "Bearer $authKey")
        }
        node.put("headers", headers)
        servers.put(serverKey, node)
        root.put("mcpServers", servers)
        if (!root.has("protocolVersion")) root.put("protocolVersion", "2025-06-18")
        return root.toString()
    }

    private fun normalizeMcpTransport(raw: String): String {
        return when (raw.trim().lowercase(Locale.US)) {
            "sse" -> AppSettings.MCP_TRANSPORT_SSE
            else -> AppSettings.MCP_TRANSPORT_STREAMABLE_HTTP
        }
    }

    private fun configKeyPart(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
    }

    private fun resolveMcpServerForConfig(identifier: String): McpServerConfig? {
        val clean = identifier.trim()
        if (clean.isBlank()) return null
        return settings.mcpServers().firstOrNull { it.id == clean || it.name == clean || it.url == clean }
    }

    private fun resolveSshServerForConfig(identifier: String): SshServerConfig? {
        val clean = identifier.trim()
        if (clean.isBlank()) return null
        return settings.sshServers().firstOrNull { it.id == clean || it.stableId == clean || it.host == clean || it.name == clean }
    }

    private fun resolveWebDavServerForConfig(identifier: String): WebDavServerConfig? {
        val clean = identifier.trim().trimEnd('/')
        if (clean.isBlank()) return null
        return settings.webDavServers().firstOrNull {
            it.id == clean || it.name == clean || it.stableId == clean || it.url.trimEnd('/') == clean
        }
    }

    private fun resolveSkillForConfig(identifier: String): SkillPack? {
        val clean = identifier.trim()
        if (clean.isBlank()) return null
        return settings.installedSkills().firstOrNull { it.id == clean || it.name == clean }
    }

    private fun downloadBytes(url: String): Pair<String, ByteArray> {
        require(url.startsWith("http://", true) || url.startsWith("https://", true)) { "下载 URL 必须是 http:// 或 https://" }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: error("下载响应为空")
            if (!response.isSuccessful) error("下载失败 HTTP ${response.code}: ${body.string().take(500)}")
            val bytes = body.bytes()
            require(bytes.isNotEmpty()) { "下载文件为空" }
            require(bytes.size <= 16 * 1024 * 1024) { "下载文件超过 16MB" }
            val fileName = response.header("Content-Disposition")
                ?.substringAfter("filename=", "")
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }
                ?: runCatching { URI(url).path.substringAfterLast('/') }.getOrNull().orEmpty().ifBlank { "Skill.zip" }
            return fileName to bytes
        }
    }

    private suspend fun downloadFile(args: JSONObject): ToolExecution {
        val url = args.getString("url").trim()
        require(url.startsWith("http://", true) || url.startsWith("https://", true)) {
            "下载 URL 必须是 http:// 或 https://"
        }
        val destination = args.optString("destination", "workspace").trim().lowercase(Locale.US)
        require(destination == "workspace" || destination == "global") {
            "destination 只能是 workspace 或 global"
        }
        val path = args.getString("path").trim()
        require(path.isNotBlank()) { "下载目标 path 不能为空" }
        val expectedSha256 = args.optString("sha256").trim().lowercase(Locale.US)
        require(expectedSha256.isBlank() || expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "sha256 必须是 64 位十六进制字符串"
        }
        val timeoutSeconds = args.optInt("timeout_seconds", 300).coerceIn(10, 1800)
        val requestHeaders = mutableListOf<Pair<String, String>>()
        args.optJSONArray("headers")?.let { headerArray ->
            for (index in 0 until headerArray.length()) {
                val line = headerArray.optString(index).trim()
                if (line.isBlank()) continue
                val separator = line.indexOf(':')
                require(separator > 0) { "headers 每项必须使用 Name: Value 格式" }
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                require(name.isNotBlank() && value.isNotBlank()) { "请求头名称和值不能为空" }
                requestHeaders += name to value
            }
        }
        val result = downloadTaskManager.download(
            DownloadTaskRequest(
                url = url,
                destination = destination,
                path = path,
                headers = requestHeaders,
                expectedSha256 = expectedSha256,
                timeoutSeconds = timeoutSeconds,
            ),
        ).await()
        return ToolExecution(
            JSONObject()
                .put("status", "downloaded")
                .put("url", url)
                .put("final_url", result.finalUrl)
                .put("destination", result.destination)
                .put("path", result.path)
                .put("bytes", result.bytes)
                .put("content_type", result.contentType)
                .put("sha256", result.sha256)
                .toString(),
        )
    }

    private fun mcpServersJson(): JSONArray = JSONArray().also { array ->
        settings.mcpServers().forEach { array.put(mcpServerJson(it)) }
    }

    private fun mcpServerJson(server: McpServerConfig): JSONObject = JSONObject()
        .put("id", server.id)
        .put("name", server.name)
        .put("url", server.url)
        .put("transport", server.transport)
        .put("timeout_seconds", server.timeoutSeconds)
        .put("enabled", server.enabled)
        .put("tools", JSONArray().also { tools ->
            val disabled = settings.disabledTools()
            server.tools.forEach { tool ->
                val functionName = settings.mcpToolFunctionName(server, tool)
                tools.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("function_name", functionName)
                        .put("description", tool.description)
                        .put("enabled", server.enabled && functionName !in disabled)
                        .put("server_enabled", server.enabled)
                        .put("tool_enabled", functionName !in disabled),
                )
            }
        })

    private fun sshServersJson(): JSONArray = JSONArray().also { array ->
        settings.sshServers().forEach { array.put(sshServerJson(it)) }
    }

    private fun sshServerJson(server: SshServerConfig): JSONObject = JSONObject()
        .put("id", server.id)
        .put("stable_id", server.stableId)
        .put("name", server.name)
        .put("host", server.host)
        .put("port", server.port)
        .put("username", server.username)
        .put("auth_type", server.authType)
        .put("timeout_seconds", server.timeoutSeconds)
        .put("enabled", server.enabled)
        .put("has_password", server.password.isNotBlank())
        .put("has_private_key", server.privateKey.isNotBlank())

    private fun webDavServersJson(): JSONArray = JSONArray().also { array ->
        settings.webDavServers().forEach { array.put(webDavServerJson(it)) }
    }

    private fun webDavServerJson(server: WebDavServerConfig): JSONObject = JSONObject()
        .put("id", server.id)
        .put("stable_id", server.stableId)
        .put("name", server.name)
        .put("url", server.url)
        .put("username", server.username)
        .put("initial_path", server.initialPath)
        .put("note", server.note)
        .put("enabled", server.enabled)
        .put("trust_all_certificates", server.trustAllCertificates)
        .put("multi_thread", server.multiThread)
        .put("hide_address", server.hideAddressInDrawer)
        .put("has_password", server.password.isNotBlank())

    private fun webDavFilesJson(server: WebDavServerConfig, files: List<com.yukisoffd.lyracode.webdav.WebDavFile>): JSONObject = JSONObject()
        .put("schema", "lyra_webdav_files_v1")
        .put("server_id", server.id)
        .put("server_name", server.name)
        .put("files", JSONArray().also { array ->
            files.forEach { file ->
                array.put(
                    JSONObject()
                        .put("path", file.path)
                        .put("directory", file.directory)
                        .put("size", file.size)
                        .put("modified", file.modified),
                )
            }
        })

    private fun fileTransferServersJson(): JSONArray = JSONArray().also { array ->
        settings.fileTransferServers().forEach { array.put(fileTransferServerJson(it)) }
    }

    private fun resolveFileTransferServerForConfig(key: String): FileTransferServerConfig? {
        val clean = key.trim()
        if (clean.isBlank()) return null
        return settings.fileTransferServers().firstOrNull {
            it.id == clean ||
                it.name.equals(clean, ignoreCase = true) ||
                it.host.equals(clean, ignoreCase = true) ||
                it.stableId.equals(clean, ignoreCase = true)
        }
    }

    private fun fileTransferServerJson(server: FileTransferServerConfig): JSONObject = JSONObject()
        .put("id", server.id)
        .put("stable_id", server.stableId)
        .put("name", server.name)
        .put("protocol", server.protocol)
        .put("host", server.host)
        .put("port", server.port)
        .put("username", server.username)
        .put("initial_path", server.initialPath)
        .put("note", server.note)
        .put("encoding", server.encoding)
        .put("enabled", server.enabled)
        .put("use_private_key", server.usePrivateKey)
        .put("passive_mode", server.passiveMode)
        .put("explicit_ftps", server.explicitFtps)
        .put("multi_thread", server.multiThread)
        .put("sync_permissions", server.syncPermissions)
        .put("hide_address", server.hideAddressInDrawer)
        .put("has_password", server.password.isNotBlank())
        .put("has_private_key", server.privateKey.isNotBlank())

    private fun fileTransferFilesJson(server: FileTransferServerConfig, files: List<com.yukisoffd.lyracode.filetransfer.FileTransferFile>): JSONObject = JSONObject()
        .put("schema", "lyra_file_transfer_files_v1")
        .put("server_id", server.id)
        .put("server_name", server.name)
        .put("protocol", server.protocol)
        .put("files", JSONArray().also { array ->
            files.forEach { file ->
                array.put(
                    JSONObject()
                        .put("path", file.path)
                        .put("directory", file.directory)
                        .put("size", file.size)
                        .put("modified", file.modified),
                )
            }
        })

    private fun parseBackupOptions(args: JSONObject): BackupOptions = BackupOptions(
        includeProfile = args.optBoolean("include_profile", true),
        includeConversations = args.optBoolean("include_conversations", true),
        includeModelProfiles = args.optBoolean("include_model_profiles", true),
        includeMcp = args.optBoolean("include_mcp", true),
        includeSsh = args.optBoolean("include_ssh", true),
        includePrompts = args.optBoolean("include_prompts", true),
        includeMemories = args.optBoolean("include_memories", true),
        includeSkills = args.optBoolean("include_skills", true),
        includeWebDav = args.optBoolean("include_webdav", true),
        includeFileTransfer = args.optBoolean("include_file_transfer", true),
        includeSecrets = args.optBoolean("include_secrets", false),
    )

    private fun skillsJson(): JSONArray = JSONArray().also { array ->
        settings.installedSkills().forEach { array.put(skillJson(it)) }
    }

    private fun skillJson(skill: SkillPack): JSONObject = JSONObject()
        .put("id", skill.id)
        .put("name", skill.name)
        .put("description", skill.description)
        .put("enabled", skill.enabled)
        .put("file_count", skill.fileCount)

    private fun agentToolsJson(): JSONArray {
        val disabled = settings.disabledTools()
        val mcpToolMeta = allMcpToolMetaForConfig()
        val names = agentToolNamesForConfig()
        return JSONArray().also { array ->
            names.forEach { name ->
                val mcpMeta = mcpToolMeta[name]
                val serverEnabled = mcpMeta?.first ?: true
                val item = JSONObject()
                    .put("name", name)
                    .put("enabled", name == "manage_app_config" || (name !in disabled && serverEnabled))
                    .put("deletable", false)
                    .put("protected", name == "manage_app_config")
                item.apply {
                    mcpMeta?.let { (mcpServerEnabled, serverName, toolName) ->
                        put("source", "mcp")
                        put("server_enabled", mcpServerEnabled)
                        put("server_name", serverName)
                        put("mcp_tool", toolName)
                        put("tool_enabled", name !in disabled)
                        put("available_in_prompt", mcpServerEnabled && name !in disabled)
                    } ?: put("source", "local")
                }
                array.put(item)
            }
        }
    }

    private fun agentToolNamesForConfig(): List<String> {
        return (CONFIGURABLE_AGENT_TOOLS + allMcpToolMetaForConfig().keys)
            .distinct()
            .sorted()
    }

    private fun manageScheduledTasks(args: JSONObject): String {
        val action = args.optString("action").trim().lowercase(Locale.US)
        if (action == "list") {
            return JSONObject()
                .put("schema", "lyra_scheduled_tasks_v1")
                .put("tasks", scheduledTaskManager.describe())
                .toString()
        }
        val taskId = args.optString("task_id")
        if (action == "delete") {
            require(taskId.isNotBlank()) { "task_id 不能为空" }
            scheduledTaskManager.delete(taskId)
            return JSONObject().put("ok", true).put("action", action).put("task_id", taskId).toString()
        }
        if (action == "enable" || action == "disable") {
            require(taskId.isNotBlank()) { "task_id 不能为空" }
            val task = scheduledTaskManager.setEnabled(taskId, action == "enable")
                ?: error("定时任务不存在: $taskId")
            return scheduledTaskResult(action, task)
        }
        require(action == "create" || action == "update") { "action 必须是 list/create/update/enable/disable/delete" }
        val existing = taskId.takeIf { it.isNotBlank() }?.let(scheduledTaskManager::task)
        if (action == "update") require(existing != null) { "定时任务不存在: $taskId" }
        val profile = settings.profiles().firstOrNull { it.id == args.optString("profile_id") }
            ?: existing?.profileId?.let { id -> settings.profiles().firstOrNull { it.id == id } }
            ?: settings.selectedProfile()
        val type = args.optString("schedule_type", existing?.type?.name.orEmpty())
            .uppercase(Locale.US)
            .let { runCatching { ScheduledTaskType.valueOf(it) }.getOrDefault(existing?.type ?: ScheduledTaskType.ONCE) }
        val runAt = args.optString("run_at").takeIf { it.isNotBlank() }?.let(::parseAgentTime)
            ?: existing?.runAtMillis
            ?: 0L
        if (type == ScheduledTaskType.ONCE) require(runAt > System.currentTimeMillis()) {
            "一次性任务的 run_at 必须是未来时间，可用 yyyy-MM-dd HH:mm 或 ISO-8601"
        }
        val task = ScheduledTask(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            title = args.optString("title").ifBlank { existing?.title ?: "定时任务" },
            prompt = args.optString("prompt").ifBlank { existing?.prompt.orEmpty() },
            type = type,
            hour = if (args.has("hour")) args.optInt("hour") else existing?.hour ?: 9,
            minute = if (args.has("minute")) args.optInt("minute") else existing?.minute ?: 0,
            runAtMillis = runAt,
            dayOfWeek = if (args.has("day_of_week")) args.optInt("day_of_week") else existing?.dayOfWeek ?: 1,
            dayOfMonth = if (args.has("day_of_month")) args.optInt("day_of_month") else existing?.dayOfMonth ?: 1,
            profileId = profile.id,
            model = args.optString("model").ifBlank { existing?.model ?: profile.selectedModel },
            enabled = if (args.has("enabled")) args.optBoolean("enabled") else existing?.enabled ?: true,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastRunAt = existing?.lastRunAt ?: 0L,
            finishedAt = existing?.finishedAt ?: 0L,
            status = existing?.status ?: com.yukisoffd.lyracode.tasks.ScheduledTaskStatus.IDLE,
            result = existing?.result.orEmpty(),
            error = existing?.error.orEmpty(),
        )
        require(task.prompt.isNotBlank()) { "prompt 不能为空" }
        return scheduledTaskResult(action, scheduledTaskManager.save(task))
    }

    private fun scheduledTaskResult(action: String, task: ScheduledTask): String = JSONObject()
        .put("ok", true)
        .put("action", action)
        .put("task_id", task.id)
        .put("title", task.title)
        .put("schedule_type", task.type.name.lowercase(Locale.US))
        .put("enabled", task.enabled)
        .put("next_run_at", task.nextRunAt)
        .put("profile_id", task.profileId)
        .put("model", task.model)
        .toString()

    private fun manageMiniServer(args: JSONObject): String {
        val action = args.optString("action", "status").lowercase(Locale.US)
        val current = settings.miniServerConfig()
        val config = current.copy(
            protocol = args.optString("protocol").ifBlank { current.protocol }.lowercase(Locale.US).let {
                if (it == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) AppSettings.MINI_SERVER_PROTOCOL_HTTPS else AppSettings.MINI_SERVER_PROTOCOL_HTTP
            },
            host = args.optString("host").ifBlank { current.host },
            port = if (args.has("port")) args.optInt("port", current.port).coerceIn(1, 65535) else current.port,
            username = args.optString("username").ifBlank { current.username },
            password = if (args.has("password")) args.optString("password") else current.password,
            customDomains = miniServerDomains(args, current.customDomains),
            forceHttps = if (args.has("force_https")) args.optBoolean("force_https") else current.forceHttps,
            tlsKeyStoreBase64 = if (args.has("tls_key_store_base64")) args.optString("tls_key_store_base64") else current.tlsKeyStoreBase64,
            tlsKeyStorePassword = if (args.has("tls_key_store_password")) args.optString("tls_key_store_password") else current.tlsKeyStorePassword,
            tlsCertificateChain = if (args.has("tls_certificate_chain")) args.optString("tls_certificate_chain") else current.tlsCertificateChain,
            tlsPrivateKey = if (args.has("tls_private_key")) args.optString("tls_private_key") else current.tlsPrivateKey,
            spaFallback = if (args.has("spa_fallback")) args.optBoolean("spa_fallback") else current.spaFallback,
            directoryListing = if (args.has("directory_listing")) args.optBoolean("directory_listing") else current.directoryListing,
            mdnsEnabled = if (args.has("mdns_enabled")) args.optBoolean("mdns_enabled") else current.mdnsEnabled,
            mdnsName = args.optString("mdns_name").ifBlank { current.mdnsName },
        )
        val status = when (action) {
            "status" -> miniServerManager.status()
            "update" -> {
                settings.saveMiniServerConfig(config)
                miniServerManager.status()
            }
            "start" -> miniServerManager.start(config.copy(enabled = true))
            "stop" -> miniServerManager.stop()
            "restart" -> miniServerManager.restart(config.copy(enabled = true))
            "reset" -> {
                if (miniServerManager.status().running) miniServerManager.stop()
                settings.saveMiniServerConfig(
                    MiniServerConfig(
                        protocol = AppSettings.MINI_SERVER_PROTOCOL_HTTP,
                        host = AppSettings.DEFAULT_MINI_SERVER_HOST,
                        port = AppSettings.DEFAULT_MINI_SERVER_PORT,
                        username = AppSettings.DEFAULT_MINI_SERVER_USERNAME,
                        password = "",
                        customDomains = emptyList(),
                        forceHttps = false,
                        tlsKeyStoreBase64 = "",
                        tlsKeyStorePassword = "",
                        tlsCertificateChain = "",
                        tlsPrivateKey = "",
                        spaFallback = true,
                        directoryListing = false,
                        mdnsEnabled = false,
                        mdnsName = AppSettings.DEFAULT_MINI_SERVER_MDNS_NAME,
                        enabled = false,
                    ),
                )
                miniServerManager.status()
            }
            else -> error("未知微型服务器动作: $action")
        }
        return miniServerManager.statusJson()
            .put("action", action)
            .put("running", status.running)
            .put("security_note", miniServerSecurityNote(config))
            .toString()
    }

    private fun readMiniServerLogs(args: JSONObject): String {
        val limit = args.optInt("limit", 120).coerceIn(1, 500)
        val level = args.optString("level").lowercase(Locale.US).takeIf { it in setOf("debug", "info", "warn", "error") }.orEmpty()
        return miniServerManager.logsJson(limit, level).toString()
    }

    private fun miniServerDomains(args: JSONObject, current: List<String>): List<String> {
        val array = args.optJSONArray("custom_domains")
        if (array != null) {
            return buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct()
        }
        return args.optString("custom_domains")
            .takeIf { it.isNotBlank() }
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.toList()
            ?: current
    }

    private fun miniServerSecurityNote(config: MiniServerConfig): String {
        return buildString {
            append("微型服务器以当前工作区作为静态站点根目录。")
            if (config.protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) {
                append(" HTTPS 使用内置自签名证书，浏览器可能提示不受信任；公网或正式分享建议通过反向代理或内网穿透提供可信 TLS。")
            }
            if (config.forceHttps) {
                append(" 已开启强制 HTTPS，HTTP 请求会被重定向到 HTTPS。")
            }
            if (config.customDomains.isNotEmpty()) {
                append(" 绑定域名：${config.customDomains.joinToString(", ")}。")
            }
            if (config.host == "0.0.0.0" || config.host == "::") {
                append(" 当前监听地址会暴露到局域网；配合端口映射或内网穿透时也可能被公网访问。")
            }
            if (config.password.isBlank()) {
                append(" 当前未设置访问密码，请仅在可信网络中使用。")
            }
            if (config.protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTP) {
                append(" HTTP 明文传输可能泄露访问路径、内容和账号密码。")
            }
        }
    }

    private fun readMemories(args: JSONObject): String {
        val query = args.optString("query").trim()
        val includeDisabled = args.optBoolean("include_disabled", false)
        val entries = settings.memories().filter { memory ->
            (includeDisabled || memory.enabled) &&
                (query.isBlank() ||
                    memory.content.contains(query, ignoreCase = true) ||
                    memory.category.contains(query, ignoreCase = true))
        }
        return JSONObject()
            .put("schema", "lyra_user_memories_v1")
            .put("count", entries.size)
            .put("memories", JSONArray().also { array -> entries.forEach { array.put(memoryJson(it)) } })
            .toString()
    }

    private fun saveMemory(args: JSONObject): String {
        val memory = settings.createMemory(
            content = args.getString("content"),
            category = args.optString("category", MemoryEntry.CATEGORY_OTHER),
        )
        return JSONObject()
            .put("schema", "lyra_user_memory_change_v1")
            .put("action", "saved")
            .put("memory", memoryJson(memory))
            .toString()
    }

    private fun updateMemory(args: JSONObject): String {
        require(args.has("content") || args.has("category") || args.has("enabled")) {
            "update_memory 至少需要 content、category 或 enabled 之一"
        }
        val memory = settings.updateMemory(
            id = args.getString("id"),
            content = args.optString("content").takeIf { args.has("content") },
            category = args.optString("category").takeIf { args.has("category") },
            enabled = args.optBoolean("enabled").takeIf { args.has("enabled") },
        )
        return JSONObject()
            .put("schema", "lyra_user_memory_change_v1")
            .put("action", "updated")
            .put("memory", memoryJson(memory))
            .toString()
    }

    private fun deleteMemory(args: JSONObject): String {
        val id = args.getString("id")
        require(settings.deleteMemory(id)) { "记忆不存在: $id" }
        return JSONObject()
            .put("schema", "lyra_user_memory_change_v1")
            .put("action", "deleted")
            .put("id", id)
            .toString()
    }

    private fun memoryJson(memory: MemoryEntry): JSONObject = JSONObject()
        .put("id", memory.id)
        .put("content", memory.content)
        .put("category", memory.category)
        .put("enabled", memory.enabled)
        .put("created_at", memory.createdAt)
        .put("updated_at", memory.updatedAt)

    private fun searchConversationHistory(args: JSONObject): String {
        val query = args.optString("query").trim()
        val start = args.optString("start_time").takeIf { it.isNotBlank() }?.let(::parseAgentTime) ?: Long.MIN_VALUE
        val end = args.optString("end_time").takeIf { it.isNotBlank() }?.let(::parseAgentTime) ?: Long.MAX_VALUE
        val limit = args.optInt("limit", 20).coerceIn(1, 100)
        val results = conversationStore.conversations(ConversationStore.MODE_NORMAL)
            .asSequence()
            .filter { it.updatedAt in start..end }
            .map { conversation ->
                val visible = conversationStore.messages(conversation.id)
                    .filter { it.role == "user" || it.role == "assistant" }
                    .filter { it.content.isNotBlank() }
                conversation to visible
            }
            .filter { (conversation, messages) ->
                query.isBlank() ||
                    conversation.title.contains(query, ignoreCase = true) ||
                    messages.any { it.content.contains(query, ignoreCase = true) }
            }
            .take(limit)
            .toList()
        return JSONObject()
            .put("schema", "lyra_conversation_search_v1")
            .put("thinking_included", false)
            .put("tool_messages_included", false)
            .put(
                "conversations",
                JSONArray().also { array ->
                    results.forEach { (conversation, messages) ->
                        array.put(
                            JSONObject()
                                .put("id", conversation.id.toString())
                                .put("title", conversation.title)
                                .put("created_at", conversation.createdAt)
                                .put("updated_at", conversation.updatedAt)
                                .put("message_count", messages.size)
                                .put(
                                    "preview",
                                    messages.asReversed().firstOrNull()?.content.orEmpty()
                                        .replace(Regex("\\s+"), " ")
                                        .take(240),
                                ),
                        )
                    }
                },
            )
            .toString()
    }

    private fun readConversationHistory(args: JSONObject): String {
        val ids = buildList {
            args.optJSONArray("conversation_ids")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).toLongOrNull()?.let(::add)
                }
            }
            args.optString("conversation_id").toLongOrNull()?.let(::add)
        }.distinct().take(20)
        require(ids.isNotEmpty()) { "conversation_id 或 conversation_ids 不能为空" }
        val maxMessages = args.optInt("max_messages", 100).coerceIn(1, 500)
        val conversations = JSONArray()
        ids.forEach { id ->
            val conversation = conversationStore.conversation(id)
            if (conversation == null || conversation.mode != ConversationStore.MODE_NORMAL) return@forEach
            val visible = conversationStore.messages(id)
                .filter { it.role == "user" || it.role == "assistant" }
                .filter { it.content.isNotBlank() }
                .takeLast(maxMessages)
            conversations.put(
                JSONObject()
                    .put("id", id.toString())
                    .put("title", conversation.title)
                    .put("created_at", conversation.createdAt)
                    .put("updated_at", conversation.updatedAt)
                    .put(
                        "messages",
                        JSONArray().also { array ->
                            visible.forEach { message ->
                                array.put(
                                    JSONObject()
                                        .put("role", message.role)
                                        .put("content", message.content)
                                        .put("created_at", message.createdAt),
                                )
                            }
                        },
                    ),
            )
        }
        return JSONObject()
            .put("schema", "lyra_conversation_history_v1")
            .put("thinking_included", false)
            .put("tool_messages_included", false)
            .put("conversations", conversations)
            .toString()
    }

    private fun parseAgentTime(value: String): Long {
        value.toLongOrNull()?.let { return it }
        val zone = ZoneId.systemDefault()
        val dateTimeFormats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        )
        dateTimeFormats.forEach { formatter ->
            try {
                return LocalDateTime.parse(value.trim(), formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return try {
            LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            error("无法解析时间: $value")
        }
    }

    private fun allMcpToolMetaForConfig(): Map<String, Triple<Boolean, String, String>> {
        return buildMap {
            settings.mcpServers().forEach { server ->
                server.tools.forEach { tool ->
                    put(settings.mcpToolFunctionName(server, tool), Triple(server.enabled, server.name, tool.name))
                }
            }
        }
    }

    private fun approvalFor(conversationId: Long, call: ToolCall): ToolApprovalRequest? {
        val args = call.arguments
        return when (call.name) {
            "write_file", "edit_file" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                if (call.name == "edit_file") "精确修改文件: ${args.optString("path")}" else "写入或覆盖文件: ${args.optString("path")}",
                "会修改工作区文件内容。",
            )
            "append_file" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "追加文件: ${args.optString("path")}",
                "会修改工作区文件内容。",
            )
            "create_folder" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "创建目录: ${args.optString("path")}",
                "会改变工作区目录结构。",
            )
            "delete_file_or_folder" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "删除文件或目录: ${args.optString("path")}",
                "会删除工作区内容，可能无法恢复。",
            )
            "rename_move" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "重命名或移动: ${args.optString("from")} -> ${args.optString("to")}",
                "会改变工作区文件路径。",
            )
            "global_write_file", "global_edit_file" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                if (call.name == "global_edit_file") "精确修改共享存储文件: ${args.optString("path")}" else "写入共享存储文件: ${args.optString("path")}",
                "会修改工作区外的 Android 共享存储文件。",
            )
            "global_append_file" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "追加共享存储文件: ${args.optString("path")}",
                "会修改工作区外的 Android 共享存储文件。",
            )
            "global_create_folder" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "创建共享存储目录: ${args.optString("path")}",
                "会改变工作区外的 Android 共享存储目录结构。",
            )
            "global_delete_file_or_folder" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "删除共享存储文件或目录: ${args.optString("path")}",
                "会删除工作区外的 Android 共享存储内容，可能无法恢复。",
            )
            "global_rename_move" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "移动共享存储文件: ${args.optString("from")} -> ${args.optString("to")}",
                "会改变工作区外的 Android 共享存储文件路径。",
            )
            "download_file" -> {
                val destination = args.optString("destination", "workspace")
                val target = when {
                    destination.equals("global", true) -> "Android 共享存储"
                    !nativeFileManager.hasWorkspaceRoot() -> "Android 共享存储 Download/LyraCode（未选择工作区）"
                    else -> "当前工作区"
                }
                ToolApprovalRequest(
                    conversationId,
                    call.name,
                    call.rawArguments,
                    "下载文件到$target: ${args.optString("path")}",
                    buildString {
                        append("将从 ${args.optString("url")} 联网下载并写入文件，可能覆盖同名内容。")
                        if (args.optString("url").startsWith("http://", true)) {
                            append(" 当前使用明文 HTTP，内容可能被监听或篡改。")
                        }
                    },
                )
            }
            "manage_scheduled_tasks" -> if (args.optString("action").lowercase(Locale.US) in setOf("create", "update", "delete", "enable", "disable")) {
                ToolApprovalRequest(
                    conversationId,
                    call.name,
                    call.rawArguments,
                    "修改后台定时任务：${args.optString("title").ifBlank { args.optString("task_id") }}",
                    "会创建、修改、启用、禁用或删除系统后台调度任务。",
                )
            } else {
                null
            }
            "run_command" -> if (requiresCommandApproval(args.optString("command"))) {
                ToolApprovalRequest(
                    conversationId,
                    call.name,
                    call.rawArguments,
                    "执行命令: ${args.optString("command")}",
                    "命令可能修改文件、安装依赖、运行脚本或改变运行环境。",
                )
            } else {
                null
            }
            "execute_shell_command" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "以 Android Shell 权限执行命令: ${args.toolCommandArgument()}",
                "Shell 可访问普通应用无法访问的系统区域，也可停用、启用、安装或卸载应用。请确认命令、包名和路径无误。",
            )
            "execute_root_command" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "以 Root 权限执行命令: ${args.toolCommandArgument()}",
                "Root 命令拥有完整系统权限，可能造成数据丢失、系统无法启动或安全风险。Root 不可用时仅会按设置回退到 Shizuku Shell。",
            )
            "ssh_exec" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "在 SSH 服务器执行命令: ${args.optString("server_id")}",
                "会登录远程服务器并执行命令，可能修改服务器文件、安装软件或改变运行环境。执行前请核对命令和目标服务器。",
            )
            "webdav_download_to_workspace" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "从 WebDAV 下载到工作区: ${args.optString("remote_path")} -> ${args.optString("local_path")}",
                "会把远程文件写入当前工作区，可能覆盖同名文件。",
            )
            "webdav_upload_from_workspace" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "上传工作区文件到 WebDAV: ${args.optString("local_path")} -> ${args.optString("remote_path")}",
                "会把本机工作区文件发送到远程 WebDAV 服务器。",
            )
            "file_transfer_download_to_workspace" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "从文件传输服务器下载到工作区: ${args.optString("remote_path")} -> ${args.optString("local_path")}",
                "会把 FTP/FTPS/SFTP 远程文件写入当前工作区，可能覆盖同名文件。",
            )
            "file_transfer_upload_from_workspace" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "上传工作区文件到文件传输服务器: ${args.optString("local_path")} -> ${args.optString("remote_path")}",
                "会把本机工作区文件发送到远程 FTP/FTPS/SFTP 服务器。",
            )
            "export_backup" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "导出 Lyra Code 备份",
                if (args.optBoolean("include_secrets")) "备份将包含 API Key、SSH/WebDAV/FTP/SFTP 密码等敏感信息，请确认保存位置可信。" else "会导出配置、对话、Skills 等数据；不包含密钥时仍可能包含私人对话内容。",
            )
            "import_backup" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "导入 Lyra Code 备份（补充模式）",
                "会把备份中的兼容配置和对话追加到当前软件；Agent 导入固定使用补充模式以降低数据丢失风险。",
            )
            "manage_mini_server" -> if (args.optString("action").lowercase(Locale.US) in setOf("update", "start", "stop", "restart", "reset")) {
                ToolApprovalRequest(
                    conversationId,
                    call.name,
                    call.rawArguments,
                    "管理微型服务器：${args.optString("action")}",
                    buildString {
                        append("会修改或控制本地 HTTP 静态站点服务，以当前工作区作为站点根目录。")
                        if (args.optString("host") == "0.0.0.0" || args.optString("host") == "::") {
                            append(" 监听地址会暴露到局域网，配合内网穿透或公网映射会被外部访问。")
                        }
                        if (args.has("password") && args.optString("password").isBlank()) {
                            append(" 未设置密码时请仅用于可信网络。")
                        }
                    },
                )
            } else {
                null
            }
            "manage_app_config" -> ToolApprovalRequest(
                conversationId,
                call.name,
                call.rawArguments,
                "管理 Lyra Code 配置: ${args.optString("target")} / ${args.optString("action")}",
                "会添加、修改、启用、禁用或删除 MCP、SSH、WebDAV、文件传输、Skills 或 Agent 工具配置；下载 Skill zip、保存密钥、删除配置均需要用户确认。",
            )
            else -> if (settings.resolveMcpTool(call.name) != null) {
                ToolApprovalRequest(
                    conversationId,
                    call.name,
                    call.rawArguments,
                    "调用远程 MCP 工具: ${call.name}",
                    "MCP 服务器可能访问外部服务、读取远程数据或执行服务器端动作。需要用户确认后才能执行。",
                )
            } else {
                null
            }
        }
    }

    private suspend fun writeFileWithDiff(path: String, content: String): ToolExecution {
        val before = nativeFileManager.readFileForEdit(path).getOrNull().orEmpty()
        return withFileActivity(path, globalStorage = false, operation = "write", content = before) {
            val editorApplied = applyFileChangeInEditor(path, before, content, globalStorage = false)
            val message = nativeFileManager.writeFile(path, content).getOrThrow()
            val after = nativeFileManager.readFileForEdit(path).getOrNull().orEmpty()
            fileMutationHandler(
                AgentFileMutation(path, after, globalStorage = false, beforeContent = before, editorApplied = editorApplied),
            )
            appendDiff(message, path, before, after)
        }
    }

    private suspend fun appendFileWithDiff(path: String, content: String): ToolExecution {
        val before = nativeFileManager.readFileForEdit(path).getOrNull().orEmpty()
        return withFileActivity(path, globalStorage = false, operation = "append", content = before) {
            val expectedAfter = before + content
            val editorApplied = applyFileChangeInEditor(path, before, expectedAfter, globalStorage = false)
            val message = nativeFileManager.appendFile(path, content).getOrThrow()
            val after = nativeFileManager.readFileForEdit(path).getOrNull().orEmpty()
            fileMutationHandler(
                AgentFileMutation(path, after, globalStorage = false, beforeContent = before, editorApplied = editorApplied),
            )
            appendDiff(message, path, before, after)
        }
    }

    private suspend fun globalWriteFileWithDiff(path: String, content: String): ToolExecution {
        val before = globalFileManager.readFileForEdit(path).getOrNull().orEmpty()
        return withFileActivity(path, globalStorage = true, operation = "write", content = before) {
            val editorApplied = applyFileChangeInEditor(path, before, content, globalStorage = true)
            val message = globalFileManager.writeFile(path, content).getOrThrow()
            val after = globalFileManager.readFileForEdit(path).getOrNull().orEmpty()
            fileMutationHandler(
                AgentFileMutation(path, after, globalStorage = true, beforeContent = before, editorApplied = editorApplied),
            )
            appendDiff(message, path, before, after)
        }
    }

    private suspend fun globalAppendFileWithDiff(path: String, content: String): ToolExecution {
        val before = globalFileManager.readFileForEdit(path).getOrNull().orEmpty()
        return withFileActivity(path, globalStorage = true, operation = "append", content = before) {
            val expectedAfter = before + content
            val editorApplied = applyFileChangeInEditor(path, before, expectedAfter, globalStorage = true)
            val message = globalFileManager.appendFile(path, content).getOrThrow()
            val after = globalFileManager.readFileForEdit(path).getOrNull().orEmpty()
            fileMutationHandler(
                AgentFileMutation(path, after, globalStorage = true, beforeContent = before, editorApplied = editorApplied),
            )
            appendDiff(message, path, before, after)
        }
    }

    private suspend fun editFileWithDiff(args: JSONObject, globalStorage: Boolean): ToolExecution {
        val path = args.getString("path")
        val before = if (globalStorage) {
            globalFileManager.readFileForEdit(path).getOrThrow()
        } else {
            nativeFileManager.readFileForEdit(path).getOrThrow()
        }
        return withFileActivity(path, globalStorage, operation = "edit", content = before) {
            val usesLineRange = args.has("start_line") || args.has("end_line")
            val usesExactMatch = args.has("old_content") || args.has("old_content_lines")
            require(usesLineRange.xor(usesExactMatch)) {
                "必须且只能选择一种编辑模式：start_line/end_line，或 old_content/old_content_lines。"
            }
            val newContent = args.toolTextArgument("new_content")
            val after = if (usesLineRange) {
                val startLine = args.getInt("start_line")
                applyLineRangeReplacement(
                    source = before,
                    startLine = startLine,
                    endLine = args.optInt("end_line", startLine),
                    newContent = newContent,
                )
            } else {
                applyExactTextReplacement(
                    source = before,
                    oldContent = args.toolTextArgument("old_content"),
                    newContent = newContent,
                    expectedReplacements = args.optInt("expected_replacements", 1),
                )
            }
            require(after != before) { "编辑结果与原文件相同，未执行写入。" }
            val editorApplied = applyFileChangeInEditor(path, before, after, globalStorage)
            val message = if (globalStorage) {
                globalFileManager.writeFile(path, after).getOrThrow()
            } else {
                nativeFileManager.writeFile(path, after).getOrThrow()
            }
            fileMutationHandler(
                AgentFileMutation(path, after, globalStorage, beforeContent = before, editorApplied = editorApplied),
            )
            appendDiff(message, path, before, after)
        }
    }

    private suspend fun applyFileChangeInEditor(
        path: String,
        before: String,
        after: String,
        globalStorage: Boolean,
    ): Boolean {
        val result = fileEditHandler(
            AgentFileMutation(
                path = path,
                content = after,
                globalStorage = globalStorage,
                beforeContent = before,
            ),
        )
        if (result.handled && !result.applied) {
            error(result.message.ifBlank { "文件编辑器未能应用 AI 修改，已取消磁盘写入。" })
        }
        return result.applied
    }

    private suspend fun readFileWithActivity(path: String, globalStorage: Boolean): ToolExecution {
        val content = if (globalStorage) {
            globalFileManager.readFile(path).getOrThrow()
        } else {
            nativeFileManager.readFile(path).getOrThrow()
        }
        return withFileActivity(path, globalStorage, operation = "read", content = content) {
            ToolExecution(content)
        }
    }

    private suspend fun readFileLines(args: JSONObject, globalStorage: Boolean): String {
        val path = args.getString("path")
        val startLine = args.optInt("start_line", 1).coerceAtLeast(1)
        val lineCount = args.optInt("line_count", 200).coerceIn(1, 1_000)
        val content = if (globalStorage) {
            globalFileManager.readFileForEdit(path).getOrThrow()
        } else {
            nativeFileManager.readFileForEdit(path).getOrThrow()
        }
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return withFileActivity(path, globalStorage, operation = "read", content = content) {
            if (startLine > lines.size) {
                return@withFileActivity "FILE_LINES path=$path total_lines=${lines.size}\n请求起始行 $startLine 超出文件范围。"
            }
            val endExclusive = (startLine - 1 + lineCount).coerceAtMost(lines.size)
            val body = buildString {
                for (index in startLine - 1 until endExclusive) {
                    append(index + 1).append("| ").append(lines[index]).append('\n')
                    if (length >= 240_000) {
                        append("...输出达到 240000 字符限制，请缩小 line_count。\n")
                        break
                    }
                }
            }
            "FILE_LINES path=$path range=$startLine-$endExclusive total_lines=${lines.size}\n$body"
        }
    }

    private suspend fun <T> withFileActivity(
        path: String,
        globalStorage: Boolean,
        operation: String,
        content: String?,
        block: suspend () -> T,
    ): T {
        fileActivityHandler(AgentFileActivity(path, globalStorage, operation, content))
        return try {
            delay(90L)
            block()
        } finally {
            fileActivityHandler(null)
        }
    }

    private fun deleteWithDiff(path: String): ToolExecution {
        val before = nativeFileManager.readFile(path).getOrNull().orEmpty()
        val message = nativeFileManager.delete(path).getOrThrow()
        return appendDiff(message, path, before, "")
    }

    private fun renameMoveWithDiff(from: String, to: String): ToolExecution {
        val before = nativeFileManager.readFile(from).getOrNull().orEmpty()
        val message = nativeFileManager.renameMove(from, to).getOrThrow()
        val after = nativeFileManager.readFile(to).getOrNull().orEmpty()
        return appendDiff(message, to, before, after)
    }

    private fun appendDiff(message: String, path: String, before: String, after: String): ToolExecution {
        val diff = FileDiff.from(path, before, after)
        return ToolExecution(message, listOf(diff))
    }

    private fun requiresCommandApproval(command: String): Boolean {
        val lowered = command.lowercase()
        val readOnlyCommands = listOf("pwd", "ls", "cat", "head", "tail", "grep", "find", "awk")
        val first = lowered.trim().split(Regex("\\s+")).firstOrNull().orEmpty().substringAfterLast("/")
        if (first !in readOnlyCommands) return true
        val mutatingFragments = listOf(
            ">", ">>", "| tee", " rm ", " mv ", " cp ", " mkdir ", " touch ", " chmod ", " sed -i",
            "pip install", "npm install", "pnpm install", "yarn add", "apt ", "pkg ", "git ",
            "python ", "python3 ", "node ",
        )
        val padded = " $lowered "
        return mutatingFragments.any { padded.contains(it) }
    }

    private fun isFileSearchCommand(command: String): Boolean {
        val lowered = command.lowercase()
        return FILE_SEARCH_COMMAND_PATTERNS.any { it.containsMatchIn(lowered) }
    }

    private suspend fun globalSearchFiles(query: String): ToolExecution {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "搜索关键词不能为空" }
        val pattern = shellSingleQuote("*$cleanQuery*")
        val command = buildString {
            append("find /storage/emulated/0 ")
            append("\\( -path '/storage/emulated/0/Android/data' -o -path '/storage/emulated/0/Android/data/*' ")
            append("-o -path '/storage/emulated/0/Android/obb' -o -path '/storage/emulated/0/Android/obb/*' ")
            append("-o -path '/storage/emulated/0/.Trash*' -o -path '/storage/emulated/0/.MediaTrash*' \\) -prune -o ")
            append("\\( -iname $pattern -o -ipath $pattern \\) -print 2>/dev/null | head -n $GLOBAL_SEARCH_RESULT_LIMIT")
        }
        val result = termuxExecutor.execute(command, workDir = null)
        if (!result.ok) {
            error(
                "全局文件搜索失败: ${result.message}\n" +
                    "请确认 Termux 已安装、allow-external-apps=true，且 Termux 已执行 termux-setup-storage。",
            )
        }
        return ToolExecution(
            "GLOBAL_SEARCH_FILES_RESULT\n" +
                "root=/storage/emulated/0\n" +
                "query=$cleanQuery\n" +
                "limit=$GLOBAL_SEARCH_RESULT_LIMIT\n" +
                "note=这是工作区外的全局共享存储搜索结果。返回的是绝对路径；原生 read_file 只能读取当前工作区相对路径。若需要读取结果文件，请让用户切换工作区到对应目录，或用 run_command 执行只读 cat/head/tail。\n" +
                result.message,
        )
    }

    private fun shellSingleQuote(value: String): String {
        return "'${value.replace("'", "'\"'\"'")}'"
    }

    private fun JSONObject.toolCommandArgument(): String {
        val lines = optJSONArray("command_lines")
        if (lines != null && lines.length() > 0) {
            return buildString {
                for (index in 0 until lines.length()) {
                    if (index > 0) append('\n')
                    append(lines.optString(index))
                }
            }
        }
        return stringFieldOrNull("command") ?: error("run_command 需要 command 或 command_lines")
    }

    private fun titleFor(conversationId: Long, userInput: String): String? {
        val existing = conversationStore.conversation(conversationId)?.title.orEmpty()
        if (existing != "新对话") return null
        return userInput.lineSequence().firstOrNull().orEmpty().take(36).ifBlank { "新对话" }
    }

    private fun ChatMessage.toPromptJson(): JSONObject {
        val raw = rawJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.also { sanitizeAssistantRaw(it) }
        if (raw == null && role == "user" && hasUploadedAttachments(content)) {
            return userPromptWithAttachments(content)
        }
        return raw ?: JSONObject()
            .put("role", role)
            .put("content", if (role == "assistant") cleanGeneratedText(content) else content)
            .apply {
                if (role == "assistant" && thinking.isNotBlank()) {
                    put("reasoning_content", cleanGeneratedText(thinking))
                }
            }
    }

    private fun hasUploadedAttachments(content: String): Boolean {
        return content.contains("<lyra_attachment_v1>") ||
            content.contains("用户上传媒体：") ||
            content.contains("用户上传文件：")
    }

    private fun userPromptWithAttachments(rawContent: String): JSONObject {
        val parts = JSONArray()
        val attachments = parseUploadedAttachments(rawContent)
        val textPart = stripUploadedFileBlocks(stripUploadedMediaBlocks(stripUploadedAttachmentBlocks(rawContent))).trim()
        parts.put(JSONObject().put("type", "text").put("text", textPart.ifBlank { "请根据用户上传的附件回答。" }))
        attachments.forEach { item ->
            when (item.kind) {
                "image" -> {
                    val dataUrl = item.dataUrl.ifBlank { mediaDataUrl(item.uri, item.mimeType) }
                    if (dataUrl != null) {
                        parts.put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", dataUrl)),
                        )
                    } else {
                        parts.put(JSONObject().put("type", "text").put("text", "图片 ${item.name} 无法读取，URI=${item.uri}"))
                    }
                }
                "audio" -> {
                    val dataUrl = item.dataUrl.ifBlank { mediaDataUrl(item.uri, item.mimeType) }
                    if (dataUrl != null) {
                        parts.put(
                            JSONObject()
                                .put("type", "input_audio")
                                .put(
                                    "input_audio",
                                    JSONObject()
                                        .put("data", dataUrl.substringAfter("base64,", dataUrl))
                                        .put("format", audioFormat(item.mimeType, item.name)),
                                ),
                        )
                    } else {
                        parts.put(JSONObject().put("type", "text").put("text", "音频 ${item.name} 无法读取，URI=${item.uri}"))
                    }
                }
                "video" -> {
                    val dataUrl = item.dataUrl.ifBlank { mediaDataUrl(item.uri, item.mimeType) }
                    if (dataUrl != null) {
                        parts.put(
                            JSONObject()
                                .put("type", "video_url")
                                .put("video_url", JSONObject().put("url", dataUrl)),
                        )
                    }
                    parts.put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", "用户上传了视频媒体：${item.name}，MIME=${item.mimeType}。如果当前模型或平台不支持 video_url，请说明限制。"),
                    )
                }
                "text" -> {
                    val body = buildString {
                        append("用户上传文件：").append(item.name).append('\n')
                        append("MIME：").append(item.mimeType).append('\n')
                        append("大小：").append(item.size).append(" bytes\n\n")
                        if (item.text.isNotBlank()) {
                            append("```text\n")
                            append(item.text)
                            append("\n```")
                        } else {
                            append("文件内容为空或无法读取。")
                        }
                    }
                    parts.put(JSONObject().put("type", "text").put("text", body))
                }
                else -> {
                    parts.put(JSONObject().put("type", "text").put("text", "用户上传了附件：${item.name}，类型=${item.kind}，MIME=${item.mimeType}，URI=${item.uri}。如果当前模型不支持该附件类型，请说明限制并给出可行替代方案。"))
                }
            }
        }
        return JSONObject().put("role", "user").put("content", parts)
    }

    private data class UploadedAttachmentPrompt(
        val name: String,
        val kind: String,
        val mimeType: String,
        val dataUrl: String,
        val uri: String,
        val size: Long,
        val text: String,
    )

    private fun parseUploadedAttachments(content: String): List<UploadedAttachmentPrompt> {
        val attachments = mutableListOf<UploadedAttachmentPrompt>()
        val markerRegex = Regex("<lyra_attachment_v1>([\\s\\S]*?)</lyra_attachment_v1>")
        markerRegex.findAll(content).forEach { match ->
            val payload = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: return@forEach
            attachments += UploadedAttachmentPrompt(
                name = payload.optString("name").ifBlank { "未命名文件" },
                kind = payload.optString("kind").ifBlank { "text" },
                mimeType = payload.optString("mime_type"),
                dataUrl = payload.optString("data_url"),
                uri = payload.optString("uri"),
                size = payload.optLong("size", 0L),
                text = payload.optString("text"),
            )
        }
        val legacyMediaRegex = Regex("用户上传媒体：([^\\n]+)\\n类型：([^\\n]+)\\nMIME：([^\\n]*)\\n(?:DATA_URL：([^\\n]*)\\n)?URI：([^\\n]*)", RegexOption.MULTILINE)
        legacyMediaRegex.findAll(content).forEach {
            attachments += UploadedAttachmentPrompt(
                name = it.groupValues[1].trim(),
                kind = it.groupValues[2].trim(),
                mimeType = it.groupValues[3].trim(),
                dataUrl = it.groupValues[4].trim(),
                uri = it.groupValues[5].trim(),
                size = 0L,
                text = "",
            )
        }
        val legacyFileRegex = Regex("用户上传文件：([^\\n]+)\\n大小：(\\d+) bytes\\n\\n```text\\n([\\s\\S]*?)\\n```", RegexOption.MULTILINE)
        legacyFileRegex.findAll(content).forEach {
            attachments += UploadedAttachmentPrompt(
                name = it.groupValues[1].trim().ifBlank { "未命名文件" },
                kind = "text",
                mimeType = "text/plain",
                dataUrl = "",
                uri = "",
                size = it.groupValues[2].toLongOrNull() ?: 0L,
                text = it.groupValues[3],
            )
        }
        return attachments
    }

    private fun stripUploadedAttachmentBlocks(content: String): String {
        return content.replace(Regex("\\n*<lyra_attachment_v1>[\\s\\S]*?</lyra_attachment_v1>\\n*"), "\n").trim()
    }

    private fun stripUploadedFileBlocks(content: String): String {
        return content
            .replace(Regex("\\n*用户上传文件：[^\\n]+\\n大小：\\d+ bytes\\n\\n```text\\n[\\s\\S]*?\\n```\\n?"), "\n")
            .replace(Regex("\\n*用户上传文件：[^\\n]+\\n大小：\\d+ bytes\\n?"), "\n")
            .trim()
    }

    private fun stripUploadedMediaBlocks(content: String): String {
        return content.replace(
            Regex("\\n*用户上传媒体：[^\\n]+\\n类型：[^\\n]+\\nMIME：[^\\n]*\\n(?:DATA_URL：[^\\n]*\\n)?URI：[^\\n]*\\n大小：[^\\n]*(\\n)?"),
            "\n",
        ).trim()
    }
    private fun mediaDataUrl(uriText: String, mimeType: String): String? = runCatching {
        val uri = Uri.parse(uriText)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_PROMPT_BYTES) return@runCatching null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: return@runCatching null
        "data:${mimeType.ifBlank { "application/octet-stream" }};base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }.getOrNull()

    private fun audioFormat(mimeType: String, name: String): String {
        val lower = "$mimeType $name".lowercase(Locale.US)
        return when {
            "wav" in lower -> "wav"
            "aac" in lower || "m4a" in lower -> "mp3"
            "ogg" in lower -> "mp3"
            else -> "mp3"
        }
    }

    private fun ChatMessage.toToolPromptJson(): JSONObject = JSONObject()
        .put("role", "tool")
        .put("tool_call_id", toolCallId)
        .put("content", content)

    private fun JSONObject.hasToolCalls(): Boolean {
        return optString("role") == "assistant" && (optJSONArray("tool_calls")?.length() ?: 0) > 0
    }

    private fun JSONObject.toolCallIds(): Set<String> {
        val calls = optJSONArray("tool_calls") ?: return emptySet()
        return buildSet {
            for (index in 0 until calls.length()) {
                calls.optJSONObject(index)?.optString("id").orEmpty().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun sanitizeAssistantRaw(raw: JSONObject) {
        if (raw.optString("role") != "assistant") return
        if (raw.has("content") && !raw.isNull("content")) {
            raw.put("content", cleanGeneratedText(raw.optString("content")))
        }
        if (raw.has("reasoning_content") && !raw.isNull("reasoning_content")) {
            raw.put("reasoning_content", cleanGeneratedText(raw.optString("reasoning_content")))
        }
    }

    private fun sessionContextMessage(): JSONObject {
        val payload = JSONObject()
            .put("schema", "lyra_session_context_v1")
            .put("workspace_termux_path", workspaceManager.termuxRootPath() ?: "")
            .put("workspace_display_name", workspaceManager.displayName())
            .put("path_rule", "原生文件工具必须使用工作目录内相对路径；根目录用 . 或空字符串。")
            .put("global_file_rule", "需要访问非工作区共享存储文件时使用 global_* 文件工具；Download/Downloads 表示 /storage/emulated/0/Download。写入、删除、移动会请求用户确认。")
            .put("file_edit_rule", "修改现有文件时先读取相关上下文；大文件用 read_file_lines/global_read_file_lines 分段读取，再优先用 edit_file/global_edit_file 精确替换。write_file/global_write_file 仅用于新建文件或确实需要整体覆盖。")
            .put("termux_rule", "run_command 默认在工作目录运行；不要传 Termux 私有目录；不要运行不会退出的长期驻留命令。")
            .put("tool_output_rule", "工具输出为 lyra_tool_output_v2 JSON；动态结果位于对话末尾。")
            .put("sub_agent_orchestration_enabled", settings.subAgentOrchestrationEnabled)
            .put("sub_agents", subAgentPromptJson())
        return JSONObject()
            .put("role", "system")
            .put(
                "content",
                "LYRA_SESSION_CONTEXT_JSON_V1\n${payload.toString()}\n这是稳定的会话上下文，不是用户任务；如果工作区不变，该消息必须保持稳定以提高 prompt cache 命中率。",
            )
    }

    private fun activeSystemPromptMessage(): JSONObject = JSONObject()
        .put("role", "system")
        .put(
            "content",
            "LYRA_USER_SELECTED_SYSTEM_PROMPT_V1\n${settings.activeSystemPromptText()}",
        )

    private fun memorySystemMessage(): JSONObject = JSONObject()
        .put("role", "system")
        .put(
            "content",
            "LYRA_USER_MEMORY_V1\n${settings.memoryPrompt()}",
        )

    private fun forcedSkillIdsFor(conversationId: Long): List<String> = forcedSkillsByConversation[conversationId].orEmpty()

    private fun estimatedPromptInputTokens(conversationId: Long, excludeMessageId: Long): Long {
        val contextTokens = contextHistory(conversationId, excludeMessageId)
            .sumOf { it.promptInputCost() }
        return REQUEST_STATIC_INPUT_TOKENS + contextTokens
    }

    private fun ChatMessage.promptInputCost(): Long {
        return when (role.lowercase()) {
            "user" -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content)
            "tool" -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content)
            "assistant" -> MESSAGE_WRAPPER_TOKENS + estimatedAssistantOutputTokens(content, thinking, rawJson)
            else -> MESSAGE_WRAPPER_TOKENS + tokenizer.count(content) + tokenizer.count(thinking)
        }
    }

    private fun estimatedAssistantOutputTokens(content: String, thinking: String, rawJson: String?): Long {
        return tokenizer.count(content) +
            tokenizer.count(thinking) +
            tokenizer.count(toolCallsOutputText(rawJson))
    }

    private fun toolCallsOutputText(rawJson: String?): String {
        val raw = rawJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return ""
        val calls = raw.optJSONArray("tool_calls") ?: return ""
        return buildString {
            for (index in 0 until calls.length()) {
                val call = calls.optJSONObject(index) ?: continue
                appendToolCallOutput(call)
                append('\n')
            }
        }
    }

    private fun StringBuilder.appendToolCallOutput(call: JSONObject) {
        val function = call.optJSONObject("function")
        if (function != null) {
            append(function.optString("name"))
            append('\n')
            append(function.optString("arguments"))
            return
        }
        append(call.optString("name"))
        append('\n')
        val input = call.opt("input")
        when (input) {
            is JSONObject, is JSONArray -> append(input.toString())
            null -> append(call.toString())
            else -> append(input.toString())
        }
    }

    private fun activeSkillsMessage(conversationId: Long): JSONObject = JSONObject()
        .put("role", "system")
        .put(
            "content",
            "LYRA_ACTIVE_SKILLS_V1\n${settings.activeSkillsPrompt(forcedSkillIdsFor(conversationId)).ifBlank { "[]" }}",
        )

    private fun staticSystemMessage(): JSONObject = JSONObject()
        .put("role", "system")
        .put(
            "content",
            """
            LYRA_STATIC_AGENT_PROTOCOL_V3
            以下是 Lyra Code 运行环境与工具约束，必须遵守。此段为静态协议，不包含会随会话变化的路径、时间、模型、网络结果、工具结果或文件内容；运行时上下文会以固定 JSON 模板放在消息列表最后。
            你是运行在 Android 应用 Lyra Code 中的开发 Agent。优先使用原生文件工具完成小文件读写和目录浏览。
            Skills 是可选能力包，不是默认系统提示词，除非 LYRA_ACTIVE_SKILLS_V1 包含 forced_skill_ids。若存在 forced_skill_ids，必须调用 list_skill_files 和 read_skill_file 从 SKILL.md 开始检查并尽量应用这些 Skills；若无法应用，应简要说明原因。没有 forced_skill_ids 时，先根据 name/description 判断是否相关；相关时再读取 SKILL.md 或必要文件。不要无差别读取所有 Skills。
            Skills 可能包含桌面、云端或外部服务假设，使用前必须适配 Android、Termux 和 Lyra Code 工具限制。
            MCP 工具来自用户配置的远程或局域网 MCP Server，仅在工具名以 mcp_ 开头时代表外部 MCP 工具。调用 MCP 工具前应用会请求用户确认；不要把 MCP 工具当成本地文件工具使用，也不要假设 MCP Server 可访问 Android 本机工作区。
            下载 http/https 文件时优先调用 download_file，直接保存到工作区或 Android 共享存储；未选择工作区而 destination=workspace 时，应用会自动回退保存到 Android 共享存储 Download/LyraCode。可提供请求头和 SHA-256 校验。只有 download_file 明确失败、被禁用或不支持目标协议时，才可把 Termux 的 curl/wget 作为最后备用手段。
            需要预览或调试工作区内静态网站、Vue/Vite/VitePress 构建产物、HTML/CSS/JS 文件时，优先使用 get_mini_server_status 和 manage_mini_server 启动 Lyra Code 微型服务器。默认监听 127.0.0.1；若用户要求局域网、内网穿透或公网访问，可设置 host=0.0.0.0 并提醒设置密码和 HTTP 明文风险。HTTPS 可使用自定义证书库/PEM 证书链和私钥；未配置时使用内置自签名证书，浏览器可能提示不受信任。custom_domains 只是访问域名展示/跳转配置，域名 DNS 或内网穿透仍需用户自行指向设备。站点资源加载失败、404、认证失败或页面 JavaScript 报错时，调用 read_mini_server_logs 读取最近日志再修复。
            只有在工具列表提供 run_command，且需要安装包、运行脚本、Git、长输出或非空目录删除时才调用 run_command；如果工具列表没有 run_command，说明用户未授予 Termux 通信权限或已禁用该工具，不要假设可执行命令。
            需要按文件名、扩展名或路径片段查找文件时，必须先调用 search_files；不要用 run_command 执行 find、fd、locate 或自行写搜索脚本来代替 search_files。
            search_files 的 query 只放文件名或关键词，例如 AvatarSkin.json、build.gradle、MainActivity；path 默认为 "."，除非用户明确限定子目录。
            如果 search_files 返回 SEARCH_EMPTY，且用户要找的是工作区外可能存在的文件，调用 global_search_files 搜索 /storage/emulated/0。不要通过反复尝试 "/", "..", "storage", "mnt" 等 path 来扩大 search_files 范围。
            global_search_files 返回的是共享存储绝对路径，不能直接交给原生 read_file；需要读取时让用户切换工作区到对应目录，或使用 run_command 执行只读 cat/head/tail。
            原生文件工具的 path 参数必须使用工作目录内的相对路径；根目录用 "." 或空字符串。
            不要把 /data/data/com.termux/files/home、/data/data/com.termux、/data/data/... 传给文件工具。
            修改现有文件时，必须先读取相关上下文。文件较大或只需查看局部内容时优先调用 read_file_lines/global_read_file_lines；工具列表提供 edit_file/global_edit_file 时，优先用唯一 old_content 或精确 start_line/end_line 做局部修改。只有新建文件或确实需要整体重写时才使用 write_file/global_write_file。
            写入代码、配置、Markdown、YAML、Python 或任何缩进敏感内容时，write_file/edit_file/append_file 及其 global_* 版本可以使用对应的 *_lines 数组逐行传递。所有 *_lines 字段必须是实际 JSON 字符串数组，例如 {"content_lines":["line 1","line 2",""]}；严禁写成 {"content_lines":"\"line 1\", \"line 2\", \"\""}，也不要给整个数组再加一层引号。content 与 content_lines 二选一；old_content 与 old_content_lines 二选一；new_content 与 new_content_lines 二选一。局部替换若匹配数量不符或工具提示参数类型错误，应根据错误信息修正 JSON 参数并重试，不要退回盲目全文件覆盖。
            如果需要运行脚本，应先用 write_file 写到工作目录相对路径，再用 run_command 在默认工作目录运行。
            运行多行脚本、here-doc 或缩进敏感命令时，run_command 优先使用 command_lines 数组逐行传递；应用会用换行原样拼接后发送给 Termux。
            run_command 会等待 Termux 回传 exit_code、stdout、stderr；命令非 0 退出也会返回 stderr，看到报错后应直接修正。不要运行不会退出的长期驻留命令。
            如遇回传超时或输出过大，再让命令把结果写入工作目录文件并用 read_file 读取。Shell 重定向 stdout 和 stderr 时必须写成 "> output.txt 2>&1"，文件名和 2>&1 之间要有空格。
            需要联网获取最新信息时，可使用 web_search 搜索，再用 read_web_page 读取候选网页正文；回答中应基于读取到的网页内容判断，不要把搜索摘要当作最终事实。
            web_search 会返回排序后的候选网页、相关性提示和可能的低质量信号，并自动过滤用户在设置中加入的网站黑名单。优先读取官方文档、原始发布源、权威媒体或和问题关键词高度匹配的页面；遇到 SEO 聚合页、广告页、搜索结果页、论坛搬运或摘要明显无关时不要反复读取，应换用更精确关键词、限定站点或读取排名更高的可信来源。
            read_web_page 会标注 readable、limited、blocked_by_user 或 blocked_or_dynamic。若页面被用户黑名单拦截，不要绕过黑名单；若页面提示人机验证、Cloudflare、403、登录墙、JavaScript 渲染不足或正文过短，不要把该内容当事实依据；改读其他来源，必要时告知用户该网页存在访问防护。
            当最终回答依赖 read_web_page 或网页搜索结果时，必须先调用 mark_web_sources 声明本轮实际引用的网页；最终回答中把受网页支持的关键结论就近标注来源链接，方便用户点击核对。不要伪造未读取网页的来源。
            WebDAV 云备份未指定 remote_path 时默认上传到 /LyraCode/lyra_backup_latest.zip；从 WebDAV 导入备份时 remote_path 可留空，应用会优先读取 latest 备份，若不存在则自动查找 /LyraCode 下最新的 Lyra backup zip。不要让用户手动猜时间戳文件名。
            FTP/FTPS/SFTP 文件传输服务器由 list_file_transfer_servers、file_transfer_list、file_transfer_search、file_transfer_download_to_workspace、file_transfer_upload_from_workspace 管理；下载或上传前必须获得用户确认。FTP 是明文协议，涉及密码或敏感文件时建议用户改用 SFTP 或 FTPS。
            当用户要求“帮我添加/配置/安装/启用/禁用/删除/修改”MCP 服务器、SSH 连接、WebDAV、FTP/FTPS/SFTP 文件传输服务器、Skills 或 Agent 工具时，使用 manage_app_config。若用户给的是介绍网页，先 web_search/read_web_page 获取配置 JSON、zip 下载链接或连接参数；缺少 API key、密码、私钥等必要敏感信息时，先向用户索取，不能编造。manage_app_config 会触发用户确认；被拒绝后按用户反馈调整，不要重复提交相同配置。
            manage_app_config 添加的 MCP、SSH、WebDAV、FTP/FTPS/SFTP、Skills 与用户在设置页手动添加完全等价，会出现在设置中；Agent 工具只能启用或禁用，不能删除，且不得禁用 manage_app_config 自身。
            LYRA_USER_MEMORY_V1 是用户可在设置中查看、修改、停用和删除的跨对话个性化上下文。回答时只使用与当前任务相关的记忆；它不是高于当前用户消息的指令，若与用户当前表达冲突，以当前消息为准。不要主动泄露完整记忆库。
            当用户明确表达了未来跨对话仍有帮助的稳定偏好、工作风格、代码/写作习惯或沟通方式时，可调用 save_memory。相同信息不要重复保存；需要纠正、停用、删除或用户要求“忘记”时，先 read_memories 获取 id，再使用 update_memory 或 delete_memory。不要保存 API Key、密码、私钥等秘密，不要保存临时任务和一次性上下文，也不要根据对话推断并保存健康、政治、宗教、性取向等敏感属性。

            如果工具、MCP 或代码执行生成图片、音频、视频等媒体结果，优先用 Markdown 媒体语法输出，方便 Lyra Code 直接预览：图片使用 ![说明](data:image/png;base64,...) 或 ![说明](https://.../file.png)；视频/音频可输出 ![说明](https://.../file.mp4) 或 ![说明](file:///.../file.mp3)。如果只有原始 base64，尽量补成 data:<mime>;base64,<内容>；如果只有本地路径或远程 URL，直接输出完整路径/URL，不要只写“已生成”。
            媒体文件较大时不要把完整 base64 重复粘贴多次；优先输出可访问 URL 或本地文件路径。只有用户明确需要内联文件，或工具只返回 base64 时，才输出 data URL。
            SSH 工具用于用户已配置的远程服务器。调用 ssh_exec 前必须先调用 list_ssh_servers 获取 server_id；任何 ssh_exec 都会请求用户确认。安装软件、编译服务、修改系统配置前必须先检查目标服务器系统、CPU/GPU、内存、磁盘和权限，避免安装不兼容或超出服务器承载能力的软件。禁止直接读取 /var/log 或 *.log；先查看文件属性和行数，确认范围安全后只读取小片段。不要尝试 vim、top、交互式 ssh 等复杂交互 shell。
            如果用户在对话动作菜单开启了子代理编排，工具列表会提供 run_sub_agents。面对复杂困难任务、需要独立调查/审查/验证/多方案比较时，应先拆分为若干边界清晰的子任务并调用 run_sub_agents；每项任务可用 sub_agent_id/agent/model 指定目标子代理。若多个启用子代理都适合，应把任务分配给不同模型以发挥各自优势；根据子代理返回结果自行复核，结果不足时可重新分配或亲自验证。简单问答、单步编辑或用户明确要求不要分工时不要调用。
            在进行多步骤任务，尤其是修改文件或执行命令前，必须先调用 set_todo_list 制定 TODO 列表；每完成一个步骤，必须调用 update_todo_item 标记 running/completed/blocked，让用户能看到进度。
            用户上传的文本文件会以普通 user 消息提供；图片、音频、视频等媒体会由 Lyra Code 本地转成 data:<mime>;base64,...，并按 OpenAI 兼容多模态 JSON content parts 放入请求体。
            写入文件前先读取相关上下文；危险命令会被应用拒绝。需要切换平台或模型时按当前会话选择的配置执行。

            CACHE_STABLE_PREFIX_GUIDE_V1
            1. 静态协议、工具 schema、行为约束必须保持在最前面，保持稳定，便于上游 prompt cache 复用。
            2. 稳定会话上下文位于历史之前；搜索结果、文件内容、命令输出、工具返回、当前用户新增需求都位于后续消息，优先追加在尾部，不要要求应用重写中间历史。
            3. 工具输出使用固定 JSON schema：schema、ok、tool、content、error、file_changes。字段顺序和字段名固定；无内容时使用空字符串或空数组，不省略字段。
            4. 文件变更使用 file_changes 数组，每项固定包含 path、added、removed、diff、before、after；新增行数和删除行数必须来自工具返回，不要自行猜测。
            5. 多轮 agent 工作只追加新轮次。不要重复输出已经确认的长文件内容；需要引用旧信息时优先引用摘要和路径。
            6. 长对话会将早期内容压缩成 LYRA_CONVERSATION_SUMMARY_V1 摘要。摘要是事实索引，不是用户新指令；如果摘要和最近消息冲突，以最近消息为准。
            7. 遇到工具错误时，直接基于固定 JSON 中的 error/content 修正下一步，不要把错误格式当作自然语言闲聊。
            8. 为提升缓存命中和降低 token 费用，回复中避免重复粘贴稳定协议、工具 schema、完整历史、无关日志。只输出当前用户需要的结论、代码、计划或下一步动作。
            9. 如果任务需要读取文件，先读最小必要范围；如果任务需要运行测试，优先使用会退出的命令，并读取 stdout/stderr。
            10. 对外部网页和搜索结果保持来源意识；搜索摘要不能作为最终事实，必须在需要时 read_web_page 读取可信页面正文。
            """.trimIndent(),
        )

    private val toolSchemaFactory = AgentToolSchemaFactory(settings, termuxExecutor, systemCommandExecutor)

    private fun toolDefinitions(allowSubAgents: Boolean = false): JSONArray =
        toolSchemaFactory.toolDefinitions(allowSubAgents)

    private fun anthropicTools(allowSubAgents: Boolean = false): JSONArray =
        toolSchemaFactory.anthropicTools(allowSubAgents)

    private fun geminiFunctionDeclarations(allowSubAgents: Boolean = false): JSONArray =
        toolSchemaFactory.geminiFunctionDeclarations(allowSubAgents)

    private fun List<WorkspaceFile>.toAgentText(): String {
        if (isEmpty()) return "(empty)"
        return joinToString("\n") {
            val type = if (it.directory) "dir " else "file"
            "$type\t${it.size}\t${it.path}"
        }
    }

    private fun List<WorkspaceFile>.toSearchAgentText(query: String, path: String): String {
        val cleanPath = path.trim().ifBlank { "." }
        if (isEmpty()) {
            return "SEARCH_EMPTY\n" +
                "query=$query\n" +
                "path=$cleanPath\n" +
                "workspace=${workspaceManager.displayName()}\n" +
                "note=只搜索了当前授权工作目录内的文件；如果用户要搜索更大范围，需要先在设置中把工作目录切换到对应上级目录，例如 /storage/emulated/0。"
        }
        return toAgentText()
    }

    private fun splitInlineThink(content: String, existingThinking: String): Pair<String, String> {
        val start = content.indexOf("<think>", ignoreCase = true)
        val end = content.indexOf("</think>", ignoreCase = true)
        if (start < 0 || end <= start) return content to existingThinking
        val thinkStart = start + "<think>".length
        val inlineThink = content.substring(thinkStart, end).trim()
        val visible = (content.substring(0, start) + content.substring(end + "</think>".length)).trim()
        val merged = listOf(existingThinking.trim(), inlineThink).filter { it.isNotBlank() }.joinToString("\n\n")
        return cleanGeneratedText(visible) to cleanGeneratedText(merged)
    }

    private fun normalizeCommandWorkDir(rawWorkDir: String): String? {
        val root = workspaceManager.termuxRootPath()
        val raw = rawWorkDir.trim().replace('\\', '/')
        if (raw.isBlank() || raw == "." || raw == "./" || raw == "/") return root
        if (root == null) {
            require(!raw.startsWith("/")) { "未选择可供 Termux 访问的内部存储工作目录，不能使用绝对 workDir: $raw" }
            return null
        }
        val cleanRoot = root.trimEnd('/')
        val sdcardRoot = cleanRoot.replace("/storage/emulated/0", "/sdcard")
        return when {
            raw == cleanRoot || raw == sdcardRoot -> cleanRoot
            raw.startsWith("$cleanRoot/") -> raw
            raw.startsWith("$sdcardRoot/") -> raw.replace(sdcardRoot, cleanRoot)
            raw.startsWith("/") -> error("run_command 的 workDir 必须位于 Lyra Code 工作目录内，不能使用: $raw")
            else -> "$cleanRoot/${raw.trim('/')}"
        }
    }

    private fun normalizeEndpointForCacheKey(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim().trimEnd('/').lowercase(Locale.US)
        val scheme = uri.scheme.orEmpty().lowercase(Locale.US).ifBlank { "https" }
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = uri.path.orEmpty().trimEnd('/')
        return "$scheme://$host$port$path"
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    private fun outputTokensPerSecond(content: String, reportedOutputTokens: Long, startedAtNanos: Long): Double {
        val tokens = reportedOutputTokens.takeIf { it > 0L } ?: tokenizer.count(content)
        val elapsedSeconds = elapsedMs(startedAtNanos) / 1000.0
        if (tokens <= 0L || elapsedSeconds <= 0.0) return 0.0
        return tokens / elapsedSeconds
    }

    private fun elapsedMs(startedAtNanos: Long): Long {
        return ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
    }

    private fun String.cleanProbeMessage(): String {
        return replace(Regex("\\s+"), " ").trim().take(300).ifBlank { uiText("请求失败") }
    }

    companion object {
        private const val AGENT_TAG = "LyraAgent"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val LOG_ARGUMENT_CHARS = 1_000
        private const val MAX_TOOL_RESULT_CHARS = 500_000
        private const val MAX_SUB_AGENT_TASKS = 6
        private const val HISTORY_COMPRESSION_MAX_OUTPUT_TOKENS = 4096
        private const val PROMPT_CACHE_KEY_HASH_CHARS = 32
        private const val REQUEST_STATIC_INPUT_TOKENS = 1024L
        private const val MESSAGE_WRAPPER_TOKENS = 8L
        private const val GLOBAL_SEARCH_RESULT_LIMIT = 120
        private const val MAX_IMAGE_PROMPT_BYTES = 8 * 1024 * 1024
        private const val DEFAULT_WEBDAV_BACKUP_PATH = "/LyraCode/lyra_backup_latest.zip"
        private const val LOCAL_MCP_CONVERSATION_ID = 0L
        private val JSON_SCHEMA_TYPES = setOf("string", "number", "integer", "boolean", "object", "array")
        private val FILE_TEXT_ARGUMENT_TOOLS = setOf(
            "write_file",
            "edit_file",
            "append_file",
            "global_write_file",
            "global_edit_file",
            "global_append_file",
        )
        private val CONFIGURABLE_AGENT_TOOLS = listOf(
            "list_directory",
            "read_file",
            "read_file_lines",
            "write_file",
            "edit_file",
            "append_file",
            "create_folder",
            "delete_file_or_folder",
            "rename_move",
            "global_list_directory",
            "global_read_file",
            "global_read_file_lines",
            "global_write_file",
            "global_edit_file",
            "global_append_file",
            "global_create_folder",
            "global_delete_file_or_folder",
            "global_rename_move",
            "download_file",
            "manage_scheduled_tasks",
            "get_mini_server_status",
            "read_mini_server_logs",
            "manage_mini_server",
            "search_conversation_history",
            "read_conversation_history",
            "read_memories",
            "save_memory",
            "update_memory",
            "delete_memory",
            "search_files",
            "global_search_files",
            "get_file_info",
            "list_skill_files",
    "read_skill_file",
    "run_command",
            "web_search",
            "read_web_page",
            "mark_web_sources",
            "manage_app_config",
            "get_current_time",
            "get_current_location",
            "get_device_hardware_info",
            "list_installed_apps",
            "execute_shell_command",
            "execute_root_command",
            "list_ssh_servers",
            "ssh_exec",
            "list_webdav_servers",
            "webdav_list",
            "webdav_search",
            "webdav_download_to_workspace",
            "webdav_upload_from_workspace",
            "list_file_transfer_servers",
            "file_transfer_list",
            "file_transfer_search",
            "file_transfer_download_to_workspace",
            "file_transfer_upload_from_workspace",
            "export_backup",
            "import_backup",
            "set_todo_list",
            "update_todo_item",
        )
        private val FILE_SEARCH_COMMAND_PATTERNS = listOf(
            Regex("""(^|[;&|()\n]\s*)find\s+.+\s-(i)?name\s+"""),
            Regex("""(^|[;&|()\n]\s*)fd\s+"""),
            Regex("""(^|[;&|()\n]\s*)fdfind\s+"""),
            Regex("""(^|[;&|()\n]\s*)locate\s+"""),
        )
    }
}

private fun ToolExecution.toToolOutputJson(toolName: String, ok: Boolean): String {
    return JSONObject()
        .put("schema", "lyra_tool_output_v2")
        .put("ok", ok)
        .put("tool", toolName)
        .put("content", content)
        .put("error", if (ok) "" else content)
        .put("file_changes", JSONArray().apply { fileChanges.forEach { put(it.toJson()) } })
        .toString()
}

private data class FileDiff(
    val path: String,
    val added: Int,
    val removed: Int,
    val diff: String,
    val before: String,
    val after: String,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("path", path)
            .put("added", added)
            .put("removed", removed)
            .put("diff", diff)
            .put("before", before)
            .put("after", after)
    }

    fun toToolText(): String {
        return """
        LYRA_FILE_CHANGE_BEGIN
        path: $path
        added: $added
        removed: $removed
        diff:
        $diff
        LYRA_FILE_BEFORE_BEGIN
        $before
        LYRA_FILE_BEFORE_END
        LYRA_FILE_AFTER_BEGIN
        $after
        LYRA_FILE_AFTER_END
        LYRA_FILE_CHANGE_END
        """.trimIndent()
    }

    companion object {
        fun from(path: String, before: String, after: String): FileDiff {
            val beforeLines = before.toDiffLines()
            val afterLines = after.toDiffLines()
            val lcs = Array(beforeLines.size + 1) { IntArray(afterLines.size + 1) }
            for (i in beforeLines.indices.reversed()) {
                for (j in afterLines.indices.reversed()) {
                    lcs[i][j] = if (beforeLines[i] == afterLines[j]) {
                        lcs[i + 1][j + 1] + 1
                    } else {
                        maxOf(lcs[i + 1][j], lcs[i][j + 1])
                    }
                }
            }
            val diffLines = mutableListOf<String>()
            var added = 0
            var removed = 0
            var i = 0
            var j = 0
            while (i < beforeLines.size && j < afterLines.size) {
                when {
                    beforeLines[i] == afterLines[j] -> {
                        diffLines += "  ${beforeLines[i]}"
                        i++
                        j++
                    }
                    lcs[i + 1][j] >= lcs[i][j + 1] -> {
                        diffLines += "- ${beforeLines[i]}"
                        removed++
                        i++
                    }
                    else -> {
                        diffLines += "+ ${afterLines[j]}"
                        added++
                        j++
                    }
                }
            }
            while (i < beforeLines.size) {
                diffLines += "- ${beforeLines[i++]}"
                removed++
            }
            while (j < afterLines.size) {
                diffLines += "+ ${afterLines[j++]}"
                added++
            }
            return FileDiff(
                path = path,
                added = added,
                removed = removed,
                diff = diffLines.take(2_000).joinToString("\n"),
                before = before.take(20_000),
                after = after.take(20_000),
            )
        }

        private fun String.toDiffLines(): List<String> {
            if (isEmpty()) return emptyList()
            return replace("\r\n", "\n").lines()
        }
    }
}

private fun JSONObject.cleanString(name: String): String {
    return stringFieldOrNull(name).orEmpty()
}

private fun JSONObject.stringFieldOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val value = opt(name) ?: return null
    val text = value as? String ?: return null
    return text.takeUnless { it.equals("null", ignoreCase = true) }
}

private fun cleanGeneratedText(text: String): String {
    return text.replace(Regex("(?:null){4,}", RegexOption.IGNORE_CASE), "").trim()
}

fun ChatMessage.toRecord(): ChatRecord = ChatRecord(
    id = id,
    role = role,
    content = if (role == "assistant") cleanGeneratedText(content) else content,
    thinking = if (role == "assistant") cleanGeneratedText(thinking) else thinking,
    profileId = profileId,
    model = model,
    createdAt = createdAt,
    tokensPerSecond = tokensPerSecond,
    toolCallId = toolCallId,
    rawJson = rawJson,
)


