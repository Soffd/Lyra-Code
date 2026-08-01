package com.yukisoffd.lyracode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
