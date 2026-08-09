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
            assertEquals("Sent", uiText("已发送"))
            assertEquals("Generating", uiText("输出中"))
            assertEquals("Model completed", uiText("模型完成"))
            assertEquals("Cache hit", uiText("缓存命中"))
            assertEquals("Native search in progress…", uiText("正在进行原生搜索…"))
            assertEquals("Native search completed", uiText("原生搜索完成"))
            assertEquals("Reading file: app/src/Main.kt", uiText("正在阅读文件：app/src/Main.kt"))
            assertEquals("Modifying file: README.md", uiText("正在修改文件：README.md"))
            assertEquals("Moving file: old.txt → new.txt", uiText("正在移动文件：old.txt → new.txt"))
            assertEquals("Using tool: read_file", uiText("调用工具：read_file"))
            assertEquals("Tool complete: edit_file", uiText("工具完成：edit_file"))
            assertEquals(
                "Sub-agent tasks: 1/3 completed · Running Android · Reading file: app/Main.kt",
                uiText("子代理任务：已完成 1/3 · 正在执行 Android · 正在阅读文件：app/Main.kt"),
            )
            assertEquals(
                "Request failed. Retrying in 5 seconds (3/5)",
                uiText("请求失败，5 秒后重试（3/5）"),
            )
            assertEquals(
                "Request interrupted after 5 automatic retries. This turn's reasoning and response have been preserved; switch models or continue the task after the API recovers.",
                uiText("请求中断：已自动重试 5 次，仍无法继续。已保留本轮已输出的思维链和正文；可切换模型，或待 API 恢复后继续任务。"),
            )
            assertEquals("Projects", uiText("项目"))
            assertEquals("Create project", uiText("创建项目"))
            assertEquals("File changes 3", "${uiText("文件变更")} 3")
            assertEquals("Search settings", uiText("搜索设置"))
            assertEquals("No matching settings", uiText("没有匹配的设置"))
            assertEquals("3 chats", uiText("3 个对话"))
            assertEquals("Created project “Lyra”", uiText("已创建项目“Lyra”"))
            assertEquals(
                "This will delete “Lyra” and every chat in the project. This action cannot be undone.",
                uiText("将删除“Lyra”及项目内的所有对话。此操作无法撤销。"),
            )
        } finally {
            UiTextBridge.languageMode = previous
        }
    }
}
