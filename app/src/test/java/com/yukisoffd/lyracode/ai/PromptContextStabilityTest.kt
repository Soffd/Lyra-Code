package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextStabilityTest {
    @Test
    fun `runtime snapshot is canonical across object insertion order`() {
        val first = JSONObject().put("workspace", "demo").put("enabled", true)
        val second = JSONObject().put("enabled", true).put("workspace", "demo")

        val firstSnapshot = buildRuntimeContextSnapshot("[]", "enabled_skills=[]", first, null)
        val secondSnapshot = buildRuntimeContextSnapshot("[]", "enabled_skills=[]", second, null)

        assertEquals(firstSnapshot, secondSnapshot)
        assertTrue(firstSnapshot.contains("supersedes all earlier"))
    }

    @Test
    fun `runtime snapshot appends only when retained content changes`() {
        val snapshot = buildRuntimeContextSnapshot("[]", "enabled_skills=[]", JSONObject(), null)
        val retained = message(5L, RUNTIME_CONTEXT_ROLE, snapshot)

        assertTrue(shouldAppendRuntimeContext(emptyList(), 0L, snapshot))
        assertFalse(shouldAppendRuntimeContext(listOf(retained), 0L, snapshot))
        assertTrue(shouldAppendRuntimeContext(listOf(retained), 5L, snapshot))
        assertTrue(shouldAppendRuntimeContext(listOf(retained), 0L, "$snapshot\nchanged"))
    }

    @Test
    fun `tool definitions use canonical name order`() {
        val tools = JSONArray()
            .put(tool("zeta"))
            .put(tool("alpha"))
            .put(tool("middle"))

        val sorted = canonicalToolDefinitions(tools)

        assertEquals(
            listOf("alpha", "middle", "zeta"),
            (0 until sorted.length()).map { sorted.getJSONObject(it).getJSONObject("function").getString("name") },
        )
    }

    @Test
    fun `local request errors are excluded from model history`() {
        val marked = message(
            id = 1L,
            role = "assistant",
            content = "请求中断：AI 请求失败 400",
            rawJson = JSONObject()
                .put("role", "assistant")
                .put("content", "请求中断：AI 请求失败 400")
                .put(LOCAL_REQUEST_ERROR_KEY, true)
                .toString(),
        )
        val legacy = message(2L, "assistant", "Request interrupted: AI request failed 400")
        val legacyPlaceholder = message(4L, "assistant", "")
        val actualModelOutput = message(
            id = 3L,
            role = "assistant",
            content = "Request interrupted is the phrase shown by the app.",
            rawJson = JSONObject().put("role", "assistant").put("content", "real output").toString(),
        )

        assertTrue(marked.isLocalRequestErrorMessage())
        assertTrue(legacy.isLocalRequestErrorMessage())
        assertTrue(legacyPlaceholder.isEmptyAssistantPlaceholder())
        assertFalse(actualModelOutput.isLocalRequestErrorMessage())
    }

    private fun tool(name: String): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject().put("name", name).put("parameters", JSONObject()))

    private fun message(id: Long, role: String, content: String, rawJson: String? = null): ChatMessage = ChatMessage(
        id = id,
        conversationId = 1L,
        role = role,
        content = content,
        thinking = "",
        profileId = "profile",
        model = "model",
        toolCallId = null,
        rawJson = rawJson,
        createdAt = id,
    )
}
