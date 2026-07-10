package com.yukisoffd.lyracode.workspace

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque

internal class WorkspaceFileIndexer(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
) {
    private val cacheLock = Any()
    @Volatile
    private var cachedWorkspaceUri = ""
    @Volatile
    private var cachedAt = 0L
    @Volatile
    private var cachedFiles: List<WorkspaceFileReference>? = null

    fun invalidate() {
        cachedWorkspaceUri = ""
        cachedAt = 0L
        cachedFiles = null
    }

    fun search(query: String, limit: Int): List<WorkspaceFileReference> {
        val workspaceUri = workspaceManager.activeWorkspaceUri()
        if (workspaceUri.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val files = synchronized(cacheLock) {
            val cached = cachedFiles
            if (cached != null && cachedWorkspaceUri == workspaceUri && now - cachedAt < INDEX_CACHE_TTL_MS) {
                cached
            } else {
                val rebuilt = buildIndex(Uri.parse(workspaceUri))
                if (workspaceManager.activeWorkspaceUri() == workspaceUri) {
                    cachedWorkspaceUri = workspaceUri
                    cachedAt = System.currentTimeMillis()
                    cachedFiles = rebuilt
                }
                rebuilt
            }
        }
        return searchWorkspaceFileIndex(files, query, limit)
    }

    private fun buildIndex(treeUri: Uri): List<WorkspaceFileReference> {
        val startedAt = System.currentTimeMillis()
        val direct = buildDirectIndex()
        if (direct?.complete == true) {
            Log.d(TAG, "workspace_index source=direct files=${direct.files.size} complete=${direct.complete} durationMs=${System.currentTimeMillis() - startedAt}")
            return direct.files
        }

        val saf = buildDocumentsContractIndex(treeUri)
        val fallback = if (saf.failures > 0 || !saf.complete || saf.files.isEmpty()) {
            buildDocumentFileIndex()
        } else {
            null
        }
        val result = LinkedHashMap<String, WorkspaceFileReference>().apply {
            direct?.files?.forEach { put(it.relativePath, it) }
            saf.files.forEach { put(it.relativePath, it) }
            fallback?.files?.forEach { putIfAbsent(it.relativePath, it) }
        }.values.toList()
        Log.d(
            TAG,
            "workspace_index source=hybrid files=${result.size} directComplete=${direct?.complete} safFailures=${saf.failures} safComplete=${saf.complete} fallback=${fallback != null} durationMs=${System.currentTimeMillis() - startedAt}",
        )
        return result
    }

    private fun buildDirectIndex(): IndexBuildResult? {
        val rootPath = workspaceManager.termuxRootPath() ?: return null
        val root = File(rootPath)
        if (!root.isDirectory || root.listFiles() == null) return null

        val files = ArrayList<WorkspaceFileReference>()
        val queue = ArrayDeque<Pair<File, String>>()
        queue.add(root to "")
        var visitedDirectories = 0
        var complete = true
        while (queue.isNotEmpty()) {
            if (visitedDirectories >= MAX_INDEX_DIRECTORIES || files.size >= MAX_INDEX_FILES) {
                complete = false
                break
            }
            val (directory, prefix) = queue.removeFirst()
            visitedDirectories++
            val children = directory.listFiles()
            if (children == null) {
                complete = false
                continue
            }
            children.forEach { child ->
                if (files.size >= MAX_INDEX_FILES) {
                    complete = false
                    return@forEach
                }
                val name = child.name
                val path = joinPath(prefix, name)
                when {
                    child.isDirectory && !isSymbolicLink(child) -> queue.add(child to path)
                    child.isFile -> files += WorkspaceFileReference(
                        name = name,
                        relativePath = path,
                        uri = child.toURI().toString(),
                        size = child.length().coerceAtLeast(0L),
                    )
                }
            }
        }
        return IndexBuildResult(files, complete)
    }

    private fun buildDocumentsContractIndex(treeUri: Uri): SafIndexBuildResult {
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrElse { return SafIndexBuildResult(emptyList(), complete = false, failures = 1) }
        val files = ArrayList<WorkspaceFileReference>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocumentId to "")
        var visitedDirectories = 0
        var failures = 0
        var complete = true

        while (queue.isNotEmpty()) {
            if (visitedDirectories >= MAX_INDEX_DIRECTORIES || files.size >= MAX_INDEX_FILES) {
                complete = false
                break
            }
            val (documentId, prefix) = queue.removeFirst()
            visitedDirectories++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            runCatching {
                context.contentResolver.query(childrenUri, INDEX_PROJECTION, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        if (files.size >= MAX_INDEX_FILES) {
                            complete = false
                            break
                        }
                        val childId = cursor.stringOrEmpty(idIndex)
                        val name = cursor.stringOrEmpty(nameIndex)
                        if (childId.isBlank() || name.isBlank()) continue
                        val path = joinPath(prefix, name)
                        if (cursor.stringOrEmpty(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(childId to path)
                        } else {
                            files += WorkspaceFileReference(
                                name = name,
                                relativePath = path,
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                                size = cursor.longOrZero(sizeIndex).coerceAtLeast(0L),
                            )
                        }
                    }
                } ?: run {
                    failures++
                    complete = false
                }
            }.onFailure {
                failures++
                complete = false
                Log.w(TAG, "workspace_index_query_failed path='$prefix' uri=$childrenUri", it)
            }
        }
        return SafIndexBuildResult(files, complete, failures)
    }

    private fun buildDocumentFileIndex(): IndexBuildResult {
        val root = workspaceManager.root() ?: return IndexBuildResult(emptyList(), complete = false)
        val files = ArrayList<WorkspaceFileReference>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(root to "")
        var visitedDirectories = 0
        var complete = true
        while (queue.isNotEmpty()) {
            if (visitedDirectories >= MAX_INDEX_DIRECTORIES || files.size >= MAX_INDEX_FILES) {
                complete = false
                break
            }
            val (directory, prefix) = queue.removeFirst()
            visitedDirectories++
            val children = runCatching { directory.listFiles() }
                .onFailure { complete = false }
                .getOrDefault(emptyArray())
            children.forEach { child ->
                if (files.size >= MAX_INDEX_FILES) {
                    complete = false
                    return@forEach
                }
                val name = child.name.orEmpty()
                if (name.isBlank()) return@forEach
                val path = joinPath(prefix, name)
                when {
                    child.isDirectory -> queue.add(child to path)
                    child.isFile -> files += WorkspaceFileReference(
                        name = name,
                        relativePath = path,
                        uri = child.uri.toString(),
                        size = child.length().coerceAtLeast(0L),
                    )
                }
            }
        }
        return IndexBuildResult(files, complete)
    }

    private fun isSymbolicLink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun joinPath(parent: String, child: String): String =
        if (parent.isBlank()) child else "$parent/$child"

    private data class IndexBuildResult(
        val files: List<WorkspaceFileReference>,
        val complete: Boolean,
    )

    private data class SafIndexBuildResult(
        val files: List<WorkspaceFileReference>,
        val complete: Boolean,
        val failures: Int,
    )

    private companion object {
        const val TAG = "WorkspaceFileIndexer"
        const val INDEX_CACHE_TTL_MS = 5 * 60_000L
        const val MAX_INDEX_DIRECTORIES = 100_000
        const val MAX_INDEX_FILES = 500_000
        val INDEX_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}

internal fun searchWorkspaceFileIndex(
    files: List<WorkspaceFileReference>,
    query: String,
    limit: Int,
): List<WorkspaceFileReference> {
    val matcher = FileSearchMatcher(query)
    return files.asSequence()
        .filter { matcher.matches(it.name, it.relativePath) }
        .map { matcher.score(it.name, it.relativePath) to it }
        .sortedWith(
            compareByDescending<Pair<Int, WorkspaceFileReference>> { it.first }
                .thenBy { it.second.relativePath.length }
                .thenBy { it.second.relativePath.lowercase() },
        )
        .take(limit.coerceIn(1, 200))
        .map { it.second }
        .toList()
}

private fun android.database.Cursor.stringOrEmpty(index: Int): String =
    if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

private fun android.database.Cursor.longOrZero(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else 0L
