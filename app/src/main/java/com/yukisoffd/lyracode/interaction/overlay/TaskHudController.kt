package com.yukisoffd.lyracode.interaction.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.interaction.model.ManualDeviceAction
import com.yukisoffd.lyracode.interaction.model.ScreenBounds
import com.yukisoffd.lyracode.interaction.model.SemanticNode
import com.yukisoffd.lyracode.interaction.policy.DeviceActionPolicy
import com.yukisoffd.lyracode.interaction.policy.DevicePolicyDecision
import com.yukisoffd.lyracode.interaction.session.ManualControlState
import com.yukisoffd.lyracode.interaction.session.ManualControlStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/** Small, user-driven application overlay for one confirmed semantic action at a time. */
internal class TaskHudController(
    private val context: Context,
    private val onSelect: (String, ManualDeviceAction) -> Unit,
    private val onConfirm: () -> Unit,
    private val onCancelSelection: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var panelHost: FrameLayout? = null
    private var highlight: HighlightView? = null
    private var highlightedBounds: ScreenBounds? = null
    private var latestState: ManualControlState? = null
    private var pendingPanelState: ManualControlState? = null
    private var lastPanelSignature: PanelSignature? = null
    private var panelTouchActive = false
    private var expanded = true
    private var panelX = dp(12)
    private var panelY = dp(96)

    fun render(state: ManualControlState) {
        latestState = state
        if (!state.isActive()) {
            removeViews()
            return
        }

        // Create the non-touchable highlight layer first so the interactive panel always stays above it.
        ensureHighlightLayer()
        val selectedBounds = state.selection?.let { selection ->
            state.latestSnapshot?.nodes?.firstOrNull { it.handle == selection.elementHandle }?.let { node ->
                node.bounds
            }
        }
        if (selectedBounds == null) hideHighlight() else updateHighlight(selectedBounds)
        if (panelTouchActive) {
            pendingPanelState = state
        } else {
            updatePanel(state)
        }
    }

    fun destroy() = removeViews()

    private fun buildPanel(state: ManualControlState): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.argb(235, 34, 36, 40), dp(16).toFloat())
        }
        val title = DragHandleView(context).apply {
            text = context.getString(R.string.manual_control_overlay_title)
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(4), dp(6), dp(6))
            setOnTouchListener(DragTouchListener())
        }
        container.addView(title, matchWrap())
        if (!expanded) return container

        val target = state.targetPackage ?: context.getString(R.string.manual_control_waiting_target)
        container.addView(
            textView(
                context.getString(R.string.manual_control_overlay_status, statusLabel(state.status), target),
                12f,
                Color.LTGRAY,
            ),
            matchWrap(),
        )

        val selection = state.selection
        if (selection != null) {
            val node = state.latestSnapshot?.nodes?.firstOrNull { it.handle == selection.elementHandle }
            container.addView(
                textView(
                    context.getString(
                        R.string.manual_control_confirm_target,
                        actionLabel(selection.action),
                        node?.let(::nodeLabel) ?: selection.elementHandle,
                    ),
                    13f,
                    Color.WHITE,
                ),
                matchWrap(),
            )
            container.addView(button(context.getString(R.string.manual_control_confirm)) { onConfirm() }, matchWrap())
            container.addView(button(context.getString(R.string.manual_control_cancel_selection)) { onCancelSelection() }, matchWrap())
        } else if (state.status !in setOf(ManualControlStatus.EXECUTING, ManualControlStatus.VERIFYING)) {
            val candidates = actionCandidates(state).take(MAX_ACTIONS)
            if (candidates.isEmpty()) {
                container.addView(
                    textView(context.getString(R.string.manual_control_no_safe_actions), 12f, Color.LTGRAY),
                    matchWrap(),
                )
            } else {
                candidates.forEach { candidate ->
                    val label = "${actionLabel(candidate.action)} · ${nodeLabel(candidate.node)}"
                    val key = candidateKey(candidate)
                    container.addView(button(label) { selectLatestCandidate(key) }, matchWrap())
                }
            }
        }
        state.lastResult?.let { result ->
            container.addView(
                textView(context.getString(R.string.manual_control_last_result, result.status.name), 12f, Color.LTGRAY),
                matchWrap(),
            )
        }
        container.addView(button(context.getString(R.string.manual_control_stop)) { onStop() }, matchWrap())
        return container
    }

    private fun actionCandidates(state: ManualControlState): List<ActionCandidate> {
        val snapshot = state.latestSnapshot ?: return emptyList()
        val packageName = state.targetPackage ?: return emptyList()
        return buildList {
            snapshot.nodes.asSequence()
                .filter { it.packageName == packageName }
                .forEach { node ->
                    ManualDeviceAction.entries.forEach { action ->
                        if (DeviceActionPolicy.evaluate(packageName, node, action) is DevicePolicyDecision.Allowed) {
                            add(ActionCandidate(node, action))
                        }
                    }
                }
        }.distinctBy { it.node.handle to it.action }
    }

    private fun nodeLabel(node: SemanticNode): String =
        listOf(node.text, node.contentDescription, node.resourceId?.substringAfterLast('/'), node.role)
            .firstOrNull { !it.isNullOrBlank() }
            ?.take(36)
            ?: node.role

    private fun actionLabel(action: ManualDeviceAction): String = when (action) {
        ManualDeviceAction.ACTIVATE -> context.getString(R.string.manual_control_action_activate)
        ManualDeviceAction.SCROLL_FORWARD -> context.getString(R.string.manual_control_action_scroll_forward)
        ManualDeviceAction.SCROLL_BACKWARD -> context.getString(R.string.manual_control_action_scroll_backward)
    }

    private fun statusLabel(status: ManualControlStatus): String = when (status) {
        ManualControlStatus.OBSERVING -> context.getString(R.string.manual_control_status_observing)
        ManualControlStatus.READY -> context.getString(R.string.manual_control_status_ready)
        ManualControlStatus.TARGET_SELECTED -> context.getString(R.string.manual_control_status_selected)
        ManualControlStatus.EXECUTING -> context.getString(R.string.manual_control_status_executing)
        ManualControlStatus.VERIFYING -> context.getString(R.string.manual_control_status_verifying)
        ManualControlStatus.PAUSED_PACKAGE_CHANGED -> context.getString(R.string.manual_control_status_package_changed)
        ManualControlStatus.FAILED -> context.getString(R.string.manual_control_status_failed)
        ManualControlStatus.CANCELLED, ManualControlStatus.IDLE -> context.getString(R.string.manual_control_status_stopped)
    }

    private fun updatePanel(state: ManualControlState) {
        val signature = panelSignature(state)
        if (signature == lastPanelSignature) return
        val view = buildPanel(state)
        val host = panelHost ?: TouchAwareFrameLayout(context).also { newHost ->
            runCatching { windowManager.addView(newHost, panelLayoutParams()) }
                .onSuccess { panelHost = newHost }
                .onFailure { Log.w(LOG_TAG, "Unable to add control panel", it) }
        }
        if (host === panelHost) {
            host.removeAllViews()
            host.addView(view)
            lastPanelSignature = signature
        }
    }

    private fun panelSignature(state: ManualControlState): PanelSignature {
        val selection = state.selection?.let { selected ->
            val node = state.latestSnapshot?.nodes?.firstOrNull { it.handle == selected.elementHandle }
            SelectionSignature(selected.action, node?.let { candidateKey(it, selected.action) })
        }
        val candidates = if (
            expanded && selection == null &&
            state.status !in setOf(ManualControlStatus.EXECUTING, ManualControlStatus.VERIFYING)
        ) {
            actionCandidates(state).take(MAX_ACTIONS).map(::candidateKey)
        } else {
            emptyList()
        }
        return PanelSignature(
            expanded = expanded,
            status = state.status,
            targetPackage = state.targetPackage,
            selection = selection,
            candidates = candidates,
            lastResultStatus = state.lastResult?.status?.name,
        )
    }

    private fun selectLatestCandidate(key: CandidateKey) {
        val candidates = latestState?.let(::actionCandidates).orEmpty()
        val candidate = candidates.firstOrNull { candidateKey(it) == key }
            ?: candidates.filter { candidateKey(it).hasSameSemanticTarget(key) }.singleOrNull()
            ?: return
        onSelect(candidate.node.handle, candidate.action)
    }

    private fun candidateKey(candidate: ActionCandidate): CandidateKey =
        candidateKey(candidate.node, candidate.action)

    private fun candidateKey(node: SemanticNode, action: ManualDeviceAction) = CandidateKey(
        action = action,
        packageName = node.packageName,
        resourceId = node.resourceId,
        text = node.text,
        contentDescription = node.contentDescription,
        role = node.role,
        bounds = node.bounds,
    )

    private fun updateHighlight(bounds: ScreenBounds) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            hideHighlight()
            return
        }
        ensureHighlightLayer()
        val view = highlight ?: return
        highlightedBounds = bounds
        view.visibility = View.VISIBLE
        runCatching { windowManager.updateViewLayout(view, highlightLayoutParams(bounds)) }
            .onFailure { Log.w(LOG_TAG, "Unable to position highlight", it) }

        // Some OEM window managers offset application overlays by an inset. Correct once using
        // the actual screen location without returning to a full-screen touch-through window.
        view.post {
            if (highlight !== view || highlightedBounds != bounds || view.visibility != View.VISIBLE) return@post
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val deltaX = bounds.left - location[0]
            val deltaY = bounds.top - location[1]
            if (deltaX != 0 || deltaY != 0) {
                val corrected = highlightLayoutParams(bounds).apply {
                    x += deltaX
                    y += deltaY
                }
                runCatching { windowManager.updateViewLayout(view, corrected) }
                    .onFailure { Log.w(LOG_TAG, "Unable to correct highlight position", it) }
            }
        }
    }

    private fun ensureHighlightLayer() {
        if (highlight != null) return
        val view = HighlightView(context).apply { visibility = View.INVISIBLE }
        runCatching { windowManager.addView(view, highlightLayoutParams(null)) }
            .onSuccess { highlight = view }
            .onFailure { Log.w(LOG_TAG, "Unable to add highlight layer", it) }
    }

    private fun hideHighlight() {
        highlightedBounds = null
        highlight?.visibility = View.INVISIBLE
    }

    private fun removeHighlight() {
        highlight?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(LOG_TAG, "Unable to remove highlight layer", it) }
        }
        highlight = null
        highlightedBounds = null
    }

    private fun removeViews() {
        panelHost?.let { runCatching { windowManager.removeView(it) } }
        panelHost = null
        latestState = null
        pendingPanelState = null
        lastPanelSignature = null
        panelTouchActive = false
        removeHighlight()
    }

    private fun panelLayoutParams() = WindowManager.LayoutParams(
        dp(300),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = panelX
        y = panelY
    }

    private fun highlightLayoutParams(bounds: ScreenBounds?) = WindowManager.LayoutParams(
        bounds?.width?.coerceAtLeast(1) ?: 1,
        bounds?.height?.coerceAtLeast(1) ?: 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds?.left ?: 0
        y = bounds?.top ?: 0
        alpha = HIGHLIGHT_WINDOW_ALPHA
    }

    private fun textView(text: String, sizeSp: Float, color: Int) = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
    }

    private fun button(text: String, action: () -> Unit) = Button(context).apply {
        this.text = text
        isAllCaps = false
        minHeight = 0
        setOnClickListener {
            Log.i(LOG_TAG, "control_button_click")
            action()
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private inner class DragTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = panelX
                    startY = panelY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelX = (startX + event.rawX - downRawX).roundToInt().coerceAtLeast(0)
                    panelY = (startY + event.rawY - downRawY).roundToInt().coerceAtLeast(0)
                    panelHost?.let { windowManager.updateViewLayout(it, panelLayoutParams()) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downRawX) < dp(8) && abs(event.rawY - downRawY) < dp(8)) {
                        expanded = !expanded
                        render(com.yukisoffd.lyracode.interaction.session.ManualControlController.state.value)
                    }
                    view.performClick()
                    return true
                }
            }
            return false
        }
    }

    private inner class TouchAwareFrameLayout(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                panelTouchActive = true
                requestUnbufferedDispatch(event)
                Log.i(LOG_TAG, "panel_touch_down")
            }
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                Log.i(LOG_TAG, if (event.actionMasked == MotionEvent.ACTION_UP) "panel_touch_up" else "panel_touch_cancel")
                panelTouchActive = false
                val pending = pendingPanelState
                pendingPanelState = null
                if (pending != null) post { render(pending) }
            }
            return handled
        }
    }

    private data class ActionCandidate(val node: SemanticNode, val action: ManualDeviceAction)

    private data class CandidateKey(
        val action: ManualDeviceAction,
        val packageName: String?,
        val resourceId: String?,
        val text: String?,
        val contentDescription: String?,
        val role: String,
        val bounds: ScreenBounds,
    ) {
        fun hasSameSemanticTarget(other: CandidateKey): Boolean =
            action == other.action &&
                packageName == other.packageName &&
                resourceId == other.resourceId &&
                text == other.text &&
                contentDescription == other.contentDescription &&
                role == other.role
    }

    private data class SelectionSignature(
        val action: ManualDeviceAction,
        val candidate: CandidateKey?,
    )

    private data class PanelSignature(
        val expanded: Boolean,
        val status: ManualControlStatus,
        val targetPackage: String?,
        val selection: SelectionSignature?,
        val candidates: List<CandidateKey>,
        val lastResultStatus: String?,
    )

    private class DragHandleView(context: Context) : TextView(context) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private class HighlightView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(77, 208, 225)
            style = Paint.Style.STROKE
            strokeWidth = 6f * resources.displayMetrics.density
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = paint.strokeWidth / 2f
            canvas.drawRect(
                inset,
                inset,
                width - inset,
                height - inset,
                paint,
            )
        }
    }

    private companion object {
        const val LOG_TAG = "LyraManualControl"
        const val MAX_ACTIONS = 8
        const val HIGHLIGHT_WINDOW_ALPHA = 0.79f
    }
}
