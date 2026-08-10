package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.data.AppSettings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StringResourcesTest {
    @Test
    fun localizedResourcesCoverEveryDefaultStringWithMatchingFormatArguments() {
        val defaults = stringsIn("src/main/res/values/strings.xml")
        val localizedFiles = listOf(
            "src/main/res/values-en/strings.xml",
            "src/main/res/values-zh-rCN/strings.xml",
            "src/main/res/values-zh-rTW/strings.xml",
        )

        localizedFiles.forEach { path ->
            val localized = stringsIn(path)
            assertEquals("Resource keys differ for $path", defaults.keys, localized.keys)
            defaults.forEach { (name, value) ->
                assertEquals(
                    "Format arguments differ for $name in $path",
                    placeholders(value),
                    placeholders(localized.getValue(name)),
                )
            }
        }
    }

    @Test
    fun defaultEnglishResourcesDoNotContainUntranslatedCjkText() {
        stringsIn("src/main/res/values/strings.xml").forEach { (name, value) ->
            if (name.startsWith("language_autonym_")) return@forEach
            assertFalse("Untranslated English resource: $name", CJK.containsMatchIn(value))
        }
    }

    @Test
    fun traditionalChineseResourcesExposeTraditionalLanguageNames() {
        val traditional = stringsIn("src/main/res/values-zh-rTW/strings.xml")

        assertEquals("繁體中文", traditional.getValue("ui_traditional_chinese"))
        assertEquals("簡體中文", traditional.getValue("ui_simplified_chinese"))
        assertEquals("英文", traditional.getValue("ui_english"))
        assertEquals(
            AppSettings.LANGUAGE_ZH_TW,
            AppSettings.normalizeLanguageMode(AppSettings.LANGUAGE_ZH_TW),
        )
    }

    @Test
    fun percentageResourcesUsedByAppearancePagesAreValidFormatStrings() {
        val localizedFiles = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-en/strings.xml",
            "src/main/res/values-zh-rCN/strings.xml",
            "src/main/res/values-zh-rTW/strings.xml",
        )
        val percentageResources = listOf(
            "ui_mask_opacity_1_s",
            "font_scale_standard",
            "font_scale_very_large",
            "font_scale_custom",
        )

        localizedFiles.forEach { path ->
            val strings = stringsIn(path)
            percentageResources.forEach { name ->
                val formatted = String.format(Locale.ROOT, strings.getValue(name), 100)
                assertTrue("Invalid percentage format for $name in $path", formatted.endsWith("100%"))
            }
        }
    }

    @Test
    fun productionCodeDoesNotUseChineseTextAsRuntimeTranslationKeys() {
        val productionSources = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(productionSources.contains("UiTextBridge"))
        assertFalse(Regex("""uiText\s*\(\s*\"""").containsMatchIn(productionSources))
        assertTrue(productionSources.contains("uiText(R.string."))
    }

    private fun stringsIn(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun placeholders(value: String): Set<String> = FORMAT_ARGUMENT
        .findAll(value)
        .map { it.value }
        .toSet()

    private companion object {
        val CJK = Regex("[\\u3400-\\u9fff]")
        val FORMAT_ARGUMENT = Regex("%[0-9]+\\$[a-zA-Z]")
    }
}
