package com.yukisoffd.lyracode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.debian.ProotLinuxManager
import com.yukisoffd.lyracode.ssh.DEFAULT_TERMINAL_BACKGROUND
import com.yukisoffd.lyracode.ssh.LocalProotTerminalSessionManager
import com.yukisoffd.lyracode.ssh.SshTerminalSessionManager
import com.yukisoffd.lyracode.ssh.SshTerminalMessage
import com.yukisoffd.lyracode.ssh.SshTerminalState
import com.yukisoffd.lyracode.ssh.SshTerminalStatus
import com.yukisoffd.lyracode.ssh.TerminalConnection
import com.yukisoffd.lyracode.ssh.TerminalSession
import com.yukisoffd.lyracode.ssh.TerminalInputEncoder
import com.yukisoffd.lyracode.ssh.TerminalInputView
import com.yukisoffd.lyracode.ssh.TerminalPosition
import com.yukisoffd.lyracode.ssh.TerminalSelection
import com.yukisoffd.lyracode.ssh.TerminalSelectionText
import com.yukisoffd.lyracode.ssh.TerminalViewport
import com.yukisoffd.lyracode.ssh.TerminalSnapshot
import kotlin.math.ceil
import kotlinx.coroutines.delay

@Composable
internal fun TerminalScreen(
    settings: AppSettings,
    sessionManager: SshTerminalSessionManager,
    localSessionManager: LocalProotTerminalSessionManager,
) {
    val context = LocalContext.current
    val runtime = remember(context) { ProotLinuxManager.getInstance(context) }
    val runtimeState by runtime.state.collectAsState()
    val servers = remember { settings.sshServers().filter { it.enabled } }
    val localInstances = runtimeState.instances.filter { it.enabled }
    val targets = buildList {
        localInstances.forEach { linux ->
            add(TerminalTarget("proot:${linux.id}", "${linux.name} · ${linux.id}", linuxId = linux.id))
        }
        servers.forEach { add(TerminalTarget(it.id, it.name, it)) }
    }
    var selectedId by rememberSaveable { mutableStateOf(localInstances.firstOrNull()?.let { "proot:${it.id}" } ?: servers.firstOrNull()?.id.orEmpty()) }
    val selectedTarget = targets.firstOrNull { it.id == selectedId } ?: targets.firstOrNull()
    LaunchedEffect(selectedTarget?.id) {
        if (selectedTarget != null && selectedTarget.id != selectedId) selectedId = selectedTarget.id
    }
    val connection: TerminalSession? = remember(selectedTarget?.id) {
        selectedTarget?.linuxId?.let(localSessionManager::connection)
            ?: selectedTarget?.server?.let(sessionManager::connection)
    }
    val state by connection?.state?.collectAsState() ?: remember { mutableStateOf(SshTerminalState()) }
    val screen by connection?.screen?.collectAsState() ?: remember { mutableStateOf(com.yukisoffd.lyracode.ssh.TerminalEmulator().snapshot()) }
    var pickerExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(DEFAULT_TERMINAL_BACKGROUND)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xff111827))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { pickerExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xff8b95a7)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xffe8edf5)),
                ) {
                    Text(
                        selectedTarget?.label ?: uiText(R.string.ssh_terminal_no_servers),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = uiText(R.string.ssh_terminal_select_server))
                }
                DropdownMenu(expanded = pickerExpanded, onDismissRequest = { pickerExpanded = false }) {
                    targets.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.label) },
                            onClick = { selectedId = target.id; pickerExpanded = false },
                        )
                    }
                }
            }
            if (state.status == SshTerminalStatus.CONNECTED || state.status == SshTerminalStatus.RECONNECTING) {
                OutlinedButton(
                    onClick = { connection?.disconnect() },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xff8b95a7)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xffe8edf5)),
                ) { Text(uiText(R.string.ssh_terminal_disconnect)) }
            } else {
                Button(
                    onClick = {
                        val server = selectedTarget?.server
                        if (server != null) (connection as? TerminalConnection)?.connect(server) else connection?.connect()
                    },
                    enabled = selectedTarget != null,
                    shape = RoundedCornerShape(12.dp),
                ) { Text(uiText(R.string.ssh_terminal_connect)) }
            }
        }
        TerminalStatusBar(state)
        TerminalSurface(screen = screen, connection = connection, enabled = state.status == SshTerminalStatus.CONNECTED)
    }
}

