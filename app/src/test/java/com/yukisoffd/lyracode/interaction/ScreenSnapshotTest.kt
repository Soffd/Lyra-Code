package com.yukisoffd.lyracode.interaction

import com.yukisoffd.lyracode.interaction.model.ScreenBounds
import com.yukisoffd.lyracode.interaction.model.ScreenDisplay
import com.yukisoffd.lyracode.interaction.model.ScreenSnapshot
import com.yukisoffd.lyracode.interaction.model.ScreenSnapshotFingerprint
import com.yukisoffd.lyracode.interaction.model.SemanticAction
import com.yukisoffd.lyracode.interaction.model.SemanticNode
import com.yukisoffd.lyracode.interaction.model.toDebugText
import com.yukisoffd.lyracode.interaction.perception.ScreenProbeController
import com.yukisoffd.lyracode.interaction.perception.SnapshotTextBudget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSnapshotTest {
    @After
    fun clearProbeState() {
        ScreenProbeController.clear()
    }

    @Test
    fun fingerprintChangesWithUiContentButNotSnapshotVersion() {
        val display = ScreenDisplay(0, 0, 1080, 2400)
        val firstNodes = listOf(node(handle = "snapshot-a:w1:n0", text = "Settings"))
        val secondNodes = listOf(node(handle = "snapshot-b:w1:n0", text = "Settings"))
        val changedNodes = listOf(node(handle = "snapshot-c:w1:n0", text = "Privacy"))

        val first = ScreenSnapshotFingerprint.create(display, "example.app", 1, emptyList(), firstNodes)
        val second = ScreenSnapshotFingerprint.create(display, "example.app", 1, emptyList(), secondNodes)
        val changed = ScreenSnapshotFingerprint.create(display, "example.app", 1, emptyList(), changedNodes)

        assertEquals(first, second)
        assertNotEquals(first, changed)
    }

    @Test
    fun debugFormatterNeverRestoresRedactedText() {
        val sensitiveNode = node(
            handle = "snapshot:w1:n0",
            text = "[REDACTED]",
            password = true,
            redacted = true,
        )
        val snapshot = snapshot(listOf(sensitiveNode))

        val debug = snapshot.toDebugText()

        assertTrue(debug.contains("[REDACTED]"))
        assertTrue(debug.contains("redacted"))
        assertFalse(debug.contains("secret-password"))
    }

    @Test
    fun textBudgetNormalizesWhitespaceAndEnforcesBothLimits() {
        val budget = SnapshotTextBudget(maxTotalChars = 8, maxCharsPerValue = 6)

        assertEquals("a b c", budget.take("  a\n b\t c  "))
        assertEquals("123", budget.take("123456"))
        assertNull(budget.take("more"))
        assertTrue(budget.truncated)
    }

    @Test
    fun probePublishesOnlyInsideItsBoundedSession() {
        val snapshot = snapshot(listOf(node()))
        ScreenProbeController.start(nowEpochMillis = 1_000L, durationMillis = 2_000L)

        ScreenProbeController.publish(snapshot, nowEpochMillis = 2_000L)
        assertEquals(snapshot, ScreenProbeController.state.value.latestSnapshot)
        assertTrue(ScreenProbeController.isActive(nowEpochMillis = 2_999L))

        ScreenProbeController.expire(nowEpochMillis = 3_000L)
        assertFalse(ScreenProbeController.state.value.isActive(3_000L))
        ScreenProbeController.clear()
        ScreenProbeController.publish(snapshot, nowEpochMillis = 4_000L)
        assertNull(ScreenProbeController.state.value.latestSnapshot)
    }

    private fun snapshot(nodes: List<SemanticNode>): ScreenSnapshot = ScreenSnapshot(
        snapshotId = "snapshot",
        capturedAtEpochMillis = 100L,
        display = ScreenDisplay(0, 0, 1080, 2400),
        activePackage = "example.app",
        activeWindowId = 1,
        windows = emptyList(),
        nodes = nodes,
        uiFingerprint = "fingerprint",
        truncated = false,
    )

    private fun node(
        handle: String = "snapshot:w1:n0",
        text: String? = "Label",
        password: Boolean = false,
        redacted: Boolean = false,
    ): SemanticNode = SemanticNode(
        handle = handle,
        windowId = 1,
        parentHandle = null,
        depth = 0,
        role = "button",
        className = "android.widget.Button",
        packageName = "example.app",
        text = text,
        contentDescription = null,
        resourceId = "example.app:id/button",
        bounds = ScreenBounds(10, 20, 200, 80),
        actions = setOf(SemanticAction.ACTIVATE),
        enabled = true,
        visible = true,
        editable = false,
        clickable = true,
        longClickable = false,
        scrollable = false,
        focusable = true,
        checkable = false,
        checked = false,
        selected = false,
        password = password,
        accessibilityDataSensitive = false,
        redacted = redacted,
    )
}
