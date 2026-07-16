package com.yukisoffd.lyracode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class AgentTextEditsTest {
    @Test
    fun contentLinesAreUsedWhenContentIsPresentButEmpty() {
        val args = JSONObject()
            .put("content", "")
            .put("content_lines", JSONArray().put("fun main() {").put("    println(\"你好\")").put("}"))
            .put("ensure_trailing_newline", true)

        assertEquals("fun main() {\n    println(\"你好\")\n}\n", args.toolTextArgument("content"))
    }

    @Test
    fun contentLinesAcceptsProviderSerializedArrayWithoutBrackets() {
        val args = JSONObject()
            .put(
                "content_lines",
                "\"<!DOCTYPE html>\", \"<html lang=\\\"zh-CN\\\">\", \"\", \"<script>const value = \\\",\\\";</script>\", \"</html>\"",
            )

        assertEquals(
            "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n\n<script>const value = \",\";</script>\n</html>",
            args.toolTextArgument("content"),
        )
    }

    @Test
    fun contentLinesAcceptsSerializedJsonArrayAndPlainMultilineText() {
        assertEquals(
            "第一行\n    second\n",
            JSONObject()
                .put("content_lines", "[\"第一行\",\"    second\",\"\"]")
                .toolTextArgument("content"),
        )
        assertEquals(
            "one\ntwo\nthree",
            JSONObject()
                .put("content_lines", "one\r\ntwo\rthree")
                .toolTextArgument("content"),
        )
    }

    @Test
    fun trailingNewlineDoesNotChangeOldContentMatchText() {
        val args = JSONObject()
            .put("old_content_lines", JSONArray().put("before"))
            .put("new_content_lines", JSONArray().put("after"))
            .put("ensure_trailing_newline", true)

        assertEquals("before", args.toolTextArgument("old_content"))
        assertEquals("after\n", args.toolTextArgument("new_content"))
    }

    @Test
    fun invalidSuppliedContentTypeIsRejectedInsteadOfWritingEmptyText() {
        assertThrows(IllegalArgumentException::class.java) {
            JSONObject().put("content_lines", JSONObject().put("line", "text"))
                .toolTextArgument("content")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JSONObject().put("content", 123)
                .toolTextArgument("content")
        }
    }

    @Test
    fun exactReplacementRequiresExpectedUniqueMatch() {
        assertEquals(
            "alpha\nBETA\ngamma",
            applyExactTextReplacement("alpha\nbeta\ngamma", "beta", "BETA"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            applyExactTextReplacement("same same", "same", "changed")
        }
    }

    @Test
    fun lineRangeReplacementPreservesFollowingLinesAndLineEnding() {
        assertEquals(
            "one\r\nTWO\r\nTHREE\r\nfour",
            applyLineRangeReplacement("one\r\ntwo\r\nthree\r\nfour", 2, 3, "TWO\nTHREE"),
        )
        assertEquals(
            "one\nfour",
            applyLineRangeReplacement("one\ntwo\nthree\nfour", 2, 3, ""),
        )
    }
}
