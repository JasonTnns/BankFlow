package com.example.bankflow.presentation.accounts

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class AccountDetailsState(
    val account: Account? = null,
    val filter: TransactionFilter = TransactionFilter(),
    val searchText: String = "",
    val items: List<Transaction> = emptyList(),
    val nextCursor: String? = null,
    val hasLoaded: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadError: AppError? = null,
    val loadMoreError: AppError? = null,
) : UiState {
    val endReached: Boolean get() = hasLoaded && nextCursor == null
    val isEmpty: Boolean get() = hasLoaded && items.isEmpty()
}

sealed interface AccountDetailsIntent : UiIntent {
    data object Refresh : AccountDetailsIntent
    data object LoadMore : AccountDetailsIntent
    data object RetryLoadMore : AccountDetailsIntent

    data class ApplyFilter(val filter: TransactionFilter) : AccountDetailsIntent

    data class SearchChanged(val text: String) : AccountDetailsIntent

    data class ApplySheetFilter(val filter: TransactionFilter) : AccountDetailsIntent

    data object ClearFilter : AccountDetailsIntent
}