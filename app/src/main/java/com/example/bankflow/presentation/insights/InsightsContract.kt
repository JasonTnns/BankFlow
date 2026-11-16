package com.example.bankflow.presentation.insights

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState
import java.time.YearMonth

data class InsightsState(
    val month: YearMonth,
    val currentMonth: YearMonth,
    val summary: SpendingSummary? = null,
    val previous: SpendingSummary? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val canGoNext: Boolean get() = month < currentMonth
    val previousMonth: YearMonth get() = month.minusMonths(1)
}

sealed interface InsightsIntent : UiIntent {
    data object PreviousMonth : InsightsIntent
    data object NextMonth : InsightsIntent

    data object Refresh : InsightsIntent
}