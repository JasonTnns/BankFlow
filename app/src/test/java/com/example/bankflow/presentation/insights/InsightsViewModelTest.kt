package com.example.bankflow.presentation.insights

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.usecase.GetSpendingSummaryUseCase
import com.example.bankflow.testutil.FakeAccountChangeNotifier
import com.example.bankflow.testutil.FixedZoneProvider
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubInsightsRepository
import com.example.bankflow.testutil.TestClock
import com.example.bankflow.testutil.testSummary
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth

class InsightsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = StubInsightsRepository()
    private val zones = FixedZoneProvider()
    private val clock = TestClock()
    private val notifier = FakeAccountChangeNotifier()

    private fun viewModel() = InsightsViewModel(GetSpendingSummaryUseCase(repository), zones, clock, notifier)

    private val oct1 = Instant.parse("2026-10-01T00:00:00Z")
    private val sep1 = Instant.parse("2026-09-01T00:00:00Z")
    private val aug1 = Instant.parse("2026-08-01T00:00:00Z")


    @Test
    fun `opening the screen loads this month and last month`() {
        val state = viewModel().state.value

        assertEquals(YearMonth.of(2026, 10), state.month)
        assertEquals(listOf(oct1, sep1), repository.calls.map { it.from })
        assertEquals(oct1, state.summary?.from)
        assertEquals(sep1, state.previous?.from)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `shows loading until the first load completes`() {
        val gate = CompletableDeferred<Unit>().also { repository.gates[oct1] = it }

        val viewModel = viewModel()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.summary)
        gate.complete(Unit)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.summary)
    }

    @Test
    fun `a failed load shows the error with no data, and Refresh recovers`() {
        repository.handler = { AppResult.Failure(AppError.NoConnection) }
        val viewModel = viewModel()

        assertEquals(AppError.NoConnection, viewModel.state.value.error)
        assertNull(viewModel.state.value.summary)
        assertFalse(viewModel.state.value.isLoading)

        repository.handler = { call -> AppResult.Success(testSummary(from = call.from, to = call.to)) }
        viewModel.onIntent(InsightsIntent.Refresh)

        assertNotNull(viewModel.state.value.summary)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `last month failing only removes the comparison, never the screen`() {
        repository.handler = { call ->
            if (call.from == sep1) {
                AppResult.Failure(AppError.Timeout)
            } else {
                AppResult.Success(testSummary(from = call.from, to = call.to))
            }
        }

        val state = viewModel().state.value

        assertNotNull(state.summary)
        assertNull(state.previous)
        assertNull(state.error)
    }


    @Test
    fun `next is not possible from the current month`() {
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.canGoNext)

        viewModel.onIntent(InsightsIntent.NextMonth)

        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun `going back loads that month and clears the old numbers straight away`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.gates[sep1] = it }

        viewModel.onIntent(InsightsIntent.PreviousMonth)

        val loading = viewModel.state.value
        assertEquals(YearMonth.of(2026, 9), loading.month)
        assertNull("another month's numbers must not sit under this title", loading.summary)
        assertTrue(loading.isLoading)

        gate.complete(Unit)
        assertEquals(sep1, viewModel.state.value.summary?.from)
        assertEquals(aug1, viewModel.state.value.previous?.from)
        assertTrue(viewModel.state.value.canGoNext)
    }

    @Test
    fun `going back and forward returns to the current month`() {
        val viewModel = viewModel()

        viewModel.onIntent(InsightsIntent.PreviousMonth)
        viewModel.onIntent(InsightsIntent.NextMonth)

        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
        assertEquals(oct1, viewModel.state.value.summary?.from)
        assertFalse(viewModel.state.value.canGoNext)
    }

    @Test
    fun `a slow answer for a month we already left can never land`() {
        val slowOctober = CompletableDeferred<Unit>().also { repository.gates[oct1] = it }
        val viewModel = viewModel()

        viewModel.onIntent(InsightsIntent.PreviousMonth)
        assertEquals(sep1, viewModel.state.value.summary?.from)

        slowOctober.complete(Unit)

        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)
        assertEquals(sep1, viewModel.state.value.summary?.from)
        assertFalse(viewModel.state.value.isLoading)
    }


    @Test
    fun `refresh keeps the numbers on screen until the new ones arrive`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.gates[oct1] = it }

        viewModel.onIntent(InsightsIntent.Refresh)

        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse("a refresh is not a first load", viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.summary)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `a failed refresh keeps the old numbers and reports the error`() {
        val viewModel = viewModel()
        repository.handler = { AppResult.Failure(AppError.NoConnection) }

        viewModel.onIntent(InsightsIntent.Refresh)

        assertNotNull(viewModel.state.value.summary)
        assertEquals(AppError.NoConnection, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `a completed transfer reloads the month in place`() {
        val viewModel = viewModel()
        repository.handler = { call ->
            AppResult.Success(testSummary(from = call.from, to = call.to, categories = listOf(Category.SHOPPING to 9_000)))
        }

        notifier.notifyChanged()

        assertEquals(9_000L, viewModel.state.value.summary?.spent?.minorUnits)
        assertEquals(4, repository.calls.size)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `when a new month begins, Refresh makes it reachable`() {
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.canGoNext)

        clock.advance(Duration.ofDays(5))
        viewModel.onIntent(InsightsIntent.Refresh)

        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month
        assertTrue(viewModel.state.value.canGoNext)
    }

    @Test
    fun `month boundaries follow the zone at the time of the request`() {
        zones.zone = java.time.ZoneId.of("Asia/Tokyo")
        val viewModel = viewModel()

        assertEquals(Instant.parse("2026-09-30T15:00:00Z"), repository.calls.first().from)
        assertEquals(YearMonth.of(2026, 10), viewModel.state.value.month)
    }
}