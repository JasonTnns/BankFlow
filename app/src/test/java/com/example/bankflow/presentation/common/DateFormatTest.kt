package com.example.bankflow.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue

class DateFormatTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-29T12:00:00Z")

    @Test
    fun `formats a medium date`() {
        assertEquals("Oct 29, 2026", Instant.parse("2026-10-29T10:00:00Z").formatDate(utc, Locale.US))
    }

    @Test
    fun `same day is Today and the day before is Yesterday`() {
        assertEquals("Today", Instant.parse("2026-10-29T00:01:00Z").dayLabel(now, utc, Locale.US))
        assertEquals("Yesterday", Instant.parse("2026-10-28T23:59:00Z").dayLabel(now, utc, Locale.US))
    }

    @Test
    fun `older days show the date`() {
        assertEquals("Oct 27, 2026", Instant.parse("2026-10-27T12:00:00Z").dayLabel(now, utc, Locale.US))
    }

    @Test
    fun `days follow the user's time zone, not UTC`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val nowInTokyo = Instant.parse("2026-10-29T01:00:00Z")
        val lateOct28Utc = Instant.parse("2026-10-28T20:00:00Z")

        assertEquals("Today", lateOct28Utc.dayLabel(nowInTokyo, tokyo, Locale.US))
        assertEquals("Yesterday", lateOct28Utc.dayLabel(nowInTokyo, utc, Locale.US))
    }

    @Test
    fun `date and time uses the user's zone`() {
        val text = Instant.parse("2026-10-29T10:30:00Z").formatDateTime(ZoneId.of("Asia/Tokyo"), Locale.US)

        assertTrue(text, text.contains("Oct 29, 2026"))
        assertTrue(text, text.contains("7:30"))
    }
}