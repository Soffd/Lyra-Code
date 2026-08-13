package com.yukisoffd.lyracode.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCompositionStateTest {
    @Test
    fun `appended composing text is sent once`() {
        val events = mutableListOf<String>()
        val state = TerminalCompositionState(events::add) { events += "BACKSPACE" }

        state.update("c")
        state.update("ca")
        state.commit("cat")

        assertEquals(listOf("c", "a", "t"), events)
    }

    @Test
    fun `composition replacement edits the remote terminal`() {
        val events = mutableListOf<String>()
        val state = TerminalCompositionState(events::add) { events += "BACKSPACE" }

        state.update("car")
        state.commit("cat")

        assertEquals(listOf("car", "BACKSPACE", "t"), events)
    }

    @Test
    fun `committed clipboard text is sent immediately`() {
        val events = mutableListOf<String>()
        val state = TerminalCompositionState(events::add) { events += "BACKSPACE" }

        state.commit("echo hello")

        assertEquals(listOf("echo hello"), events)
    }

    @Test
    fun `recreated input connection forgets stale composition`() {
        val events = mutableListOf<String>()
        val state = TerminalCompositionState(events::add) { events += "BACKSPACE" }

        state.update("old")
        state.finish()
        state.update("n")

        assertEquals(listOf("old", "n"), events)
    }
}
