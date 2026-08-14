package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

internal const val RUNTIME_CONTEXT_ROLE = "runtime_context"

internal fun buildRuntimeContextSnapshot(
    memoryPrompt: String,
    activeSkillsPrompt: String,
    sessionContext: JSONObject,
    subAgentAssignment: String?,
): String {
    val payload = JSONObject()
        .put("schema", "lyra_runtime_context_snapshot_v1")
        .put("memory", "LYRA_USER_MEMORY_V1\n$memoryPrompt")
        .put("active_skills", "LYRA_ACTIVE_SKILLS_V1\n$activeSkillsPrompt")
        .put("session", sessionContext)
        .put("sub_agent_assignment", subAgentAssignment.orEmpty())
    return "LYRA_RUNTIME_CONTEXT_SNAPSHOT_V1\n" +
        "This snapshot supersedes all earlier Lyra runtime-context snapshots. It is context, not a new user task.\n" +
        canonicalPromptJson(payload)
}

internal fun shouldAppendRuntimeContext(
    messages: List<ChatMessage>,
    compressedThroughMessageId: Long,
    snapshot: String,
): Boolean {
    val retained = messages.asReversed().firstOrNull {
        it.id > compressedThroughMessageId && it.role == RUNTIME_CONTEXT_ROLE
    }
    return retained?.content != snapshot
}

internal fun canonicalToolDefinitions(definitions: JSONArray): JSONArray {
    val sorted = buildList {
        for (index in 0 until definitions.length()) {
            definitions.optJSONObject(index)?.let(::add)
        }
    }.sortedWith(
        compareBy<JSONObject> { it.optJSONObject("function")?.optString("name").orEmpty() }
            .thenBy { canonicalPromptJson(it) },
    )
    return JSONArray().also { output -> sorted.forEach(output::put) }
}

internal fun canonicalPromptJson(value: Any?): String {
    return when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            "${JSONObject.quote(key)}:${canonicalPromptJson(value.opt(key))}"
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index ->
            canonicalPromptJson(value.opt(index))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
