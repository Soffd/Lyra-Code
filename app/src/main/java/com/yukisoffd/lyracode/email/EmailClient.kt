package com.yukisoffd.lyracode.email

import android.content.Context
import android.text.Html
import com.sun.mail.imap.IMAPFolder
import com.yukisoffd.lyracode.data.EmailServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import javax.activation.DataHandler
import javax.mail.Address
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.ContentType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.search.FlagTerm
import javax.mail.util.ByteArrayDataSource

data class OutgoingAttachment(val name: String, val bytes: ByteArray)

data class EmailComposeRequest(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val textBody: String = "",
    val htmlBody: String = "",
    val attachments: List<OutgoingAttachment> = emptyList(),
    val replyFolder: String = "",
    val replyUid: Long = 0L,
    val allowReplyToAnswered: Boolean = false,
)

internal fun validateEmailAttachmentSizes(sizes: List<Long>, maxBytes: Long = EmailClient.MAX_ATTACHMENT_BYTES.toLong()) {
    require(sizes.none { it < 0L || it > maxBytes }) {
        "Attachments larger than 20 MB are not allowed. Use a cloud link or file-transfer service instead."
    }
    require(sizes.fold(0L) { total, size -> if (total > maxBytes - size) maxBytes + 1L else total + size } <= maxBytes) {
        "Combined attachments exceed 20 MB. Use a cloud link or file-transfer service instead."
    }
}

internal fun buildEmailReplyReferences(existing: String, messageId: String, maxChars: Int = 8_000): String {
    val ids = (existing + " " + messageId).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val kept = ArrayDeque<String>()
    var length = 0
    for (id in ids.asReversed()) {
        if (kept.isNotEmpty() && length + 1 + id.length > maxChars) break
        if (kept.isEmpty() && id.length > maxChars) continue
        kept.addFirst(id)
        length += id.length + if (kept.size > 1) 1 else 0
    }
    return kept.joinToString(" ").ifBlank { messageId }
}

class EmailClient(private val context: Context) {
    private val journal = context.getSharedPreferences("email_delivery_journal", Context.MODE_PRIVATE)
    private val quarantineRoot = File(context.cacheDir, "email_attachment_quarantine").apply { mkdirs() }

    fun accountsJson(accounts: List<EmailServerConfig>): String = JSONObject()
        .put("schema", "lyra_email_accounts_v1")
        .put("accounts", JSONArray().also { array ->
            accounts.filter { it.enabled }.forEach { account ->
                array.put(
                    JSONObject()
                        .put("id", account.id)
                        .put("name", account.name)
                        .put("email_address", account.emailAddress)
                        .put("imap_host", account.imapHost)
                        .put("smtp_host", account.smtpHost),
                )
            }
        }).toString()

    fun listFolders(account: EmailServerConfig): String = withStore(account) { store ->
        val folders = store.defaultFolder.list("*")
        JSONObject()
            .put("schema", "lyra_email_folders_v1")
            .put("account_id", account.id)
            .put("folders", JSONArray().also { array ->
                folders.forEach { folder ->
                    val attributes = (folder as? IMAPFolder)?.attributes.orEmpty().map { it.lowercase(Locale.US) }
                    array.put(
                        JSONObject()
                            .put("name", folder.fullName)
                            .put("can_hold_messages", folder.type and Folder.HOLDS_MESSAGES != 0)
                            .put("can_hold_folders", folder.type and Folder.HOLDS_FOLDERS != 0)
                            .put("attributes", JSONArray(attributes))
                            .put("drafts", isDraftFolder(folder.fullName, attributes)),
                    )
                }
            }).toString()
    }

