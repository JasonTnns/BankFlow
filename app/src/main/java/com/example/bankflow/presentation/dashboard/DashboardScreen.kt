@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.dashboard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.domain.model.Money
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.components.AccountCard
import com.example.bankflow.presentation.components.EmptyState
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.InlineError
import com.example.bankflow.presentation.components.SectionTitle
import com.example.bankflow.presentation.components.SkeletonHeader
import com.example.bankflow.presentation.components.SkeletonTransactionRow
import com.example.bankflow.presentation.components.TransactionRow
import org.koin.androidx.compose.koinViewModel
import com.example.bankflow.domain.model.Transaction

@Composable
fun DashboardScreen(
    userName: String,
    onAccountClick: (accountId: String) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        userName = userName,
        onIntent = viewModel::onIntent,
        onAccountClick = onAccountClick,
        onTransactionClick = onTransactionClick
    )
}

@Composable
fun DashboardContent(
    state: DashboardState,
    userName: String,
    onIntent: (DashboardIntent) -> Unit,
    onAccountClick: (accountId: String) -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
) {
    val firstName = userName.trim().substringBefore(' ')

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstName.isEmpty()) "Hello" else "Hi, $firstName") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(DashboardIntent.Refresh) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                val dashboard = state.dashboard
                if (dashboard == null) {
                    if (state.isLoading) {
                        item { SkeletonHeader() }
                        items(5) { SkeletonTransactionRow() }
                    } else {
                        state.error?.let { error ->
                            item { ErrorState(error, onRetry = { onIntent(DashboardIntent.Refresh) }) }
                        }
                    }
                } else {
                    state.error?.let { error ->
                        item { InlineError(error, onRetry = { onIntent(DashboardIntent.Refresh) }) }
                    }

                    if (dashboard.totals.isNotEmpty()) {
                        item { BalanceHeader(dashboard.totals) }
                    }

                    item { SectionTitle("Accounts") }
                    if (dashboard.accounts.isEmpty()) {
                        item { EmptyState("No accounts yet", "Your accounts will show up here.") }
                    } else {
                        items(dashboard.accounts, key = { it.id }) { account ->
                            AccountCard(account = account, onClick = { onAccountClick(account.id) })
                        }
                    }

                    item { SectionTitle("Recent activity") }
                    if (dashboard.recentTransactions.isEmpty()) {
                        item { EmptyState("No transactions yet", "Your activity will show up here.") }
                    } else {
                        items(dashboard.recentTransactions, key = { it.key }) { transaction ->
                            TransactionRow(transaction, onClick = { onTransactionClick(transaction) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHeader(totals: List<Money>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = "Total balance",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        totals.forEach { total ->
            Text(total.format(), style = MaterialTheme.typography.headlineLarge)
        }
    }
}