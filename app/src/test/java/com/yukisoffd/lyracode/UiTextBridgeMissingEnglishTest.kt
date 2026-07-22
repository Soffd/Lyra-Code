package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextBridgeMissingEnglishTest {
    @Test
    fun translatesPreviouslyUnmappedUiText() {
        val previous = UiTextBridge.languageMode
        UiTextBridge.languageMode = AppSettings.LANGUAGE_EN
        try {
            assertEquals("No workspace selected", uiText("未选择工作目录"))
            assertEquals(
                "Request endpoint: https://api.example.com/v1/chat/completions; model list: https://api.example.com/v1/models",
                uiText("请求端点：https://api.example.com/v1/chat/completions；模型列表：https://api.example.com/v1/models"),
            )
            assertEquals("Status: Running · Not started", uiText("状态：Running · 未启动"))
            assertEquals("Local address: http://127.0.0.1:8765", uiText("本地地址：http://127.0.0.1:8765"))
            assertEquals("Serving demo", uiText("正在服务 demo"))
            assertEquals("Matching 3 / 12 tools", uiText("匹配 3 / 12 个工具"))
            assertEquals("External private files 1", uiText("外部私有文件 1"))
            assertEquals("External cache 2", uiText("外部缓存 2"))
            assertEquals("Uploaded notes.txt", uiText("已上传 notes.txt"))
            assertEquals("2 attachments pending", uiText("待发送 2 个附件"))
            assertEquals("Waiting for confirmation: write_file", uiText("等待确认：write_file"))
            assertEquals(
                "Request failed. Retrying in 5 seconds (3/5)",
                uiText("请求失败，5 秒后重试（3/5）"),
            )
        } finally {
            UiTextBridge.languageMode = previous
        }
    }
}
