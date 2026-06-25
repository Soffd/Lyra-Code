package com.yukisoffd.lyracode.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yukisoffd.lyracode.R
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupOptions(
    val includeProfile: Boolean = true,
    val includeConversations: Boolean = true,
    val includeRoleplay: Boolean = true,
    val includeModelProfiles: Boolean = true,
    val includeMcp: Boolean = true,
    val includeSsh: Boolean = true,
    val includePrompts: Boolean = true,
    val includeSkills: Boolean = true,
    val includeWebDav: Boolean = true,
    val includeFileTransfer: Boolean = true,
    val includeSecrets: Boolean = false,
)

class BackupManager(
    private val context: Context,
    private val settings: AppSettings,
    private val conversationStore: ConversationStore,
) {
    fun exportZip(options: BackupOptions): ByteArray {
        val settingsJson = settings.exportSettingsJson(options.includeSecrets).apply {
            if (!options.includeProfile) {
                remove("userNickname")
                remove("userAvatarPath")
            }
            if (!options.includeModelProfiles) {
                remove("selectedApiProfileId")
                remove("profiles")
            }
            if (!options.includeMcp) remove("mcpServers")
            if (!options.includeSsh) remove("sshServers")
            if (!options.includePrompts) {
                remove("selectedSystemPromptId")
                remove("customSystemPrompts")
                remove("systemPromptConfigs")
                remove("reasoningDepth")
            }
            if (!options.includeWebDav) remove("webDavServers")
            if (!options.includeFileTransfer) remove("fileTransferServers")
            if (!options.includeRoleplay) {
                remove("immersiveRoleplayEnabled")
                remove("selectedRoleplayId")
                remove("roleplayAffections")
            }
        }
        val root = JSONObject()
            .put("schema", "lyra_backup_manifest_v1")
            .put("createdAt", System.currentTimeMillis())
            .put("includeSecrets", options.includeSecrets)
            .put("settings", settingsJson)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(root.toString(2).toByteArray())
            zip.closeEntry()
            if (options.includeConversations) {
                zip.putNextEntry(ZipEntry("conversations.json"))
                zip.write(conversationStore.exportJson().toString(2).toByteArray())
                zip.closeEntry()
            }
            if (options.includeSkills) {
                val skillsRoot = settings.skillsRootDir()
                if (skillsRoot.exists()) {
                    skillsRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(skillsRoot).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("skills/$relative"))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }
            }
            if (options.includeRoleplay) {
                val roleplayRoot = settings.roleplayRootDir()
                if (roleplayRoot.exists()) {
                    roleplayRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(roleplayRoot).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("roleplay/$relative"))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }
            }
        }
        return output.toByteArray()
    }

    fun exportToDownloads(options: BackupOptions): String {
        val bytes = exportZip(options)
        val name = "lyra_backup_${System.currentTimeMillis()}${if (options.includeSecrets) "_with_keys" else ""}.zip"
        saveBytesToDownloads(bytes, name, "application/zip")
        return context.getString(R.string.backup_exported_to, name)
    }

    fun importFromUri(uri: Uri, mode: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(context.getString(R.string.error_cannot_read_backup))
        return importZip(bytes, mode)
    }

    fun importZip(bytes: ByteArray, mode: String): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.length < 240 && !entry.name.contains("..")) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val messages = mutableListOf<String>()
        entries["manifest.json"]?.let { manifestBytes ->
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            manifest.optJSONObject("settings")?.let { messages += settings.importSettingsJson(it, mode) }
        }
        entries["conversations.json"]?.let { messages += conversationStore.importJson(JSONObject(it.toString(Charsets.UTF_8)), mode) }
        val skillEntries = entries.filterKeys { it.startsWith("skills/") }
        if (skillEntries.isNotEmpty()) {
            restoreSkillEntries(skillEntries, mode)
            messages += context.getString(R.string.backup_import_skills, skillEntries.size)
        }
        val roleplayEntries = entries.filterKeys { it.startsWith("roleplay/") }
        if (roleplayEntries.isNotEmpty()) {
            restoreRoleplayEntries(roleplayEntries, mode)
            messages += context.getString(R.string.backup_import_roleplay, roleplayEntries.size)
        }
        return messages.ifEmpty { listOf(context.getString(R.string.backup_no_compatible_data)) }.joinToString("；")
    }

    private fun restoreSkillEntries(entries: Map<String, ByteArray>, mode: String) {
        val root = settings.skillsRootDir()
        if (mode == "replace") root.deleteRecursively()
        root.mkdirs()
        entries.forEach { (path, bytes) ->
            val relative = path.removePrefix("skills/").replace('\\', '/')
            if (relative.isBlank() || relative.contains("..")) return@forEach
            val target = File(root, relative)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    private fun restoreRoleplayEntries(entries: Map<String, ByteArray>, mode: String) {
        val root = settings.roleplayRootDir()
        if (mode == "replace") root.deleteRecursively()
        root.mkdirs()
        entries.forEach { (path, bytes) ->
            val relative = path.removePrefix("roleplay/").replace('\\', '/')
            if (relative.isBlank() || relative.contains("..")) return@forEach
            val target = File(root, relative)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    private fun saveBytesToDownloads(bytes: ByteArray, name: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LyraCode")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error(context.getString(R.string.error_cannot_create_export))
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error(context.getString(R.string.error_cannot_write_export))
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LyraCode").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }
    }
}
