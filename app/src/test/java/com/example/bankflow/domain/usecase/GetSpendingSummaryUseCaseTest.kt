package com.example.bankflow.domain.usecase

import com.example.bankflow.testutil.StubInsightsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class GetSpendingSummaryUseCaseTest {

    private val repository = StubInsightsRepository()
    private val useCase = GetSpendingSummaryUseCase(repository)

    private fun rangeOf(month: YearMonth, zone: String): Pair<Instant, Instant> = runTestBlocking {
        useCase(month, ZoneId.of(zone))
        repository.calls.last().let { it.from to it.to }
    }

    private fun <T> runTestBlocking(block: suspend () -> T): T {
        var result: T? = null
        runTest { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun `a month in UTC runs from the first to the first of the next month`() {
        val (from, to) = rangeOf(YearMonth.of(2026, 10), "UTC")

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), from)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), to)
    }

    @Test
    fun `the boundaries follow the user's own midnight`() {
        val (from, to) = rangeOf(YearMonth.of(2026, 12), "Asia/Tokyo")

        assertEquals(Instant.parse("2026-11-30T15:00:00Z"), from)
        assertEquals(Instant.parse("2026-12-31T15:00:00Z"), to)
    }

    @Test
    fun `December rolls over into January of the next year`() {
        val (_, to) = rangeOf(YearMonth.of(2026, 12), "UTC")

        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), to)
    }

    @Test
    fun `a month with a daylight-saving change is not 24 hours times its days`() {
        val (from, to) = rangeOf(YearMonth.of(2026, 11), "America/New_York")

        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), from)
        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), to)
    }

    @Test
    fun `a leap-year February has twenty-nine days`() {
        val (from, to) = rangeOf(YearMonth.of(2028, 2), "UTC")

        assertEquals(29L, java.time.Duration.between(from, to).toDays())
    }

    @Test
    fun `consecutive months share a boundary, so nothing is counted twice or missed`() {
        val (_, octoberEnd) = rangeOf(YearMonth.of(2026, 10), "Europe/Paris")
        val (novemberStart, _) = rangeOf(YearMonth.of(2026, 11), "Europe/Paris")

        assertEquals(octoberEnd, novemberStart)
    }

    @Test
    fun `the default currency is USD and another can be requested`() = runTest {
        useCase(YearMonth.of(2026, 10), ZoneId.of("UTC"))
        useCase(YearMonth.of(2026, 10), ZoneId.of("UTC"), currency = "EUR")

        assertEquals(listOf("USD", "EUR"), repository.calls.map { it.currency })
    }
}