package com.example.bankflow.presentation.accounts

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.usecase.GetAccountsUseCase
import com.example.bankflow.domain.usecase.GetTransactionsUseCase
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

@OptIn(FlowPreview::class)
@KoinViewModel
class AccountDetailsViewModel(
    @InjectedParam private val accountId: String,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    changes: AccountChangeNotifier,
) : MviViewModel<AccountDetailsState, AccountDetailsIntent, NoEffect>(AccountDetailsState()) {

    private var listJob: Job? = null
    private var accountJob: Job? = null

    private val searchInput = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        loadAccount()
        reload(keepItems = false)
        observeSearch()
        viewModelScope.launch { changes.changes.collect { handleIntent(AccountDetailsIntent.Refresh) } }
    }

    override fun handleIntent(intent: AccountDetailsIntent) {
        when (intent) {
            AccountDetailsIntent.Refresh -> {
                loadAccount()
                reload(keepItems = currentState.items.isNotEmpty())
            }
            AccountDetailsIntent.LoadMore ->
                if (currentState.loadMoreError == null) loadMore()
            AccountDetailsIntent.RetryLoadMore -> loadMore()
            is AccountDetailsIntent.ApplyFilter -> changeFilter(intent.filter)
            is AccountDetailsIntent.SearchChanged -> {
                val text = intent.text.take(MAX_SEARCH_LENGTH)
                setState { copy(searchText = text) }
                searchInput.tryEmit(text)
            }
            is AccountDetailsIntent.ApplySheetFilter ->
                changeFilter(intent.filter.copy(search = currentState.searchText.toSearch()))
            AccountDetailsIntent.ClearFilter -> {
                setState { copy(searchText = "") }
                searchInput.tryEmit("")
                changeFilter(TransactionFilter())
            }
        }
    }


    private fun observeSearch() {
        viewModelScope.launch {
            searchInput.debounce(SEARCH_DEBOUNCE_MS).collect { text ->
                changeFilter(currentState.filter.copy(search = text.toSearch()))
            }
        }
    }

    private fun String.toSearch(): String? = trim().ifEmpty { null }

    private fun changeFilter(filter: TransactionFilter) {
        if (filter == currentState.filter) return
        setState { copy(filter = filter) }
        reload(keepItems = false)
    }

    private fun loadAccount() {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            val result = getAccounts(accountId)
            ensureActive()
            if (result is AppResult.Success) setState { copy(account = result.value) }
        }
    }

    private fun reload(keepItems: Boolean) {
        listJob?.cancel()
        val filter = currentState.filter

        setState {
            copy(
                items = if (keepItems) items else emptyList(),
                nextCursor = if (keepItems) nextCursor else null,
                hasLoaded = keepItems && hasLoaded,
                isInitialLoading = !keepItems,
                isRefreshing = keepItems,
                isLoadingMore = false,
                loadError = null,
                loadMoreError = null,
            )
        }

        listJob = viewModelScope.launch {
            val result = getTransactions(accountId = accountId, filter = filter, cursor = null)
            ensureActive()
            when (result) {
                is AppResult.Success -> setState {
                    copy(
                        items = result.value.items,
                        nextCursor = result.value.nextCursor,
                        hasLoaded = true,
                        isInitialLoading = false,
                        isRefreshing = false,
                        loadError = null,
                    )
                }
                is AppResult.Failure -> setState {
                    copy(isInitialLoading = false, isRefreshing = false, loadError = result.error)
                }
            }
        }
    }

    private fun loadMore() {
        val current = currentState
        val cursor = current.nextCursor ?: return
        if (!current.hasLoaded || current.isInitialLoading || current.isRefreshing || current.isLoadingMore) return

        setState { copy(isLoadingMore = true, loadMoreError = null) }
        listJob = viewModelScope.launch {
            val result = getTransactions(accountId = accountId, filter = current.filter, cursor = cursor)
            ensureActive()
            when (result) {
                is AppResult.Success -> setState {
                    val known = items.mapTo(HashSet()) { it.key }
                    copy(
                        items = items + result.value.items.filter { it.key !in known },
                        nextCursor = result.value.nextCursor,
                        isLoadingMore = false,
                    )
                }
                is AppResult.Failure -> setState { copy(isLoadingMore = false, loadMoreError = result.error) }
            }
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MAX_SEARCH_LENGTH = 100
    }
}