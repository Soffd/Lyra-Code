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

    private fun tool(name: String): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject().put("name", name).put("parameters", JSONObject()))

    private fun message(id: Long, role: String, content: String): ChatMessage = ChatMessage(
        id = id,
        conversationId = 1L,
        role = role,
        content = content,
        thinking = "",
        profileId = "profile",
        model = "model",
        toolCallId = null,
        rawJson = null,
        createdAt = id,
    )
}
