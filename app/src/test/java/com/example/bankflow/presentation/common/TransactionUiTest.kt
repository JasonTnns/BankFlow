package com.example.bankflow.presentation.common

import com.example.bankflow.domain.model.Direction
import com.example.bankflow.testutil.testTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TransactionUiTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-29T12:00:00Z")

    @Test
    fun `incoming and outgoing amounts carry a sign`() {
        assertEquals("+$1,000.00", testTransaction("a", Direction.IN, 100_000).signedAmount(Locale.US))
        assertEquals("\u2212$25.00", testTransaction("b", Direction.OUT, 2_500).signedAmount(Locale.US))
    }

    @Test
    fun `subtitle prefers the description and falls back to the category`() {
        assertEquals(
            "Welcome bonus \u00B7 Today",
            testTransaction("a", description = "Welcome bonus").subtitle(now, utc, Locale.US),
        )
        assertEquals("Food \u00B7 Today", testTransaction("b").subtitle(now, utc, Locale.US))
        assertEquals("Food \u00B7 Today", testTransaction("c", description = "   ").subtitle(now, utc, Locale.US))
    }

    @Test
    fun `screen readers hear the whole transaction`() {
        val received = testTransaction("a", Direction.IN, 100_000, "Welcome bonus")
        val sent = testTransaction("b", Direction.OUT, 2_500)

        assertEquals(
            "Received $1,000.00 from Fresh Market, Welcome bonus, Today",
            received.spokenDescription(now, utc, Locale.US),
        )
        assertEquals(
            "Sent $25.00 to Fresh Market, Food, Today",
            sent.spokenDescription(now, utc, Locale.US),
        )
    }
}