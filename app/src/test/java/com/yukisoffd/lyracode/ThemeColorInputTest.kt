package com.yukisoffd.lyracode

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorInputTest {
    @Test
    fun acceptsColorWithHash() {
        assertEquals("#A1B2C3", sanitizeThemeColorInput("#A1B2C3"))
    }

    @Test
    fun acceptsColorWithoutHash() {
        assertEquals("#A1B2C3", sanitizeThemeColorInput("a1b2c3"))
    }

    @Test
    fun pastedColorReplacesRetainedHashAndExistingValue() {
        assertEquals("#ABCDEF", sanitizeThemeColorInput("##ABCDEF"))
        assertEquals("#ABCDEF", sanitizeThemeColorInput("#112233#ABCDEF"))
    }

    @Test
    fun filtersNonHexCharactersAndLimitsLength() {
        assertEquals("#ABC123", sanitizeThemeColorInput("#ab-c12z345"))
    }
}
