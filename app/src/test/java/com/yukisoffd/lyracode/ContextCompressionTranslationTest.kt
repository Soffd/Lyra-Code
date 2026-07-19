package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextCompressionTranslationTest {
    @Test
    fun contextCompressionScreensTranslateInEnglishMode() {
        val previous = UiTextBridge.languageMode
        UiTextBridge.languageMode = AppSettings.LANGUAGE_EN
        try {
            assertEquals("Context window", uiText("上下文窗口"))
            assertEquals("Automatic compression", uiText("自动压缩"))
            assertEquals("Fixed turns", uiText("固定轮次"))
            assertEquals("About 718709 tokens", uiText("约 718709 tokens"))
            assertEquals(
                "The current API context contains 567 messages; 17 turns since the last compression",
                uiText("当前 API 上下文包含 567 条消息；距上次压缩 17 轮"),
            )
            assertEquals(
                "Generate short titles for new conversations",
                uiText("为新对话生成简短标题"),
            )
            assertEquals(
                "Choose the model used for manual and automatic compression",
                uiText("设置手动与自动压缩使用的模型"),
            )
        } finally {
            UiTextBridge.languageMode = previous
        }
    }
}
