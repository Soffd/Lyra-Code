package com.yukisoffd.lyracode

import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichMarkdownPreprocessingTest {
    @Test
    fun `adds block boundary before table following bold title`() {
        val markdown = """
            **Platforms（编译/合包目标，API 级别）**
            | Platform | ApiLevel | ExtensionLevel |
            | -------- | -------- | -------------- |
            | android-37 | 37.0 | 22 |
        """.trimIndent()

        val normalized = normalizeMarkdownTableBoundaries(markdown)

        assertEquals(
            """
                **Platforms（编译/合包目标，API 级别）**

                | Platform | ApiLevel | ExtensionLevel |
                | -------- | -------- | -------------- |
                | android-37 | 37.0 | 22 |
            """.trimIndent(),
            normalized,
        )
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(normalized)
        assertTrue(tree.children.any { it.type == GFMElementTypes.TABLE })
    }

    @Test
    fun `adds block boundary before pipe-less-edge table following prose`() {
        val markdown = """
            Supported targets:
            Platform | ApiLevel
            -------- | --------
            android-37 | 37
        """.trimIndent()

        val normalized = normalizeMarkdownTableBoundaries(markdown)

        assertEquals("Supported targets:\n\nPlatform | ApiLevel\n-------- | --------\nandroid-37 | 37", normalized)
    }

    @Test
    fun `does not alter an already separated table`() {
        val markdown = "Title\n\n| A | B |\n| --- | --- |\n| 1 | 2 |"

        assertEquals(markdown, normalizeMarkdownTableBoundaries(markdown))
    }

    @Test
    fun `does not alter table-shaped content inside code fence`() {
        val markdown = "Intro\n```text\nnot a table\n| A | B |\n| --- | --- |\n```"

        assertEquals(markdown, normalizeMarkdownTableBoundaries(markdown))
    }
}
