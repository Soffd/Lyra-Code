package com.yukisoffd.lyracode.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.yukisoffd.lyracode.data.AppSettings

class WorkspaceManager(
    private val context: Context,
    @Suppress("unused") private val settings: AppSettings,
) {
    private var activeWorkspaceUri: String = ""

    fun persistWorkspace(uri: Uri): String {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        activeWorkspaceUri = uri.toString()
        return activeWorkspaceUri
    }

    fun setActiveWorkspaceUri(uri: String?) {
        activeWorkspaceUri = uri.orEmpty()
    }

    fun activeWorkspaceUri(): String = activeWorkspaceUri

    fun rootUri(): Uri? = activeWorkspaceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)

    fun root(): DocumentFile? {
        val uri = rootUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun displayName(): String = root()?.name ?: "未选择工作目录"

    fun termuxRootPath(): String? {
        val uri = rootUri() ?: return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val split = docId.split(":", limit = 2)
        if (split.size != 2) return null
        return when (split[0]) {
            "primary" -> "/storage/emulated/0/${split[1].trimStart('/')}"
            else -> null
        }
    }

    fun termuxPath(relativePath: String): String? {
        val root = termuxRootPath() ?: return null
        val normalized = relativePath.trim('/').replace('\\', '/')
        return if (normalized.isBlank()) root else "$root/$normalized"
    }
}
