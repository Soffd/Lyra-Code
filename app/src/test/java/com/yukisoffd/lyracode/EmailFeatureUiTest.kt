package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailFeatureUiTest {
    @Test
    fun emailUiAndToolLabelsTranslateInEnglishMode() {
        val previous = UiTextBridge.languageMode
        UiTextBridge.languageMode = AppSettings.LANGUAGE_EN
        try {
            assertEquals("Email server saved", uiText("邮件服务器已保存"))
            assertEquals("Name", uiText("名称"))
            assertEquals("Email server configuration", uiText("邮件服务器配置"))
            assertEquals("No email servers yet", uiText("暂无邮件服务器"))
            assertEquals("Send email", uiText("发送邮件"))
            assertEquals(
                "Provides IMAP message reading, folder and draft management, SMTP sending, and MIME handling for the app.",
                uiText("为应用提供 IMAP 邮件读取、文件夹与草稿管理，以及 SMTP 邮件发送和 MIME 处理。"),
            )
            assertEquals("Connection failed: timeout", uiText("连接失败：timeout"))
            assertEquals("Send email through SMTP: Status report", uiText("通过 SMTP 发送邮件：Status report"))
            listOf(
                "删除邮件服务器",
                "SMTP / IMAP 邮件",
                "密码加密保存在本机。读取正文不会改变已读状态；SMTP 发送每次都要求确认。",
                "建议使用邮箱服务商生成的应用专用密码，并启用 SSL/TLS。",
                "安全警告：连接未加密，账号、密码和邮件内容可能被窃听。",
                "正在测试 IMAP 连接…",
                "IMAP 连接成功",
                "测试 IMAP",
                "邮件服务器",
                "邮箱地址",
                "登录用户名（留空则使用邮箱地址）",
                "密码 / 应用专用密码",
                "安全模式填写 ssl、starttls 或 none。推荐 ssl；none 仅用于受信任的本地测试服务器。",
                "请填写有效邮箱、密码、服务器和端口。",
                "安全模式",
            ).forEach { source ->
                assertTrue("Missing English translation for: $source", uiText(source).none { it in '\u4e00'..'\u9fff' })
            }
            assertTrue(agentToolCatalog().filter { it.name.startsWith("list_email") || it.name.contains("email") }.all {
                it.title.none { character -> character in '\u4e00'..'\u9fff' } &&
                    it.description.none { character -> character in '\u4e00'..'\u9fff' }
            })
        } finally {
            UiTextBridge.languageMode = previous
        }
    }

    @Test
    fun allEmailAgentToolsAreVisibleInSettingsCatalog() {
        val expected = setOf(
            "list_email_accounts",
            "list_email_folders",
            "list_emails",
            "read_email",
            "set_email_flags",
            "download_email_attachment",
            "record_email_attachment_scan",
            "save_email_draft",
            "send_email",
        )
        assertTrue(agentToolCatalog().map { it.name }.containsAll(expected))
        assertTrue(LicenseTexts.EPL_2_0.contains("Eclipse Public License - v 2.0"))
        assertTrue(LicenseTexts.EPL_2_0.contains("https://www.eclipse.org/legal/epl-2.0/"))
    }
}
