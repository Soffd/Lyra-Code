package com.yukisoffd.lyracode.ssh

/** Computes a visual crop without changing the PTY or terminal buffer dimensions. */
internal object TerminalViewport {
    fun firstVisibleRow(totalRows: Int, cursorRow: Int, visibleRows: Int): Int {
        if (totalRows <= 0 || visibleRows >= totalRows) return 0
        val safeVisibleRows = visibleRows.coerceAtLeast(1)
        return (cursorRow - safeVisibleRows + 1).coerceIn(0, totalRows - safeVisibleRows)
    }
}
