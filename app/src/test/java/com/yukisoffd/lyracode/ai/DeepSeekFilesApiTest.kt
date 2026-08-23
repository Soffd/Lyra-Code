package com.yukisoffd.lyracode.ai

import com.yukisoffd.lyracode.data.ApiProfile
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DeepSeekFilesApiTest {
    @Test
    fun filesApiIsLimitedToDeepSeekVisionModels() {
        val deepSeek = profile("https://api.deepseek.com/v1", "deepseek-v4-flash-vision-exp")

        assertTrue(supportsDeepSeekFilesApi(deepSeek, deepSeek.selectedModel))
        assertFalse(supportsDeepSeekFilesApi(deepSeek, "deepseek-v4-flash"))
        assertFalse(
            supportsDeepSeekFilesApi(
                profile("https://api.example.com/v1", "example-vision"),
                "example-vision",
            ),
        )
        assertEquals("https://api.deepseek.com/files", deepSeekFilesEndpoint(deepSeek))
        assertEquals(
            "https://proxy.example.com/files",
            deepSeekFilesEndpoint(profile("https://proxy.example.com/anthropic/v1", "vision")),
        )
    }

    @Test
    fun parsesOnlySupportedInlineImageDataUrls() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"

        val parsed = parseDeepSeekInlineImage(dataUrl)

        assertEquals("image/png", parsed?.mimeType)
        assertArrayEquals(bytes, parsed?.bytes)
        assertTrue(parsed?.filename?.endsWith(".png") == true)
        assertNull(parseDeepSeekInlineImage("https://example.com/image.png"))
        assertNull(parseDeepSeekInlineImage("data:image/svg+xml;base64,PHN2Zy8+"))
    }

    @Test
    fun openAiInlineImagesBecomeReusableFileBlocksOnlyInUserMessages() {
        val dataUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7))}"
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "describe"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "https://example.com/a.jpg")))
        val assistantContent = JSONArray()
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        val messages = JSONArray()
            .put(JSONObject().put("role", "user").put("content", userContent))
            .put(JSONObject().put("role", "assistant").put("content", assistantContent))
        var resolutions = 0

        val replaced = replaceOpenAiInlineImagesWithDeepSeekFiles(messages) {
            resolutions++
            "file-api-reused"
        }

        assertTrue(replaced)
        assertEquals(1, resolutions)
        assertEquals("file", userContent.getJSONObject(1).getString("type"))
        assertEquals("file-api-reused", userContent.getJSONObject(1).getString("file_id"))
        assertEquals("image_url", userContent.getJSONObject(2).getString("type"))
        assertEquals("image_url", assistantContent.getJSONObject(0).getString("type"))
    }

    @Test
    fun anthropicInlineImagesBecomeFileSources() {
        val data = Base64.getEncoder().encodeToString(byteArrayOf(5, 4, 3))
        val content = JSONArray().put(
            JSONObject()
                .put("type", "image")
                .put(
                    "source",
                    JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/webp")
                        .put("data", data),
                ),
        )
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))

        assertTrue(replaceAnthropicInlineImagesWithDeepSeekFiles(messages) { "file-api-anthropic" })

        val source = content.getJSONObject(0).getJSONObject("source")
        assertEquals("file", source.getString("type"))
        assertEquals("file-api-anthropic", source.getString("file_id"))
        assertFalse(source.has("data"))
    }

    @Test
    fun failedUploadLeavesInlineImageAvailableForFallback() {
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(byteArrayOf(1))}"
        val content = JSONArray().put(
            JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)),
        )
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))

        assertFalse(replaceOpenAiInlineImagesWithDeepSeekFiles(messages) { null })
        assertEquals("image_url", content.getJSONObject(0).getString("type"))
    }

    @Test
    fun cachedFileIsReusedUntilExpirySafetyWindow() {
        val now = 10_000L
        val cached = DeepSeekCachedFile("file-api-cached", now + 2 * 60 * 60 * 1000L)
        var uploads = 0

        val reused = reusableDeepSeekFileId(now, cached) {
            uploads++
            DeepSeekCachedFile("file-api-new", Long.MAX_VALUE)
        }
        val refreshed = reusableDeepSeekFileId(now, cached.copy(expiresAtMillis = now + 30_000L)) {
            uploads++
            DeepSeekCachedFile("file-api-new", Long.MAX_VALUE)
        }

        assertEquals("file-api-cached", reused.fileId)
        assertEquals("file-api-new", refreshed.fileId)
        assertEquals(1, uploads)
    }

    @Test
    fun uploadRequestUsesDocumentedMultipartContract() {
        val profile = profile("https://api.deepseek.com", "deepseek-v4-flash-vision-exp")
        val image = DeepSeekInlineImage("image/png", byteArrayOf(1, 2, 3))

        val request = deepSeekFileUploadRequest(profile, deepSeekFilesEndpoint(profile), image)
        val body = request.body as okhttp3.MultipartBody
        val encoded = Buffer().also(body::writeTo).readUtf8()

        assertEquals("https://api.deepseek.com/files", request.url.toString())
        assertEquals("Bearer key", request.header("Authorization"))
        assertEquals(4, body.parts.size)
        assertTrue(encoded.contains("name=\"purpose\""))
        assertTrue(encoded.contains("user_data"))
        assertTrue(encoded.contains("name=\"expires_after[anchor]\""))
        assertTrue(encoded.contains("created_at"))
        assertTrue(encoded.contains("name=\"expires_after[seconds]\""))
        assertTrue(encoded.contains("2592000"))
        assertTrue(encoded.contains("name=\"file\""))
        assertTrue(encoded.contains("filename=\"${image.filename}\""))
    }

    private fun profile(baseUrl: String, model: String) = ApiProfile(
        id = "test",
        name = "Test",
        apiKey = "key",
        baseUrl = baseUrl,
        selectedModel = model,
        savedModels = listOf(model),
        presetId = if (baseUrl.contains("deepseek.com")) "deepseek" else "",
    )
}
