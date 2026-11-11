package com.example.bankflow.presentation.dashboard
import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.usecase.GetDashboardUseCase
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class DashboardViewModel(
    private val getDashboard: GetDashboardUseCase,
    changes: AccountChangeNotifier,
) : MviViewModel<DashboardState, DashboardIntent, NoEffect>(DashboardState()) {

    private var loadJob: Job? = null

    init {
        load()

        viewModelScope.launch { changes.changes.collect { load(force = true) } }
    }

    override fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Refresh -> load()
        }
    }

    private fun load(force: Boolean = false) {
        val current = currentState
        if (!force && (current.isLoading || current.isRefreshing)) return // one request at a time

        loadJob?.cancel()
        val hasData = current.dashboard != null
        setState { copy(isLoading = !hasData, isRefreshing = hasData, error = null) }

        loadJob = viewModelScope.launch {
            val result = getDashboard()
            ensureActive() // a response that arrives after we moved on must not be applied
            when (result) {
                is AppResult.Success -> setState {
                    copy(dashboard = result.value, isLoading = false, isRefreshing = false, error = null)
                }
                // Keep whatever data we already had; only the error is new.
                is AppResult.Failure -> setState {
                    copy(isLoading = false, isRefreshing = false, error = result.error)
                }
            }
        }
    }
}