@Composable
private fun TerminalStatusBar(state: SshTerminalState) {
    val color = when (state.status) {
        SshTerminalStatus.CONNECTED -> Color(0xff73daca)
        SshTerminalStatus.CONNECTING, SshTerminalStatus.RECONNECTING -> Color(0xffe0af68)
        SshTerminalStatus.ERROR -> Color(0xfff7768e)
        else -> Color(0xff7f8ca3)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xff0e1521))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Text(terminalStatusText(state), color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun terminalStatusText(state: SshTerminalState): String = when (state.message) {
    SshTerminalMessage.IDLE -> uiText(R.string.ssh_terminal_idle)
    SshTerminalMessage.CONNECTING -> uiText(R.string.ssh_terminal_connecting)
    SshTerminalMessage.CONNECTED -> uiText(R.string.ssh_terminal_connected)
    SshTerminalMessage.RECONNECTING -> uiText(R.string.ssh_terminal_reconnecting, state.reconnectAttempt.coerceAtLeast(1))
    SshTerminalMessage.DISCONNECTED -> uiText(R.string.ssh_terminal_disconnected)
    SshTerminalMessage.HOST_KEY_CHANGED -> uiText(R.string.ssh_terminal_host_key_changed)
    SshTerminalMessage.AUTH_FAILED -> uiText(R.string.ssh_terminal_auth_failed)
    SshTerminalMessage.TIMEOUT -> uiText(R.string.ssh_terminal_timeout)
    SshTerminalMessage.CONNECTION_FAILED -> uiText(R.string.ssh_terminal_connection_failed)
}

@Composable
private fun TerminalSurface(screen: TerminalSnapshot, connection: TerminalSession?, enabled: Boolean) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val keyboardOffsetPx = rememberAnimatedKeyboardAvoidanceOffsetPx()
    val keyboardOffsetDp = with(density) { keyboardOffsetPx.toDp() }
    val keyboard = LocalSoftwareKeyboardController.current
    var terminalInputView by remember { mutableStateOf<TerminalInputView?>(null) }
    var ctrl by rememberSaveable { mutableStateOf(false) }
    var alt by rememberSaveable { mutableStateOf(false) }
    var renderWidth by remember { mutableIntStateOf(0) }
    var renderHeight by remember { mutableIntStateOf(0) }
    var scrollOffsetRows by remember(connection) { mutableIntStateOf(0) }
    var priorSnapshotRows by remember(connection) { mutableIntStateOf(screen.rows) }
    var scrollRemainder by remember(connection) { mutableStateOf(0f) }
    var selection by remember(connection, screen.columns) { mutableStateOf<TerminalSelection?>(null) }
    var startHandleFixed by remember(connection, screen.columns) { mutableStateOf<TerminalPosition?>(null) }
    var endHandleFixed by remember(connection, screen.columns) { mutableStateOf<TerminalPosition?>(null) }
    val textSizePx = with(density) { 13.sp.toPx() }
    val normalTypeface = remember { Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) }
    val boldTypeface = remember { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    val paint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = normalTypeface
            textSize = textSizePx
            isSubpixelText = false
        }
    }
    val cellWidth = remember(textSizePx) {
        paint.typeface = normalTypeface
        paint.textScaleX = 1f
        // Android OEM monospace families can fall back to a proportional bold/CJK face. The
        // terminal column advance is therefore based on the stable ASCII digit advance; wider
        // fallback glyphs are fitted into their one- or two-column slot while drawing.
        ceil(paint.measureText("0").toDouble()).toFloat()
    }
    val cellHeight = remember(textSizePx) { ceil((paint.fontMetrics.descent - paint.fontMetrics.ascent).toDouble()).toFloat() }
    val visibleRows = (renderHeight / cellHeight).toInt().coerceAtLeast(1)
    val followCursorFirstRow = TerminalViewport.firstVisibleRow(screen.rows, screen.cursorRow, visibleRows)
    val firstVisibleRow = (followCursorFirstRow - scrollOffsetRows)
        .coerceIn(0, (screen.rows - visibleRows).coerceAtLeast(0))
    val lastVisibleRow = (firstVisibleRow + visibleRows).coerceAtMost(screen.rows)

    fun positionAt(x: Float, y: Float): TerminalPosition = TerminalPosition(
        row = (firstVisibleRow + (y / cellHeight).toInt()).coerceIn(0, (screen.rows - 1).coerceAtLeast(0)),
        column = (x / cellWidth).toInt().coerceIn(0, (screen.columns - 1).coerceAtLeast(0)),
    )

    fun moveSelectionHandle(
        absolutePosition: Offset,
        isStart: Boolean,
        fixedEndpoint: TerminalPosition,
    ) {
        var targetScrollOffset = scrollOffsetRows
        val edge = cellHeight * 1.5f
        if (absolutePosition.y < edge && targetScrollOffset < followCursorFirstRow) {
            targetScrollOffset = (targetScrollOffset + 1).coerceAtMost(followCursorFirstRow)
        } else if (absolutePosition.y > renderHeight - edge && targetScrollOffset > 0) {
            targetScrollOffset = (targetScrollOffset - 1).coerceAtLeast(0)
        }
        scrollOffsetRows = targetScrollOffset
        val targetFirstVisibleRow = (followCursorFirstRow - targetScrollOffset)
            .coerceIn(0, (screen.rows - visibleRows).coerceAtLeast(0))
        val visibleRowIndex = ((absolutePosition.y / cellHeight).toInt() - 1)
            .coerceIn(0, (visibleRows - 1).coerceAtLeast(0))
        val row = (targetFirstVisibleRow + visibleRowIndex)
            .coerceIn(0, (screen.rows - 1).coerceAtLeast(0))
        val rawColumn = (absolutePosition.x / cellWidth).toInt() - if (isStart) 0 else 1
        val candidate = TerminalPosition(
            row = row,
            column = rawColumn.coerceIn(0, (screen.columns - 1).coerceAtLeast(0)),
        )
        val candidateIndex = candidate.row * screen.columns + candidate.column
        val fixedIndex = fixedEndpoint.row * screen.columns + fixedEndpoint.column
        val constrained = when {
            isStart && candidateIndex > fixedIndex -> fixedEndpoint
            !isStart && candidateIndex < fixedIndex -> fixedEndpoint
            else -> candidate
        }
        selection = if (isStart) {
            TerminalSelection(constrained, fixedEndpoint)
        } else {
            TerminalSelection(fixedEndpoint, constrained)
        }
    }

    fun sendRaw(text: String) {
        if (enabled) connection?.write(text)
    }

    fun sendCharacter(codePoint: Int) {
        val bytes = TerminalInputEncoder.encode(codePoint, ctrl = ctrl, alt = alt)
        if (enabled) connection?.write(bytes)
        ctrl = false
        alt = false
    }

    fun sendText(text: String) {
        text.codePoints().forEach(::sendCharacter)
    }

    fun focusTerminalInput() {
        terminalInputView?.focusAndShowKeyboard() ?: keyboard?.show()
    }

    fun sendKey(sequence: String) {
        var value = sequence
        if (alt) value = "\u001b$value"
        if (enabled) connection?.write(value)
        if (sequence.length == 1 && sequence[0].code >= 32) { ctrl = false; alt = false }
    }

    LaunchedEffect(renderWidth, renderHeight, cellWidth, cellHeight, connection) {
        if (renderWidth > 0 && renderHeight > 0 && connection != null) {
            // Let an IME animation settle before sending a window-change. Full-screen programs
            // then redraw their status/command rows inside the actually visible terminal area.
            delay(TERMINAL_RESIZE_SETTLE_MILLIS)
            val columns = ((renderWidth - with(density) { 4.dp.toPx() }) / cellWidth).toInt().coerceAtLeast(2)
            val rows = (renderHeight / cellHeight).toInt().coerceAtLeast(2)
            connection.resize(columns, rows, renderWidth, renderHeight)
        }
    }
    LaunchedEffect(screen.rows) {
        val addedRows = (screen.rows - priorSnapshotRows).coerceAtLeast(0)
        if (scrollOffsetRows > 0 && addedRows > 0) scrollOffsetRows += addedRows
        priorSnapshotRows = screen.rows
        scrollOffsetRows = scrollOffsetRows.coerceAtMost(followCursorFirstRow)
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .padding(bottom = keyboardOffsetDp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(DEFAULT_TERMINAL_BACKGROUND))
                .pointerInput(connection, selection == null) {
                    if (selection == null) {
                        detectTapGestures(onTap = {
                            scrollOffsetRows = 0
                            focusTerminalInput()
                        })
                    }
                }
                .pointerInput(screen.rows, visibleRows, followCursorFirstRow, selection == null) {
                    if (selection == null) {
                        detectVerticalDragGestures { _, dragAmount ->
                            scrollRemainder += dragAmount
                            val rowDelta = (scrollRemainder / cellHeight).toInt()
                            if (rowDelta != 0) {
                                scrollOffsetRows = (scrollOffsetRows + rowDelta).coerceIn(0, followCursorFirstRow)
                                scrollRemainder -= rowDelta * cellHeight
                            }
                        }
                    }
                }
                .pointerInput(screen, firstVisibleRow) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            keyboard?.hide()
                            val position = positionAt(offset.x, offset.y)
                            selection = TerminalSelectionText.wordAt(screen, position)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            selection = selection?.copy(extent = positionAt(change.position.x, change.position.y))
                        },
                    )
                }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val sequence = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
                        KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
                        KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
                        KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
                        KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
                        KeyEvent.KEYCODE_TAB -> "\t"
                        KeyEvent.KEYCODE_ESCAPE -> "\u001b"
                        KeyEvent.KEYCODE_DEL -> "\u007f"
                        KeyEvent.KEYCODE_ENTER -> "\r"
                        else -> null
                    }
                    if (sequence != null) { sendRaw(sequence); true }
                    else if (native.isCtrlPressed && native.unicodeChar != 0) { connection?.write(byteArrayOf((native.unicodeChar and 0x1f).toByte())); true }
                    else if (native.isAltPressed && native.unicodeChar != 0) { sendRaw("\u001b" + String(Character.toChars(native.unicodeChar))); true }
                    else false
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        renderWidth = it.width
                        renderHeight = it.height
                    },
            ) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val orderedSelection = selection?.ordered(screen.columns)
                    screen.cells.forEachIndexed { index, cell ->
                        if (cell.continuation) return@forEachIndexed
                        val column = index % screen.columns
                        val row = index / screen.columns
                        if (row !in firstVisibleRow until lastVisibleRow) return@forEachIndexed
                        val x = column * cellWidth
                        val y = (row - firstVisibleRow) * cellHeight
                        if (orderedSelection != null) {
                            val absoluteIndex = row * screen.columns + column
                            val startIndex = orderedSelection.first.row * screen.columns + orderedSelection.first.column
                            val endIndex = orderedSelection.second.row * screen.columns + orderedSelection.second.column
                            if (absoluteIndex in startIndex..endIndex) {
                                paint.color = 0xff284f78.toInt()
                                native.drawRect(x, y, x + cellWidth, y + cellHeight, paint)
                            }
                        }
                        val foreground = if (cell.inverse) cell.background else cell.foreground
                        val background = if (cell.inverse) cell.foreground else cell.background
                        if (background != DEFAULT_TERMINAL_BACKGROUND) {
                            paint.color = background
                            native.drawRect(x, y, x + cellWidth * (if (index + 1 < screen.cells.size && screen.cells[index + 1].continuation) 2 else 1), y + cellHeight, paint)
                        }
                        if (cell.codePoint != 32) {
                            paint.color = foreground
                            paint.typeface = if (cell.bold) boldTypeface else normalTypeface
                            paint.textScaleX = 1f
                            val glyph = String(Character.toChars(cell.codePoint))
                            val slotWidth = cellWidth * if (index + 1 < screen.cells.size && screen.cells[index + 1].continuation) 2f else 1f
                            val naturalWidth = paint.measureText(glyph)
                            if (naturalWidth > slotWidth && naturalWidth > 0f) {
                                paint.textScaleX = slotWidth / naturalWidth
                            }
                            val fittedWidth = paint.measureText(glyph)
                            val glyphX = x + ((slotWidth - fittedWidth) / 2f).coerceAtLeast(0f)
                            native.drawText(glyph, glyphX, y - paint.fontMetrics.ascent + (cellHeight + paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f, paint)
                        }
                    }
                    if (screen.cursorVisible && enabled) {
                        paint.color = 0xff7aa2f7.toInt()
                        val x = screen.cursorColumn * cellWidth
                        if (screen.cursorRow in firstVisibleRow until lastVisibleRow) {
                            val y = (screen.cursorRow - firstVisibleRow) * cellHeight
                            native.drawRect(x, y + cellHeight - with(density) { 2.dp.toPx() }, x + cellWidth, y + cellHeight, paint)
                        }
                    }
                }
            }
            AndroidView(
                factory = { context ->
                    TerminalInputView(context).also {
                        terminalInputView = it
                        it.onText = ::sendText
                        it.onBackspace = { sendRaw("\u007f") }
                        it.onEnter = { sendRaw("\r") }
                        it.onTerminalKey = ::sendRaw
                    }
                },
                update = {
                    terminalInputView = it
                    it.onText = ::sendText
                    it.onBackspace = { sendRaw("\u007f") }
                    it.onEnter = { sendRaw("\r") }
                    it.onTerminalKey = ::sendRaw
                },
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.BottomStart),
            )
            selection?.let { activeSelection ->
                val ordered = activeSelection.ordered(screen.columns)
                val startCenter = Offset(
                    x = ordered.first.column * cellWidth,
                    y = (ordered.first.row - firstVisibleRow + 1) * cellHeight,
                )
                val endCenter = Offset(
                    x = (ordered.second.column + 1) * cellWidth,
                    y = (ordered.second.row - firstVisibleRow + 1) * cellHeight,
                )
                if (ordered.first.row in firstVisibleRow until lastVisibleRow) {
                    TerminalSelectionHandle(
                        center = startCenter,
                        isStart = true,
                        onDragStart = { startHandleFixed = selection?.ordered(screen.columns)?.second },
                        onPosition = { position ->
                            startHandleFixed?.let { moveSelectionHandle(position, true, it) }
                        },
                    )
                }
                if (ordered.second.row in firstVisibleRow until lastVisibleRow) {
                    TerminalSelectionHandle(
                        center = endCenter,
                        isStart = false,
                        onDragStart = { endHandleFixed = selection?.ordered(screen.columns)?.first },
                        onPosition = { position ->
                            endHandleFixed?.let { moveSelectionHandle(position, false, it) }
                        },
                    )
                }
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xff1d2939), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = {
                        val copied = TerminalSelectionText.extract(screen, activeSelection)
                        if (copied.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", copied))
                        }
                        selection = null
                    }) { Text(uiText(R.string.action_copy)) }
                    OutlinedButton(
                        onClick = { selection = null },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xffe8edf5)),
                    ) { Text(uiText(R.string.ui_clear_selection)) }
                }
            }
            if (scrollOffsetRows > 0 && selection == null) {
                Text(
                    text = "↑ $scrollOffsetRows",
                    color = Color(0xffe8edf5),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xcc1d2939), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        TerminalKeyToolbar(
            ctrl = ctrl,
            alt = alt,
            onCtrl = {
                terminalInputView?.finishComposingInput()
                ctrl = !ctrl
                focusTerminalInput()
            },
            onAlt = {
                terminalInputView?.finishComposingInput()
                alt = !alt
                focusTerminalInput()
            },
            onKey = { sequence ->
                terminalInputView?.finishComposingInput()
                sendKey(sequence)
                focusTerminalInput()
            },
        )
    }
}

