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
) : MviViewModel<DashboardState, DashboardIntent, NoEffect>(DashboardState(isLoading = true)) {

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            getDashboard.cached()?.let { saved ->
                setState {
                    if (dashboard == null) copy(dashboard = saved.value, savedAt = saved.savedAt, isLoading = false) else this
                }
            }
            load()
            changes.changes.collect { load(force = true) }
        }
    }

    override fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.Refresh -> load()
        }
    }

    private fun load(force: Boolean = false) {
        if (!force && loadJob?.isActive == true) return

        loadJob?.cancel()
        val hasData = currentState.dashboard != null
        setState { copy(isLoading = !hasData, isRefreshing = hasData, error = null) }

        loadJob = viewModelScope.launch {
            val result = getDashboard()
            ensureActive()
            when (result) {
                is AppResult.Success -> setState {
                    copy(dashboard = result.value, savedAt = null, isLoading = false, isRefreshing = false, error = null)
                }
                is AppResult.Failure -> setState {
                    copy(isLoading = false, isRefreshing = false, error = result.error)
                }
            }
        }
    }
}