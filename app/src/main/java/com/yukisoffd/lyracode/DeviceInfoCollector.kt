package com.yukisoffd.lyracode

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DeviceInfoItem(
    val label: String,
    val value: String,
)

data class DeviceInfoSection(
    val title: String,
    val items: List<DeviceInfoItem>,
)

data class DeviceInfoSnapshot(
    val sections: List<DeviceInfoSection>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "lyra_device_info_v1")
        put(
            "sections",
            JSONArray().also { sectionArray ->
                sections.forEach { section ->
                    sectionArray.put(
                        JSONObject()
                            .put("title", section.title)
                            .put(
                                "items",
                                JSONArray().also { itemArray ->
                                    section.items.forEach { item ->
                                        itemArray.put(JSONObject().put("label", item.label).put("value", item.value))
                                    }
                                },
                            ),
                    )
                }
            },
        )
    }
}

object DeviceInfoCollector {
    fun collect(context: Context): DeviceInfoSnapshot {
        return DeviceInfoSnapshot(
            listOf(
                systemSection(context),
                hardwareSection(context),
                storageSection(context),
                networkSection(context),
                batterySection(context),
            ),
        )
    }

    fun collectJson(context: Context): String = collect(context).toJson().toString(2)

    private fun systemSection(context: Context): DeviceInfoSection = DeviceInfoSection(
        context.getString(R.string.device_section_system),
        listOf(
            DeviceInfoItem(context.getString(R.string.device_manufacturer), Build.MANUFACTURER.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_brand), Build.BRAND.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_model), Build.MODEL.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_codename), Build.DEVICE.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_android_version), Build.VERSION.RELEASE.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_sdk), Build.VERSION.SDK_INT.toString()),
            DeviceInfoItem(context.getString(R.string.device_build), Build.DISPLAY.orUnknown()),
            DeviceInfoItem(context.getString(R.string.device_security_patch), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH.orUnknown() else context.getString(R.string.device_not_supported)),
        ),
    )

    private fun hardwareSection(context: Context): DeviceInfoSection {
        val display = displayInfo(context)
        return DeviceInfoSection(
            context.getString(R.string.device_section_hardware),
            listOf(
                DeviceInfoItem(context.getString(R.string.device_cpu), cpuInfo()),
                DeviceInfoItem(context.getString(R.string.device_cpu_cores), Runtime.getRuntime().availableProcessors().toString()),
                DeviceInfoItem(context.getString(R.string.device_abi), Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { context.getString(R.string.device_battery_unknown) }),
                DeviceInfoItem(context.getString(R.string.device_memory), memoryInfo(context)),
                DeviceInfoItem(context.getString(R.string.device_resolution), display.first),
                DeviceInfoItem(context.getString(R.string.device_density), display.second),
            ),
        )
    }

    private fun storageSection(context: Context): DeviceInfoSection {
        val items = mutableListOf<DeviceInfoItem>()
        items += DeviceInfoItem(context.getString(R.string.device_internal_storage), storageText(Environment.getDataDirectory()))
        val primaryExternal = Environment.getExternalStorageDirectory()
        if (primaryExternal.exists()) {
            items += DeviceInfoItem(context.getString(R.string.device_shared_storage), storageText(primaryExternal))
        }
        val storageManager = context.getSystemService(StorageManager::class.java)
        runCatching {
            storageManager.storageVolumes.forEachIndexed { index, volume ->
                val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory else null
                val label = buildString {
                    append(
                        when {
                            volume.isPrimary -> context.getString(R.string.device_primary_storage)
                            volume.isRemovable -> context.getString(R.string.device_removable_storage)
                            else -> context.getString(R.string.device_storage_volume)
                        },
                    )
                    append(" ${index + 1}")
                    val description = runCatching { volume.getDescription(context) }.getOrNull().orEmpty()
                    if (description.isNotBlank()) append(" · ").append(description)
                }
                val value = if (directory != null && directory.exists()) {
                    storageText(directory)
                } else {
                    context.getString(R.string.device_path_not_readable, volume.uuid ?: context.getString(R.string.device_none), volume.isRemovable.toString())
                }
                if (items.none { it.value == value }) {
                    items += DeviceInfoItem(label, value)
                }
            }
        }
        return DeviceInfoSection(context.getString(R.string.device_section_storage), items)
    }

    private fun networkSection(context: Context): DeviceInfoSection {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val networkType = when {
            caps == null -> context.getString(R.string.device_network_none)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> context.getString(R.string.device_network_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.device_network_cellular)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.device_network_ethernet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> context.getString(R.string.device_network_bluetooth)
            else -> context.getString(R.string.device_network_other)
        }
        val bluetooth = runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            when {
                adapter == null -> context.getString(R.string.device_bluetooth_not_supported)
                adapter.isEnabled -> context.getString(R.string.device_bluetooth_on)
                else -> context.getString(R.string.device_bluetooth_off)
            }
        }.getOrElse { context.getString(R.string.device_read_error, it.javaClass.simpleName) }
        return DeviceInfoSection(
            context.getString(R.string.device_section_connection),
            listOf(
                DeviceInfoItem(context.getString(R.string.device_network), networkType),
                DeviceInfoItem(context.getString(R.string.device_bluetooth), bluetooth),
            ),
        )
    }

    private fun batterySection(context: Context): DeviceInfoSection {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else context.getString(R.string.device_battery_unknown)
        val status = when (battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.device_battery_charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.device_battery_discharging)
            BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.device_battery_full)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.device_battery_not_charging)
            else -> context.getString(R.string.device_battery_unknown)
        }
        val plugged = when (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(R.string.device_plugged_wireless)
            else -> context.getString(R.string.device_plugged_none)
        }
        return DeviceInfoSection(
            context.getString(R.string.device_section_battery),
            listOf(
                DeviceInfoItem(context.getString(R.string.device_battery_level), percent),
                DeviceInfoItem(context.getString(R.string.device_battery_status), status),
                DeviceInfoItem(context.getString(R.string.device_battery_power), plugged),
            ),
        )
    }

    private fun displayInfo(context: Context): Pair<String, String> {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val bounds = windowManager.currentWindowMetrics.bounds
            context.display?.getRealMetrics(metrics)
            return "${bounds.width()} × ${bounds.height()}" to "${metrics.densityDpi} dpi / ${String.format(Locale.US, "%.2f", metrics.density)}x"
        }
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        return "${metrics.widthPixels} × ${metrics.heightPixels}" to "${metrics.densityDpi} dpi / ${String.format(Locale.US, "%.2f", metrics.density)}x"
    }

    private fun memoryInfo(context: Context): String {
        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
        val info = android.app.ActivityManager.MemoryInfo()
        return runCatching {
            activityManager.getMemoryInfo(info)
            context.getString(R.string.device_memory_format, formatBytes(info.availMem), formatBytes(info.totalMem))
        }.getOrDefault(context.getString(R.string.device_battery_unknown))
    }

    private fun cpuInfo(): String {
        val text = runCatching { File("/proc/cpuinfo").readText().lineSequence().take(80).joinToString("\n") }.getOrDefault("")
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.cleanCpuValue() else null
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.cleanCpuValue() else null
        val hardware = cpuInfoField(text, "Hardware")
        val modelName = cpuInfoField(text, "model name")
        val processor = cpuInfoField(text, "Processor")?.takeUnless { it.toIntOrNull() != null }
        val buildHardware = Build.HARDWARE.cleanCpuValue()
        val primaryId = listOfNotNull(socModel, hardware, buildHardware, modelName, processor).firstOrNull()
        val friendlyName = primaryId?.let(::friendlyChipName)
        return listOfNotNull(
            friendlyName,
            socManufacturer?.takeUnless { friendlyName?.contains(it, ignoreCase = true) == true },
            primaryId?.takeUnless { it.equals(friendlyName, ignoreCase = true) },
        )
            .distinct()
            .joinToString(" · ")
            .ifBlank { buildHardware.orUnknown() }
    }

    private fun cpuInfoField(text: String, key: String): String? {
        return Regex("""(?im)^${Regex.escape(key)}\s*:\s*(.+)$""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanCpuValue()
    }

    private fun friendlyChipName(raw: String): String {
        val normalized = raw.lowercase(Locale.US)
            .replace("""[\s_-]+""".toRegex(), "")
        return when {
            normalized.contains("sm8750") || normalized.contains("volcano") -> "Snapdragon 8 Elite"
            normalized.contains("sm8650") || normalized.contains("pineapple") -> "Snapdragon 8 Gen 3"
            normalized.contains("sm8550") || normalized.contains("kalama") -> "Snapdragon 8 Gen 2"
            normalized.contains("sm8475") -> "Snapdragon 8+ Gen 1"
            normalized.contains("sm8450") || normalized.contains("taro") -> "Snapdragon 8 Gen 1"
            normalized.contains("sm8350") || normalized.contains("lahaina") -> "Snapdragon 888"
            normalized.contains("sm8250") || normalized.contains("kona") -> "Snapdragon 865"
            normalized.contains("mt6991") -> "Dimensity 9400"
            normalized.contains("mt6989") -> "Dimensity 9300"
            normalized.contains("mt6985") -> "Dimensity 9200"
            normalized.contains("mt6983") -> "Dimensity 9000"
            normalized.contains("mt6897") -> "Dimensity 8300"
            normalized.contains("mt6896") -> "Dimensity 8200"
            normalized.contains("mt6895") -> "Dimensity 8100/8000"
            normalized.contains("exynos2400") || normalized.contains("s5e9945") -> "Exynos 2400"
            normalized.contains("exynos2200") || normalized.contains("s5e9925") -> "Exynos 2200"
            normalized.contains("gs101") -> "Google Tensor"
            normalized.contains("gs201") -> "Google Tensor G2"
            normalized.contains("zuma") || normalized.contains("gs301") -> "Google Tensor G3"
            normalized.contains("zumapro") || normalized.contains("gs401") -> "Google Tensor G4"
            else -> raw
        }
    }

    private fun storageText(path: File): String {
        return runCatching {
            val stat = StatFs(path.absolutePath)
            val total = stat.blockSizeLong * stat.blockCountLong
            val available = stat.blockSizeLong * stat.availableBlocksLong
            formatBytes(available) + uiText(" 可用 / ") + formatBytes(total) + uiText(" 总计 · ") + path.absolutePath
        }.getOrElse { uiText("无法读取：") + path.absolutePath }
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: uiText("未知")

    private fun String?.cleanCpuValue(): String? {
        return this
            ?.trim()
            ?.trim('\u0000')
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    }
}
