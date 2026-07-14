package com.yukisoffd.lyracode

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.yukisoffd.lyracode.ai.ChatRecord
import com.yukisoffd.lyracode.ai.ChatUpdate
import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.ai.ModelReachabilityResult
import com.yukisoffd.lyracode.ai.ProviderReachabilityReport
import com.yukisoffd.lyracode.ai.ProviderReachabilityResult
import com.yukisoffd.lyracode.ai.ToolApprovalDecision
import com.yukisoffd.lyracode.ai.ToolApprovalRequest
import com.yukisoffd.lyracode.ai.TodoItem
import com.yukisoffd.lyracode.ai.toRecord
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.Conversation
import android.content.Context
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.workspace.UploadedFile
import com.yukisoffd.lyracode.workspace.UploadedFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import com.yukisoffd.lyracode.workspace.WorkspaceFileReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PendingToolApproval(
    val id: Long,
    val request: ToolApprovalRequest,
)

class ChatController(
    private val appContext: Context,
    private val settings: AppSettings,
    private val conversationStore: ConversationStore,
    private val uploadedFileManager: UploadedFileManager,
    private val workspaceManager: WorkspaceManager,
    private val agent: OpenAiAgent,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Long, Job>()

    val conversations = mutableStateListOf<Conversation>()
    val archivedConversations = mutableStateListOf<Conversation>()
    private val _messages = mutableStateOf<List<ChatRecord>>(emptyList())
    val messages: State<List<ChatRecord>> = _messages
    val profiles = mutableStateListOf<ApiProfile>()
    val activeConversationId = mutableStateOf(0L)
    val activeProfileId = mutableStateOf("")
    val activeModel = mutableStateOf("")
    val status = mutableStateOf("")
    val uploadingStatus = mutableStateOf("")
    val pendingUploads = mutableStateListOf<UploadedFile>()
    val pendingToolApproval = mutableStateOf<PendingToolApproval?>(null)
    val todoItems = mutableStateListOf<TodoItem>()
    val settingsRevision = mutableIntStateOf(0)
    private var lastMessageReloadAt = 0L
    private var approvalId = 0L
    private val approvalWaiters = mutableMapOf<Long, CompletableDeferred<ToolApprovalDecision>>()
    private val autoApprovedConversations = mutableSetOf<Long>()
    private var transientWorkspaceUri = ""
    private var transientAutoApprovalEnabled = false
    private val todoByConversation = mutableMapOf<Long, MutableList<TodoItem>>()

    init {
        agent.approvalHandler = ::requestToolApproval
        agent.todoSetHandler = ::setTodos
        agent.todoUpdateHandler = ::updateTodo
        agent.configChangedHandler = ::handleConfigChanged
        reloadProfiles()
        markAbandonedRunsInterrupted()
        reloadConversations()
        showTransientNewConversation()
    }

    fun close() {
        scope.cancel()
    }

    fun usageStore(): ConversationStore = conversationStore
    fun inputDraftKey(): String = "normal:${activeConversationId.value}"

    fun loadInputDraft(): String = settings.chatInputDraft(inputDraftKey())

    fun saveInputDraft(text: String) {
        settings.setChatInputDraft(inputDraftKey(), text)
    }

    fun clearInputDraft() {
        settings.setChatInputDraft(inputDraftKey(), "")
    }

    fun reloadProfiles() {
        profiles.clear()
        profiles.addAll(settings.profiles())
        val selected = settings.selectedProfile()
        activeProfileId.value = selected.id
        activeModel.value = selected.selectedModel
    }

    fun saveProfiles(updated: List<ApiProfile>, selectedId: String = activeProfileId.value) {
        settings.saveProfiles(updated, selectedId)
        reloadProfiles()
    }

    private suspend fun handleConfigChanged() {
        withContext(Dispatchers.Main) {
            settingsRevision.intValue++
            reloadProfiles()
            reloadConversations()
        }
    }

    fun selectProfile(profileId: String) {
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        settings.selectedApiProfileId = profile.id
        activeProfileId.value = profile.id
        activeModel.value = profile.selectedModel
        activeConversationId.value.takeIf { it > 0 }?.let {
            conversationStore.setConversationMeta(it, profileId = profile.id, model = profile.selectedModel)
            reloadConversations()
        }
    }

    fun selectModel(model: String) {
        activeModel.value = model
        val updated = profiles.map {
            if (it.id == activeProfileId.value) it.copy(
                selectedModel = model,
                savedModels = (it.savedModels + model).filter { item -> item.isNotBlank() }.distinct(),
            ) else it
        }
        saveProfiles(updated, activeProfileId.value)
        activeConversationId.value.takeIf { it > 0 }?.let {
            conversationStore.setConversationMeta(it, profileId = activeProfileId.value, model = model)
            reloadConversations()
        }
    }

    fun selectSystemPrompt(promptId: String) {
        settings.selectedSystemPromptId = promptId
        settingsRevision.intValue++
    }

    fun selectReasoningDepth(depth: String) {
        settings.reasoningDepth = depth
        settingsRevision.intValue++
    }

    fun newConversation() {
        showTransientNewConversation()
    }

    private fun createPersistedConversation(): Long {
        val profile = currentProfile()
        val id = conversationStore.createConversation(
            profileId = profile.id,
            model = activeModel.value.ifBlank { profile.selectedModel },
            title = appContext.getString(R.string.title_new_chat),
            workspaceUri = transientWorkspaceUri,
        )
        todoByConversation[id] = mutableListOf()
        if (transientAutoApprovalEnabled) autoApprovedConversations += id
        reloadConversations()
        selectConversation(id)
        return id
    }

    private fun showTransientNewConversation() {
        activeConversationId.value = 0L
        _messages.value = emptyList()
        todoItems.clear()
        pendingUploads.clear()
        uploadingStatus.value = ""
        status.value = ""
        transientWorkspaceUri = ""
        transientAutoApprovalEnabled = false
        workspaceManager.setActiveWorkspaceUri("")
        settingsRevision.intValue++
    }

    fun requestNewConversation(): Boolean {
        if (activeConversationId.value <= 0L || isCurrentConversationBlank()) {
            return false
        }
        newConversation()
        return true
    }

    fun selectConversation(id: Long) {
        activeConversationId.value = id
        val conversation = conversationStore.conversation(id)
        if (conversation != null) {
            activeProfileId.value = conversation.profileId.ifBlank { activeProfileId.value }
            activeModel.value = conversation.model.ifBlank { activeModel.value }
            workspaceManager.setActiveWorkspaceUri(conversation.workspaceUri)
        }
        reloadMessages()
        reloadTodos()
    }

    fun deleteConversation(id: Long) {
        jobs.remove(id)?.cancel()
        autoApprovedConversations.remove(id)
        todoByConversation.remove(id)
        conversationStore.deleteConversation(id)
        reloadConversations()
        val next = conversations.firstOrNull()?.id
        if (next == null) {
            showTransientNewConversation()
        } else {
            selectConversation(next)
        }
    }

    fun renameConversation(id: Long, title: String) {
        conversationStore.setConversationMeta(id, title = title)
        reloadConversations()
    }

    fun persistWorkspaceForActiveSession(uri: Uri): String {
        val workspaceUri = workspaceManager.persistWorkspace(uri)
        val conversationId = activeConversationId.value
        if (conversationId > 0L) {
            conversationStore.setConversationMeta(conversationId, workspaceUri = workspaceUri)
            reloadConversations()
        } else {
            transientWorkspaceUri = workspaceUri
        }
        settingsRevision.intValue++
        return workspaceManager.displayName()
    }

    fun workspaceDisplayName(): String = workspaceManager.displayName()

    fun hasWorkspace(): Boolean = workspaceManager.rootUri() != null

    fun searchWorkspaceFiles(query: String): List<WorkspaceFileReference> = workspaceManager.searchFiles(query)

    fun isAutoApprovalEnabledForActiveSession(): Boolean {
        val conversationId = activeConversationId.value
        return if (conversationId > 0L) conversationId in autoApprovedConversations else transientAutoApprovalEnabled
    }

    fun setAutoApprovalForActiveSession(enabled: Boolean) {
        val conversationId = activeConversationId.value
        if (conversationId > 0L) {
            if (enabled) autoApprovedConversations += conversationId else autoApprovedConversations -= conversationId
        } else {
            transientAutoApprovalEnabled = enabled
        }
        settingsRevision.intValue++
    }

    fun setConversationPinned(id: Long, pinned: Boolean) {
        conversationStore.setPinned(id, pinned)
        reloadConversations()
    }

    fun archiveConversation(id: Long) {
        conversationStore.setArchived(id, true)
        reloadConversations()
    }

    fun restoreArchivedConversation(id: Long) {
        conversationStore.setArchived(id, false)
        reloadConversations()
    }

    fun permanentlyDeleteArchivedConversation(id: Long) {
        if (conversationStore.conversation(id)?.archivedAt?.let { it > 0L } != true) return
        jobs.remove(id)?.cancel()
        autoApprovedConversations.remove(id)
        todoByConversation.remove(id)
        conversationStore.deleteConversation(id)
        reloadConversations()
    }

    fun deleteConversations(ids: Collection<Long>) {
        ids.forEach { id ->
            jobs.remove(id)?.cancel()
            autoApprovedConversations.remove(id)
            todoByConversation.remove(id)
            conversationStore.deleteConversation(id)
        }
        reloadConversations()
        if (activeConversationId.value in ids) {
            val next = conversations.firstOrNull()?.id
            if (next == null) {
                showTransientNewConversation()
            } else {
                selectConversation(next)
            }
        }
    }

    fun setConversationsPinned(ids: Collection<Long>, pinned: Boolean) {
        ids.forEach { conversationStore.setPinned(it, pinned) }
        reloadConversations()
    }

    fun send(text: String, forcedSkillIds: List<String> = emptyList(), workspaceFiles: List<WorkspaceFileReference> = emptyList()) {
        val uploads = pendingUploads.toList()
        if (text.isBlank() && uploads.isEmpty() && workspaceFiles.isEmpty()) return
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: createPersistedConversation()
        conversationStore.conversation(conversationId)?.let { workspaceManager.setActiveWorkspaceUri(it.workspaceUri) }
        if (jobs[conversationId]?.isActive == true) return
        val isFirstUserMessage = conversationStore.messages(conversationId).none { it.role == "user" }
        val profile = currentProfile()
        val model = activeModel.value.ifBlank { profile.selectedModel }
        val userInput = composeUserInput(text, uploads, workspaceFiles)
        val titleBeforeSend = activeConversation()?.title
        val provisionalTitle = if (titleBeforeSend == appContext.getString(R.string.default_conversation_title)) {
            fallbackConversationTitle(userInput)
        } else {
            null
        }
        val topicTitleGuard = provisionalTitle ?: titleBeforeSend
        conversationStore.setConversationMeta(
            conversationId,
            title = provisionalTitle,
            status = ConversationStore.STATUS_RUNNING,
            profileId = profile.id,
            model = model,
        )
        conversationStore.addMessage(conversationId, "user", userInput, profileId = profile.id, model = model)
        reloadMessages()
        reloadConversations()
        pendingUploads.clear()
        uploadingStatus.value = ""
        if (isFirstUserMessage) {
            val topicProfile = settings.topicSummaryProfile()
            val topicModel = settings.topicSummaryModel.ifBlank { topicProfile.selectedModel }
            val topicInput = text.trim().ifBlank { fallbackConversationTitle(userInput) }
            scope.launch {
                runCatching { agent.summarizeConversationTopic(topicProfile, topicModel, topicInput) }
                    .onSuccess { title ->
                        val current = conversationStore.conversation(conversationId)
                        if (current != null && current.title == topicTitleGuard) {
                            conversationStore.setConversationMeta(conversationId, title = title)
                            reloadConversations()
                        }
                    }
            }
        }
        jobs[conversationId] = scope.launch {
            status.value = appContext.getString(R.string.status_running)
            agent.chat(conversationId, userInput, profile, model, userMessagePersisted = true, forcedSkillIds = forcedSkillIds) {
                withContext(Dispatchers.Main) {
                    applyChatUpdate(it)
                    status.value = it.status
                }
            }
            reloadMessages()
            reloadConversations()
            markConversationFinished(conversationId)
        }
    }

    fun stopActive() {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        jobs.remove(conversationId)?.cancel()
        conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_INTERRUPTED)
        pendingToolApproval.value?.takeIf { it.request.conversationId == conversationId }?.let { pending ->
            approvalWaiters.remove(pending.id)?.complete(
                ToolApprovalDecision(approved = false, feedback = appContext.getString(R.string.label_user_interrupted)),
            )
            pendingToolApproval.value = null
        }
        reloadConversations()
        reloadMessages()
        status.value = appContext.getString(R.string.status_interrupted)
    }

    fun continueActive() {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        if (jobs[conversationId]?.isActive == true) return
        conversationStore.conversation(conversationId)?.let { workspaceManager.setActiveWorkspaceUri(it.workspaceUri) }
        val profile = currentProfile()
        val model = activeModel.value.ifBlank { profile.selectedModel }
        jobs[conversationId] = scope.launch {
            status.value = appContext.getString(R.string.status_continue)
            agent.continueConversation(conversationId, profile, model) {
                withContext(Dispatchers.Main) {
                    applyChatUpdate(it)
                    status.value = it.status
                }
            }
            reloadMessages()
            reloadConversations()
            markConversationFinished(conversationId)
        }
    }

    fun editAndRegenerateUserMessage(messageId: Long, newContent: String) {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        if (jobs[conversationId]?.isActive == true) return
        val message = conversationStore.message(messageId) ?: return
        if (message.conversationId != conversationId || message.role != "user") return
        conversationStore.conversation(conversationId)?.let { workspaceManager.setActiveWorkspaceUri(it.workspaceUri) }
        val content = newContent.trim().ifBlank { message.content }
        conversationStore.updateMessage(messageId, content = content, thinking = "")
        conversationStore.deleteMessagesAfter(conversationId, messageId)
        reloadMessages()
        reloadConversations()
        val profile = currentProfile()
        val model = activeModel.value.ifBlank { profile.selectedModel }
        jobs[conversationId] = scope.launch {
            status.value = appContext.getString(R.string.status_regenerate)
            agent.continueConversation(conversationId, profile, model) {
                withContext(Dispatchers.Main) {
                    applyChatUpdate(it)
                    status.value = it.status
                }
            }
            reloadMessages()
            reloadConversations()
            markConversationFinished(conversationId)
        }
    }

    private fun markConversationFinished(conversationId: Long) {
        status.value = appContext.getString(R.string.status_done)
        scope.launch {
            delay(2400L)
            if (activeConversationId.value == conversationId && jobs[conversationId]?.isActive != true && status.value == appContext.getString(R.string.status_done)) {
                status.value = ""
            }
        }
    }

    fun attachUploadedFile(uri: Uri) {
        scope.launch {
            uploadingStatus.value = appContext.getString(R.string.status_reading_upload)
            val result = withContext(Dispatchers.IO) { uploadedFileManager.readText(uri) }
            result.fold(
                onSuccess = { file ->
                    pendingUploads += file
                    uploadingStatus.value = appContext.getString(R.string.status_uploaded, file.name)
                },
                onFailure = { uploadingStatus.value = it.message.orEmpty() },
            )
        }
    }

    fun attachCapturedImage(bitmap: Bitmap) {
        scope.launch {
            uploadingStatus.value = appContext.getString(R.string.status_processing_photo)
            val result = withContext(Dispatchers.IO) { uploadedFileManager.saveCapturedImage(bitmap) }
            result.fold(
                onSuccess = { file ->
                    pendingUploads += file
                    uploadingStatus.value = appContext.getString(R.string.status_uploaded, file.name)
                },
                onFailure = { uploadingStatus.value = it.message.orEmpty() },
            )
        }
    }

    fun removePendingUpload(index: Int) {
        pendingUploads.getOrNull(index) ?: return
        pendingUploads.removeAt(index)
        uploadingStatus.value = if (pendingUploads.isEmpty()) "" else appContext.getString(R.string.label_pending_attachments, pendingUploads.size)
    }

    private fun composeUserInput(text: String, uploads: List<UploadedFile>, workspaceFiles: List<WorkspaceFileReference> = emptyList()): String {
        return buildString {
            val cleanText = text.trim()
            if (cleanText.isNotBlank()) append(cleanText)
            workspaceFiles.distinctBy { it.relativePath }.take(24).takeIf { it.isNotEmpty() }?.let { files ->
                if (isNotBlank()) append("\n\n")
                append(workspaceReferenceMarker(files))
            }
            uploads.forEach { file ->
                if (isNotBlank()) append("\n\n")
                append(uploadedAttachmentMarker(file))
            }
        }
    }


    private fun workspaceReferenceMarker(files: List<WorkspaceFileReference>): String {
        val payload = JSONObject()
            .put("instruction", "优先读取并处理这些用户明确选中的工作区文件；路径均为工作区相对路径。")
            .put("files", org.json.JSONArray().also { array ->
                files.forEach { file ->
                    array.put(JSONObject().put("name", file.name).put("path", file.relativePath).put("size", file.size))
                }
            })
        return "$WORKSPACE_REFERENCE_MARKER_START$payload$WORKSPACE_REFERENCE_MARKER_END"
    }

    private fun uploadedAttachmentMarker(file: UploadedFile): String {
        val payload = JSONObject()
            .put("name", file.name)
            .put("kind", file.mediaKind)
            .put("mime_type", file.mimeType)
            .put("size", file.size)
            .put("uri", file.uri)
        if (file.mediaKind == "text") {
            payload.put("text", file.content)
        } else if (file.content.startsWith("data:", ignoreCase = true)) {
            payload.put("data_url", file.content)
        }
        return "$ATTACHMENT_MARKER_START$payload$ATTACHMENT_MARKER_END"
    }

    private companion object {
        const val ATTACHMENT_MARKER_START = "<lyra_attachment_v1>"
        const val ATTACHMENT_MARKER_END = "</lyra_attachment_v1>"
        const val WORKSPACE_REFERENCE_MARKER_START = "<lyra_workspace_refs_v1>"
        const val WORKSPACE_REFERENCE_MARKER_END = "</lyra_workspace_refs_v1>"
    }
    private fun fallbackConversationTitle(userInput: String): String {
        val markerRegex = Regex("<lyra_attachment_v1>([\\s\\S]*?)</lyra_attachment_v1>")
        val workspaceRegex = Regex("<lyra_workspace_refs_v1>([\\s\\S]*?)</lyra_workspace_refs_v1>")
        val workspaceTitle = workspaceRegex.find(userInput)?.let { match ->
            runCatching { JSONObject(match.groupValues[1]).optJSONArray("files")?.optJSONObject(0)?.optString("name") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { "@$it" }
        }
        val attachmentTitle = markerRegex.find(userInput)?.let { match ->
            runCatching { JSONObject(match.groupValues[1]).optString("name") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { "上传附件：$it" }
        }
        return workspaceRegex.replace(markerRegex.replace(userInput, ""), "")
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(36)
            .ifBlank { workspaceTitle ?: attachmentTitle ?: appContext.getString(R.string.default_conversation_title) }
    }

    fun fetchModels(onDone: (Result<List<String>>) -> Unit) {
        val profile = currentProfile()
        scope.launch {
            status.value = appContext.getString(R.string.status_fetching_models)
            val result = withContext(Dispatchers.IO) { agent.fetchModels(profile) }
            result.onSuccess { models ->
                val updated = profiles.map {
                    if (it.id == profile.id) it.copy(
                        savedModels = (it.savedModels + models).distinct(),
                    ) else it
                }
                saveProfiles(updated, profile.id)
            }
            status.value = ""
            onDone(result)
        }
    }


    fun checkReachabilityForProfile(profile: ApiProfile, models: List<String>, onDone: (Result<ProviderReachabilityReport>) -> Unit) {
        scope.launch {
            status.value = appContext.getString(R.string.status_checking_reachability)
            val result = withContext(Dispatchers.IO) {
                runCatching { agent.checkReachability(profile, models) }
            }
            status.value = ""
            onDone(result)
        }
    }

    fun checkReachabilityForProfileIncremental(
        profile: ApiProfile,
        models: List<String>,
        onProviderResult: (ProviderReachabilityResult) -> Unit,
        onModelChecking: (String) -> Unit = {},
        onModelResult: (ModelReachabilityResult) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ) {
        scope.launch {
            status.value = appContext.getString(R.string.status_checking_reachability)
            val result = runCatching {
                val targets = models
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(profile.selectedModel) }
                    .distinct()
                val provider = withContext(Dispatchers.IO) { agent.checkProviderReachability(profile) }
                onProviderResult(provider)
                targets.forEach { model ->
                    onModelChecking(model)
                    val modelResult = withContext(Dispatchers.IO) { agent.checkModelReachability(profile, model) }
                    onModelResult(modelResult)
                }
            }
            status.value = ""
            onDone(result)
        }
    }

    fun fetchModelsForProfile(profile: ApiProfile, onDone: (Result<List<String>>) -> Unit) {
        scope.launch {
            status.value = appContext.getString(R.string.status_fetching_models)
            val result = withContext(Dispatchers.IO) { agent.fetchModels(profile) }
            status.value = ""
            onDone(result)
        }
    }

    fun reloadConversations() {
        conversations.clear()
        conversations.addAll(conversationStore.conversations(ConversationStore.MODE_NORMAL))
        archivedConversations.clear()
        archivedConversations.addAll(conversationStore.conversations(ConversationStore.MODE_NORMAL, archived = true))
        val active = activeConversationId.value
        if (active > 0 && conversations.none { it.id == active }) {
            val next = conversations.firstOrNull()?.id
            if (next == null) {
                activeConversationId.value = 0L
                _messages.value = emptyList()
                workspaceManager.setActiveWorkspaceUri("")
            } else {
                selectConversation(next)
            }
        }
    }

    fun reloadMessages() {
        val id = activeConversationId.value
        _messages.value = if (id <= 0) {
            emptyList()
        } else {
            enrichToolRecords(conversationStore.messages(id).map { it.toRecord() })
        }
        lastMessageReloadAt = System.currentTimeMillis()
    }

    private fun enrichToolRecords(records: List<ChatRecord>): List<ChatRecord> {
        val calls = mutableMapOf<String, Pair<String, String>>()
        return records.map { record ->
            if (record.role == "assistant") {
                runCatching { JSONObject(record.rawJson.orEmpty()) }.getOrNull()
                    ?.optJSONArray("tool_calls")
                    ?.let { array ->
                        for (index in 0 until array.length()) {
                            val call = array.optJSONObject(index) ?: continue
                            val id = call.optString("id")
                            val function = call.optJSONObject("function") ?: continue
                            if (id.isNotBlank()) {
                                calls[id] = function.optString("name") to prettyToolJson(function.optString("arguments"))
                            }
                        }
                    }
                record
            } else if (record.role == "tool") {
                val details = calls[record.toolCallId]
                record.copy(toolName = details?.first.orEmpty(), toolInput = details?.second.orEmpty())
            } else {
                record
            }
        }
    }

    private fun prettyToolJson(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "{}"
        return runCatching { JSONObject(trimmed).toString(2) }
            .recoverCatching { org.json.JSONArray(trimmed).toString(2) }
            .getOrDefault(trimmed)
    }
    fun reloadTodos() {
        todoItems.clear()
        todoItems.addAll(todoByConversation[activeConversationId.value].orEmpty())
    }

    fun isActiveConversationRunning(): Boolean = jobs[activeConversationId.value]?.isActive == true

    fun activeConversation(): Conversation? = conversationStore.conversation(activeConversationId.value)

    private fun isCurrentConversationBlank(): Boolean {
        val id = activeConversationId.value.takeIf { it > 0 } ?: return true
        return !conversationHasUserMessage(id)
    }

    private fun conversationHasUserMessage(id: Long): Boolean {
        return conversationStore.messages(id).any { it.role == "user" }
    }

    fun answerToolApproval(approved: Boolean, rememberForConversation: Boolean, feedback: String) {
        val pending = pendingToolApproval.value ?: return
        approvalWaiters.remove(pending.id)?.complete(
            ToolApprovalDecision(
                approved = approved,
                rememberForConversation = rememberForConversation,
                feedback = feedback.trim(),
            ),
        )
        if (approved && rememberForConversation) {
            autoApprovedConversations += pending.request.conversationId
        }
        pendingToolApproval.value = null
        status.value = if (approved) appContext.getString(R.string.status_approved_tool) else appContext.getString(R.string.status_rejected_tool)
    }

    private fun currentProfile(): ApiProfile {
        return profiles.firstOrNull { it.id == activeProfileId.value } ?: profiles.first()
    }

    private fun reloadMessagesThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastMessageReloadAt < 180L) return
        reloadMessages()
    }

    private fun applyChatUpdate(update: ChatUpdate) {
        if (update.messageId <= 0L) {
            reloadMessagesThrottled()
            return
        }
        val current = _messages.value
        val index = current.indexOfFirst { it.id == update.messageId }
        if (index < 0) {
            reloadMessages()
            return
        }
        val updated = current[index].copy(
            content = update.content,
            thinking = update.thinking,
            tokensPerSecond = update.tokensPerSecond.takeIf { value -> value > 0.0 } ?: current[index].tokensPerSecond,
        )
        _messages.value = current.toMutableList().also { it[index] = updated }
        lastMessageReloadAt = System.currentTimeMillis()
        if (update.status.startsWith("工具完成")) {
            reloadConversations()
            reloadMessages()
        }
    }

    private suspend fun requestToolApproval(request: ToolApprovalRequest): ToolApprovalDecision {
        if (request.conversationId in autoApprovedConversations) return ToolApprovalDecision.Approved
        return withContext(Dispatchers.Main) {
            val id = ++approvalId
            val waiter = CompletableDeferred<ToolApprovalDecision>()
            approvalWaiters[id] = waiter
            pendingToolApproval.value = PendingToolApproval(id, request)
            status.value = appContext.getString(R.string.status_waiting_confirm, request.toolName)
            waiter
        }.await()
    }

    private suspend fun setTodos(conversationId: Long, items: List<TodoItem>): String = withContext(Dispatchers.Main) {
        val normalized = items.ifEmpty { listOf(TodoItem("1", appContext.getString(R.string.todo_default_task), "pending")) }
            .mapIndexed { index, item ->
                item.copy(
                    id = item.id.ifBlank { (index + 1).toString() },
                    status = item.status.ifBlank { "pending" },
                )
            }
            .toMutableList()
        todoByConversation[conversationId] = normalized
        if (activeConversationId.value == conversationId) reloadTodos()
        appContext.getString(R.string.todo_list_set, normalized.size)
    }

    private suspend fun updateTodo(conversationId: Long, id: String, status: String, note: String): String = withContext(Dispatchers.Main) {
        val list = todoByConversation.getOrPut(conversationId) { mutableListOf() }
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(
                status = status.ifBlank { list[index].status },
                note = note.ifBlank { list[index].note },
            )
        } else {
            list += TodoItem(id.ifBlank { (list.size + 1).toString() }, note.ifBlank { appContext.getString(R.string.todo_item_default_name) }, status.ifBlank { appContext.getString(R.string.todo_status_completed) })
        }
        if (activeConversationId.value == conversationId) reloadTodos()
        appContext.getString(R.string.todo_marked_as, id.ifBlank { list.last().id }, status.ifBlank { appContext.getString(R.string.todo_status_completed) })
    }

    private fun markAbandonedRunsInterrupted() {
        conversationStore.conversations(archived = null)
            .filter { it.status == ConversationStore.STATUS_RUNNING }
            .forEach { conversationStore.setConversationMeta(it.id, status = ConversationStore.STATUS_INTERRUPTED) }
    }
}
