package com.yukisoffd.lyracode.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalViewportTest {
    @Test
    fun `full viewport starts at terminal top`() {
        assertEquals(0, TerminalViewport.firstVisibleRow(totalRows = 60, cursorRow = 20, visibleRows = 60))
    }

    @Test
    fun `keyboard reduced viewport follows cursor without resizing terminal`() {
        assertEquals(31, TerminalViewport.firstVisibleRow(totalRows = 60, cursorRow = 50, visibleRows = 20))
    }

    @Test
    fun `cursor near top does not create blank space`() {
        assertEquals(0, TerminalViewport.firstVisibleRow(totalRows = 60, cursorRow = 5, visibleRows = 20))
    }
}
