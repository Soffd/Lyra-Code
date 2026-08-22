package com.yukisoffd.lyracode

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Compatibility checks for behavior changes introduced with Android 17 (API 37). */
internal object Android17Compatibility {
    const val API_LEVEL = 37
    const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun requiresLocalNetworkPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= API_LEVEL

    fun hasLocalNetworkAccess(context: Context): Boolean =
        !requiresLocalNetworkPermission() ||
            ContextCompat.checkSelfPermission(
                context,
                ACCESS_LOCAL_NETWORK_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
}
