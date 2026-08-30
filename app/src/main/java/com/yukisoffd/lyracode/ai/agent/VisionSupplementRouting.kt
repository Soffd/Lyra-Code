package com.yukisoffd.lyracode.ai

import org.json.JSONArray
import org.json.JSONObject

internal data class VisionMcpImage(
    val name: String,
    val mimeType: String,
    val dataUrl: String,
)

internal fun visionSupplementPlaceholder(messageId: Long, name: String, mimeType: String): String = buildString {
    append("LYRA_WITHHELD_IMAGE_V1\n")
    append("The image attachment was intentionally withheld from the main model because visual supplement routing is enabled. ")
    append("Call analyze_image for visual details or extract_image_text for exact visible text before answering when the image is relevant.\n")
    if (messageId > 0L) append("message_id=").append(messageId).append('\n')
    append("name=").append(name).append('\n')
    append("mime_type=").append(mimeType.ifBlank { "image/*" })
}

internal fun buildVisionMcpArguments(
    inputSchema: String,
    image: VisionMcpImage,
    instruction: String,
): JSONObject {
    val schema = runCatching { JSONObject(inputSchema.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    val properties = schema.optJSONObject("properties") ?: JSONObject()
    val arguments = JSONObject()
    val imageField = preferredProperty(
        properties,
        listOf(
            "image_data_url", "data_url", "image_url", "image_base64", "base64_image", "image", "images",
            "picture", "photo", "file_data", "data", "content", "url", "uri", "path", "file",
        ),
    ) ?: if (properties.length() == 0) "image" else null
    require(imageField != null) {
        "The selected MCP tool has no recognizable image input. Use a tool schema with an image, image_url, data_url, base64, data, content, url, uri, path, or file field."
    }
    val imageSchema = properties.optJSONObject(imageField) ?: JSONObject().put("type", "string")
    arguments.put(imageField, mcpImageValue(imageField, imageSchema, image))

    preferredProperty(properties, listOf("instruction", "prompt", "query", "question", "task", "text"))
        ?.takeIf { it != imageField }
        ?.let { arguments.put(it, instruction) }
    preferredProperty(properties, listOf("mime_type", "mimeType", "media_type"))
        ?.takeIf { it != imageField }
        ?.let { arguments.put(it, image.mimeType) }
    preferredProperty(properties, listOf("filename", "file_name", "name"))
        ?.takeIf { it != imageField }
        ?.let { arguments.put(it, image.name) }

    val required = schema.optJSONArray("required") ?: JSONArray()
    val missing = buildList {
        for (index in 0 until required.length()) {
            required.optString(index).takeIf { it.isNotBlank() && !arguments.has(it) }?.let(::add)
        }
    }
    require(missing.isEmpty()) {
        "The selected MCP vision tool requires unsupported arguments: ${missing.joinToString()}. Choose a tool whose required fields describe the image and optional instruction."
    }
    return arguments
}

private fun preferredProperty(properties: JSONObject, candidates: List<String>): String? {
    val names = properties.keys().asSequence().toList()
    candidates.forEach { candidate ->
        names.firstOrNull { it.equals(candidate, ignoreCase = true) }?.let { return it }
    }
    return null
}

private fun mcpImageValue(fieldName: String, schema: JSONObject, image: VisionMcpImage): Any {
    val normalized = fieldName.lowercase()
    val type = schema.optString("type").lowercase()
    val rawBase64 = image.dataUrl.substringAfter("base64,", image.dataUrl)
    return when (type) {
        "array" -> JSONArray().put(
            mcpImageValue(
                fieldName.removeSuffix("s"),
                schema.optJSONObject("items") ?: JSONObject().put("type", "string"),
                image,
            ),
        )
        "object" -> {
            val properties = schema.optJSONObject("properties") ?: JSONObject()
            if (properties.length() == 0) {
                JSONObject()
                    .put("url", image.dataUrl)
                    .put("data_url", image.dataUrl)
                    .put("base64", rawBase64)
                    .put("mime_type", image.mimeType)
                    .put("name", image.name)
            } else {
                JSONObject().also { output ->
                    preferredProperty(properties, listOf("url", "image_url", "data_url"))
                        ?.let { output.put(it, image.dataUrl) }
                    preferredProperty(properties, listOf("base64", "image_base64", "data", "content"))
                        ?.let { output.put(it, rawBase64) }
                    preferredProperty(properties, listOf("mime_type", "mimeType", "media_type"))
                        ?.let { output.put(it, image.mimeType) }
                    preferredProperty(properties, listOf("filename", "file_name", "name"))
                        ?.let { output.put(it, image.name) }
                    val required = schema.optJSONArray("required") ?: JSONArray()
                    val missing = buildList {
                        for (index in 0 until required.length()) {
                            required.optString(index).takeIf { it.isNotBlank() && !output.has(it) }?.let(::add)
                        }
                    }
                    require(missing.isEmpty()) {
                        "The selected MCP tool's image object requires unsupported fields: ${missing.joinToString()}."
                    }
                }
            }
        }
        else -> if ("base64" in normalized) rawBase64 else image.dataUrl
    }
}
