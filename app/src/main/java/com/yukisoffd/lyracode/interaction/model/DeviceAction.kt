package com.yukisoffd.lyracode.interaction.model

enum class ManualDeviceAction {
    ACTIVATE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
}

data class ManualActionSelection(
    val snapshotId: String,
    val elementHandle: String,
    val action: ManualDeviceAction,
    val expectedPackage: String,
)

enum class DeviceActionStatus {
    SUCCEEDED,
    STALE,
    AMBIGUOUS,
    BLOCKED,
    SYSTEM_REJECTED,
    NO_CHANGE,
    PACKAGE_CHANGED,
    SERVICE_DISCONNECTED,
    USER_CANCELLED,
    CAPTURE_FAILED,
}

data class DeviceActionResult(
    val status: DeviceActionStatus,
    val action: ManualDeviceAction,
    val expectedPackage: String,
    val actualPackage: String?,
    val elementHandle: String,
    val executionMethod: String?,
    val beforeFingerprint: String,
    val afterFingerprint: String? = null,
)
