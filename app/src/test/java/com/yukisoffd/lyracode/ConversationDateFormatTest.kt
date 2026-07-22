package com.yukisoffd.lyracode

import com.yukisoffd.lyracode.data.AppSettings
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDateFormatTest {
    @Test
    fun `english mode formats month in English regardless of system locale`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(2026, Calendar.JANUARY, 15)
        }

        assertEquals(
            "Jan 2026",
            formatConversationYearMonth(
                timestamp = calendar.timeInMillis,
                languageMode = AppSettings.LANGUAGE_EN,
                systemLocale = Locale.SIMPLIFIED_CHINESE,
            ),
        )
    }
}