private const val TERMINAL_RESIZE_SETTLE_MILLIS = 120L

private data class TerminalTarget(
    val id: String,
    val label: String,
    val server: SshServerConfig? = null,
    val linuxId: String? = null,
)

@Composable
private fun TerminalSelectionHandle(
    center: Offset,
    isStart: Boolean,
    onDragStart: () -> Unit,
    onPosition: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val handleSize = 32.dp
    val handleSizePx = with(density) { handleSize.toPx() }
    val latestCenter by rememberUpdatedState(center)
    val latestOnPosition by rememberUpdatedState(onPosition)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    Box(
        Modifier
            .offset {
                IntOffset(
                    x = (center.x - handleSizePx / 2f).toInt(),
                    y = (center.y - handleSizePx / 2f).toInt(),
                )
            }
            .size(handleSize)
            .zIndex(4f)
            .background(Color(0xff7aa2f7), CircleShape)
            .border(2.dp, Color(0xffdbe7ff), CircleShape)
            .semantics {
                contentDescription = if (isStart) "terminal-selection-start" else "terminal-selection-end"
            }
            .pointerInput(isStart) {
                var dragCenter = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        dragCenter = latestCenter
                        latestOnDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragCenter += dragAmount
                        latestOnPosition(dragCenter)
                    },
                )
            },
    )
}

