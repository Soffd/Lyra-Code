package com.yukisoffd.lyracode.interaction.action

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.yukisoffd.lyracode.interaction.model.ManualDeviceAction
import com.yukisoffd.lyracode.interaction.model.SemanticNode
import java.util.ArrayDeque
import kotlin.math.abs

internal sealed interface NodeResolution {
    data class Resolved(
        val node: AccessibilityNodeInfo,
        val actionId: Int,
    ) : NodeResolution

    data object NotFound : NodeResolution
    data object Ambiguous : NodeResolution
}

/** Re-resolves an immutable semantic target immediately before one native node action. */
internal class SemanticNodeResolver(
    private val service: AccessibilityService,
) {
    fun resolve(
        expected: SemanticNode,
        expectedPackage: String,
        action: ManualDeviceAction,
        preferredRoot: AccessibilityNodeInfo? = null,
    ): NodeResolution {
        val candidates = mutableListOf<Candidate>()
        val roots = if (preferredRoot != null) {
            listOf(preferredRoot)
        } else {
            listOfNotNull(service.rootInActiveWindow).ifEmpty {
                service.windows.orEmpty()
                    .filter { it.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                    .mapNotNull { window -> runCatching { window.root }.getOrNull() }
            }
        }

        expected.resourceId?.let { resourceId ->
            val directCandidates = roots.asSequence()
                .flatMap { root ->
                    runCatching { root.findAccessibilityNodeInfosByViewId(resourceId).orEmpty().asSequence() }
                        .getOrDefault(emptySequence())
                }
                .take(MAX_DIRECT_CANDIDATES)
                .mapNotNull { candidate(it, expected, expectedPackage, action) }
                .toList()
            val directResolution = rank(directCandidates)
            if (directResolution !is NodeResolution.NotFound) return directResolution
        }

        val textQuery = expected.text?.takeIf(String::isNotBlank)
            ?: expected.contentDescription?.takeIf(String::isNotBlank)
        textQuery?.let { query ->
            val directCandidates = roots.asSequence()
                .flatMap { root ->
                    runCatching { root.findAccessibilityNodeInfosByText(query).orEmpty().asSequence() }
                        .getOrDefault(emptySequence())
                }
                .take(MAX_DIRECT_CANDIDATES)
                .mapNotNull { candidate(it, expected, expectedPackage, action) }
                .toList()
            val directResolution = rank(directCandidates)
            if (directResolution !is NodeResolution.NotFound) return directResolution
        }

        val fallbackStartedAt = SystemClock.elapsedRealtime()
        var visited = 0
        val queue = ArrayDeque<NodeFrame>()
        roots.forEach { queue.add(NodeFrame(it, 0)) }
        while (
            queue.isNotEmpty() &&
            visited < MAX_NODES &&
            SystemClock.elapsedRealtime() - fallbackStartedAt < MAX_FALLBACK_MILLIS
        ) {
            val frame = queue.removeFirst()
            val node = frame.node
            visited++
            if (frame.depth <= MAX_DEPTH) candidate(node, expected, expectedPackage, action)?.let(candidates::add)
            if (frame.depth < MAX_DEPTH) {
                for (index in 0 until node.childCount.coerceAtMost(MAX_CHILDREN)) {
                    runCatching { node.getChild(index) }.getOrNull()?.let {
                        queue.add(NodeFrame(it, frame.depth + 1))
                    }
                }
            }
        }

        return rank(candidates)
    }

    private fun candidate(
        node: AccessibilityNodeInfo,
        expected: SemanticNode,
        expectedPackage: String,
        action: ManualDeviceAction,
    ): Candidate? {
        if (node.packageName?.toString() != expectedPackage) return null
        if (!runCatching { node.isEnabled }.getOrDefault(false)) return null
        if (!runCatching { node.isVisibleToUser }.getOrDefault(false)) return null
        val actionId = resolveActionId(node, action) ?: return null
        val score = score(expected, node)
        return Candidate(node, actionId, score).takeIf { score >= MIN_SCORE }
    }

    private fun rank(candidates: List<Candidate>): NodeResolution {
        val ranked = candidates.sortedByDescending(Candidate::score)
        val best = ranked.firstOrNull() ?: return NodeResolution.NotFound
        if (ranked.getOrNull(1)?.let { best.score - it.score < MIN_SCORE_MARGIN } == true) {
            return NodeResolution.Ambiguous
        }
        return NodeResolution.Resolved(best.node, best.actionId)
    }

    private fun score(expected: SemanticNode, actual: AccessibilityNodeInfo): Int {
        var score = 0
        if (actual.windowId == expected.windowId) score += 30
        if (expected.resourceId != null && actual.viewIdResourceName == expected.resourceId) score += 80
        if (expected.className != null && actual.className?.toString() == expected.className) score += 20
        if (expected.text != null && actual.text?.toString() == expected.text) score += 40
        if (expected.contentDescription != null && actual.contentDescription?.toString() == expected.contentDescription) score += 40

        val bounds = Rect().also { runCatching { actual.getBoundsInScreen(it) } }
        if (
            bounds.left == expected.bounds.left && bounds.top == expected.bounds.top &&
            bounds.right == expected.bounds.right && bounds.bottom == expected.bounds.bottom
        ) {
            score += 30
        } else {
            val expectedCenterX = (expected.bounds.left + expected.bounds.right) / 2
            val expectedCenterY = (expected.bounds.top + expected.bounds.bottom) / 2
            if (abs(bounds.centerX() - expectedCenterX) <= 24 && abs(bounds.centerY() - expectedCenterY) <= 24) {
                score += 20
            }
        }
        return score
    }

    private fun resolveActionId(node: AccessibilityNodeInfo, action: ManualDeviceAction): Int? {
        val available = node.actionList.orEmpty().mapTo(mutableSetOf()) { it.id }
        return when (action) {
            ManualDeviceAction.ACTIVATE -> AccessibilityNodeInfo.ACTION_CLICK.takeIf(available::contains)
            ManualDeviceAction.SCROLL_FORWARD -> firstAvailable(
                available,
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
            )
            ManualDeviceAction.SCROLL_BACKWARD -> firstAvailable(
                available,
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
            )
        }
    }

    private fun firstAvailable(available: Set<Int>, vararg choices: Int): Int? =
        choices.firstOrNull(available::contains)

    private data class NodeFrame(val node: AccessibilityNodeInfo, val depth: Int)
    private data class Candidate(val node: AccessibilityNodeInfo, val actionId: Int, val score: Int)

    private companion object {
        const val MAX_NODES = 160
        const val MAX_DEPTH = 24
        const val MAX_CHILDREN = 64
        const val MAX_DIRECT_CANDIDATES = 48
        const val MAX_FALLBACK_MILLIS = 400L
        const val MIN_SCORE = 70
        const val MIN_SCORE_MARGIN = 12
    }
}
