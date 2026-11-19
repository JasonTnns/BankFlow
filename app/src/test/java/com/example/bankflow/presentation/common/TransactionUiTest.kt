package com.example.bankflow.presentation.common

import com.example.bankflow.R
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.testutil.testTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TransactionUiTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val today = UiText.Res(R.string.date_today)

    @Test
    fun `incoming and outgoing amounts carry a sign`() {
        assertEquals("+$1,000.00", testTransaction("a", Direction.IN, 100_000).signedAmount(Locale.US))
        assertEquals("\u2212$25.00", testTransaction("b", Direction.OUT, 2_500).signedAmount(Locale.US))
    }

    @Test
    fun `subtitle prefers the description and falls back to the category`() {
        assertEquals(
            UiText.Res(R.string.pair_dot, listOf(UiText.Plain("Welcome bonus"), today)),
            testTransaction("a", description = "Welcome bonus").subtitleText(now, utc, Locale.US),
        )
        val categoryFallback = UiText.Res(R.string.pair_dot, listOf(UiText.Res(R.string.category_food), today))
        assertEquals(categoryFallback, testTransaction("b").subtitleText(now, utc, Locale.US))
        assertEquals(categoryFallback, testTransaction("c", description = "   ").subtitleText(now, utc, Locale.US))
    }

    @Test
    fun `screen readers hear the whole transaction`() {
        val received = testTransaction("a", Direction.IN, 100_000, "Welcome bonus")
        val sent = testTransaction("b", Direction.OUT, 2_500)

        assertEquals(
            UiText.Res(R.string.transaction_spoken_in, listOf(Money(100_000), "Fresh Market", UiText.Plain("Welcome bonus"), today)),
            received.spokenText(now, utc, Locale.US),
        )
        assertEquals(
            UiText.Res(R.string.transaction_spoken_out, listOf(Money(2_500), "Fresh Market", UiText.Res(R.string.category_food), today)),
            sent.spokenText(now, utc, Locale.US),
        )
    }
}