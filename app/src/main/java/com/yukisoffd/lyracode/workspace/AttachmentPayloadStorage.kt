package com.yukisoffd.lyracode.workspace

import org.json.JSONObject
import java.util.Base64

internal data class ExternalizedAttachmentContent(
    val content: String,
    val attachmentCount: Int,
)

internal fun externalizeInlineAttachmentDataUrls(
    content: String,
    persist: (name: String, mimeType: String, bytes: ByteArray) -> String,
): ExternalizedAttachmentContent {
    var attachmentCount = 0
    val updated = ATTACHMENT_MARKER_REGEX.replace(content) { match ->
        val payload = runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
            ?: return@replace match.value
        val dataUrl = payload.optString("data_url")
        val decoded = decodeAttachmentDataUrl(dataUrl) ?: return@replace match.value
        val name = payload.optString("name").ifBlank { "attachment" }
        val mimeType = payload.optString("mime_type").ifBlank { decoded.first }
        val storedPath = runCatching { persist(name, mimeType, decoded.second) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@replace match.value
        payload.remove("data_url")
        payload.put("uri", storedPath)
        attachmentCount++
        "$ATTACHMENT_MARKER_START$payload$ATTACHMENT_MARKER_END"
    }
    return ExternalizedAttachmentContent(updated, attachmentCount)
}

internal fun inlineLocalAttachmentDataUrls(
    content: String,
    load: (uri: String, mimeType: String) -> ByteArray?,
): String = ATTACHMENT_MARKER_REGEX.replace(content) { match ->
    val payload = runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
        ?: return@replace match.value
    if (payload.optString("data_url").isNotBlank()) return@replace match.value
    if (payload.optString("kind").ifBlank { "text" } == "text") return@replace match.value
    val uri = payload.optString("uri")
    if (uri.isBlank()) return@replace match.value
    val mimeType = payload.optString("mime_type").ifBlank { "application/octet-stream" }
    val bytes = runCatching { load(uri, mimeType) }.getOrNull() ?: return@replace match.value
    payload.put("data_url", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}")
    "$ATTACHMENT_MARKER_START$payload$ATTACHMENT_MARKER_END"
}

private fun decodeAttachmentDataUrl(value: String): Pair<String, ByteArray>? {
    val match = DATA_URL_REGEX.matchEntire(value.trim()) ?: return null
    val bytes = runCatching { Base64.getMimeDecoder().decode(match.groupValues[2]) }.getOrNull() ?: return null
    return match.groupValues[1].ifBlank { "application/octet-stream" } to bytes
}

private const val ATTACHMENT_MARKER_START = "<lyra_attachment_v1>"
private const val ATTACHMENT_MARKER_END = "</lyra_attachment_v1>"
private val ATTACHMENT_MARKER_REGEX = Regex("<lyra_attachment_v1>([\\s\\S]*?)</lyra_attachment_v1>")
private val DATA_URL_REGEX = Regex("^data:([^;,]*);base64,([A-Za-z0-9+/=\\r\\n]+)$", RegexOption.IGNORE_CASE)
