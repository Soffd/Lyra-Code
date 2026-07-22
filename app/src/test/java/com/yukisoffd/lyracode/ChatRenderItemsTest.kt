package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.ai.ChatRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRenderItemsTest {
    @Test
    fun `collapses intermediate assistant output into one process item`() {
        val messages = listOf(
            ChatRecord(id = 1L, role = "user", content = "start", createdAt = 1_000L),
            ChatRecord(id = 2L, role = "assistant", content = "intermediate one", thinking = "plan", createdAt = 2_000L),
            ChatRecord(id = 3L, role = "tool", content = "tool result", createdAt = 3_000L),
            ChatRecord(id = 4L, role = "assistant", content = "intermediate two", createdAt = 4_000L),
            ChatRecord(id = 5L, role = "assistant", content = "final answer", thinking = "final check", createdAt = 5_000L),
        )

        val items = chatRenderItems(messages)

        assertEquals(3, items.size)
        assertEquals("user", items[0].message?.role)
        assertEquals(listOf(2L, 3L, 4L, -5L), items[1].process.map { it.id })
        assertEquals(2_000L, items[1].processStartedAt)
        assertEquals(5_000L, items[1].processFinishedAt)
        assertEquals("final answer", items[2].message?.content)
        assertTrue(items[2].message?.thinking.isNullOrBlank())
    }

    @Test
    fun `keeps a simple assistant response without a process item`() {
        val items = chatRenderItems(
            listOf(
                ChatRecord(id = 1L, role = "user", content = "hello"),
                ChatRecord(id = 2L, role = "assistant", content = "hi"),
            ),
        )

        assertEquals(2, items.size)
        assertTrue(items.none { it.process.isNotEmpty() })
        assertEquals("hi", items.last().message?.content)
    }
}
