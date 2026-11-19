@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.formatDateTime
import com.example.bankflow.presentation.common.label
import com.example.bankflow.presentation.common.ltr
import com.example.bankflow.presentation.common.signedAmount
import com.example.bankflow.presentation.common.uiString
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.categoryIcon
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionDetailsScreen(
    accountId: String,
    transferId: String,
    onBack: () -> Unit,
    viewModel: TransactionDetailsViewModel = koinViewModel(
        parameters = { parametersOf(TransactionRef(accountId, transferId)) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionDetailsContent(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun TransactionDetailsContent(
    state: TransactionDetailsState,
    onIntent: (TransactionDetailsIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val transaction = state.transaction
            val error = state.error
            when {
                transaction != null -> DetailsBody(transaction)
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .semantics { contentDescription = loadingText},
                )
                error != null -> ErrorState(
                    error = error,
                    onRetry = { onIntent(TransactionDetailsIntent.Retry) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun DetailsBody(transaction: Transaction) {
    val isIncoming = transaction.direction == Direction.IN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(transaction.category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = transaction.counterparty.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = transaction.signedAmount(),
                style = MaterialTheme.typography.headlineLarge.ltr(),
                color = if (isIncoming) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("details_amount"),
            )
            Text(
                text = transaction.status.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                DetailRow(stringResource(if (isIncoming) R.string.transfer_from else R.string.transfer_to), transaction.counterparty.name)
                transaction.counterparty.accountNumberMasked?.let {
                    DetailRow(stringResource(R.string.details_account), if (transaction.counterparty.isOwnAccount) uiString(R.string.details_your_account, it) else it)
                }
                DetailRow(stringResource(R.string.details_date), transaction.createdAt.formatDateTime())
                DetailRow(stringResource(R.string.details_category), transaction.category.label())
                transaction.description?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.details_description), it) }
                DetailRow(stringResource(R.string.details_type), transaction.kind.label())
                DetailRow(stringResource(R.string.details_balance_after), transaction.balanceAfter.format())
                ReferenceRow(transaction.transferId)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier, ltr: Boolean = false) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium.let { if (ltr) it.ltr() else it }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium.let { if (ltr) it.ltr() else it }, modifier = Modifier.padding(start = 16.dp))
        }
        HorizontalDivider()
    }
}

@Composable
private fun ReferenceRow(reference: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.details_reference), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = reference,
                    style = MaterialTheme.typography.bodySmall.ltr(),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("details_reference"),
                )
            }
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(reference))
                    copied = true
                },
                modifier = Modifier.testTag("details_copy"),
            ) { Text(stringResource(if (copied) R.string.action_copied else R.string.action_copy)) }
        }
    }
}