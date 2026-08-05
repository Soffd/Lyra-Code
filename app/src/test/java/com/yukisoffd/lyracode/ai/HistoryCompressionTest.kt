package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ApiProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class HistoryCompressionTest {
    @Test
    fun splitPreservesTranscriptAndRequestedChunkCount() {
        val transcript = (1..80).joinToString("\n") { "message-$it: ${"detail ".repeat(it % 7 + 1)}" }

        val chunks = splitCompressionTranscript(transcript, 8)

        assertEquals(8, chunks.size)
        assertEquals(transcript, chunks.joinToString(""))
        assertTrue(chunks.all { it.isNotEmpty() })
        assertTrue(chunks.maxOf(String::length) < transcript.length / 4)
    }

    @Test
    fun splitNeverCreatesMoreChunksThanCharacters() {
        assertEquals(listOf("a", "b", "c"), splitCompressionTranscript("abc", 16))
        assertEquals(listOf("😀"), splitCompressionTranscript("😀", 16))
        assertEquals(listOf("a", "😀"), splitCompressionTranscript("a😀", 2))
        assertEquals(emptyList<String>(), splitCompressionTranscript("", 4))
    }

    @Test
    fun splitDoesNotCutUtf16SurrogatePair() {
        val transcript = "aaaa😀bbbb"

        val chunks = splitCompressionTranscript(transcript, 2)

        assertEquals(listOf("aaaa😀", "bbbb"), chunks)
        assertEquals(transcript, chunks.joinToString(""))
    }

    @Test
    fun structuredSchemaContainsDurableAgentStateFields() {
        listOf(
            "current_goal",
            "confirmed_facts",
            "completed_tasks",
            "pending_tasks",
            "attention_items",
            "next_actions",
        ).forEach { field -> assertTrue("Missing $field", HISTORY_COMPRESSION_SCHEMA_V2.contains("$field:")) }
    }

    @Test
    fun extractsOpenAiChatStringAndArrayContent() {
        val stringResponse = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", "plain summary"))),
        )
        val arrayResponse = JSONObject().put(
            "choices",
            JSONArray().put(
                JSONObject().put(
                    "message",
                    JSONObject().put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", "first"))
                            .put(JSONObject().put("type", "output_text").put("text", "second")),
                    ),
                ),
            ),
        )

        assertEquals("plain summary", extractModelResponseText(stringResponse, ApiProfile.API_FORMAT_OPENAI))
        assertEquals("first\nsecond", extractModelResponseText(arrayResponse, ApiProfile.API_FORMAT_OPENAI))
    }

    @Test
    fun extractsStandardAndCompatibleResponsesApiText() {
        val standard = JSONObject().put(
            "output",
            JSONArray()
                .put(JSONObject().put("type", "reasoning").put("summary", JSONArray().put(JSONObject().put("text", "hidden"))))
                .put(
                    JSONObject().put("type", "message").put(
                        "content",
                        JSONArray().put(JSONObject().put("type", "output_text").put("text", "visible summary")),
                    ),
                ),
        )
        val topLevel = JSONObject().put("output_text", "top-level summary")
        val compatibleChat = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", "compat summary"))),
        )

        assertEquals("visible summary", extractModelResponseText(standard, ApiProfile.API_FORMAT_OPENAI, useResponsesApi = true))
        assertEquals("top-level summary", extractModelResponseText(topLevel, ApiProfile.API_FORMAT_OPENAI, useResponsesApi = true))
        assertEquals("compat summary", extractModelResponseText(compatibleChat, ApiProfile.API_FORMAT_OPENAI, useResponsesApi = true))
    }

    @Test
    fun extractsAnthropicAndGeminiTextWithoutUsingReasoning() {
        val anthropic = JSONObject().put(
            "content",
            JSONArray()
                .put(JSONObject().put("type", "thinking").put("text", "hidden"))
                .put(JSONObject().put("type", "text").put("text", "anthropic summary")),
        )
        val gemini = JSONObject().put(
            "candidates",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "gemini summary"))),
                ),
            ),
        )

        assertEquals("anthropic summary", extractModelResponseText(anthropic, ApiProfile.API_FORMAT_ANTHROPIC))
        assertEquals("gemini summary", extractModelResponseText(gemini, ApiProfile.API_FORMAT_GEMINI))
    }
}
