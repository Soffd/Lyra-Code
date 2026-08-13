package com.yukisoffd.lyracode.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    @Test
    fun `parses cursor movement and ansi colors`() {
        val terminal = TerminalEmulator(12, 4)
        terminal.accept("abc\u001b[2;3H\u001b[31mX".toByteArray())

        val snapshot = terminal.snapshot()
        assertEquals('a'.code, snapshot.cells[0].codePoint)
        assertEquals('X'.code, snapshot.cells[1 * snapshot.columns + 2].codePoint)
        assertEquals(0xfff7768e.toInt(), snapshot.cells[1 * snapshot.columns + 2].foreground)
    }

    @Test
    fun `supports alternate screen used by vim`() {
        val terminal = TerminalEmulator(10, 3)
        terminal.accept("shell".toByteArray())
        terminal.accept("\u001b[?1049hvim".toByteArray())
        assertEquals('v'.code, terminal.snapshot().cells[0].codePoint)

        terminal.accept("\u001b[?1049l".toByteArray())
        val restored = terminal.snapshot()
        assertEquals("shell", restored.cells.take(5).map { it.codePoint.toChar() }.joinToString(""))
    }

    @Test
    fun `preserves split utf8 input and wide cell continuation`() {
        val terminal = TerminalEmulator(10, 2)
        val bytes = "你".toByteArray()
        terminal.accept(bytes.copyOfRange(0, 2))
        terminal.accept(bytes.copyOfRange(2, bytes.size))

        val snapshot = terminal.snapshot()
        assertEquals('你'.code, snapshot.cells[0].codePoint)
        assertTrue(snapshot.cells[1].continuation)
        assertFalse(snapshot.cells[2].continuation)
    }

    @Test
    fun `erase display clears visible buffer`() {
        val terminal = TerminalEmulator(8, 2)
        terminal.accept("content\u001b[2J".toByteArray())
        assertTrue(terminal.snapshot().cells.all { it.codePoint == 32 })
    }

    @Test
    fun `growing rows keeps existing output at top`() {
        val terminal = TerminalEmulator(8, 5)
        terminal.accept("one\r\ntwo".toByteArray())

        terminal.resize(8, 8)

        val snapshot = terminal.snapshot()
        val top = snapshot.cells.take(snapshot.columns)
            .map { it.codePoint.toChar() }
            .joinToString("")
            .trimEnd()
        assertEquals("one", top)
        assertEquals(1, snapshot.cursorRow)
    }

    @Test
    fun `normal screen keeps scrolled output in history`() {
        val terminal = TerminalEmulator(6, 2)
        terminal.accept("one\r\ntwo\r\ntri".toByteArray())

        val snapshot = terminal.snapshot()
        assertEquals(3, snapshot.rows)
        assertEquals("one", snapshot.cells.take(6).map { it.codePoint.toChar() }.joinToString("").trimEnd())
        assertEquals(2, snapshot.cursorRow)
    }

    @Test
    fun `selection extracts text without continuation cells or trailing padding`() {
        val terminal = TerminalEmulator(6, 2)
        terminal.accept("你a\r\nb".toByteArray())
        val snapshot = terminal.snapshot()

        val copied = TerminalSelectionText.extract(
            snapshot,
            TerminalSelection(TerminalPosition(0, 0), TerminalPosition(1, 5)),
        )

        assertEquals("你a\nb", copied)
    }

    @Test
    fun `long press selects a complete word`() {
        val terminal = TerminalEmulator(16, 2)
        terminal.accept("echo hello world".toByteArray())
        val snapshot = terminal.snapshot()

        val selection = TerminalSelectionText.wordAt(snapshot, TerminalPosition(0, 7))

        assertEquals("hello", TerminalSelectionText.extract(snapshot, selection))
    }
}
