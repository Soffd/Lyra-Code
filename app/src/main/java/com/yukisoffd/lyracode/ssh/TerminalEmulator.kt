package com.yukisoffd.lyracode.ssh

import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class TerminalCell(
    var codePoint: Int = 32,
    var foreground: Int = DEFAULT_TERMINAL_FOREGROUND,
    var background: Int = DEFAULT_TERMINAL_BACKGROUND,
    var bold: Boolean = false,
    var inverse: Boolean = false,
    var continuation: Boolean = false,
)

data class TerminalSnapshot(
    val columns: Int,
    val rows: Int,
    val cells: List<TerminalCell>,
    val cursorColumn: Int,
    val cursorRow: Int,
    val cursorVisible: Boolean,
)

/** Small VT100/xterm screen model sufficient for full-screen programs such as vim and top. */
class TerminalEmulator(initialColumns: Int = 80, initialRows: Int = 24) {
    private var columns = initialColumns.coerceAtLeast(2)
    private var rows = initialRows.coerceAtLeast(2)
    private var cells = blankScreen()
    private val scrollback = ArrayDeque<List<TerminalCell>>()
    private var normalScreen: MutableList<TerminalCell>? = null
    private var cursorColumn = 0
    private var cursorRow = 0
    private var savedColumn = 0
    private var savedRow = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var foreground = DEFAULT_TERMINAL_FOREGROUND
    private var background = DEFAULT_TERMINAL_BACKGROUND
    private var bold = false
    private var inverse = false
    private var cursorVisible = true
    private var state = ParseState.NORMAL
    private val sequence = StringBuilder()
    private var utf8Remainder = ByteArray(0)
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    @Synchronized
    fun resize(newColumns: Int, newRows: Int) {
        val targetColumns = newColumns.coerceIn(2, 500)
        val targetRows = newRows.coerceIn(2, 300)
        if (targetColumns == columns && targetRows == rows) return
        val old = cells
        val oldColumns = columns
        val oldRows = rows
        if (targetColumns != oldColumns) scrollback.clear()
        columns = targetColumns
        rows = targetRows
        cells = blankScreen()
        for (row in 0 until minOf(oldRows, rows)) {
            for (column in 0 until minOf(oldColumns, columns)) {
                cells[index(column, row)] = old[row * oldColumns + column].copy()
            }
        }
        cursorColumn = cursorColumn.coerceIn(0, columns - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        savedRow = savedRow.coerceIn(0, rows - 1)
        scrollTop = 0
        scrollBottom = rows - 1
    }

    @Synchronized
    fun accept(bytes: ByteArray) {
        val combined = ByteArray(utf8Remainder.size + bytes.size)
        utf8Remainder.copyInto(combined)
        bytes.copyInto(combined, utf8Remainder.size)
        val source = ByteBuffer.wrap(combined)
        val chars = CharBuffer.allocate(combined.size + 1)
        decoder.decode(source, chars, false)
        utf8Remainder = ByteArray(source.remaining()).also { source.get(it) }
        chars.flip()
        while (chars.hasRemaining()) {
            val first = chars.get()
            val codePoint = if (Character.isHighSurrogate(first) && chars.hasRemaining()) {
                val second = chars.get()
                if (Character.isLowSurrogate(second)) Character.toCodePoint(first, second) else first.code
            } else first.code
            process(codePoint)
        }
    }

    @Synchronized
    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        columns = columns,
        rows = if (normalScreen == null) scrollback.size + rows else rows,
        cells = if (normalScreen == null) {
            buildList((scrollback.size + rows) * columns) {
                scrollback.forEach { row -> row.forEach { add(it.copy()) } }
                cells.forEach { add(it.copy()) }
            }
        } else {
            cells.map(TerminalCell::copy)
        },
        cursorColumn = cursorColumn,
        cursorRow = if (normalScreen == null) scrollback.size + cursorRow else cursorRow,
        cursorVisible = cursorVisible,
    )

