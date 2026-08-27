package com.yukisoffd.lyracode.filemanager

import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

internal data class LocalFileEntry(
    val file: File,
    val name: String = file.name,
    val directory: Boolean = file.isDirectory,
    val size: Long = file.length(),
    val modifiedAt: Long = file.lastModified(),
)

internal data class TextFileContent(
    val text: String,
    val hasUtf8Errors: Boolean,
)

internal object LocalFileOperations {
    val storageRoot: File
        get() = Environment.getExternalStorageDirectory()

    fun list(directory: File): Result<List<LocalFileEntry>> = runCatching {
        require(directory.isDirectory) { "Not a directory: ${directory.path}" }
        directory.listFiles().orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map(::LocalFileEntry)
    }

    fun search(
        directory: File,
        query: String,
        includeFiles: Boolean = true,
        includeDirectories: Boolean = true,
        maxResults: Int = 500,
    ): Result<List<LocalFileEntry>> = runCatching {
        require(directory.isDirectory) { "Not a directory: ${directory.path}" }
        val keyword = query.trim()
        require(keyword.isNotEmpty()) { "Search query is empty" }
        require(includeFiles || includeDirectories) { "At least one result type must be enabled" }
        val pending = ArrayDeque<File>()
        val visited = mutableSetOf<String>()
        val results = mutableListOf<LocalFileEntry>()
        pending.add(directory)
        while (pending.isNotEmpty() && results.size < maxResults) {
            val current = pending.removeFirst()
            val canonicalPath = runCatching { current.canonicalPath }.getOrElse { current.absolutePath }
            if (!visited.add(canonicalPath)) continue
            current.listFiles().orEmpty().forEach { child ->
                if (child.isDirectory) pending.addLast(child)
                val includedType = if (child.isDirectory) includeDirectories else includeFiles
                if (includedType && child.name.contains(keyword, ignoreCase = true) && results.size < maxResults) {
                    results += LocalFileEntry(child)
                }
            }
        }
        results
    }

    fun readUtf8(file: File, allowBinaryPreview: Boolean = false): Result<TextFileContent> = runCatching {
        require(file.isFile) { "Not a file: ${file.path}" }
        require(file.length() <= MAX_EDIT_BYTES) { "File is larger than 16 MiB" }
        val bytes = file.readBytes()
        val hasNullBytes = bytes.any { it == 0.toByte() }
        require(allowBinaryPreview || !hasNullBytes) { "Binary files cannot be edited as text" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        TextFileContent(
            text = decoded.getOrElse { String(bytes, StandardCharsets.UTF_8) },
            hasUtf8Errors = decoded.isFailure || hasNullBytes,
        )
    }

    fun saveUtf8WithBackup(file: File, text: String): Result<File?> = runCatching {
        require(file.parentFile?.isDirectory == true) { "Parent directory is unavailable" }
        var backup: File? = null
        if (file.exists()) {
            backup = File(file.parentFile, "${file.name}.bak")
            copyFile(file, backup, overwrite = true)
        }
        val temp = File(file.parentFile, ".${file.name}.lyra-tmp")
        temp.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { it.write(text) }
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("Unable to replace ${file.path}")
        }
        if (!temp.renameTo(file)) {
            copyFile(temp, file, overwrite = true)
            temp.delete()
        }
        backup
    }

    fun createDirectory(parent: File, name: String): Result<File> = runCatching {
        val target = safeChild(parent, name)
        require(target.mkdir() || target.isDirectory) { "Unable to create folder" }
        target
    }

    fun createFile(parent: File, name: String): Result<File> = runCatching {
        val target = safeChild(parent, name)
        require(!target.exists()) { "A file with this name already exists" }
        require(target.createNewFile()) { "Unable to create file" }
        target
    }

    fun rename(source: File, name: String): Result<File> = runCatching {
        val target = safeChild(source.parentFile ?: error("Missing parent"), name)
        require(!target.exists()) { "A file with this name already exists" }
        require(source.renameTo(target)) { "Unable to rename" }
        target
    }

    fun copy(source: File, destinationDirectory: File): Result<File> = runCatching {
        require(destinationDirectory.isDirectory) { "Destination is not a directory" }
        val target = File(destinationDirectory, source.name)
        require(!target.exists()) { "Destination already contains ${source.name}" }
        require(!source.isDirectory || !isInside(destinationDirectory, source)) { "Cannot copy a folder into itself" }
        copyRecursively(source, target)
        target
    }

