package com.yukisoffd.lyracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTextFadeTest {
    @Test
    fun keepsStablePrefixOpaqueAndNewestCharacterSoft() {
        val fade = StreamingTextFade(contentLength = 100, opaquePosition = 20f)

        assertEquals(1f, fade.alphaAt(0))
        assertTrue(fade.alphaAt(99) < 0.15f)
        assertTrue(fade.alphaAt(40) > fade.alphaAt(80))
    }

    @Test
    fun charactersBecomeMoreOpaqueAsFadePositionCatchesUp() {
        val early = StreamingTextFade(contentLength = 100, opaquePosition = 20f)
        val later = StreamingTextFade(contentLength = 100, opaquePosition = 50f)

        assertTrue(later.alphaAt(60) > early.alphaAt(60))
    }

    @Test
    fun limitsAnimatedGradientToNewestTail() {
        val fade = StreamingTextFade(contentLength = 1_000, opaquePosition = 100f)

        assertEquals(880, fade.firstFadingSourceIndex)
        assertEquals(1f, fade.alphaAt(879))
        assertTrue(fade.alphaAt(999) < 0.15f)
    }
}
