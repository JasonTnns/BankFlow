package com.example.bankflow.presentation.dashboard

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.usecase.GetDashboardUseCase
import com.example.bankflow.testutil.FakeAccountChangeNotifier
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubAccountRepository
import com.example.bankflow.testutil.TEST_DASHBOARD
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()
    private val notifier = FakeAccountChangeNotifier()

    private val repository = StubAccountRepository()
    private fun viewModel() = DashboardViewModel(GetDashboardUseCase(repository), notifier)

    @Test
    fun `opening the screen loads the dashboard`() {
        val state = viewModel().state.value

        assertEquals(TEST_DASHBOARD, state.dashboard)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `shows loading while the first load is in flight`() {
        val gate = CompletableDeferred<Unit>().also { repository.dashboardGate = it }
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.dashboard)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(TEST_DASHBOARD, viewModel.state.value.dashboard)
    }

    @Test
    fun `a failed first load shows the error with no data, and Refresh recovers`() {
        repository.dashboardResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        assertEquals(AppError.NoConnection, viewModel.state.value.error)
        assertNull(viewModel.state.value.dashboard)
        assertFalse(viewModel.state.value.isLoading)

        repository.dashboardResult = AppResult.Success(TEST_DASHBOARD)
        viewModel.onIntent(DashboardIntent.Refresh)

        assertEquals(TEST_DASHBOARD, viewModel.state.value.dashboard)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `refresh keeps the current data visible until the new data arrives`() {
        val viewModel = viewModel()
        val updated = Dashboard(TEST_DASHBOARD.accounts, emptyList(), emptyList())
        repository.dashboardResult = AppResult.Success(updated)
        val gate = CompletableDeferred<Unit>().also { repository.dashboardGate = it }

        viewModel.onIntent(DashboardIntent.Refresh)

        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse("a refresh is not a first load", viewModel.state.value.isLoading)
        assertEquals(TEST_DASHBOARD, viewModel.state.value.dashboard)

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(updated, viewModel.state.value.dashboard)
    }

    @Test
    fun `a failed refresh keeps the stale data and reports the error`() {
        val viewModel = viewModel()
        repository.dashboardResult = AppResult.Failure(AppError.Timeout)

        viewModel.onIntent(DashboardIntent.Refresh)

        assertEquals(TEST_DASHBOARD, viewModel.state.value.dashboard)
        assertEquals(AppError.Timeout, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `Refresh while a load is in flight is ignored`() {
        val gate = CompletableDeferred<Unit>().also { repository.dashboardGate = it }
        val viewModel = viewModel()

        repeat(3) { viewModel.onIntent(DashboardIntent.Refresh) }

        assertEquals(1, repository.dashboardCalls)
        gate.complete(Unit)
    }

    @Test
    fun `a completed transfer refreshes the dashboard in place`() {
        val viewModel = viewModel()
        val updated = Dashboard(TEST_DASHBOARD.accounts, listOf(Money(97_500)), emptyList())
        repository.dashboardResult = AppResult.Success(updated)

        notifier.notifyChanged()

        assertEquals(updated, viewModel.state.value.dashboard)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `a change during a load restarts it, so data from before the transfer never wins`() {
        val gate = CompletableDeferred<Unit>().also { repository.dashboardGate = it }
        val viewModel = viewModel()
        val updated = Dashboard(TEST_DASHBOARD.accounts, listOf(Money(97_500)), emptyList())
        repository.dashboardResult = AppResult.Success(updated)

        notifier.notifyChanged()
        assertEquals(2, repository.dashboardCalls)

        gate.complete(Unit)
        assertEquals(updated, viewModel.state.value.dashboard)
    }
}