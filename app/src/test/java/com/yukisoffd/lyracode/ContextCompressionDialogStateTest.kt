package com.yukisoffd.lyracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompressionDialogStateTest {
    @Test
    fun generationDoesNotMasqueradeAsCompressionOrTrapTheDialog() {
        val state = contextCompressionDialogState(isGenerating = true, isCompressing = false)

        assertEquals(ContextCompressionDialogStatus.WAITING_FOR_RESPONSE, state.status)
        assertTrue(state.canDismiss)
        assertTrue(state.canEdit)
        assertFalse(state.canStart)
    }

    @Test
    fun actualCompressionLocksMutableControlsUntilCompletion() {
        val state = contextCompressionDialogState(isGenerating = false, isCompressing = true)

        assertEquals(ContextCompressionDialogStatus.COMPRESSING, state.status)
        assertFalse(state.canDismiss)
        assertFalse(state.canEdit)
        assertFalse(state.canStart)
    }

    @Test
    fun idleDialogAllowsCompression() {
        val state = contextCompressionDialogState(isGenerating = false, isCompressing = false)

        assertEquals(ContextCompressionDialogStatus.IDLE, state.status)
        assertTrue(state.canDismiss)
        assertTrue(state.canEdit)
        assertTrue(state.canStart)
    }
}
