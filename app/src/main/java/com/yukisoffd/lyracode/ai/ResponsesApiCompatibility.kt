package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.ProviderCatalog
import com.yukisoffd.lyracode.data.ApiProfile
import org.json.JSONArray
import org.json.JSONObject

internal const val RESPONSES_REPLAY_ITEMS_KEY = "_lyra_responses_replay_items"

/** DeepSeek's built-in web search is a server-side Responses API tool, not Lyra's web_search function. */
internal fun supportsDeepSeekNativeWebSearch(profile: ApiProfile): Boolean {
    return profile.useResponsesApi &&
        profile.apiFormat == ApiProfile.API_FORMAT_OPENAI &&
        (profile.presetId == "deepseek" || ProviderCatalog.match(profile)?.id == "deepseek")
}

internal fun buildResponsesToolDefinitions(chatTools: JSONArray, includeDeepSeekWebSearch: Boolean): JSONArray {
    return JSONArray().also { output ->
        for (index in 0 until chatTools.length()) {
            val function = chatTools.optJSONObject(index)?.optJSONObject("function") ?: continue
            output.put(
                JSONObject()
                    .put("type", "function")
                    .put("name", function.optString("name"))
                    .put("description", function.optString("description"))
                    .put("parameters", function.optJSONObject("parameters") ?: JSONObject())
                    .put("strict", false),
            )
        }
        if (includeDeepSeekWebSearch) {
            output.put(JSONObject().put("type", "web_search"))
        }
    }
}

internal fun responsesStreamEventType(payload: JSONObject, sseEventType: String): String {
    return payload.optString("type").ifBlank { sseEventType }
}

internal fun collectReplayableResponseItems(response: JSONObject, destination: JSONArray) {
    val output = response.optJSONArray("output") ?: return
    for (index in 0 until output.length()) {
        output.optJSONObject(index)?.let { collectReplayableResponseItem(it, destination) }
    }
}

internal fun collectReplayableResponseItem(item: JSONObject, destination: JSONArray) {
    val replayItem = when (item.optString("type")) {
        "web_search_call" -> JSONObject(item.toString())
        "reasoning" -> replayableReasoningItem(item) ?: return
        else -> return
    }
    val id = item.optString("id")
    for (index in 0 until destination.length()) {
        val existing = destination.optJSONObject(index) ?: continue
        if (id.isNotBlank() && existing.optString("id") == id) {
            destination.put(index, replayItem)
            return
        }
    }
    destination.put(replayItem)
}

internal fun appendReplayableResponseItems(
    rawAssistantMessage: JSONObject,
    destination: JSONArray,
    includeReasoningTextFallback: Boolean = false,
) {
    val replayItems = rawAssistantMessage.optJSONArray(RESPONSES_REPLAY_ITEMS_KEY) ?: JSONArray()
    val hasStoredReasoning = (0 until replayItems.length()).any { index ->
        replayItems.optJSONObject(index)?.optString("type") == "reasoning"
    }
    if (includeReasoningTextFallback && !hasStoredReasoning) {
        val reasoningText = rawAssistantMessage.optString("reasoning_content")
            .ifBlank { rawAssistantMessage.optString("thinking_content") }
        if (reasoningText.isNotBlank()) {
            destination.put(
                JSONObject()
                    .put("type", "reasoning")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "reasoning_text")
                                .put("text", reasoningText),
                        ),
                    ),
            )
        }
    }
    for (index in 0 until replayItems.length()) {
        val item = replayItems.optJSONObject(index) ?: continue
        if (item.optString("type") == "web_search_call" || item.optString("type") == "reasoning") {
            destination.put(JSONObject(item.toString()))
        }
    }
}

private fun replayableReasoningItem(item: JSONObject): JSONObject? {
    val content = item.optJSONArray("content") ?: return null
    val reasoningContent = JSONArray()
    for (index in 0 until content.length()) {
        val part = content.optJSONObject(index) ?: continue
        if (part.optString("type") == "reasoning_text" && part.optString("text").isNotBlank()) {
            reasoningContent.put(JSONObject(part.toString()))
        }
    }
    if (reasoningContent.length() == 0) return null
    return JSONObject()
        .put("type", "reasoning")
        .also { replay ->
            item.optString("id").takeIf { it.isNotBlank() }?.let { replay.put("id", it) }
        }
        .put("content", reasoningContent)
}

internal fun copyReplayableResponseItems(source: JSONObject, destination: JSONObject) {
    val replayItems = source.optJSONArray(RESPONSES_REPLAY_ITEMS_KEY) ?: return
    if (replayItems.length() > 0) {
        destination.put(RESPONSES_REPLAY_ITEMS_KEY, JSONArray(replayItems.toString()))
    }
}
