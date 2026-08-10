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
        Text(uiText(R.string.title_storage_usage), style = MaterialTheme.typography.titleMedium)
        KimiDivider()
        KimiMenuRow(Icons.Default.Storage, uiText(R.string.menu_total_usage), formatBytes(scan.totalBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Android, uiText(R.string.menu_app_package), formatBytes(scan.appBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Folder, uiText(R.string.menu_app_data), formatBytes(scan.dataBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.Memory, uiText(R.string.menu_system_cache), formatBytes(scan.cacheBytes))
        KimiDivider()
        KimiMenuRow(Icons.Default.CleaningServices, uiText(R.string.menu_cleanable_cache), formatBytes(scan.cleanableBytes))
        Text(uiText(R.string.storage_explanation), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
    }
    scan.items.forEach { item ->
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.path, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text(uiText(R.string.storage_item_summary, formatBytes(item.bytes), item.fileCount), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (item.cleanable && item.bytes > 0L) {
                    OutlinedButton(
                        onClick = {
                            status = uiText(R.string.storage_clearing_item, item.title)
                            scope.launch(Dispatchers.IO) {
                                deleteCacheTarget(item.file)
                                withContext(Dispatchers.Main) {
                                    status = uiText(R.string.status_cleaned, item.title)
                                    refresh()
                                }
                            }
                        },
                        shape = KimiPillShape,
                    ) { Text(uiText(R.string.action_clean)) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { refresh(); status = uiText(R.string.status_scan_complete) }, shape = KimiPillShape) { Text(uiText(R.string.action_rescan)) }
        OutlinedButton(
            enabled = scan.cleanableBytes > 0L,
            onClick = {
                status = uiText(R.string.ui_clearing_cache)
                scope.launch(Dispatchers.IO) {
                    scan.items.filter { it.cleanable }.forEach { deleteCacheTarget(it.file) }
                    withContext(Dispatchers.Main) {
                        status = uiText(R.string.status_cache_cleaned)
                        refresh()
                    }
                }
            },
            shape = KimiPillShape,
        ) { Text(uiText(R.string.action_clean_all_cache)) }
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
        add(storageItem(uiText(R.string.cache_ai_response), File(context.cacheDir, "ai_response_cache"), cleanable = true))
        add(storageItem(uiText(R.string.cache_crop_image), File(context.cacheDir, "uploads"), cleanable = true))
        add(storageItem(uiText(R.string.cache_camera_upload), File(context.cacheDir, "upload_crop"), cleanable = true))
        add(storageItem(uiText(R.string.cache_system_temp), context.cacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem(uiText(R.string.cache_code), context.codeCacheDir, cleanable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) add(storageItem(uiText(R.string.cache_no_backup), context.noBackupFilesDir, cleanable = false))
        add(storageItem(uiText(R.string.cache_persistent), context.filesDir, cleanable = false))
        context.getExternalFilesDirs(null).filterNotNull().forEachIndexed { index, dir ->
            add(storageItem(context.getString(R.string.cache_external_private, index + 1), dir, cleanable = false))
        }
        context.externalCacheDirs.filterNotNull().forEachIndexed { index, dir ->
            add(storageItem(context.getString(R.string.cache_external_cache, index + 1), dir, cleanable = true))
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

