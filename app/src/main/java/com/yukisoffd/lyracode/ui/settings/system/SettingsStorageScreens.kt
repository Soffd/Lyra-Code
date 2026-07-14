package com.yukisoffd.lyracode

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max



@Composable
internal fun StorageCacheSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scan by remember { mutableStateOf(scanStorageUsage(context)) }
    var status by remember { mutableStateOf("") }
    fun refresh() {
        scan = scanStorageUsage(context)
    }
    KimiCardBox {
        Text(uiText("存储占用"), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        KimiMenuRow(Icons.Default.Storage, uiText("应用总占用"), formatBytes(scan.totalBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Android, uiText("应用安装包"), formatBytes(scan.appBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Folder, uiText("应用数据"), formatBytes(scan.dataBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Memory, uiText("系统缓存"), formatBytes(scan.cacheBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.CleaningServices, uiText("可安全清理缓存"), formatBytes(scan.cleanableBytes))
        Text(uiText("总占用按 Android 设置页常见口径估算：安装包 + 应用数据 + 缓存。清理范围仅包含临时上传、图片裁剪、拍照预览和 AI 响应磁盘缓存；不会删除历史对话、模型配置、API Key、MCP/SSH、Skills、头像或工作目录文件。"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
    scan.items.forEach { item ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.path, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text(uiText("${formatBytes(item.bytes)} · ${item.fileCount} 个文件"), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (item.cleanable && item.bytes > 0L) {
                    OutlinedButton(
                        onClick = {
                            status = uiText("正在清理 ${item.title}...")
                            scope.launch(Dispatchers.IO) {
                                deleteCacheTarget(item.file)
                                withContext(Dispatchers.Main) {
                                    status = uiText("已清理 ${item.title}")
                                    refresh()
                                }
                            }
                        },
                        shape = KimiPillShape,
                    ) { Text(uiText("清理")) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { refresh(); status = uiText("扫描完成") }, shape = KimiPillShape) { Text(uiText("重新扫描")) }
        OutlinedButton(
            enabled = scan.cleanableBytes > 0L,
            onClick = {
                status = uiText("正在清理缓存...")
                scope.launch(Dispatchers.IO) {
                    scan.items.filter { it.cleanable }.forEach { deleteCacheTarget(it.file) }
                    withContext(Dispatchers.Main) {
                        status = uiText("缓存已清理")
                        refresh()
                    }
                }
            },
            shape = KimiPillShape,
        ) { Text(uiText("清理全部缓存")) }
    }
    if (status.isNotBlank()) Text(status, color = KimiMuted, style = MaterialTheme.typography.bodySmall)
}

internal data class StorageScanResult(
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val cleanableBytes: Long,
    val items: List<StorageCacheItem>,
)

internal data class StorageCacheItem(
    val title: String,
    val file: File,
    val path: String,
    val bytes: Long,
    val fileCount: Int,
    val cleanable: Boolean,
)

internal fun scanStorageUsage(context: Context): StorageScanResult {
    val recursiveAppBytes = safeInstalledAppBytes(context)
    val recursiveCacheBytes = safeDirSize(context.cacheDir) +
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) safeDirSize(context.codeCacheDir) else 0L
    val recursiveDataRootBytes = safeDirSize(File(context.applicationInfo.dataDir)) +
        context.getExternalFilesDirs(null).filterNotNull().sumOf { safeDirSize(it) } +
        context.externalCacheDirs.filterNotNull().sumOf { safeDirSize(it) }
    val systemStats = querySystemStorageStats(context)
    val appBytes = max(systemStats?.appBytes ?: 0L, recursiveAppBytes)
    val cacheBytes = max(systemStats?.cacheBytes ?: 0L, recursiveCacheBytes)
    val dataBytes = max(
        systemStats?.let { (it.dataBytes - it.cacheBytes).coerceAtLeast(0L) } ?: 0L,
        (recursiveDataRootBytes - recursiveCacheBytes).coerceAtLeast(0L),
    )
    val items = buildList {
        add(storageItem(uiText("AI 响应缓存"), File(context.cacheDir, "ai_response_cache"), cleanable = true))
        add(storageItem(uiText("裁剪图片临时文件"), File(context.cacheDir, "uploads"), cleanable = true))
        add(storageItem(uiText("拍照上传临时文件"), File(context.cacheDir, "upload_crop"), cleanable = true))
        add(storageItem(uiText("系统临时缓存"), context.cacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem(uiText("代码缓存"), context.codeCacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem(uiText("No backup 数据"), context.noBackupFilesDir, cleanable = false))
        add(storageItem(uiText("应用持久数据"), context.filesDir, cleanable = false))
        context.getExternalFilesDirs(null).filterNotNull().forEachIndexed { index, dir ->
            add(storageItem(uiText("外部私有文件 ${index + 1}"), dir, cleanable = false))
        }
        context.externalCacheDirs.filterNotNull().forEachIndexed { index, dir ->
            add(storageItem(uiText("外部缓存 ${index + 1}"), dir, cleanable = true))
        }
    }
    val total = appBytes + dataBytes + cacheBytes
    val cleanable = items.filter { it.cleanable }.sumOf { it.bytes }
    return StorageScanResult(total, appBytes, dataBytes, cacheBytes, cleanable, items)
}

internal data class SystemStorageStats(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long)

internal fun querySystemStorageStats(context: Context): SystemStorageStats? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, context.applicationInfo.uid)
        SystemStorageStats(
            appBytes = stats.appBytes,
            dataBytes = stats.dataBytes,
            cacheBytes = stats.cacheBytes,
        )
    }.getOrNull()
}

internal fun safeInstalledAppBytes(context: Context): Long = runCatching {
    val appInfo = context.applicationInfo
    val files = buildList {
        add(File(appInfo.sourceDir))
        appInfo.splitSourceDirs?.forEach { add(File(it)) }
        appInfo.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
    }
    files.distinctBy { it.absolutePath }.sumOf { safeDirSize(it) }
}.getOrDefault(0L)

internal fun storageItem(title: String, file: File, cleanable: Boolean): StorageCacheItem {
    return StorageCacheItem(
        title = title,
        file = file,
        path = file.absolutePath,
        bytes = safeDirSize(file),
        fileCount = safeFileCount(file),
        cleanable = cleanable,
    )
}

internal fun safeDirSize(file: File): Long = runCatching {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

internal fun safeFileCount(file: File): Int = runCatching {
    if (!file.exists()) return 0
    if (file.isFile) return 1
    file.walkTopDown().count { it.isFile }
}.getOrDefault(0)

internal fun deleteCacheTarget(file: File) {
    runCatching {
        if (!file.exists()) return
        if (file.isFile) {
            file.delete()
        } else {
            file.listFiles()?.forEach { child ->
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble().coerceAtLeast(0.0)
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) "${bytes.coerceAtLeast(0)} ${units[index]}" else String.format(Locale.US, "%.1f %s", value, units[index])
}