    fun listMessages(account: EmailServerConfig, folderName: String, unreadOnly: Boolean, limit: Int): String =
        withFolder(account, folderName, Folder.READ_ONLY) { folder ->
            val count = folder.messageCount
            val selected = if (count <= 0) emptyArray() else if (unreadOnly) {
                folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false)).takeLast(limit.coerceIn(1, 100)).reversed().toTypedArray()
            } else {
                val start = (count - limit.coerceIn(1, 100) + 1).coerceAtLeast(1)
                folder.getMessages(start, count).reversedArray()
            }
            val uidFolder = folder as? UIDFolder
            JSONObject()
                .put("schema", "lyra_email_messages_v1")
                .put("account_id", account.id)
                .put("folder", folder.fullName)
                .put("folder_read_only", folder.mode == Folder.READ_ONLY)
                .put("messages", JSONArray().also { array ->
                    selected.forEach { message -> array.put(messageSummary(message, uidFolder?.getUID(message) ?: -1L, folder.fullName)) }
                }).toString()
        }

    fun readMessage(account: EmailServerConfig, folderName: String, uid: Long): String =
        withFolder(account, folderName, Folder.READ_ONLY) { folder ->
            val message = messageByUid(folder, uid)
            val extracted = extractBody(message)
            messageSummary(message, uid, folder.fullName)
                .put("schema", "lyra_email_message_v1")
                .put("folder_read_only", true)
                .put("body", extracted.text.take(MAX_BODY_CHARS))
                .put("body_truncated", extracted.text.length > MAX_BODY_CHARS || extracted.truncated)
                .put("body_source", extracted.source)
                .put("html_cleaned", extracted.htmlCleaned)
                .put("multimedia_omitted", extracted.mediaOmitted)
                .put("attachments", JSONArray().also { array ->
                    attachmentParts(message).forEachIndexed { index, attachment ->
                        array.put(
                            JSONObject()
                                .put("attachment_id", index.toString())
                                .put("name", safeFileName(attachment.fileName ?: "attachment-$index"))
                                .put("mime_type", attachment.contentType.substringBefore(';'))
                                .put("size", attachment.size.toLong().coerceAtLeast(-1L))
                                .put("quarantined", false)
                                .put("readable_by_ai", false),
                        )
                    }
                })
                .toString()
        }

    fun setFlags(account: EmailServerConfig, folderName: String, uid: Long, seen: Boolean?, flagged: Boolean?): String =
        withFolder(account, folderName, Folder.READ_WRITE) { folder ->
            require(seen != null || flagged != null) { "At least one of seen or flagged is required." }
            val message = messageByUid(folder, uid)
            seen?.let { message.setFlag(Flags.Flag.SEEN, it) }
            flagged?.let { message.setFlag(Flags.Flag.FLAGGED, it) }
            JSONObject()
                .put("status", "updated")
                .put("folder", folder.fullName)
                .put("uid", uid)
                .put("seen", message.isSet(Flags.Flag.SEEN))
                .put("flagged", message.isSet(Flags.Flag.FLAGGED))
                .put("answered", message.isSet(Flags.Flag.ANSWERED))
                .put("draft", message.isSet(Flags.Flag.DRAFT))
                .toString()
        }

    fun downloadAttachment(account: EmailServerConfig, folderName: String, uid: Long, attachmentIndex: Int): String =
        withFolder(account, folderName, Folder.READ_ONLY) { folder ->
            val message = messageByUid(folder, uid)
            val part = attachmentParts(message).getOrNull(attachmentIndex) ?: error("Attachment does not exist: $attachmentIndex")
            val token = UUID.randomUUID().toString()
            val targetDir = File(quarantineRoot, token).apply { mkdirs() }
            val target = File(targetDir, safeFileName(part.fileName ?: "attachment-$attachmentIndex.bin"))
            var total = 0L
            try {
                part.inputStream.use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_QUARANTINE_BYTES) { "Attachment exceeds the 100 MB quarantine limit." }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (error: Throwable) {
                target.delete()
                targetDir.delete()
                throw error
            }
            File(targetDir, "status.json").writeText(
                JSONObject().put("scanned", false).put("safe", false).put("created_at", System.currentTimeMillis()).toString(),
                Charsets.UTF_8,
            )
            JSONObject()
                .put("status", "quarantined_scan_required")
                .put("attachment_token", token)
                .put("name", target.name)
                .put("bytes", total)
                .put("temporary_path", target.absolutePath)
                .put("readable_by_ai", false)
                .put("scan_required", true)
                .put("instruction", "Do not read or open this file. Ask the user to scan it with trusted antivirus software and explicitly report the result.")
                .toString()
        }

    fun recordAttachmentScan(token: String, safe: Boolean): String {
        require(token.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid attachment token." }
        val dir = File(quarantineRoot, token).canonicalFile
        require(dir.parentFile == quarantineRoot.canonicalFile && dir.isDirectory) { "Quarantined attachment does not exist." }
        File(dir, "status.json").writeText(
            JSONObject().put("scanned", true).put("safe", safe).put("scanned_at", System.currentTimeMillis()).toString(),
            Charsets.UTF_8,
        )
        return JSONObject()
            .put("status", if (safe) "scan_reported_safe" else "scan_reported_unsafe")
            .put("attachment_token", token)
            .put("readable_by_ai", false)
            .put("instruction", if (safe) "The file remains outside AI-readable tools." else "Do not open or attach this file; delete it from the temporary cache.")
            .toString()
    }

    fun saveDraft(account: EmailServerConfig, request: EmailComposeRequest): String {
        val message = buildMessageWithReply(account, request)
        val fingerprint = fingerprint(account, request, "draft")
        journalResultIfDuplicate(fingerprint)?.let { return it }
        return withStore(account) { store ->
            val drafts = discoverDraftFolder(store)
                ?: error("IMAP_DRAFTS_UNAVAILABLE: The server exposes no drafts folder. Ask the user whether to send through SMTP or stop.")
            try {
                runCatching { drafts.open(Folder.READ_WRITE) }.getOrElse { error ->
                    throw IllegalStateException(
                        "IMAP_DRAFTS_UNAVAILABLE: The discovered drafts folder is not writable. Ask the user whether to send through SMTP or stop. ${error.message}",
                        error,
                    )
                }
                journal.edit().putString(fingerprint, "appending:${System.currentTimeMillis()}").commit()
                try {
                    message.setFlag(Flags.Flag.DRAFT, true)
                    drafts.appendMessages(arrayOf(message))
                    journal.edit().putString(fingerprint, "drafted:${System.currentTimeMillis()}").commit()
                    JSONObject()
                        .put("status", "draft_saved")
                        .put("folder", drafts.fullName)
                        .put("message_id", message.getHeader("Message-ID")?.firstOrNull().orEmpty())
                        .put("operation_id", fingerprint)
                        .toString()
                } catch (error: Throwable) {
                    journal.edit().putString(fingerprint, "uncertain:${System.currentTimeMillis()}").commit()
                    throw IllegalStateException(
                        "IMAP_APPEND_UNCERTAIN: ${error.message}. The same draft is blocked from automatic retry; inspect the drafts folder or ask the user whether to change the content.",
                        error,
                    )
                }
            } finally {
                if (drafts.isOpen) drafts.close(false)
            }
        }
    }

    fun send(account: EmailServerConfig, request: EmailComposeRequest): String {
        require(request.to.isNotEmpty() || request.cc.isNotEmpty() || request.bcc.isNotEmpty()) { "At least one recipient is required." }
        val fingerprint = fingerprint(account, request, "send")
        journalResultIfDuplicate(fingerprint)?.let { return it }
        val message = buildMessageWithReply(account, request)
        journal.edit().putString(fingerprint, "sending:${System.currentTimeMillis()}").commit()
        return try {
            smtpTransport(account, message)
            journal.edit().putString(fingerprint, "sent:${System.currentTimeMillis()}").commit()
            if (request.replyUid > 0L) markAnsweredAfterSend(account, request.replyFolder.ifBlank { "INBOX" }, request.replyUid)
            JSONObject()
                .put("status", "sent")
                .put("message_id", message.getHeader("Message-ID")?.firstOrNull().orEmpty())
                .put("operation_id", fingerprint)
                .put("duplicate_prevented", false)
                .toString()
        } catch (error: Throwable) {
            journal.edit().putString(fingerprint, "uncertain:${System.currentTimeMillis()}").commit()
            throw IllegalStateException(
                "SMTP_DELIVERY_UNCERTAIN: ${error.message}. The same message is blocked from automatic retry; verify Sent mail/server logs or change the message after user direction.",
                error,
            )
        }
    }

    private fun buildMessageWithReply(account: EmailServerConfig, request: EmailComposeRequest): MimeMessage =
        if (request.replyUid > 0L) {
            withFolder(account, request.replyFolder.ifBlank { "INBOX" }, Folder.READ_ONLY) { folder ->
                val original = messageByUid(folder, request.replyUid)
                val ownAddresses = setOf(account.emailAddress, account.username)
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { '@' in it }
                    .toSet()
                require(original.from.orEmpty().none { addressOf(it).trim().lowercase(Locale.US) in ownAddresses }) {
                    "SELF_REPLY_BLOCKED: The referenced message was sent by this account. Refusing to create an automatic reply loop."
                }
                require(!original.isSet(Flags.Flag.ANSWERED) || request.allowReplyToAnswered) {
                    "ALREADY_ANSWERED: The original message is already marked Answered. Refusing a possible duplicate; only continue when the user explicitly requests another reply and confirms allow_reply_to_answered."
                }
                buildMessage(account, request, original)
            }
        } else buildMessage(account, request)

    private fun smtpTransport(account: EmailServerConfig, message: MimeMessage) {
        val session = mailSession(account, smtp = true)
        val protocol = if (account.smtpSecurity == "ssl") "smtps" else "smtp"
        session.getTransport(protocol).use { transport ->
            transport.connect(account.smtpHost, account.smtpPort, account.username, account.password)
            transport.sendMessage(message, message.allRecipients)
        }
    }

    private fun buildMessage(account: EmailServerConfig, request: EmailComposeRequest, replyTo: Message? = null): MimeMessage {
        validateEmailAttachmentSizes(request.attachments.map { it.bytes.size.toLong() })
        val message = MimeMessage(mailSession(account, smtp = false))
        message.setFrom(InternetAddress(account.emailAddress))
        request.to.forEach { message.addRecipient(Message.RecipientType.TO, InternetAddress(it, true)) }
        request.cc.forEach { message.addRecipient(Message.RecipientType.CC, InternetAddress(it, true)) }
        request.bcc.forEach { message.addRecipient(Message.RecipientType.BCC, InternetAddress(it, true)) }
        message.setSubject(request.subject, "UTF-8")
        message.sentDate = Date()
        replyTo?.let { original ->
            val messageId = original.getHeader("Message-ID")?.firstOrNull()?.trim().orEmpty()
            require(messageId.isNotBlank()) { "The original message has no Message-ID; a standards-compliant threaded reply cannot be built." }
            message.setHeader("In-Reply-To", messageId)
            message.setHeader("References", buildEmailReplyReferences(original.getHeader("References")?.joinToString(" ").orEmpty(), messageId))
        }
        val content = bodyContent(request)
        if (request.attachments.isEmpty()) {
            message.setContent(content)
        } else {
            val mixed = MimeMultipart("mixed")
            mixed.addBodyPart(MimeBodyPart().apply { setContent(content) })
            request.attachments.forEach { attachment ->
                mixed.addBodyPart(
                    MimeBodyPart().apply {
                        dataHandler = DataHandler(ByteArrayDataSource(attachment.bytes, "application/octet-stream"))
                        fileName = MimeUtility.encodeText(safeFileName(attachment.name), "UTF-8", null)
                        disposition = Part.ATTACHMENT
                    },
                )
            }
            message.setContent(mixed)
        }
        message.saveChanges()
        return message
    }

    private fun bodyContent(request: EmailComposeRequest): Multipart {
        val alternative = MimeMultipart("alternative")
        if (request.textBody.isNotBlank() || request.htmlBody.isBlank()) {
            alternative.addBodyPart(MimeBodyPart().apply { setText(request.textBody, "UTF-8") })
        }
        if (request.htmlBody.isNotBlank()) {
            alternative.addBodyPart(MimeBodyPart().apply { setContent(request.htmlBody, "text/html; charset=UTF-8") })
        }
        return alternative
    }

    private fun markAnsweredAfterSend(account: EmailServerConfig, folderName: String, uid: Long) {
        runCatching {
            withFolder(account, folderName, Folder.READ_WRITE) { folder -> messageByUid(folder, uid).setFlag(Flags.Flag.ANSWERED, true) }
        }
    }

    private fun journalResultIfDuplicate(fingerprint: String): String? {
        val value = journal.getString(fingerprint, null) ?: return null
        val state = value.substringBefore(':')
        return JSONObject()
            .put("status", "duplicate_blocked")
            .put("previous_state", state)
            .put("operation_id", fingerprint)
            .put("duplicate_prevented", true)
            .put("instruction", "Do not retry automatically. Verify mailbox state and ask the user before creating materially different content.")
            .toString()
    }

    private fun fingerprint(account: EmailServerConfig, request: EmailComposeRequest, operation: String): String {
        val canonical = buildString {
            append(operation).append('|').append(account.stableId).append('|')
            append(request.to.map { it.trim().lowercase() }.sorted()).append('|')
            append(request.cc.map { it.trim().lowercase() }.sorted()).append('|')
            append(request.bcc.map { it.trim().lowercase() }.sorted()).append('|')
            append(request.subject.trim()).append('|').append(request.textBody).append('|').append(request.htmlBody).append('|')
            append(request.replyFolder.trim().lowercase(Locale.US)).append('|').append(request.replyUid).append('|')
            request.attachments
                .map { "${it.name}:${it.bytes.size}:${sha256(it.bytes)}" }
                .sorted()
                .forEach { append(it).append('|') }
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun discoverDraftFolder(store: Store): Folder? {
        val folders = store.defaultFolder.list("*").filter { it.type and Folder.HOLDS_MESSAGES != 0 }
        return folders.firstOrNull { folder ->
            val attrs = (folder as? IMAPFolder)?.attributes.orEmpty()
            attrs.any { it.equals("\\Drafts", ignoreCase = true) }
        } ?: folders.firstOrNull { isDraftFolder(it.fullName, emptyList()) }
    }

    private fun isDraftFolder(name: String, attributes: List<String>): Boolean {
        if (attributes.any { it.equals("\\drafts", ignoreCase = true) }) return true
        val leaf = name.substringAfterLast('/').substringAfterLast('.').trim().lowercase(Locale.US)
        return leaf in setOf("draft", "drafts", "草稿", "草稿箱", "entwürfe", "brouillons", "bozze")
    }

    private fun messageSummary(message: Message, uid: Long, folder: String): JSONObject = JSONObject()
        .put("uid", uid)
        .put("folder", folder)
        .put("message_id", message.getHeader("Message-ID")?.firstOrNull().orEmpty())
        .put("subject", decodeHeader(message.subject.orEmpty()))
        .put("from", addresses(message.from))
        .put("to", addresses(message.getRecipients(Message.RecipientType.TO)))
        .put("cc", addresses(message.getRecipients(Message.RecipientType.CC)))
        .put("sent_at", message.sentDate?.time ?: 0L)
        .put("received_at", message.receivedDate?.time ?: 0L)
        .put("seen", message.isSet(Flags.Flag.SEEN))
        .put("unread", !message.isSet(Flags.Flag.SEEN))
        .put("answered", message.isSet(Flags.Flag.ANSWERED))
        .put("flagged", message.isSet(Flags.Flag.FLAGGED))
        .put("deleted", message.isSet(Flags.Flag.DELETED))
        .put("draft", message.isSet(Flags.Flag.DRAFT) || isDraftFolder(folder, emptyList()))
        .put("recent", message.isSet(Flags.Flag.RECENT))
        .put("user_flags", JSONArray(message.flags.userFlags.orEmpty().toList()))

    private fun extractBody(part: Part): ExtractedBody {
        if (part.isMimeType("text/plain")) return ExtractedBody(decodeTextPart(part), "text/plain")
        if (part.isMimeType("text/html")) {
            val html = decodeTextPart(part)
            val clean = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            return ExtractedBody(clean, "text/html", htmlCleaned = true)
        }
        if (part.isMimeType("multipart/alternative")) {
            val multipart = part.content as? Multipart ?: return ExtractedBody("", "none")
            var html: ExtractedBody? = null
            for (index in 0 until multipart.count) {
                val child = multipart.getBodyPart(index)
                if (isAttachment(child)) continue
                val result = extractBody(child)
                if (child.isMimeType("text/plain") && result.text.isNotBlank()) return result
                if (result.text.isNotBlank()) html = result
            }
            return html ?: ExtractedBody("", "none")
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as? Multipart ?: return ExtractedBody("", "none")
            val text = StringBuilder()
            var htmlCleaned = false
            var mediaOmitted = false
            var truncated = false
            for (index in 0 until multipart.count) {
                val child = multipart.getBodyPart(index)
                if (isAttachment(child)) continue
                if (child.isMimeType("image/*") || child.isMimeType("audio/*") || child.isMimeType("video/*")) {
                    mediaOmitted = true
                    continue
                }
                val result = extractBody(child)
                if (result.text.isNotBlank()) {
                    if (text.isNotEmpty()) text.append("\n\n")
                    text.append(result.text)
                }
                htmlCleaned = htmlCleaned || result.htmlCleaned
                mediaOmitted = mediaOmitted || result.mediaOmitted
                truncated = truncated || result.truncated
                if (text.length > MAX_BODY_CHARS) { truncated = true; break }
            }
            return ExtractedBody(text.toString(), "multipart", htmlCleaned, mediaOmitted, truncated)
        }
        return ExtractedBody("", "none", mediaOmitted = part.isMimeType("image/*") || part.isMimeType("audio/*") || part.isMimeType("video/*"))
    }

    private fun decodeTextPart(part: Part): String {
        val bytes = readBounded(part, MAX_BODY_BYTES)
        val declared = runCatching { ContentType(part.contentType).getParameter("charset") }.getOrNull()
        val candidates = listOfNotNull(declared, "UTF-8", "GB18030", "Big5", "windows-1252", "ISO-8859-1").distinctBy { it.lowercase() }
        for (name in candidates) {
            val decoded = runCatching {
                val charset = java.nio.charset.Charset.forName(name)
                charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrNull()
            if (decoded != null && '\uFFFD' !in decoded) return decoded
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readBounded(part: Part, max: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(max, 32 * 1024))
        part.inputStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < max) {
                val read = input.read(buffer, 0, minOf(buffer.size, max - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun attachmentParts(part: Part): List<Part> {
        val result = mutableListOf<Part>()
        fun visit(current: Part) {
            if (isAttachment(current)) { result += current; return }
            if (current.isMimeType("multipart/*")) {
                val multipart = runCatching { current.content as? Multipart }.getOrNull() ?: return
                for (index in 0 until multipart.count) visit(multipart.getBodyPart(index))
            }
        }
        visit(part)
        return result
    }

    private fun isAttachment(part: Part): Boolean {
        val disposition = part.disposition.orEmpty()
        val fileName = part.fileName
        return disposition.equals(Part.ATTACHMENT, true) ||
            (fileName != null && !disposition.equals(Part.INLINE, true)) ||
            part.isMimeType("application/*") || part.isMimeType("message/rfc822")
    }

    private fun messageByUid(folder: Folder, uid: Long): Message {
        require(uid > 0) { "A positive IMAP UID is required." }
        val uidFolder = folder as? UIDFolder ?: error("This folder does not expose stable IMAP UIDs.")
        return uidFolder.getMessageByUID(uid) ?: error("Message UID $uid does not exist in ${folder.fullName}.")
    }

    private fun <T> withStore(account: EmailServerConfig, block: (Store) -> T): T {
        val session = mailSession(account, smtp = false)
        val protocol = if (account.imapSecurity == "ssl") "imaps" else "imap"
        val store = session.getStore(protocol)
        try {
            store.connect(account.imapHost, account.imapPort, account.username, account.password)
            return block(store)
        } finally {
            if (store.isConnected) store.close()
        }
    }

    private fun <T> withFolder(account: EmailServerConfig, name: String, mode: Int, block: (Folder) -> T): T =
        withStore(account) { store ->
            val folder = store.getFolder(name.ifBlank { "INBOX" })
            require(folder.exists()) { "Mail folder does not exist: ${name.ifBlank { "INBOX" }}" }
            try {
                folder.open(mode)
                block(folder)
            } finally {
                if (folder.isOpen) folder.close(false)
            }
        }

    private fun mailSession(account: EmailServerConfig, smtp: Boolean): Session {
        val properties = Properties()
        properties["mail.mime.address.strict"] = "true"
        properties["mail.mime.decodetext.strict"] = "false"
        properties["mail.mime.decodefilename"] = "true"
        properties["mail.mime.encodefilename"] = "true"
        properties["mail.imap.connectiontimeout"] = TIMEOUT_MS.toString()
        properties["mail.imap.timeout"] = TIMEOUT_MS.toString()
        properties["mail.imaps.connectiontimeout"] = TIMEOUT_MS.toString()
        properties["mail.imaps.timeout"] = TIMEOUT_MS.toString()
        if (account.imapSecurity == "starttls") {
            properties["mail.imap.starttls.enable"] = "true"
            properties["mail.imap.starttls.required"] = "true"
        }
        if (smtp) {
            val prefix = if (account.smtpSecurity == "ssl") "mail.smtps" else "mail.smtp"
            properties["$prefix.auth"] = "true"
            properties["$prefix.connectiontimeout"] = TIMEOUT_MS.toString()
            properties["$prefix.timeout"] = TIMEOUT_MS.toString()
            properties["$prefix.writetimeout"] = TIMEOUT_MS.toString()
            if (account.smtpSecurity == "starttls") {
                properties["mail.smtp.starttls.enable"] = "true"
                properties["mail.smtp.starttls.required"] = "true"
            }
        }
        return Session.getInstance(properties)
    }

    private fun addresses(values: Array<Address>?): JSONArray = JSONArray().also { array ->
        values.orEmpty().forEach { array.put(decodeHeader(it.toString())) }
    }

    private fun addressOf(address: Address): String = (address as? InternetAddress)?.address.orEmpty().ifBlank { address.toString() }
    private fun decodeHeader(value: String): String = runCatching { MimeUtility.decodeText(value) }.getOrDefault(value)
    private fun safeFileName(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._ ()\\-\\u4e00-\\u9fff]"), "_").take(120).ifBlank { "attachment.bin" }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class ExtractedBody(
        val text: String,
        val source: String,
        val htmlCleaned: Boolean = false,
        val mediaOmitted: Boolean = false,
        val truncated: Boolean = false,
    )

    companion object {
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
        private const val MAX_QUARANTINE_BYTES = 100L * 1024 * 1024
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_BODY_CHARS = 120_000
        private const val TIMEOUT_MS = 30_000
    }
}
