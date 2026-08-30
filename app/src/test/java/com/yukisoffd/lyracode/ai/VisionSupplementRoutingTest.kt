package com.yukisoffd.lyracode.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSupplementRoutingTest {
    private val image = VisionMcpImage("screen.png", "image/png", "data:image/png;base64,YWJj")

    @Test
    fun placeholderNeverContainsImageBytes() {
        val placeholder = visionSupplementPlaceholder(42L, image.name, image.mimeType)

        assertTrue(placeholder.contains("message_id=42"))
        assertTrue(placeholder.contains("screen.png"))
        assertFalse(placeholder.contains("YWJj"))
    }

    @Test
    fun mcpArgumentsMapDataUrlAndInstruction() {
        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("image_url", JSONObject().put("type", "string"))
                    .put("prompt", JSONObject().put("type", "string")),
            )
            .put("required", org.json.JSONArray().put("image_url"))

        val arguments = buildVisionMcpArguments(schema.toString(), image, "Describe the layout")

        assertEquals(image.dataUrl, arguments.getString("image_url"))
        assertEquals("Describe the layout", arguments.getString("prompt"))
    }

    @Test
    fun mcpArgumentsUseRawBase64ForExplicitBase64Field() {
        val schema = """{"type":"object","properties":{"image_base64":{"type":"string"}}}"""

        assertEquals("YWJj", buildVisionMcpArguments(schema, image, "").getString("image_base64"))
    }

    @Test
    fun mcpArgumentsRespectNestedImageObjectSchema() {
        val schema = """
            {
              "type":"object",
              "properties":{
                "image":{
                  "type":"object",
                  "properties":{
                    "data":{"type":"string"},
                    "mime_type":{"type":"string"}
                  },
                  "required":["data","mime_type"]
                }
              },
              "required":["image"]
            }
        """.trimIndent()

        val mappedImage = buildVisionMcpArguments(schema, image, "").getJSONObject("image")

        assertEquals("YWJj", mappedImage.getString("data"))
        assertEquals(image.mimeType, mappedImage.getString("mime_type"))
        assertFalse(mappedImage.has("url"))
    }
}