    private fun process(codePoint: Int) {
        when (state) {
            ParseState.NORMAL -> when (codePoint) {
                0x1b -> state = ParseState.ESCAPE
                0x08 -> cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
                0x09 -> cursorColumn = (((cursorColumn / 8) + 1) * 8).coerceAtMost(columns - 1)
                0x0a, 0x0b, 0x0c -> lineFeed()
                0x0d -> cursorColumn = 0
                in 0x20..0x10ffff -> put(codePoint)
            }
            ParseState.ESCAPE -> when (codePoint.toChar()) {
                '[' -> { sequence.clear(); state = ParseState.CSI }
                ']' -> { sequence.clear(); state = ParseState.OSC }
                '7' -> { saveCursor(); state = ParseState.NORMAL }
                '8' -> { restoreCursor(); state = ParseState.NORMAL }
                'D' -> { lineFeed(); state = ParseState.NORMAL }
                'M' -> { reverseIndex(); state = ParseState.NORMAL }
                'E' -> { cursorColumn = 0; lineFeed(); state = ParseState.NORMAL }
                'c' -> { reset(); state = ParseState.NORMAL }
                else -> state = ParseState.NORMAL
            }
            ParseState.CSI -> {
                if (codePoint in 0x40..0x7e) {
                    handleCsi(sequence.toString(), codePoint.toChar())
                    sequence.clear()
                    state = ParseState.NORMAL
                } else if (sequence.length < 128) sequence.appendCodePoint(codePoint)
            }
            ParseState.OSC -> when (codePoint) {
                0x07 -> state = ParseState.NORMAL
                0x1b -> state = ParseState.OSC_ESCAPE
                else -> Unit
            }
            ParseState.OSC_ESCAPE -> state = if (codePoint == '\\'.code) ParseState.NORMAL else ParseState.OSC
        }
    }

