package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ApiProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AiResponseCacheTest {
    private val profile = ApiProfile(
        id = "p1",
        name = "Test",
        apiKey = "secret",
        baseUrl = "https://example.com/v1",
        selectedModel = "test-model",
        savedModels = listOf("test-model"),
    )

    @Test
    fun cacheKeyNormalizesToolIdsAndReasoning() {
        val cache = AiResponseCache(tempDir())
        val first = request(toolId = "call_abc", toolMessageId = "call_abc", reasoning = "first reasoning")
        val second = request(toolId = "call_xyz", toolMessageId = "call_xyz", reasoning = "different reasoning")

        assertEquals(cache.cacheKey(profile, first), cache.cacheKey(profile, second))
    }

    @Test
    fun cacheKeyNormalizesToolOrder() {
        val cache = AiResponseCache(tempDir())
        val first = request(tools = JSONArray().put(tool("b_tool")).put(tool("a_tool")))
        val second = request(tools = JSONArray().put(tool("a_tool")).put(tool("b_tool")))

        assertEquals(cache.cacheKey(profile, first), cache.cacheKey(profile, second))
    }

    @Test
    fun cacheKeyIgnoresProviderCacheHints() {
        val cache = AiResponseCache(tempDir())
        val first = request()
            .put("prompt_cache_key", "lyra-a")
            .put("prompt_cache_retention", "24h")
        val second = request()
            .put("prompt_cache_key", "lyra-b")
            .put("prompt_cache_retention", "in_memory")

        assertEquals(cache.cacheKey(profile, first), cache.cacheKey(profile, second))
    }

    @Test
    fun cacheKeySeparatesConversationScopes() {
        val cache = AiResponseCache(tempDir())
        val request = request()

        assertNotEquals(
            cache.cacheKey(profile, request, "conversation:100"),
            cache.cacheKey(profile, request, "conversation:101"),
        )
    }

    @Test
    fun cacheKeySeparatesReasoningEffortFormats() {
        val cache = AiResponseCache(tempDir())
        val low = request().put("reasoning_effort", "low")
        val high = request().put("reasoning_effort", "high")
        val responsesLow = request().put("reasoning", JSONObject().put("effort", "low"))
        val responsesHigh = request().put("reasoning", JSONObject().put("effort", "high"))
        val anthropicLow = request().put("output_config", JSONObject().put("effort", "low"))
        val anthropicHigh = request().put("output_config", JSONObject().put("effort", "high"))

        assertNotEquals(cache.cacheKey(profile, low), cache.cacheKey(profile, high))
        assertNotEquals(cache.cacheKey(profile, responsesLow), cache.cacheKey(profile, responsesHigh))
        assertNotEquals(cache.cacheKey(profile, anthropicLow), cache.cacheKey(profile, anthropicHigh))
    }

    @Test
    fun diskCachePersistsResponse() {
        val root = tempDir()
        val writer = AiResponseCache(root)
        val request = request()
        writer.put(
            profile,
            request,
            "conversation:100",
            AiCachedResponse("ok", "think", JSONObject().put("role", "assistant").put("content", "ok").toString()),
        )

        val reader = AiResponseCache(root)
        val cached = reader.get(profile, request, "conversation:100")

        assertNotNull(cached)
        assertEquals("ok", cached?.content)
    }

    @Test
    fun diskCacheDoesNotCrossConversationScopes() {
        val root = tempDir()
        val cache = AiResponseCache(root)
        val request = request()
        cache.put(
            profile,
            request,
            "conversation:100",
            AiCachedResponse("old", "", JSONObject().put("role", "assistant").put("content", "old").toString()),
        )

        assertEquals(null, cache.get(profile, request, "conversation:101"))
    }

    private fun request(
        toolId: String = "call_1",
        toolMessageId: String = "call_1",
        reasoning: String = "",
        tools: JSONArray = JSONArray().put(tool("read_file")),
    ): JSONObject {
        return JSONObject()
            .put("model", "test-model")
            .put("temperature", 0.2)
            .put("stream", true)
            .put("tool_choice", "auto")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "system\r\n"))
                    .put(JSONObject().put("role", "user").put("content", "hello"))
                    .put(
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", "")
                            .put("reasoning_content", reasoning)
                            .put(
                                "tool_calls",
                                JSONArray().put(
                                    JSONObject()
                                        .put("id", toolId)
                                        .put("type", "function")
                                        .put(
                                            "function",
                                            JSONObject()
                                                .put("name", "read_file")
                                                .put("arguments", """{"path":"MainActivity.kt"}"""),
                                        ),
                                ),
                            ),
                    )
                    .put(JSONObject().put("role", "tool").put("tool_call_id", toolMessageId).put("content", "file")),
            )
            .put("tools", tools)
    }

    private fun tool(name: String): JSONObject {
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", "desc")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject().put("path", JSONObject().put("type", "string"))),
                    ),
            )
    }

    private fun tempDir(): File {
        return createTempDirectory(prefix = "lyra-cache-test-").toFile().also { it.deleteOnExit() }
    }
}
