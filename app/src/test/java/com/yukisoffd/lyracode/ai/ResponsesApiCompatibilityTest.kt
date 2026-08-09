package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ApiProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsesApiCompatibilityTest {
    @Test
    fun deepSeekResponsesApiAddsNativeSearchWithoutReplacingLyraSearch() {
        val localWebSearch = JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", "web_search")
                    .put("description", "Lyra WebView search")
                    .put("parameters", JSONObject().put("type", "object")),
            )

        val tools = buildResponsesToolDefinitions(JSONArray().put(localWebSearch), includeDeepSeekWebSearch = true)

        assertEquals(2, tools.length())
        assertEquals("function", tools.getJSONObject(0).getString("type"))
        assertEquals("web_search", tools.getJSONObject(0).getString("name"))
        assertEquals("web_search", tools.getJSONObject(1).getString("type"))
        assertFalse(tools.getJSONObject(1).has("name"))
    }

    @Test
    fun nativeSearchIsLimitedToDeepSeekResponsesProfiles() {
        val deepSeekResponses = profile(baseUrl = "https://api.deepseek.com/v1", useResponsesApi = true)
        val deepSeekChat = deepSeekResponses.copy(useResponsesApi = false)
        val otherResponses = profile(baseUrl = "https://api.example.com/v1", useResponsesApi = true)

        assertTrue(supportsDeepSeekNativeWebSearch(deepSeekResponses))
        assertFalse(supportsDeepSeekNativeWebSearch(deepSeekChat))
        assertFalse(supportsDeepSeekNativeWebSearch(otherResponses))
    }

    @Test
    fun webSearchCallIsStoredExactlyOnceAndReplayed() {
        val searchCall = JSONObject()
            .put("type", "web_search_call")
            .put("id", "ws_123")
            .put("status", "completed")
            .put("action", JSONObject().put("type", "search").put("query", "DeepSeek V4"))
        val response = JSONObject().put("output", JSONArray().put(searchCall))
        val replayItems = JSONArray()

        collectReplayableResponseItem(searchCall, replayItems)
        collectReplayableResponseItems(response, replayItems)
        val raw = JSONObject().put(RESPONSES_REPLAY_ITEMS_KEY, replayItems)
        val nextInput = JSONArray()
        appendReplayableResponseItems(raw, nextInput)

        assertEquals(1, replayItems.length())
        assertEquals(1, nextInput.length())
        assertEquals(searchCall.toString(), nextInput.getJSONObject(0).toString())
    }

    @Test
    fun reasoningTextIsReplayedWithNativeSearchBeforeLocalToolContinuation() {
        val reasoning = JSONObject()
            .put("type", "reasoning")
            .put("id", "rs_123")
            .put("status", "completed")
            .put("summary", JSONArray())
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "reasoning_text")
                        .put("text", "I should search and then mark the sources."),
                ),
            )
        val searchCall = JSONObject()
            .put("type", "web_search_call")
            .put("id", "ws_123")
            .put("status", "completed")
        val completedResponse = JSONObject().put("output", JSONArray().put(reasoning).put(searchCall))
        val replayItems = JSONArray()

        collectReplayableResponseItems(completedResponse, replayItems)
        val raw = JSONObject().put(RESPONSES_REPLAY_ITEMS_KEY, replayItems)
        val nextInput = JSONArray()
        appendReplayableResponseItems(raw, nextInput)

        assertEquals(2, nextInput.length())
        val replayedReasoning = nextInput.getJSONObject(0)
        assertEquals("reasoning", replayedReasoning.getString("type"))
        assertEquals("rs_123", replayedReasoning.getString("id"))
        assertEquals(
            "I should search and then mark the sources.",
            replayedReasoning.getJSONArray("content").getJSONObject(0).getString("text"),
        )
        assertFalse(replayedReasoning.has("summary"))
        assertFalse(replayedReasoning.has("status"))
        assertEquals("web_search_call", nextInput.getJSONObject(1).getString("type"))
    }

    @Test
    fun legacyAssistantReasoningTextCanResumeAnInterruptedConversation() {
        val legacyRaw = JSONObject()
            .put("role", "assistant")
            .put("content", "")
            .put("reasoning_content", "I need to mark the sources returned by native search.")
            .put(
                RESPONSES_REPLAY_ITEMS_KEY,
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search_call")
                        .put("id", "ws_legacy")
                        .put("status", "completed"),
                ),
            )
        val nextInput = JSONArray()

        appendReplayableResponseItems(legacyRaw, nextInput, includeReasoningTextFallback = true)

        assertEquals(2, nextInput.length())
        assertEquals("reasoning", nextInput.getJSONObject(0).getString("type"))
        assertEquals(
            "I need to mark the sources returned by native search.",
            nextInput.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"),
        )
        assertEquals("web_search_call", nextInput.getJSONObject(1).getString("type"))
    }

    @Test
    fun streamEventTypeSupportsDeepSeekSseEventField() {
        assertEquals(
            "response.web_search_call.searching",
            responsesStreamEventType(JSONObject(), "response.web_search_call.searching"),
        )
        assertEquals(
            "response.output_text.delta",
            responsesStreamEventType(
                JSONObject().put("type", "response.output_text.delta"),
                "ignored.sse.event",
            ),
        )
    }

    private fun profile(baseUrl: String, useResponsesApi: Boolean) = ApiProfile(
        id = "test",
        name = "Test",
        apiKey = "key",
        baseUrl = baseUrl,
        selectedModel = "deepseek-v4-flash",
        savedModels = listOf("deepseek-v4-flash"),
        useResponsesApi = useResponsesApi,
    )
}
