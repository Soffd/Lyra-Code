package com.yukisoffd.lyracode.interaction.policy

import com.yukisoffd.lyracode.interaction.model.ManualDeviceAction
import com.yukisoffd.lyracode.interaction.model.SemanticAction
import com.yukisoffd.lyracode.interaction.model.SemanticNode

internal sealed interface DevicePolicyDecision {
    data object Allowed : DevicePolicyDecision
    data class Blocked(val reason: DevicePolicyBlockReason) : DevicePolicyDecision
}

internal enum class DevicePolicyBlockReason {
    PACKAGE_NOT_ALLOWED,
    SENSITIVE_NODE,
    HIGH_RISK_CONTROL,
    ACTION_NOT_ADVERTISED,
    INVISIBLE_OR_DISABLED,
}

internal object DeviceActionPolicy {
    fun evaluate(
        packageName: String,
        node: SemanticNode,
        action: ManualDeviceAction,
    ): DevicePolicyDecision {
        if (!isPackageAllowed(packageName)) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.PACKAGE_NOT_ALLOWED)
        }
        if (node.packageName != null && node.packageName != packageName) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.PACKAGE_NOT_ALLOWED)
        }
        if (node.password || node.accessibilityDataSensitive || node.redacted) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.SENSITIVE_NODE)
        }
        if (!node.enabled || !node.visible) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.INVISIBLE_OR_DISABLED)
        }
        if (!advertises(node, action)) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.ACTION_NOT_ADVERTISED)
        }
        if (action == ManualDeviceAction.ACTIVATE && containsHighRiskLanguage(node)) {
            return DevicePolicyDecision.Blocked(DevicePolicyBlockReason.HIGH_RISK_CONTROL)
        }
        return DevicePolicyDecision.Allowed
    }

    fun isPackageAllowed(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return normalized.isNotBlank() &&
            normalized !in BLOCKED_SYSTEM_PACKAGES &&
            BLOCKED_SYSTEM_SUFFIXES.none(normalized::endsWith)
    }

    private fun advertises(node: SemanticNode, action: ManualDeviceAction): Boolean = when (action) {
        ManualDeviceAction.ACTIVATE -> SemanticAction.ACTIVATE in node.actions
        ManualDeviceAction.SCROLL_FORWARD -> {
            SemanticAction.SCROLL_FORWARD in node.actions ||
                SemanticAction.SCROLL_DOWN in node.actions ||
                SemanticAction.SCROLL_RIGHT in node.actions
        }
        ManualDeviceAction.SCROLL_BACKWARD -> {
            SemanticAction.SCROLL_BACKWARD in node.actions ||
                SemanticAction.SCROLL_UP in node.actions ||
                SemanticAction.SCROLL_LEFT in node.actions
        }
    }

    private fun containsHighRiskLanguage(node: SemanticNode): Boolean {
        val labels = listOfNotNull(node.text, node.contentDescription)
            .map { it.trim().lowercase() }
        return labels.any { label ->
            HIGH_RISK_ENGLISH.containsMatchIn(label) ||
                HIGH_RISK_CJK_TERMS.any(label::contains)
        }
    }

    private val BLOCKED_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.settings",
    )

    private val BLOCKED_SYSTEM_SUFFIXES = setOf(
        ".permissioncontroller",
        ".packageinstaller",
    )

    private val HIGH_RISK_ENGLISH = Regex(
        "\\b(send|publish|delete|purchase|buy|pay|transfer|install|uninstall|authorize|password|otp)\\b|verification\\s+code",
        RegexOption.IGNORE_CASE,
    )

    private val HIGH_RISK_CJK_TERMS = setOf(
        "发送",
        "傳送",
        "发布",
        "發佈",
        "删除",
        "刪除",
        "购买",
        "購買",
        "支付",
        "付款",
        "转账",
        "轉帳",
        "安装",
        "安裝",
        "卸载",
        "解除安裝",
        "授权",
        "授權",
        "密码",
        "密碼",
        "验证码",
        "驗證碼",
        "下单",
        "下單",
    )
}
