package com.yukisoffd.lyracode.ssh

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TerminalInputEncoderTest {
    @Test
    fun `ctrl c is case insensitive for phone keyboard input`() {
        assertArrayEquals(byteArrayOf(0x03), TerminalInputEncoder.encode('c'.code, ctrl = true, alt = false))
        assertArrayEquals(byteArrayOf(0x03), TerminalInputEncoder.encode('C'.code, ctrl = true, alt = false))
    }

    @Test
    fun `alt prefixes input with escape and combines with ctrl`() {
        assertArrayEquals(byteArrayOf(0x1b, 'c'.code.toByte()), TerminalInputEncoder.encode('c'.code, ctrl = false, alt = true))
        assertArrayEquals(byteArrayOf(0x1b, 0x03), TerminalInputEncoder.encode('C'.code, ctrl = true, alt = true))
    }

    @Test
    fun `common terminal control punctuation is supported`() {
        assertArrayEquals(byteArrayOf(0x00), TerminalInputEncoder.encode(' '.code, ctrl = true, alt = false))
        assertArrayEquals(byteArrayOf(0x1b), TerminalInputEncoder.encode('['.code, ctrl = true, alt = false))
        assertArrayEquals(byteArrayOf(0x7f), TerminalInputEncoder.encode('?'.code, ctrl = true, alt = false))
    }
}