    fun move(source: File, destinationDirectory: File): Result<File> = runCatching {
        require(destinationDirectory.isDirectory) { "Destination is not a directory" }
        val target = File(destinationDirectory, source.name)
        require(!target.exists()) { "Destination already contains ${source.name}" }
        require(!isInside(destinationDirectory, source)) { "Cannot move a folder into itself" }
        if (!source.renameTo(target)) {
            copyRecursively(source, target)
            require(deleteRecursively(source)) { "Copied, but the source could not be deleted" }
        }
        target
    }

    fun copyAll(sources: Collection<File>, destinationDirectory: File): Result<List<File>> = runCatching {
        require(sources.isNotEmpty()) { "No files selected" }
        sources.map { copy(it, destinationDirectory).getOrThrow() }
    }

    fun moveAll(sources: Collection<File>, destinationDirectory: File): Result<List<File>> = runCatching {
        require(sources.isNotEmpty()) { "No files selected" }
        sources.map { move(it, destinationDirectory).getOrThrow() }
    }

    fun deleteAll(sources: Collection<File>): Result<Unit> = deleteAll(sources, storageRoot)

    internal fun deleteAll(sources: Collection<File>, protectedRoot: File): Result<Unit> = runCatching {
        require(sources.isNotEmpty()) { "No files selected" }
        sources.forEach { delete(it, protectedRoot) }
    }

    fun delete(source: File): Result<Unit> = runCatching { delete(source, storageRoot) }

    fun totalSize(source: File): Result<Long> = runCatching {
        if (source.isFile) return@runCatching source.length()
        require(source.isDirectory) { "Path is unavailable: ${source.path}" }
        val pending = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        var total = 0L
        pending.add(source)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current.isDirectory) {
                val canonicalPath = current.canonicalPath
                if (!visitedDirectories.add(canonicalPath)) continue
                current.listFiles().orEmpty().forEach(pending::add)
            } else if (current.isFile) {
                val length = current.length().coerceAtLeast(0L)
                total = if (Long.MAX_VALUE - total < length) Long.MAX_VALUE else total + length
            }
        }
        total
    }

    private fun delete(source: File, protectedRoot: File) {
        require(source.canonicalFile != protectedRoot.canonicalFile) { "Storage root cannot be deleted" }
        require(deleteRecursively(source)) { "Unable to delete ${source.name}" }
    }

    fun unzip(source: File, destinationDirectory: File): Result<File> = runCatching {
        require(source.extension.equals("zip", ignoreCase = true)) { "Only ZIP archives are supported" }
        val output = File(destinationDirectory, source.nameWithoutExtension.ifBlank { "archive" })
        require(!output.exists() || output.isDirectory) { "Output path is not a directory" }
        output.mkdirs()
        val canonicalRoot = output.canonicalFile
        var expanded = 0L
        var entries = 0
        ZipInputStream(FileInputStream(source).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ZIP_ENTRIES) { "Archive contains too many entries" }
                val target = File(output, entry.name).canonicalFile
                require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Unsafe path in archive: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            expanded += count
                            require(expanded <= MAX_UNZIPPED_BYTES) { "Expanded archive is too large" }
                            out.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        output
    }

    private fun safeChild(parent: File, name: String): File {
        val clean = name.trim()
        require(clean.isNotBlank() && clean != "." && clean != "..") { "Invalid name" }
        require('/' !in clean && '\\' !in clean) { "Name cannot contain path separators" }
        val child = File(parent, clean).canonicalFile
        require(child.parentFile == parent.canonicalFile) { "Invalid path" }
        return child
    }

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            require(target.mkdir() || target.isDirectory) { "Unable to create ${target.name}" }
            source.listFiles().orEmpty().forEach { copyRecursively(it, File(target, it.name)) }
        } else {
            copyFile(source, target, overwrite = false)
        }
    }

    private fun copyFile(source: File, target: File, overwrite: Boolean) {
        require(overwrite || !target.exists()) { "Destination already exists" }
        target.parentFile?.mkdirs()
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        target.setLastModified(source.lastModified())
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles().orEmpty().forEach { if (!deleteRecursively(it)) return false }
        return file.delete() || !file.exists()
    }

    private fun isInside(candidate: File, parent: File): Boolean {
        val child = candidate.canonicalFile.path
        val root = parent.canonicalFile.path
        return child == root || child.startsWith(root + File.separator)
    }

    private const val MAX_EDIT_BYTES = 16L * 1024L * 1024L
    private const val MAX_UNZIPPED_BYTES = 2L * 1024L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 100_000
}