@Composable
private fun TerminalKeyToolbar(
    ctrl: Boolean,
    alt: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xff111827))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalKey("Esc", Modifier.weight(1f)) { onKey("\u001b") }
            TerminalKey("/", Modifier.weight(1f)) { onKey("/") }
            TerminalKey("-", Modifier.weight(1f)) { onKey("-") }
            TerminalKey("Home", Modifier.weight(1f)) { onKey("\u001b[H") }
            TerminalKey("↑", Modifier.weight(1f)) { onKey("\u001b[A") }
            TerminalKey("End", Modifier.weight(1f)) { onKey("\u001b[F") }
            TerminalKey("PgUp", Modifier.weight(1f)) { onKey("\u001b[5~") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalKey("Tab", Modifier.weight(1f)) { onKey("\t") }
            TerminalKey("Ctrl", Modifier.weight(1f), selected = ctrl, onClick = onCtrl)
            TerminalKey("Alt", Modifier.weight(1f), selected = alt, onClick = onAlt)
            TerminalKey("←", Modifier.weight(1f)) { onKey("\u001b[D") }
            TerminalKey("↓", Modifier.weight(1f)) { onKey("\u001b[B") }
            TerminalKey("→", Modifier.weight(1f)) { onKey("\u001b[C") }
            TerminalKey("PgDn", Modifier.weight(1f)) { onKey("\u001b[6~") }
        }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier
            .height(38.dp)
            .background(if (selected) Color(0xff7aa2f7) else Color(0xff1d2939), shape)
            .border(1.dp, if (selected) Color(0xff9db8ff) else Color(0xff344054), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(0xff07101f) else Color(0xffd7e0ea), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
    }
}
