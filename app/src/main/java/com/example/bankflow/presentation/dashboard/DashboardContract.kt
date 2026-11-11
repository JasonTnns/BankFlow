package com.example.bankflow.presentation.dashboard
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class DashboardState(
    val dashboard: Dashboard? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
): UiState

sealed interface DashboardIntent : UiIntent {
    data object Refresh : DashboardIntent
}