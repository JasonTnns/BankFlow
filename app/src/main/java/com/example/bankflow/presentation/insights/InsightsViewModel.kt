package com.example.bankflow.presentation.insights

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ZoneProvider
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.usecase.GetSpendingSummaryUseCase
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.Clock
import java.time.YearMonth

private fun currentMonth(clock: Clock, zones: ZoneProvider): YearMonth =
    YearMonth.from(clock.instant().atZone(zones.zone()))

@KoinViewModel
class InsightsViewModel(
    private val getSpendingSummary: GetSpendingSummaryUseCase,
    private val zones: ZoneProvider,
    private val clock: Clock,
    changes: AccountChangeNotifier,
) : MviViewModel<InsightsState, InsightsIntent, NoEffect>(
    InsightsState(month = currentMonth(clock, zones), currentMonth = currentMonth(clock, zones)),
) {

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { changes.changes.collect { load() } }
    }

    override fun handleIntent(intent: InsightsIntent) {
        when (intent) {
            InsightsIntent.PreviousMonth -> changeMonth(currentState.month.minusMonths(1))
            InsightsIntent.NextMonth -> if (currentState.canGoNext) changeMonth(currentState.month.plusMonths(1))
            InsightsIntent.Refresh -> {
                setState { copy(currentMonth = currentMonth(clock, zones)) }
                load()
            }
        }
    }

    private fun changeMonth(month: YearMonth) {
        setState { copy(month = month, summary = null, previous = null, error = null) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val state = currentState
        val month = state.month
        val zone = zones.zone()
        val hasData = state.summary != null

        setState { copy(isLoading = !hasData, isRefreshing = hasData, error = null) }

        loadJob = viewModelScope.launch {
            val (current, previous) = coroutineScope {
                val thisMonth = async { getSpendingSummary(month, zone) }
                val lastMonth = async { getSpendingSummary(month.minusMonths(1), zone) }
                thisMonth.await() to lastMonth.await()
            }
            ensureActive()

            when (current) {
                is AppResult.Success -> setState {
                    copy(
                        summary = current.value,
                        previous = (previous as? AppResult.Success)?.value,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
                is AppResult.Failure -> setState {
                    copy(isLoading = false, isRefreshing = false, error = current.error)
                }
            }
        }
    }
}