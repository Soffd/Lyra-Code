package com.yukisoffd.lyracode.workspace

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.ArrayDeque

data class WorkspaceFile(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
)

class NativeFileManager(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
) {
    fun hasWorkspaceRoot(): Boolean = workspaceManager.rootUri() != null

    fun listDirectory(path: String = ""): Result<List<WorkspaceFile>> = runCatching {
        val dir = resolve(path) ?: throw FileNotFoundException("目录不存在: $path")
        require(dir.isDirectory) { "不是目录: $path" }
        dir.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
            .map {
                WorkspaceFile(
                    name = it.name.orEmpty(),
                    path = joinPath(path, it.name.orEmpty()),
                    directory = it.isDirectory,
                    size = it.length(),
                    modifiedAt = it.lastModified(),
                )
            }
    }

    fun readFile(path: String): Result<String> = runCatching {
        readText(path, MAX_READ_BYTES)
    }

    fun readFileForEdit(path: String): Result<String> = runCatching {
        readText(path, MAX_EDIT_BYTES)
    }

    private fun readText(path: String, maxBytes: Long): String {
        val file = resolve(path) ?: throw FileNotFoundException("文件不存在: $path")
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= maxBytes) { "文件超过 ${maxBytes / 1024 / 1024}MB，无法安全编辑: $path" }
        return context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("无法读取: $path")
    }

    fun readBytes(path: String, maxBytes: Long = MAX_BINARY_BYTES): Result<ByteArray> = runCatching {
        val file = resolve(path) ?: throw FileNotFoundException("文件不存在: $path")
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= maxBytes) { "文件超过 ${maxBytes / 1024 / 1024}MB: $path" }
        context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("无法读取: $path")
    }

    fun writeFile(path: String, content: String): Result<String> = runCatching {
        backupExistingTextFile(path)
        val file = findOrCreateFile(path)
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: throw FileNotFoundException("无法写入: $path")
        "已写入 ${content.length} 字符: $path"
    }

    fun writeBytes(path: String, bytes: ByteArray): Result<String> = runCatching {
        val file = findOrCreateFile(path)
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: throw FileNotFoundException("无法写入: $path")
        "已写入 ${bytes.size} 字节: $path"
    }

    fun writeStream(path: String, input: InputStream): Result<Long> = runCatching {
        val file = findOrCreateFile(path)
        context.contentResolver.openOutputStream(file.uri, "wt")?.buffered()?.use { output ->
            input.copyTo(output)
        } ?: throw FileNotFoundException("无法写入: $path")
    }

    fun appendFile(path: String, content: String): Result<String> = runCatching {
        backupExistingTextFile(path)
        val file = findOrCreateFile(path)
        context.contentResolver.openOutputStream(file.uri, "wa")?.bufferedWriter()?.use { it.write(content) }
            ?: throw FileNotFoundException("无法追加: $path")
        "已追加 ${content.length} 字符: $path"
    }

    fun createFolder(path: String): Result<String> = runCatching {
        val clean = normalize(path)
        require(clean.isNotBlank()) { "目录名不能为空" }
        val segments = clean.split("/")
        val folderName = segments.last()
        val parent = resolve(segments.dropLast(1).joinToString("/")) ?: throw FileNotFoundException("父目录不存在")
        parent.findFile(folderName) ?: parent.createDirectory(folderName)
            ?: throw FileNotFoundException("无法创建目录: $path")
        "已创建目录: $path"
    }

    fun delete(path: String): Result<String> = runCatching {
        val file = resolve(path) ?: throw FileNotFoundException("不存在: $path")
        require(file.delete()) { "删除失败，非空目录请改用 Termux rm -rf 并确认风险" }
        "已删除: $path"
    }

    fun renameMove(from: String, to: String): Result<String> = runCatching {
        val source = resolve(from) ?: throw FileNotFoundException("源不存在: $from")
        val sourceParent = parentPath(from)
        val targetParent = parentPath(to)
        require(sourceParent == targetParent) { "SAF 原生工具只支持同目录重命名，跨目录移动请使用 Termux" }
        require(source.renameTo(normalize(to).substringAfterLast("/"))) { "重命名失败: $from" }
        "已重命名: $from -> $to"
    }

    fun searchFiles(query: String, basePath: String = ""): Result<List<WorkspaceFile>> = runCatching {
        val startedAt = System.currentTimeMillis()
        val cleanBasePath = normalize(basePath)
        val base = resolve(cleanBasePath) ?: throw FileNotFoundException("目录不存在: $basePath")
        val matcher = FileSearchMatcher(query)
        val results = LinkedHashMap<String, WorkspaceFile>()
        val stats = SearchStats(query = query, basePath = cleanBasePath, rootUri = workspaceManager.rootUri()?.toString().orEmpty())
        Log.d(TAG, "search_start query='$query' base='$cleanBasePath' root='${stats.rootUri}'")
        searchWithDocumentsContract(cleanBasePath, matcher, results, stats)
        if (results.size < SEARCH_LIMIT) {
            searchWithDocumentFile(base, cleanBasePath, matcher, results, stats)
        }
        if (results.isEmpty() && cleanBasePath.isBlank()) {
            fuzzyPathCandidates(matcher).forEach { candidate ->
                resolve(candidate)?.let {
                    results[candidate] = WorkspaceFile(it.name.orEmpty(), candidate, it.isDirectory, it.length(), it.lastModified())
                    stats.candidateHits++
                }
            }
        }
        val sorted = results.values.sortedWith(searchComparator(matcher)).take(SEARCH_LIMIT)
        Log.d(
            TAG,
            "search_end query='$query' base='$cleanBasePath' results=${sorted.size} " +
                "safVisited=${stats.safVisited} safChildren=${stats.safChildren} safErrors=${stats.safErrors} " +
                "docVisited=${stats.documentFileVisited} docChildren=${stats.documentFileChildren} " +
                "candidateHits=${stats.candidateHits} durationMs=${System.currentTimeMillis() - startedAt} " +
                "sample=${sorted.take(8).joinToString { it.path }}",
        )
        sorted
    }

    fun fileInfo(path: String): Result<String> = runCatching {
        val file = resolve(path) ?: throw FileNotFoundException("不存在: $path")
        val clean = normalize(path)
        """
        path: ${clean.ifBlank { "." }}
        name: ${file.name}
        type: ${if (file.isDirectory) "directory" else "file"}
        size: ${file.length()}
        modifiedAt: ${file.lastModified()}
        uri: ${file.uri}
        termuxPath: ${workspaceManager.termuxPath(clean).orEmpty()}
        """.trimIndent()
    }

    private fun findOrCreateFile(path: String): DocumentFile {
        val clean = normalize(path)
        require(clean.isNotBlank()) { "文件路径不能为空" }
        val parent = findOrCreateDirectory(parentPath(clean))
        val name = clean.substringAfterLast("/")
        parent.findFile(name)?.let { return it }
        val created = parent.createFile(mimeFor(name), name)
            ?: throw FileNotFoundException("无法创建文件: $path")
        if (created.name != name) {
            val renamed = created.renameTo(name)
            val resolved = parent.findFile(name)
            if (!renamed || resolved == null) {
                val actual = created.name.orEmpty()
                created.delete()
                throw FileNotFoundException("SAF 创建文件时被系统改名为 $actual，无法创建目标文件名: $name")
            }
            return resolved
        }
        return created
    }

    private fun backupExistingTextFile(path: String) {
        if (path.endsWith(".bak", ignoreCase = true)) return
        val source = resolve(path)?.takeIf { it.isFile } ?: return
        val backup = findOrCreateFile("${normalize(path)}.bak")
        context.contentResolver.openInputStream(source.uri)?.buffered()?.use { input ->
            context.contentResolver.openOutputStream(backup.uri, "wt")?.buffered()?.use { output ->
                input.copyTo(output)
            } ?: throw FileNotFoundException("无法写入备份: $path.bak")
        } ?: throw FileNotFoundException("无法读取原文件以生成备份: $path")
    }

    private fun findOrCreateDirectory(path: String): DocumentFile {
        var current = workspaceManager.root() ?: throw FileNotFoundException("未选择工作目录")
        val clean = normalize(path)
        if (clean.isBlank()) return current
        clean.split("/").forEach { segment ->
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment)
                    ?: throw FileNotFoundException("无法创建目录: $segment")
                existing.isDirectory -> existing
                else -> throw FileNotFoundException("父路径不是目录: $segment")
            }
        }
        return current
    }

    private fun resolve(path: String): DocumentFile? {
        var current = workspaceManager.root() ?: return null
        val clean = normalize(path)
        if (clean.isBlank()) return current
        for (segment in clean.split("/")) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun searchWithDocumentsContract(
        basePath: String,
        matcher: FileSearchMatcher,
        results: LinkedHashMap<String, WorkspaceFile>,
        stats: SearchStats,
    ) {
        val treeUri = workspaceManager.rootUri() ?: run {
            Log.d(TAG, "saf_skip no_root_uri")
            return
        }
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .onFailure { Log.w(TAG, "saf_tree_doc_id_failed uri=$treeUri", it) }
            .getOrNull() ?: return
        val baseDocId = documentIdForRelativePath(rootDocId, basePath)
        Log.d(TAG, "saf_start rootDocId='$rootDocId' baseDocId='$baseDocId'")
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(baseDocId to basePath)
        var visited = 0
        while (queue.isNotEmpty() && results.size < SEARCH_LIMIT && visited < SEARCH_VISIT_LIMIT) {
            val (docId, currentPath) = queue.removeFirst()
            visited++
            stats.safVisited++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val children = queryChildren(childrenUri, currentPath, stats)
            stats.safChildren += children.size
            children.forEach { child ->
                val childPath = joinPath(currentPath, child.name)
                if (matcher.matches(child.name, childPath)) {
                    results[childPath] = WorkspaceFile(child.name, childPath, child.directory, child.size, child.modifiedAt)
                }
                if (child.directory) queue.add(child.documentId to childPath)
            }
        }
        Log.d(TAG, "saf_done visited=${stats.safVisited} children=${stats.safChildren} results=${results.size} queue=${queue.size}")
    }

    private fun queryChildren(childrenUri: Uri, currentPath: String, stats: SearchStats): List<DocumentEntry> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getStringOrEmpty(idIndex)
                        val name = cursor.getStringOrEmpty(nameIndex)
                        if (id.isBlank() || name.isBlank()) continue
                        val mime = cursor.getStringOrEmpty(mimeIndex)
                        add(
                            DocumentEntry(
                                documentId = id,
                                name = name,
                                directory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                                size = cursor.getLongOrZero(sizeIndex),
                                modifiedAt = cursor.getLongOrZero(modifiedIndex),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.onFailure {
            stats.safErrors++
            Log.w(TAG, "saf_query_failed path='$currentPath' uri=$childrenUri", it)
        }.getOrDefault(emptyList())
    }

    private fun searchWithDocumentFile(
        base: DocumentFile,
        basePath: String,
        matcher: FileSearchMatcher,
        results: LinkedHashMap<String, WorkspaceFile>,
        stats: SearchStats,
    ) {
        Log.d(TAG, "documentfile_start base='$basePath' existingResults=${results.size}")
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(base to basePath)
        var visited = 0
        while (queue.isNotEmpty() && results.size < SEARCH_LIMIT && visited < SEARCH_VISIT_LIMIT) {
            val (dir, currentPath) = queue.removeFirst()
            visited++
            stats.documentFileVisited++
            val children = runCatching { dir.listFiles() }
                .onFailure { Log.w(TAG, "documentfile_list_failed path='$currentPath' uri=${dir.uri}", it) }
                .getOrDefault(emptyArray())
            stats.documentFileChildren += children.size
            children.forEach { child ->
                val name = child.name.orEmpty()
                if (name.isBlank()) return@forEach
                val childPath = joinPath(currentPath, name)
                if (matcher.matches(name, childPath)) {
                    results[childPath] = WorkspaceFile(name, childPath, child.isDirectory, child.length(), child.lastModified())
                }
                if (child.isDirectory) queue.add(child to childPath)
            }
        }
        Log.d(TAG, "documentfile_done visited=${stats.documentFileVisited} children=${stats.documentFileChildren} results=${results.size} queue=${queue.size}")
    }

    private fun fuzzyPathCandidates(matcher: FileSearchMatcher): List<String> {
        return matcher.rawTerms
            .filter { it.contains("/") }
            .flatMap { term ->
                val clean = runCatching { normalize(term) }.getOrNull().orEmpty()
                listOf(clean, clean.substringAfterLast("/", ""))
            }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun documentIdForRelativePath(rootDocId: String, relativePath: String): String {
        if (relativePath.isBlank()) return rootDocId
        return when {
            rootDocId.endsWith(":") -> rootDocId + relativePath
            rootDocId.contains(":") -> "$rootDocId/$relativePath"
            else -> "$rootDocId:$relativePath"
        }
    }

    private fun searchComparator(matcher: FileSearchMatcher): Comparator<WorkspaceFile> {
        return compareByDescending<WorkspaceFile> { matcher.score(it.name, it.path) }
            .thenBy { it.path.length }
            .thenBy { it.path.lowercase() }
    }

    private fun normalize(path: String): String {
        var clean = path.replace('\\', '/').trim()
        if (clean.isBlank() || clean == "." || clean == "./" || clean == "/") return ""
        if (clean.startsWith("./")) clean = clean.removePrefix("./")

        workspaceManager.termuxRootPath()?.trimEnd('/')?.let { root ->
            val aliases = listOf(root, root.replace("/storage/emulated/0", "/sdcard"))
            aliases.forEach { alias ->
                when {
                    clean == alias -> return ""
                    clean.startsWith("$alias/") -> {
                        clean = clean.removePrefix("$alias/")
                        return normalizeRelative(clean)
                    }
                }
            }
        }

        require(!clean.startsWith("/data/data/com.termux")) {
            "文件工具只能访问 Lyra Code 工作目录，不能访问 Termux 私有目录。请使用相对路径或工作区路径。"
        }
        require(!clean.startsWith("/data/data/")) {
            "文件工具只能访问 Lyra Code 工作目录，不能访问 Android 应用私有目录。"
        }
        require(!clean.startsWith("/")) {
            "绝对路径不在当前工作目录内: $path。请改用相对路径，例如 . 或 src/main.py。"
        }
        return normalizeRelative(clean)
    }

    private fun normalizeRelative(path: String): String {
        val parts = path.trim('/').split('/').filter { it.isNotBlank() && it != "." }
        require(parts.none { it == ".." }) { "路径不能包含 .." }
        return parts.joinToString("/")
    }

    private fun parentPath(path: String): String = normalize(path).substringBeforeLast("/", missingDelimiterValue = "")

    private fun joinPath(parent: String, child: String): String {
        val cleanParent = normalize(parent)
        return if (cleanParent.isBlank()) child else "$cleanParent/$child"
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "json" -> "application/json"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    companion object {
        private const val TAG = "LyraSearch"
        private const val MAX_READ_BYTES = 1_048_576L
        private const val MAX_EDIT_BYTES = 16L * 1024L * 1024L
        private const val MAX_BINARY_BYTES = 200L * 1024L * 1024L
        private const val SEARCH_LIMIT = 200
        private const val SEARCH_VISIT_LIMIT = 10_000
    }
}

private data class SearchStats(
    val query: String,
    val basePath: String,
    val rootUri: String,
    var safVisited: Int = 0,
    var safChildren: Int = 0,
    var safErrors: Int = 0,
    var documentFileVisited: Int = 0,
    var documentFileChildren: Int = 0,
    var candidateHits: Int = 0,
)

private fun Cursor.getStringOrEmpty(index: Int): String {
    return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
}

private fun Cursor.getLongOrZero(index: Int): Long {
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
}

private data class DocumentEntry(
    val documentId: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
)

internal class FileSearchMatcher(query: String) {
    val rawTerms: List<String> = query
        .replace('\\', '/')
        .split(Regex("\\s+"))
        .map { it.trim().trim('"', '\'', '`') }
        .filter { it.isNotBlank() }

    private val terms = rawTerms.map { normalizeToken(it) }.filter { it.isNotBlank() }

    fun matches(name: String, path: String): Boolean {
        if (terms.isEmpty()) return true
        val normalizedName = normalizeToken(name)
        val normalizedPath = normalizeToken(path)
        return terms.all { term ->
            normalizedName.contains(term) ||
                normalizedPath.contains(term) ||
                fuzzyContains(normalizedName, term) ||
                fuzzyContains(normalizedPath, term)
        }
    }

    fun score(name: String, path: String): Int {
        if (terms.isEmpty()) return 1
        if (!matches(name, path)) return 0
        val normalizedName = normalizeToken(name)
        val normalizedPath = normalizeToken(path)
        return terms.sumOf { term ->
            when {
                normalizedName == term -> 100
                normalizedPath.endsWith("/$term") -> 90
                normalizedName.startsWith(term) -> 80
                normalizedName.contains(term) -> 70
                normalizedPath.contains(term) -> 50
                fuzzyContains(normalizedName, term) -> 30
                fuzzyContains(normalizedPath, term) -> 20
                else -> 0
            }
        }
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase()
            .replace('\\', '/')
            .replace(Regex("[_\\-.]+"), "")
            .trim('/')
    }

    private fun fuzzyContains(haystack: String, needle: String): Boolean {
        if (needle.length < 3) return false
        var index = 0
        for (char in haystack) {
            if (char == needle[index]) index++
            if (index == needle.length) return true
        }
        return false
    }
}
