package com.yukisoffd.lyracode.workspace

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.util.ArrayDeque

class GlobalFileManager {
    private val searchIndexLock = Any()
    private var searchIndex: GlobalSearchIndex? = null

    fun listDirectory(path: String = ""): Result<List<WorkspaceFile>> = runCatching {
        val dir = resolve(path)
        require(dir.isDirectory) { "不是目录: $path" }
        dir.listFiles().orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map {
                WorkspaceFile(
                    name = it.name,
                    path = it.toPublicPath(),
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
        val file = resolve(path)
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= maxBytes) { "文件超过 ${maxBytes / 1024 / 1024}MB，无法安全编辑: $path" }
        return file.readText()
    }

    fun readBytes(path: String, maxBytes: Long = MAX_BINARY_BYTES): Result<ByteArray> = runCatching {
        val file = resolve(path)
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= maxBytes) { "文件超过 ${maxBytes / 1024 / 1024}MB: $path" }
        file.readBytes()
    }

    fun writeFile(path: String, content: String): Result<String> = runCatching {
        val file = resolveForWrite(path)
        file.parentFile?.mkdirs()
        if (file.exists() && !file.name.endsWith(".bak", ignoreCase = true)) {
            file.copyTo(File(file.parentFile, "${file.name}.bak"), overwrite = true)
        }
        file.writeText(content)
        invalidateSearchIndex()
        "已写入 ${content.length} 字符并在同目录保留 .bak 备份: ${file.toPublicPath()}"
    }

    fun writeStream(path: String, input: InputStream): Result<Long> = runCatching {
        val file = resolveForWrite(path)
        file.parentFile?.mkdirs()
        val written = file.outputStream().buffered().use { output ->
            input.copyTo(output)
        }
        invalidateSearchIndex()
        written
    }

    fun appendFile(path: String, content: String): Result<String> = runCatching {
        val file = resolveForWrite(path)
        file.parentFile?.mkdirs()
        file.appendText(content)
        invalidateSearchIndex()
        "已追加 ${content.length} 字符: ${file.toPublicPath()}"
    }

    fun createFolder(path: String): Result<String> = runCatching {
        val dir = resolveForWrite(path)
        require(dir.mkdirs() || dir.isDirectory) { "无法创建目录: $path" }
        invalidateSearchIndex()
        "已创建目录: ${dir.toPublicPath()}"
    }

    fun delete(path: String): Result<String> = runCatching {
        val file = resolve(path)
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        require(ok) { "删除失败: $path" }
        invalidateSearchIndex()
        "已删除: ${file.toPublicPath()}"
    }

    fun renameMove(from: String, to: String): Result<String> = runCatching {
        val source = resolve(from)
        val target = resolveForWrite(to)
        target.parentFile?.mkdirs()
        require(source.renameTo(target)) { "移动或重命名失败: $from -> $to" }
        invalidateSearchIndex()
        "已移动/重命名: ${source.toPublicPath()} -> ${target.toPublicPath()}"
    }

    fun searchFiles(query: String, limit: Int = SEARCH_LIMIT): Result<List<WorkspaceFile>> = runCatching {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Search query must not be empty." }
        val startedAt = System.currentTimeMillis()
        val resultLimit = limit.coerceIn(1, SEARCH_LIMIT)
        var cacheHit = false
        var indexedEntries = 0
        val matches = synchronized(searchIndexLock) {
            val index = searchIndex ?: GlobalSearchIndex(publicRoot().canonicalFile).also {
                require(it.root.isDirectory) {
                    "无法读取 Android 共享存储，请为 Lyra Code 授予所有文件访问权限。"
                }
                it.pendingDirectories.add(it.root to "")
                searchIndex = it
            }
            cacheHit = index.entries.isNotEmpty()
            try {
                extendSearchIndex(index, FileSearchMatcher(cleanQuery), resultLimit)
            } catch (error: Throwable) {
                searchIndex = null
                throw error
            }
            indexedEntries = index.entries.size
            searchWorkspaceFileIndex(
                files = index.entries,
                query = cleanQuery,
                limit = resultLimit,
                includeDirectories = true,
            ).map {
                WorkspaceFile(
                    name = it.name,
                    path = it.relativePath,
                    directory = it.directory,
                    size = it.size,
                    modifiedAt = it.modifiedAt,
                )
            }
        }
        Log.d(
            TAG,
            "global_search query='$cleanQuery' cacheHit=$cacheHit indexed=$indexedEntries " +
                "results=${matches.size} durationMs=${System.currentTimeMillis() - startedAt}",
        )
        matches
    }