    private fun put(codePoint: Int) {
        val width = cellWidth(codePoint)
        if (cursorColumn >= columns || (width == 2 && cursorColumn == columns - 1)) {
            cursorColumn = 0
            lineFeed()
        }
        cells[index(cursorColumn, cursorRow)] = TerminalCell(codePoint, foreground, background, bold, inverse)
        if (width == 2 && cursorColumn + 1 < columns) {
            cells[index(cursorColumn + 1, cursorRow)] = TerminalCell(continuation = true)
        }
        cursorColumn += width
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) scrollUp(1) else cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1) else cursorRow = (cursorRow - 1).coerceAtLeast(0)
    }

    private fun handleCsi(raw: String, command: Char) {
        val privateMode = raw.startsWith('?')
        val clean = raw.trimStart('?', '>', '!').filter { it.isDigit() || it == ';' || it == ':' }
        val params = clean.split(';').map { it.substringBefore(':').toIntOrNull() ?: 0 }
        fun p(index: Int, default: Int = 1) = params.getOrNull(index)?.takeIf { it != 0 } ?: default
        when (command) {
            'A' -> cursorRow = (cursorRow - p(0)).coerceAtLeast(scrollTop)
            'B', 'e' -> cursorRow = (cursorRow + p(0)).coerceAtMost(scrollBottom)
            'C', 'a' -> cursorColumn = (cursorColumn + p(0)).coerceAtMost(columns - 1)
            'D' -> cursorColumn = (cursorColumn - p(0)).coerceAtLeast(0)
            'E' -> { cursorRow = (cursorRow + p(0)).coerceAtMost(scrollBottom); cursorColumn = 0 }
            'F' -> { cursorRow = (cursorRow - p(0)).coerceAtLeast(scrollTop); cursorColumn = 0 }
            'G', '`' -> cursorColumn = (p(0) - 1).coerceIn(0, columns - 1)
            'd' -> cursorRow = (p(0) - 1).coerceIn(0, rows - 1)
            'H', 'f' -> {
                cursorRow = (p(0) - 1).coerceIn(0, rows - 1)
                cursorColumn = (p(1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> eraseDisplay(params.firstOrNull() ?: 0)
            'K' -> eraseLine(params.firstOrNull() ?: 0)
            'X' -> repeat(p(0)) { column -> if (cursorColumn + column < columns) blank(cursorColumn + column, cursorRow) }
            '@' -> insertCharacters(p(0))
            'P' -> deleteCharacters(p(0))
            'L' -> insertLines(p(0))
            'M' -> deleteLines(p(0))
            'S' -> scrollUp(p(0))
            'T' -> scrollDown(p(0))
            'm' -> applySgr(params.ifEmpty { listOf(0) })
            'r' -> {
                scrollTop = (p(0) - 1).coerceIn(0, rows - 1)
                scrollBottom = (p(1, rows) - 1).coerceIn(scrollTop, rows - 1)
                cursorColumn = 0
                cursorRow = 0
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h', 'l' -> if (privateMode) setPrivateMode(params, command == 'h')
        }
    }

    private fun setPrivateMode(params: List<Int>, enabled: Boolean) {
        params.forEach { mode ->
            when (mode) {
                25 -> cursorVisible = enabled
                47, 1047, 1049 -> if (enabled) enterAlternateScreen() else leaveAlternateScreen()
            }
        }
    }

    private fun enterAlternateScreen() {
        if (normalScreen == null) normalScreen = cells.map(TerminalCell::copy).toMutableList()
        cells = blankScreen()
        saveCursor()
        cursorColumn = 0
        cursorRow = 0
    }

    private fun leaveAlternateScreen() {
        normalScreen?.let { cells = it }
        normalScreen = null
        restoreCursor()
    }

    private fun applySgr(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            when (val value = params[i]) {
                0 -> { foreground = DEFAULT_TERMINAL_FOREGROUND; background = DEFAULT_TERMINAL_BACKGROUND; bold = false; inverse = false }
                1 -> bold = true
                7 -> inverse = true
                22 -> bold = false
                27 -> inverse = false
                30, 31, 32, 33, 34, 35, 36, 37 -> foreground = ANSI_COLORS[value - 30]
                39 -> foreground = DEFAULT_TERMINAL_FOREGROUND
                40, 41, 42, 43, 44, 45, 46, 47 -> background = ANSI_COLORS[value - 40]
                49 -> background = DEFAULT_TERMINAL_BACKGROUND
                90, 91, 92, 93, 94, 95, 96, 97 -> foreground = ANSI_BRIGHT_COLORS[value - 90]
                100, 101, 102, 103, 104, 105, 106, 107 -> background = ANSI_BRIGHT_COLORS[value - 100]
                38, 48 -> {
                    val isForeground = value == 38
                    if (params.getOrNull(i + 1) == 5 && params.getOrNull(i + 2) != null) {
                        val color = xtermColor(params[i + 2])
                        if (isForeground) foreground = color else background = color
                        i += 2
                    } else if (params.getOrNull(i + 1) == 2 && i + 4 < params.size) {
                        val color = Color.rgb(params[i + 2].coerceIn(0, 255), params[i + 3].coerceIn(0, 255), params[i + 4].coerceIn(0, 255))
                        if (isForeground) foreground = color else background = color
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> for (row in cursorRow until rows) for (column in (if (row == cursorRow) cursorColumn else 0) until columns) blank(column, row)
            1 -> for (row in 0..cursorRow) for (column in 0..(if (row == cursorRow) cursorColumn else columns - 1)) blank(column, row)
            2, 3 -> cells = blankScreen()
        }
    }

    private fun eraseLine(mode: Int) {
        val range = when (mode) { 1 -> 0..cursorColumn; 2 -> 0 until columns; else -> cursorColumn until columns }
        range.forEach { blank(it, cursorRow) }
    }

    private fun insertCharacters(count: Int) {
        for (column in columns - 1 downTo cursorColumn + count) cells[index(column, cursorRow)] = cells[index(column - count, cursorRow)].copy()
        repeat(count.coerceAtMost(columns - cursorColumn)) { blank(cursorColumn + it, cursorRow) }
    }

    private fun deleteCharacters(count: Int) {
        for (column in cursorColumn until columns - count) cells[index(column, cursorRow)] = cells[index(column + count, cursorRow)].copy()
        for (column in (columns - count).coerceAtLeast(cursorColumn) until columns) blank(column, cursorRow)
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (row in scrollBottom downTo cursorRow + 1) copyRow(row - 1, row)
            clearRow(cursorRow)
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceAtMost(scrollBottom - cursorRow + 1)) {
            for (row in cursorRow until scrollBottom) copyRow(row + 1, row)
            clearRow(scrollBottom)
        }
    }

    private fun scrollUp(count: Int) = repeat(count.coerceAtMost(scrollBottom - scrollTop + 1)) {
        if (normalScreen == null && scrollTop == 0 && scrollBottom == rows - 1) {
            scrollback.addLast(List(columns) { column -> cells[index(column, 0)].copy() })
            while (scrollback.size > MAX_SCROLLBACK_ROWS) scrollback.removeFirst()
        }
        for (row in scrollTop until scrollBottom) copyRow(row + 1, row)
        clearRow(scrollBottom)
    }

    private fun scrollDown(count: Int) = repeat(count.coerceAtMost(scrollBottom - scrollTop + 1)) {
        for (row in scrollBottom downTo scrollTop + 1) copyRow(row - 1, row)
        clearRow(scrollTop)
    }

    private fun copyRow(from: Int, to: Int) = repeat(columns) { column -> cells[index(column, to)] = cells[index(column, from)].copy() }
    private fun clearRow(row: Int) = repeat(columns) { blank(it, row) }
    private fun blank(column: Int, row: Int) { cells[index(column, row)] = TerminalCell(foreground = foreground, background = background) }
    private fun index(column: Int, row: Int) = row * columns + column
    private fun blankScreen() = MutableList(columns * rows) { TerminalCell() }
    private fun saveCursor() { savedColumn = cursorColumn; savedRow = cursorRow }
    private fun restoreCursor() { cursorColumn = savedColumn.coerceIn(0, columns - 1); cursorRow = savedRow.coerceIn(0, rows - 1) }

    private fun reset() {
        cells = blankScreen(); scrollback.clear(); cursorColumn = 0; cursorRow = 0; scrollTop = 0; scrollBottom = rows - 1
        foreground = DEFAULT_TERMINAL_FOREGROUND; background = DEFAULT_TERMINAL_BACKGROUND; bold = false; inverse = false; cursorVisible = true
    }

    private fun cellWidth(codePoint: Int): Int = if (
        codePoint >= 0x1100 && (codePoint <= 0x115f || codePoint in 0x2e80..0xa4cf || codePoint in 0xac00..0xd7a3 || codePoint in 0xf900..0xfaff || codePoint in 0xfe10..0xfe6f || codePoint in 0xff00..0xff60 || codePoint in 0x1f300..0x1faff)
    ) 2 else 1

    private fun xtermColor(value: Int): Int {
        val index = value.coerceIn(0, 255)
        if (index < 8) return ANSI_COLORS[index]
        if (index < 16) return ANSI_BRIGHT_COLORS[index - 8]
        if (index >= 232) { val gray = 8 + (index - 232) * 10; return Color.rgb(gray, gray, gray) }
        val cube = index - 16
        fun component(n: Int) = if (n == 0) 0 else 55 + n * 40
        return Color.rgb(component(cube / 36), component((cube / 6) % 6), component(cube % 6))
    }

    private enum class ParseState { NORMAL, ESCAPE, CSI, OSC, OSC_ESCAPE }

    private companion object {
        const val MAX_SCROLLBACK_ROWS = 2_000
    }
}

const val DEFAULT_TERMINAL_BACKGROUND: Int = 0xff0b1018.toInt()
const val DEFAULT_TERMINAL_FOREGROUND: Int = 0xffd7e0ea.toInt()
private val ANSI_COLORS = intArrayOf(0xff151b25.toInt(), 0xfff7768e.toInt(), 0xff9ece6a.toInt(), 0xffe0af68.toInt(), 0xff7aa2f7.toInt(), 0xffbb9af7.toInt(), 0xff7dcfff.toInt(), 0xffc0caf5.toInt())
private val ANSI_BRIGHT_COLORS = intArrayOf(0xff565f89.toInt(), 0xffff899d.toInt(), 0xffb9f27c.toInt(), 0xffffc777.toInt(), 0xff89b4fa.toInt(), 0xffcba6f7.toInt(), 0xff89dceb.toInt(), 0xffffffff.toInt())
