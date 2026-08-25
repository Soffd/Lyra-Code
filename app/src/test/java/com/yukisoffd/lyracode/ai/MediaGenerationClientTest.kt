package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.MediaGenerationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGenerationClientTest {
    @Test
    fun `generated media markdown stores references outside the main model result`() {
        val markdown = generatedMediaMarkdown(
            MediaGenerationKind.VIDEO,
            listOf(
                GeneratedMediaAsset("C:/generated/clip.mp4", "video/mp4"),
                GeneratedMediaAsset("C:/generated/preview.webm", "video/webm"),
            ),
        )

        assertEquals(
            "![Generated video 1](C:/generated/clip.mp4)\n\n" +
                "![Generated video 2](C:/generated/preview.webm)",
            markdown,
        )
    }

    @Test
    fun `extracts only rendered media sources for private reference reuse`() {
        val content = """
            The file is rendered below.
            ![Generated image 1](C:/generated/sketch.png)
            ![Remote](https://cdn.example.com/final.webp?token=abc)
        """.trimIndent()

        val sources = extractRenderedMediaSources(content)

        assertEquals(
            listOf("C:/generated/sketch.png", "https://cdn.example.com/final.webp?token=abc"),
            sources,
        )
        assertTrue(sources.none { it.startsWith("data:") })
        assertFalse(content.toByteArray().contentEquals(sources.joinToString().toByteArray()))
    }

    @Test
    fun `sanitizes media bytes from provider errors`() {
        val encoded = "A".repeat(256)
        val sanitized = sanitizeMediaGenerationError(
            "upstream rejected data:image/png;base64,$encoded, " +
                "https://cdn.example.com/rejected.png?token=secret, and payload $encoded",
        )

        assertTrue(sanitized.contains("[media data omitted]"))
        assertTrue(sanitized.contains("[media URL omitted]"))
        assertTrue(sanitized.contains("[binary data omitted]"))
        assertFalse(sanitized.contains(encoded))
        assertFalse(sanitized.contains("token=secret"))
    }
}
