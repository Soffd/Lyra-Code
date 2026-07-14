package com.yukisoffd.lyracode.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yukisoffd.lyracode.R
import org.json.JSONArray
import org.json.JSONObject

data class Conversation(
    val id: Long,
    val title: String,
    val status: String,
    val profileId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedAt: Long,
    val mode: String = ConversationStore.MODE_NORMAL,
    val workspaceUri: String = "",
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val thinking: String,
    val profileId: String,
    val model: String,
    val toolCallId: String?,
    val rawJson: String?,
    val tokensPerSecond: Double = 0.0,
    val createdAt: Long,
)

data class UsageMessageEvent(
    val messageId: Long,
    val conversationId: Long,
    val role: String,
    val createdAt: Long,
)

data class UsageModelRequest(
    val assistantMessageId: Long,
    val conversationId: Long,
    val createdAt: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)

class ConversationStore(private val appContext: Context) : SQLiteOpenHelper(
    appContext.applicationContext,
    "lyra_conversations.db",
    null,
    7,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                profile_id TEXT NOT NULL,
                model TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned_at INTEGER NOT NULL DEFAULT 0,
                mode TEXT NOT NULL DEFAULT 'normal',
                workspace_uri TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                thinking TEXT NOT NULL,
                profile_id TEXT NOT NULL,
                model TEXT NOT NULL,
                tool_call_id TEXT,
                raw_json TEXT,
                tokens_per_second REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createUsageTables(db)
    }

    private fun createUsageTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usage_message_events (
                message_id INTEGER PRIMARY KEY,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usage_model_requests (
                assistant_message_id INTEGER PRIMARY KEY,
                conversation_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                input_tokens INTEGER NOT NULL,
                output_tokens INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_message_events_created_at ON usage_message_events(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_model_requests_created_at ON usage_model_requests(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN pinned_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN mode TEXT NOT NULL DEFAULT 'normal'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN roleplay_id TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN workspace_uri TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE messages ADD COLUMN tokens_per_second REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            createUsageTables(db)
            db.execSQL(
                """
                INSERT OR IGNORE INTO usage_message_events (message_id, conversation_id, role, created_at)
                SELECT id, conversation_id, role, created_at FROM messages
                """.trimIndent(),
            )
        }
        if (oldVersion < 7) {
            db.execSQL("DELETE FROM usage_message_events WHERE conversation_id IN (SELECT id FROM conversations WHERE mode = 'roleplay')")
            db.execSQL("DELETE FROM usage_model_requests WHERE conversation_id IN (SELECT id FROM conversations WHERE mode = 'roleplay')")
            db.execSQL("DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE mode = 'roleplay')")
            db.execSQL("DELETE FROM conversations WHERE mode = 'roleplay'")
            db.execSQL(
                """
                CREATE TABLE conversations_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    profile_id TEXT NOT NULL,
                    model TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    pinned_at INTEGER NOT NULL DEFAULT 0,
                    mode TEXT NOT NULL DEFAULT 'normal',
                    workspace_uri TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO conversations_new (id, title, status, profile_id, model, created_at, updated_at, pinned_at, mode, workspace_uri)
                SELECT id, title, status, profile_id, model, created_at, updated_at, pinned_at, mode, workspace_uri
                FROM conversations
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE conversations")
            db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
        }
    }

    fun createConversation(
        profileId: String,
        model: String,
        title: String = appContext.getString(R.string.default_conversation_title),
        mode: String = MODE_NORMAL,
        workspaceUri: String = "",
    ): Long {
        val now = System.currentTimeMillis()
        return writableDatabase.insert(
            "conversations",
            null,
            ContentValues().apply {
                put("title", title)
                put("status", STATUS_IDLE)
                put("profile_id", profileId)
                put("model", model)
                put("created_at", now)
                put("updated_at", now)
                put("mode", mode)
                put("workspace_uri", workspaceUri)
            },
        )
    }

    private fun createImportedConversation(
        profileId: String,
        model: String,
        title: String,
        status: String,
        createdAt: Long,
        updatedAt: Long,
        pinnedAt: Long,
        mode: String,
        workspaceUri: String,
    ): Long {
        return writableDatabase.insert(
            "conversations",
            null,
            ContentValues().apply {
                put("title", title.take(120))
                put("status", status)
                put("profile_id", profileId)
                put("model", model)
                put("created_at", createdAt)
                put("updated_at", updatedAt)
                put("pinned_at", pinnedAt)
                put("mode", mode)
                put("workspace_uri", workspaceUri)
            },
        )
    }

    fun conversations(mode: String? = null, includeSubAgents: Boolean = false): List<Conversation> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        mode?.let {
            clauses += "mode=?"
            args += it
        }
        if (mode == null && !includeSubAgents) {
            clauses += "mode<>?"
            args += MODE_SUBAGENT
        }
        val cursor = readableDatabase.query(
            "conversations",
            arrayOf("id", "title", "status", "profile_id", "model", "created_at", "updated_at", "pinned_at", "mode", "workspace_uri"),
            clauses.joinToString(" AND ").ifBlank { null },
            args.toTypedArray().ifEmpty { null },
            null,
            null,
            "CASE WHEN pinned_at > 0 THEN 0 ELSE 1 END ASC, pinned_at DESC, updated_at DESC",
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Conversation(
                            id = it.getLong(0),
                            title = it.getString(1),
                            status = it.getString(2),
                            profileId = it.getString(3),
                            model = it.getString(4),
                            createdAt = it.getLong(5),
                            updatedAt = it.getLong(6),
                            pinnedAt = it.getLong(7),
                            mode = it.getString(8),
                            workspaceUri = it.getString(9),
                        ),
                    )
                }
            }
        }
    }

    fun conversation(id: Long): Conversation? {
        return readableDatabase.query(
            "conversations",
            arrayOf("id", "title", "status", "profile_id", "model", "created_at", "updated_at", "pinned_at", "mode", "workspace_uri"),
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use {
            if (!it.moveToFirst()) return null
            Conversation(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getLong(5), it.getLong(6), it.getLong(7), it.getString(8), it.getString(9))
        }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        writableDatabase.update(
            "conversations",
            ContentValues().apply { put("pinned_at", if (pinned) System.currentTimeMillis() else 0L) },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    fun deleteConversation(id: Long) {
        writableDatabase.delete("messages", "conversation_id=?", arrayOf(id.toString()))
        writableDatabase.delete("conversations", "id=?", arrayOf(id.toString()))
    }


    fun setConversationMeta(id: Long, title: String? = null, status: String? = null, profileId: String? = null, model: String? = null, workspaceUri: String? = null) {
        val values = ContentValues().apply {
            title?.let { put("title", it.take(120)) }
            status?.let { put("status", it) }
            profileId?.let { put("profile_id", it) }
            model?.let { put("model", it) }
            workspaceUri?.let { put("workspace_uri", it) }
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("conversations", values, "id=?", arrayOf(id.toString()))
    }

    fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        thinking: String = "",
        profileId: String = "",
        model: String = "",
        toolCallId: String? = null,
        rawJson: String? = null,
        tokensPerSecond: Double = 0.0,
    ): Long {
        val now = System.currentTimeMillis()
        val id = writableDatabase.insert(
            "messages",
            null,
            ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", role)
                put("content", content)
                put("thinking", thinking)
                put("profile_id", profileId)
                put("model", model)
                put("tool_call_id", toolCallId)
                put("raw_json", rawJson)
                put("tokens_per_second", tokensPerSecond.coerceAtLeast(0.0))
                put("created_at", now)
            },
        )
        setConversationMeta(conversationId)
        recordUsageMessageEvent(id, conversationId, role, now)
        return id
    }

    private fun addImportedMessage(
        conversationId: Long,
        role: String,
        content: String,
        thinking: String = "",
        profileId: String = "",
        model: String = "",
        toolCallId: String? = null,
        rawJson: String? = null,
        tokensPerSecond: Double = 0.0,
        createdAt: Long,
    ): Long {
        val id = writableDatabase.insert(
            "messages",
            null,
            ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", role)
                put("content", content)
                put("thinking", thinking)
                put("profile_id", profileId)
                put("model", model)
                put("tool_call_id", toolCallId)
                put("raw_json", rawJson)
                put("tokens_per_second", tokensPerSecond.coerceAtLeast(0.0))
                put("created_at", createdAt)
            },
        )
        recordUsageMessageEvent(id, conversationId, role, createdAt)
        return id
    }

    fun updateMessage(id: Long, content: String? = null, thinking: String? = null, rawJson: String? = null, tokensPerSecond: Double? = null) {
        val values = ContentValues().apply {
            content?.let { put("content", it) }
            thinking?.let { put("thinking", it) }
            rawJson?.let { put("raw_json", it) }
            tokensPerSecond?.let { put("tokens_per_second", it.coerceAtLeast(0.0)) }
        }
        writableDatabase.update("messages", values, "id=?", arrayOf(id.toString()))
        val conversationId = readableDatabase.query("messages", arrayOf("conversation_id"), "id=?", arrayOf(id.toString()), null, null, null).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
        conversationId?.let { setConversationMeta(it) }
    }

    fun message(id: Long): ChatMessage? {
        return readableDatabase.query(
            "messages",
            arrayOf("id", "conversation_id", "role", "content", "thinking", "profile_id", "model", "tool_call_id", "raw_json", "tokens_per_second", "created_at"),
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use {
            if (!it.moveToFirst()) return null
            ChatMessage(
                id = it.getLong(0),
                conversationId = it.getLong(1),
                role = it.getString(2),
                content = it.getString(3),
                thinking = it.getString(4),
                profileId = it.getString(5),
                model = it.getString(6),
                toolCallId = if (it.isNull(7)) null else it.getString(7),
                rawJson = if (it.isNull(8)) null else it.getString(8),
                tokensPerSecond = it.getDouble(9),
                createdAt = it.getLong(10),
            )
        }
    }

    fun deleteMessagesAfter(conversationId: Long, messageId: Long) {
        writableDatabase.delete(
            "messages",
            "conversation_id=? AND id>?",
            arrayOf(conversationId.toString(), messageId.toString()),
        )
        setConversationMeta(conversationId)
    }

    fun recordUsageModelRequest(assistantMessageId: Long, inputTokens: Long, outputTokens: Long) {
        val message = message(assistantMessageId) ?: return
        if (message.role.lowercase() != "assistant") return
        writableDatabase.insertWithOnConflict(
            "usage_model_requests",
            null,
            ContentValues().apply {
                put("assistant_message_id", assistantMessageId)
                put("conversation_id", message.conversationId)
                put("created_at", message.createdAt)
                put("input_tokens", inputTokens.coerceAtLeast(0L))
                put("output_tokens", outputTokens.coerceAtLeast(0L))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun usageMessageEvents(startAt: Long, endAt: Long): List<UsageMessageEvent> {
        return readableDatabase.query(
            "usage_message_events",
            arrayOf("message_id", "conversation_id", "role", "created_at"),
            "created_at>=? AND created_at<?",
            arrayOf(startAt.toString(), endAt.toString()),
            null,
            null,
            "created_at ASC, message_id ASC",
        ).use {
            buildList {
                while (it.moveToNext()) {
                    add(UsageMessageEvent(it.getLong(0), it.getLong(1), it.getString(2), it.getLong(3)))
                }
            }
        }
    }

    fun usageModelRequests(startAt: Long, endAt: Long): List<UsageModelRequest> {
        return readableDatabase.query(
            "usage_model_requests",
            arrayOf("assistant_message_id", "conversation_id", "created_at", "input_tokens", "output_tokens"),
            "created_at>=? AND created_at<?",
            arrayOf(startAt.toString(), endAt.toString()),
            null,
            null,
            "created_at ASC, assistant_message_id ASC",
        ).use {
            buildList {
                while (it.moveToNext()) {
                    add(UsageModelRequest(it.getLong(0), it.getLong(1), it.getLong(2), it.getLong(3), it.getLong(4)))
                }
            }
        }
    }

    fun usageModelRequestMessageIds(): Set<Long> {
        return readableDatabase.query(
            "usage_model_requests",
            arrayOf("assistant_message_id"),
            null,
            null,
            null,
            null,
            null,
        ).use {
            buildSet {
                while (it.moveToNext()) add(it.getLong(0))
            }
        }
    }

    private fun recordUsageMessageEvent(messageId: Long, conversationId: Long, role: String, createdAt: Long) {
        writableDatabase.insertWithOnConflict(
            "usage_message_events",
            null,
            ContentValues().apply {
                put("message_id", messageId)
                put("conversation_id", conversationId)
                put("role", role)
                put("created_at", createdAt)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun messages(conversationId: Long): List<ChatMessage> {
        val cursor = readableDatabase.query(
            "messages",
            arrayOf("id", "conversation_id", "role", "content", "thinking", "profile_id", "model", "tool_call_id", "raw_json", "tokens_per_second", "created_at"),
            "conversation_id=?",
            arrayOf(conversationId.toString()),
            null,
            null,
            "id ASC",
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        ChatMessage(
                            id = it.getLong(0),
                            conversationId = it.getLong(1),
                            role = it.getString(2),
                            content = it.getString(3),
                            thinking = it.getString(4),
                            profileId = it.getString(5),
                            model = it.getString(6),
                            toolCallId = if (it.isNull(7)) null else it.getString(7),
                            rawJson = if (it.isNull(8)) null else it.getString(8),
                            tokensPerSecond = it.getDouble(9),
                            createdAt = it.getLong(10),
                        ),
                    )
                }
            }
        }
    }

    fun exportJson(): JSONObject {
        return JSONObject()
            .put("schema", "lyra_conversations_backup_v1")
            .put("conversations", JSONArray().also { array ->
                conversations().forEach { conversation ->
                    array.put(
                        JSONObject()
                            .put("id", conversation.id)
                            .put("title", conversation.title)
                            .put("status", conversation.status)
                            .put("profileId", conversation.profileId)
                            .put("model", conversation.model)
                            .put("createdAt", conversation.createdAt)
                            .put("updatedAt", conversation.updatedAt)
                            .put("pinnedAt", conversation.pinnedAt)
                            .put("mode", conversation.mode)
                            .put("workspaceUri", conversation.workspaceUri)
                            .put("messages", JSONArray().also { messages ->
                                messages(conversation.id).forEach { message ->
                                    messages.put(
                                        JSONObject()
                                            .put("role", message.role)
                                            .put("content", message.content)
                                            .put("thinking", message.thinking)
                                            .put("profileId", message.profileId)
                                            .put("model", message.model)
                                            .put("toolCallId", message.toolCallId)
                                            .put("rawJson", message.rawJson)
                                            .put("tokensPerSecond", message.tokensPerSecond)
                                            .put("createdAt", message.createdAt),
                                    )
                                }
                            }),
                    )
                }
            })
    }

    fun importJson(root: JSONObject, mode: String): String {
        val array = root.optJSONArray("conversations") ?: return appContext.getString(R.string.notice_no_compatible_data)
        if (mode == "replace") {
            writableDatabase.delete("messages", null, null)
            writableDatabase.delete("conversations", null, null)
        }
        var importedConversations = 0
        var importedMessages = 0
        var skippedConversations = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val importedMode = item.optString("mode").ifBlank { MODE_NORMAL }
            if (importedMode == "roleplay") {
                skippedConversations++
                continue
            }
            val title = item.optString("title").ifBlank { appContext.getString(R.string.default_import_title) }
            val exportedCreatedAt = item.optLong("createdAt", System.currentTimeMillis())
            val exportedUpdatedAt = item.optLong("updatedAt", exportedCreatedAt)
            val messages = item.optJSONArray("messages") ?: JSONArray()
            if (mode != "replace" && importedConversationExists(
                    title = title,
                    profileId = item.optString("profileId"),
                    model = item.optString("model"),
                    createdAt = exportedCreatedAt,
                    updatedAt = exportedUpdatedAt,
                    mode = importedMode,
                    messages = messages,
                )
            ) {
                skippedConversations++
                continue
            }
            val conversationId = createImportedConversation(
                profileId = item.optString("profileId"),
                model = item.optString("model"),
                title = title,
                status = item.optString("status").ifBlank { STATUS_IDLE },
                createdAt = exportedCreatedAt,
                updatedAt = exportedUpdatedAt,
                pinnedAt = item.optLong("pinnedAt", 0L),
                mode = importedMode,
                workspaceUri = item.optString("workspaceUri"),
            )
            for (messageIndex in 0 until messages.length()) {
                val message = messages.optJSONObject(messageIndex) ?: continue
                addImportedMessage(
                    conversationId = conversationId,
                    role = message.optString("role"),
                    content = message.optString("content"),
                    thinking = message.optString("thinking"),
                    profileId = message.optString("profileId"),
                    model = message.optString("model"),
                    toolCallId = message.optString("toolCallId").ifBlank { null },
                    rawJson = message.optString("rawJson").ifBlank { null },
                    tokensPerSecond = message.optDouble("tokensPerSecond", 0.0),
                    createdAt = message.optLong("createdAt", exportedCreatedAt + messageIndex),
                )
                importedMessages++
            }
            importedConversations++
        }
        return buildString {
            append(appContext.getString(R.string.import_result_conversations, importedConversations, importedMessages))
            if (skippedConversations > 0) append(appContext.getString(R.string.import_result_skipped, skippedConversations))
        }
    }

    private fun importedConversationExists(
        title: String,
        profileId: String,
        model: String,
        createdAt: Long,
        updatedAt: Long,
        mode: String,
        messages: JSONArray,
    ): Boolean {
        val exportedSignature = importedMessagesSignature(messages)
        return readableDatabase.query(
            "conversations",
            arrayOf("id"),
            "title=? AND profile_id=? AND model=? AND created_at=? AND updated_at=? AND mode=?",
            arrayOf(title.take(120), profileId, model, createdAt.toString(), updatedAt.toString(), mode),
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (storedMessagesSignature(cursor.getLong(0)) == exportedSignature) return@use true
            }
            false
        }
    }

    private fun importedMessagesSignature(messages: JSONArray): String {
        return buildString {
            append(messages.length()).append('|')
            for (index in 0 until messages.length()) {
                val message = messages.optJSONObject(index) ?: continue
                append(message.optString("role")).append('\u001F')
                append(message.optString("content")).append('\u001F')
                append(message.optString("thinking")).append('\u001F')
                append(message.optString("profileId")).append('\u001F')
                append(message.optString("model")).append('\u001F')
                append(message.optString("toolCallId")).append('\u001F')
                append(message.optString("rawJson")).append('\u001F')
                append(message.optLong("createdAt")).append('\u001E')
            }
        }
    }

    private fun storedMessagesSignature(conversationId: Long): String {
        return buildString {
            val rows = messages(conversationId)
            append(rows.size).append('|')
            rows.forEach { message ->
                append(message.role).append('\u001F')
                append(message.content).append('\u001F')
                append(message.thinking).append('\u001F')
                append(message.profileId).append('\u001F')
                append(message.model).append('\u001F')
                append(message.toolCallId.orEmpty()).append('\u001F')
                append(message.rawJson.orEmpty()).append('\u001F')
                append(message.createdAt).append('\u001E')
            }
        }
    }

    fun openAiMessages(conversationId: Long, excludeMessageId: Long? = null, maxMessages: Int = 40): JSONArray {
        val source = messages(conversationId).filter { it.id != excludeMessageId }
        val groups = mutableListOf<List<JSONObject>>()
        var index = 0
        while (index < source.size) {
            val message = source[index]
            if (message.role == "tool") {
                index++
                continue
            }

            val json = message.toOpenAiJson()
            if (json.hasToolCalls()) {
                val requiredToolIds = json.toolCallIds()
                val toolMessages = mutableListOf<JSONObject>()
                var next = index + 1
                while (next < source.size && source[next].role == "tool") {
                    val tool = source[next]
                    val toolCallId = tool.toolCallId.orEmpty()
                    if (toolCallId in requiredToolIds) {
                        toolMessages.add(tool.toToolJson())
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
        return groups.takeLastMessages(maxMessages)
    }

    private fun ChatMessage.toOpenAiJson(): JSONObject {
        val raw = rawJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.also { sanitizeAssistantRaw(it) }
        return raw ?: JSONObject()
            .put("role", role)
            .put("content", if (role == "assistant") cleanGeneratedText(content) else content)
            .apply {
                if (role == "assistant" && thinking.isNotBlank()) {
                    put("reasoning_content", cleanGeneratedText(thinking))
                }
            }
    }

    private fun ChatMessage.toToolJson(): JSONObject = JSONObject()
        .put("role", "tool")
        .put("tool_call_id", toolCallId)
        .put("content", content)

    private fun JSONObject.hasToolCalls(): Boolean {
        if (optString("role") != "assistant") return false
        return (optJSONArray("tool_calls")?.length() ?: 0) > 0
    }

    private fun JSONObject.toolCallIds(): Set<String> {
        val calls = optJSONArray("tool_calls") ?: return emptySet()
        return buildSet {
            for (index in 0 until calls.length()) {
                calls.optJSONObject(index)?.optString("id").orEmpty().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun List<List<JSONObject>>.takeLastMessages(maxMessages: Int): JSONArray {
        val selected = ArrayDeque<List<JSONObject>>()
        var count = 0
        asReversed().forEach { group ->
            if (selected.isNotEmpty() && count + group.size > maxMessages) return@forEach
            selected.addFirst(group)
            count += group.size
        }
        return JSONArray().apply {
            selected.forEach { group -> group.forEach { put(it) } }
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

    private fun cleanGeneratedText(text: String): String {
        return text.replace(Regex("(?:null){4,}", RegexOption.IGNORE_CASE), "").trim()
    }

    companion object {
        const val STATUS_IDLE = "idle"
        const val STATUS_RUNNING = "running"
        const val STATUS_INTERRUPTED = "interrupted"
        const val MODE_NORMAL = "normal"
        const val MODE_SUBAGENT = "subagent"
        const val MODE_TASK = "task"
    }
}
