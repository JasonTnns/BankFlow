@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.presentation.accounts.filter.activeChips
import com.example.bankflow.presentation.accounts.filter.without
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.label
import com.example.bankflow.presentation.common.uiString
import com.example.bankflow.presentation.components.AnimatedAmount
import com.example.bankflow.presentation.components.EmptyState
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.InlineError
import com.example.bankflow.presentation.components.SavedDataNotice
import com.example.bankflow.presentation.components.SectionTitle
import com.example.bankflow.presentation.components.SkeletonHeader
import com.example.bankflow.presentation.components.SkeletonTransactionRow
import com.example.bankflow.presentation.components.TransactionRow
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.ZoneId

private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun AccountDetailsScreen(
    accountId: String,
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    viewModel: AccountDetailsViewModel = koinViewModel(parameters = { parametersOf(accountId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore = stringResource(R.string.history_loading_more)
    AccountDetailsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onTransactionClick = onTransactionClick,
    )
}

@Composable
fun AccountDetailsContent(
    state: AccountDetailsState,
    onIntent: (AccountDetailsIntent) -> Unit,
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
) {
    val listState = rememberLazyListState()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val currency = state.account?.balance?.currency ?: Money.DEFAULT_CURRENCY
    val chips = remember(state.filter, currency) { state.filter.activeChips(zone = zone, currency = currency) }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd) onIntent(AccountDetailsIntent.LoadMore)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.account?.name ?: stringResource(R.string.account_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            HistoryFilterBar(
                searchText = state.searchText,
                chips = chips,
                onSearchChange = { onIntent(AccountDetailsIntent.SearchChanged(it)) },
                onOpenFilters = { showFilters = true },
                onRemoveFilter = { kind ->
                    onIntent(AccountDetailsIntent.ApplyFilter(state.filter.without(kind)))
                },
                onClearAll = { onIntent(AccountDetailsIntent.ClearFilter) },
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(AccountDetailsIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("history_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") { AccountHeader(state.account) }
                    item(key = "title") { SectionTitle(stringResource(R.string.history_transactions)) }
                    state.savedAt?.let { savedAt -> item(key = "saved_notice") { SavedDataNotice(savedAt) } }

                    val loadError = state.loadError
                    when {
                        state.isInitialLoading -> items(6, contentType = { "skeleton" }) { SkeletonTransactionRow() }
                        loadError != null && state.items.isEmpty() -> item(key = "error") {
                            ErrorState(loadError, onRetry = { onIntent(AccountDetailsIntent.Refresh) })
                        }

                        state.isEmpty -> item(key = "empty") {
                            if (state.filter.isActive) {
                                EmptyState(
                                    title = stringResource(R.string.history_empty_filtered_title),
                                    message = stringResource(R.string.history_empty_filtered_message),
                                    actionLabel = stringResource(R.string.history_clear_filters),
                                    onAction = { onIntent(AccountDetailsIntent.ClearFilter) },
                                )
                            } else {
                                EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message))                            }
                        }

                        else -> {
                            if (loadError != null) {
                                item(key = "refresh_error") {
                                    InlineError(loadError, onRetry = { onIntent(AccountDetailsIntent.Refresh) })
                                }
                            }

                            items(state.items, key = { it.key }, contentType = { "transaction" }) { transaction ->
                                TransactionRow(
                                    transaction = transaction,
                                    modifier = Modifier.animateItem(),
                                    onClick = { onTransactionClick(transaction) },
                                )
                            }

                            val moreError = state.loadMoreError
                            when {
                                state.isLoadingMore -> item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .semantics { contentDescription = loadingMore },
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                                moreError != null -> item(key = "load_more_error") {
                                    InlineError(moreError, onRetry = { onIntent(AccountDetailsIntent.RetryLoadMore) })
                                }
                                state.endReached -> item(key = "end") {
                                    Text(
                                        text = stringResource(R.string.history_end),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filter = state.filter,
            onApply = {
                onIntent(AccountDetailsIntent.ApplySheetFilter(it))
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun AccountHeader(account: Account?) {
    if (account == null) {
        SkeletonHeader()
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = uiString(R.string.pair_dot, account.type.label(), account.maskedNumber),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedAmount(account.balance.format(), MaterialTheme.typography.headlineLarge)    }
}