    private fun extendSearchIndex(index: GlobalSearchIndex, matcher: FileSearchMatcher, limit: Int) {
        if (index.complete) return
        val startedAt = System.currentTimeMillis()
        val entriesBefore = index.entries.size
        var strictMatches = index.entries.count {
            matcher.strictMatchScore(it.name, it.relativePath) != null
        }
        while (index.pendingDirectories.isNotEmpty() && strictMatches < limit) {
            if (index.visitedDirectories >= MAX_SEARCH_DIRECTORIES || index.entries.size >= MAX_SEARCH_ENTRIES) {
                index.truncated = true
                index.complete = true
                index.pendingDirectories.clear()
                break
            }
            val (directory, prefix) = index.pendingDirectories.removeFirst()
            index.visitedDirectories++
            val children = directory.listFiles()
            if (children == null) {
                if (directory == index.root) {
                    error("无法读取 Android 共享存储，请为 Lyra Code 授予所有文件访问权限。")
                }
                continue
            }
            for (child in children) {
                if (index.entries.size >= MAX_SEARCH_ENTRIES) {
                    index.truncated = true
                    index.complete = true
                    index.pendingDirectories.clear()
                    break
                }
                val relativePath = if (prefix.isBlank()) child.name else "$prefix/${child.name}"
                if (shouldPrune(relativePath)) continue
                val publicPath = "${index.root.invariantPath()}/$relativePath"
                val entry = when {
                    child.isDirectory -> {
                        if (!isSymbolicLink(child)) index.pendingDirectories.add(child to relativePath)
                        WorkspaceFileReference(
                            name = child.name,
                            relativePath = publicPath,
                            uri = child.toURI().toString(),
                            directory = true,
                            modifiedAt = child.lastModified().coerceAtLeast(0L),
                        )
                    }
                    child.isFile -> WorkspaceFileReference(
                        name = child.name,
                        relativePath = publicPath,
                        uri = child.toURI().toString(),
                        size = child.length().coerceAtLeast(0L),
                        modifiedAt = child.lastModified().coerceAtLeast(0L),
                    )
                    else -> null
                }
                if (entry != null) {
                    index.entries += entry
                    if (matcher.strictMatchScore(entry.name, entry.relativePath) != null) strictMatches++
                }
            }
        }
        if (index.pendingDirectories.isEmpty()) index.complete = true
        Log.d(
            TAG,
            "global_search_index added=${index.entries.size - entriesBefore} entries=${index.entries.size} " +
                "directories=${index.visitedDirectories} complete=${index.complete} truncated=${index.truncated} " +
                "durationMs=${System.currentTimeMillis() - startedAt}",
        )
    }

    private fun invalidateSearchIndex() {
        synchronized(searchIndexLock) {
            searchIndex = null
        }
    }

    private fun shouldPrune(relativePath: String): Boolean {
        val clean = relativePath.replace('\\', '/')
        return clean.equals("Android/data", ignoreCase = true) ||
            clean.startsWith("Android/data/", ignoreCase = true) ||
            clean.equals("Android/obb", ignoreCase = true) ||
            clean.startsWith("Android/obb/", ignoreCase = true) ||
            clean.substringBefore('/').startsWith(".Trash", ignoreCase = true) ||
            clean.substringBefore('/').startsWith(".MediaTrash", ignoreCase = true)
    }

    private fun isSymbolicLink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun resolve(path: String): File {
        val file = resolveForWrite(path)
        if (!file.exists()) throw FileNotFoundException("不存在: $path")
        return file
    }

    private fun resolveForWrite(path: String): File {
        val clean = normalize(path)
        val root = publicRoot()
        val file = if (clean.isBlank()) root else File(root, clean)
        val canonicalRoot = root.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path == canonicalRoot.path || canonicalFile.path.startsWith(canonicalRoot.path + File.separator)) {
            "全局文件工具只能访问 Android 共享存储: /storage/emulated/0"
        }
        val relative = canonicalFile.relativeTo(canonicalRoot).invariantSeparatorsPath
        require(relative.isBlank() || !relative.startsWith("Android/data") && !relative.startsWith("Android/obb")) {
            "不允许访问 Android/data 或 Android/obb"
        }
        return canonicalFile
    }

    private fun normalize(path: String): String {
        var clean = path.trim().replace('\\', '/')
        if (clean.isBlank() || clean == "." || clean == "/" || clean.equals("download", true) || clean.equals("downloads", true)) {
            return if (clean.equals("download", true) || clean.equals("downloads", true)) Environment.DIRECTORY_DOWNLOADS else ""
        }
        clean = clean.removePrefix("/sdcard/").removePrefix("sdcard/")
        clean = clean.removePrefix("/storage/emulated/0/").removePrefix("storage/emulated/0/")
        if (clean.equals("download", true) || clean.equals("downloads", true)) return Environment.DIRECTORY_DOWNLOADS
        clean = clean.trim('/')
        val parts = clean.split('/').filter { it.isNotBlank() && it != "." }
        require(parts.none { it == ".." }) { "路径不能包含 .." }
        require(parts.firstOrNull() != "data") { "不允许访问 /data" }
        return parts.joinToString("/")
    }

    private fun publicRoot(): File = Environment.getExternalStorageDirectory()

    private fun File.toPublicPath(): String {
        val root = publicRoot().canonicalFile
        val file = canonicalFile
        val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault(file.path)
        return if (relative.isBlank() || relative == ".") "." else relative
    }

    private fun File.invariantPath(): String = absolutePath.replace(File.separatorChar, '/')

    private data class GlobalSearchIndex(
        val root: File,
        val entries: ArrayList<WorkspaceFileReference> = ArrayList(),
        val pendingDirectories: ArrayDeque<Pair<File, String>> = ArrayDeque(),
        var visitedDirectories: Int = 0,
        var complete: Boolean = false,
        var truncated: Boolean = false,
    )

    companion object {
        private const val TAG = "GlobalFileSearch"
        private const val MAX_READ_BYTES = 1_048_576L
        private const val MAX_EDIT_BYTES = 16L * 1024L * 1024L
        private const val MAX_BINARY_BYTES = 200L * 1024L * 1024L
        private const val SEARCH_LIMIT = 120
        private const val MAX_SEARCH_DIRECTORIES = 100_000
        private const val MAX_SEARCH_ENTRIES = 500_000
    }
}
