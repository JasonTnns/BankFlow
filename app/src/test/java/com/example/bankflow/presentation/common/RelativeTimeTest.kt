package com.example.bankflow.presentation.common

import com.example.bankflow.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class RelativeTimeTest {

    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val utc = ZoneId.of("UTC")

    private fun text(savedAt: String) = lastUpdatedText(Instant.parse(savedAt), now, utc, Locale.US)

    @Test
    fun `under a minute is just now, and so is a clock that runs slightly fast`() {
        assertEquals(UiText.Res(R.string.saved_data_just_now), text("2026-10-29T11:59:30Z"))
        assertEquals(UiText.Res(R.string.saved_data_just_now), text("2026-10-29T12:00:20Z"))
    }

    @Test
    fun `minutes and hours are plurals, so every language gets its own forms`() {
        assertEquals(UiText.Plural(R.plurals.saved_data_minutes, 1), text("2026-10-29T11:59:00Z"))
        assertEquals(UiText.Plural(R.plurals.saved_data_minutes, 59), text("2026-10-29T11:00:30Z"))
        assertEquals(UiText.Plural(R.plurals.saved_data_hours, 1), text("2026-10-29T11:00:00Z"))
        assertEquals(UiText.Plural(R.plurals.saved_data_hours, 23), text("2026-10-28T12:30:00Z"))
    }

    @Test
    fun `a day or more shows the date`() {
        assertEquals(UiText.Res(R.string.saved_data_date, listOf("Oct 28, 2026")), text("2026-10-28T12:00:00Z"))
    }
}