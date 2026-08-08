package com.yukisoffd.lyracode.email

import com.yukisoffd.lyracode.data.EmailServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSafetyTest {
    @Test
    fun attachmentLimitRejectsSingleAndCombinedOversizePayloads() {
        val max = EmailClient.MAX_ATTACHMENT_BYTES.toLong()
        validateEmailAttachmentSizes(listOf(max))
        assertTrue(runCatching { validateEmailAttachmentSizes(listOf(max + 1)) }.isFailure)
        assertTrue(runCatching { validateEmailAttachmentSizes(listOf(max / 2 + 1, max / 2)) }.isFailure)
    }

    @Test
    fun replyReferencesKeepsNewestCompleteMessageIds() {
        val result = buildEmailReplyReferences("<old@example> <middle@example>", "<new@example>", maxChars = 31)
        assertEquals("<middle@example> <new@example>", result)
    }

    @Test
    fun accountStableIdNormalizesAddressCaseAndWhitespace() {
        val account = EmailServerConfig(
            id = "id",
            name = "mail",
            emailAddress = " User@Example.COM ",
            username = "user",
            password = "secret",
            imapHost = "imap.example.com",
            smtpHost = "smtp.example.com",
        )
        assertEquals("user@example.com", account.stableId)
    }
}
