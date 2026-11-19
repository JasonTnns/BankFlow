@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.model.CategorySpend
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.formatSigned
import com.example.bankflow.presentation.common.label
import com.example.bankflow.presentation.common.labelRes
import com.example.bankflow.presentation.common.resolve
import com.example.bankflow.presentation.common.uiString
import com.example.bankflow.presentation.components.EmptyState
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.InlineError
import com.example.bankflow.presentation.components.SectionTitle
import com.example.bankflow.presentation.components.SkeletonBlock
import com.example.bankflow.presentation.components.categoryIcon
import org.koin.androidx.compose.koinViewModel

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun InsightsContent(
    state: InsightsState,
    onIntent: (InsightsIntent) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.insights_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthSelector(
                title = state.month.title(),
                canGoNext = state.canGoNext,
                onPrevious = { onIntent(InsightsIntent.PreviousMonth) },
                onNext = { onIntent(InsightsIntent.NextMonth) },
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(InsightsIntent.Refresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("insights_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    val summary = state.summary
                    val error = state.error
                    when {
                        summary != null -> {
                            if (error != null) {
                                item(key = "refresh_error") {
                                    InlineError(error, onRetry = { onIntent(InsightsIntent.Refresh) })
                                }
                            }
                            summaryItems(state, summary)
                        }
                        state.isLoading -> item(key = "loading") { InsightsSkeleton() }
                        error != null -> item(key = "error") {
                            ErrorState(error, onRetry = { onIntent(InsightsIntent.Refresh) })
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(
    state: InsightsState,
    summary: SpendingSummary,
) {
    if (summary.isEmpty) {
        item(key = "empty") {
            EmptyState(
                title = uiString(R.string.insights_empty_title, state.month.title()),
                message = stringResource(R.string.insights_empty_message),            )
        }
        return
    }

    item(key = "totals") { TotalsCards(summary) }

    comparisonText(summary, state.previous, state.previousMonth)?.let { text ->
        item(key = "comparison") {
            Text(
                text = text.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("insights_comparison"),
            )
        }
    }

    item(key = "categories_title") { SectionTitle(stringResource(R.string.insights_categories_title)) }

    if (summary.byCategory.isEmpty()) {
        item(key = "no_spending") {
            Text(
                text = stringResource(R.string.insights_no_spending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("insights_no_spending"),
            )
        }
    } else {
        val largest = summary.byCategory.maxOf { it.spent.minorUnits }
        items(summary.byCategory, key = { it.category }) { spend ->
            CategoryRow(
                spend = spend,
                percent = summary.sharePercent(spend),
                barFraction = spend.spent.minorUnits.toFloat() / largest.toFloat(),
            )
        }
    }
}

@Composable
private fun MonthSelector(
    title: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("month_previous")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.insights_previous_month))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("month_title").semantics { heading() },
        )
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.testTag("month_next")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.insights_next_month))
        }
    }
}

@Composable
private fun TotalsCards(summary: SpendingSummary) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalCard(stringResource(R.string.insights_spent), summary.spent.format(), "insights_spent", Modifier.weight(1f))
            TotalCard(stringResource(R.string.insights_income), summary.income.format(), "insights_income", Modifier.weight(1f))
        }
        TotalCard(stringResource(R.string.insights_net), summary.net.formatSigned(), "insights_net", Modifier.fillMaxWidth())
    }
}

@Composable
private fun TotalCard(label: String, value: String, tag: String, modifier: Modifier) {
    val spoken = uiString(R.string.insights_total_spoken, label, value)
    Card(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun CategoryRow(spend: CategorySpend, percent: Int, barFraction: Float) {
    val name = spend.category.label()
    val amount = spend.spent.format()
    val spoken = UiText.Res(
        R.string.insights_category_spoken,
        listOf(
            UiText.Res(spend.category.labelRes()),
            spend.spent,
            shareLabel(percent),
            UiText.Plural(R.plurals.insights_transactions, spend.count),
        ),
    ).resolve()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("category_${spend.category.name}")
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = categoryIcon(spend.category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(name, style = MaterialTheme.typography.bodyLarge)
            }
            Text(amount, style = MaterialTheme.typography.titleSmall)
        }
        LinearProgressIndicator(progress = { barFraction }, modifier = Modifier.fillMaxWidth())
        Text(
            text = uiString(R.string.insights_share, shareLabel(percent).resolve()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsSkeleton() {
    val loadingText = stringResource(R.string.insights_loading)
    Column(
        modifier = Modifier
            .padding(16.dp)
            .semantics { contentDescription = loadingText },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(72.dp))
            SkeletonBlock(Modifier.weight(1f).height(72.dp))
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp))
        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(40.dp)) }
    }
}