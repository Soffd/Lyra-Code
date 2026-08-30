package com.yukisoffd.lyracode.workspace

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AttachmentPayloadStorageTest {
    @Test
    fun externalizationRemovesLargeInlinePayloadAndPreservesMetadata() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val content = attachmentContent(bytes)
        var persistedBytes: ByteArray? = null

        val result = externalizeInlineAttachmentDataUrls(content) { name, mimeType, value ->
            assertEquals("large.png", name)
            assertEquals("image/png", mimeType)
            persistedBytes = value
            "/private/chat_uploads/large.png"
        }

        assertEquals(1, result.attachmentCount)
        assertFalse(result.content.contains("data_url"))
        assertFalse(result.content.contains(Base64.getEncoder().encodeToString(bytes).take(128)))
        assertTrue(result.content.contains("/private/chat_uploads/large.png"))
        assertArrayEquals(bytes, persistedBytes)
    }

    @Test
    fun exportCanInlineAnExternalizedAttachmentAgain() {
        val bytes = "image bytes".toByteArray()
        val externalized = externalizeInlineAttachmentDataUrls(attachmentContent(bytes)) { _, _, _ ->
            "/private/chat_uploads/image.png"
        }.content

        val exported = inlineLocalAttachmentDataUrls(externalized) { uri, mimeType ->
            assertEquals("/private/chat_uploads/image.png", uri)
            assertEquals("image/png", mimeType)
            bytes
        }

        assertTrue(exported.contains("data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"))
    }

    private fun attachmentContent(bytes: ByteArray): String {
        val payload = JSONObject()
            .put("name", "large.png")
            .put("kind", "image")
            .put("mime_type", "image/png")
            .put("uri", "content://old")
            .put("data_url", "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}")
        return "look at this\n\n<lyra_attachment_v1>$payload</lyra_attachment_v1>"
    }
}
