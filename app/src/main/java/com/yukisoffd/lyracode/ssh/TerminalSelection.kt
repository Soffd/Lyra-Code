package com.yukisoffd.lyracode.ssh

data class TerminalPosition(val row: Int, val column: Int)

data class TerminalSelection(val anchor: TerminalPosition, val extent: TerminalPosition) {
    fun ordered(columns: Int): Pair<TerminalPosition, TerminalPosition> {
        val anchorIndex = anchor.row * columns + anchor.column
        val extentIndex = extent.row * columns + extent.column
        return if (anchorIndex <= extentIndex) anchor to extent else extent to anchor
    }
}

internal object TerminalSelectionText {
    fun wordAt(snapshot: TerminalSnapshot, position: TerminalPosition): TerminalSelection {
        val safe = position.clamp(snapshot)
        fun cell(column: Int) = snapshot.cells[safe.row * snapshot.columns + column]
        fun selectable(column: Int): Boolean {
            val value = cell(column)
            return !value.continuation && value.codePoint != 32 && !Character.isWhitespace(value.codePoint)
        }
        if (!selectable(safe.column)) return TerminalSelection(safe, safe)
        var start = safe.column
        var end = safe.column
        while (start > 0 && (selectable(start - 1) || cell(start).continuation)) start--
        while (end + 1 < snapshot.columns && (selectable(end + 1) || cell(end + 1).continuation)) end++
        return TerminalSelection(
            TerminalPosition(safe.row, start),
            TerminalPosition(safe.row, end),
        )
    }

    fun extract(snapshot: TerminalSnapshot, selection: TerminalSelection): String {
        if (snapshot.columns <= 0 || snapshot.rows <= 0) return ""
        val (rawStart, rawEnd) = selection.ordered(snapshot.columns)
        val start = rawStart.clamp(snapshot)
        val end = rawEnd.clamp(snapshot)
        return buildString {
            for (row in start.row..end.row) {
                val fromColumn = if (row == start.row) start.column else 0
                val toColumn = if (row == end.row) end.column else snapshot.columns - 1
                val line = buildString {
                    for (column in fromColumn..toColumn) {
                        val cell = snapshot.cells[row * snapshot.columns + column]
                        if (!cell.continuation) appendCodePoint(cell.codePoint)
                    }
                }.trimEnd()
                append(line)
                if (row != end.row) append('\n')
            }
        }
    }

    private fun TerminalPosition.clamp(snapshot: TerminalSnapshot) = TerminalPosition(
        row = row.coerceIn(0, snapshot.rows - 1),
        column = column.coerceIn(0, snapshot.columns - 1),
    )
}
