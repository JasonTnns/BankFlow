package com.example.bankflow.presentation.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bankflow.R
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.auth.LoadingButton
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.label
import com.example.bankflow.presentation.common.ltr
import com.example.bankflow.presentation.common.uiString

@Composable
fun TransferReviewContent(
    review: TransferReview,
    isSubmitting: Boolean,
    submitError: com.example.bankflow.domain.common.AppError?,
    onIntent: (TransferIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = review.request

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        submitError?.let { ErrorBanner(it.unknownOutcomeMessage(), Modifier.testTag("review_error")) }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.review_sending), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = request.amount.format(),
                style = MaterialTheme.typography.displaySmall.ltr(),
                modifier = Modifier.testTag("review_amount"),
            )
            Text(
                text = uiString(R.string.review_to, review.recipientName),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("review_recipient"),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ReviewRow(stringResource(R.string.transfer_from), uiString(R.string.pair_dot, review.source.name, review.source.maskedNumber))
                val recipientLine = uiString(R.string.pair_dot, review.recipientName, review.recipientDetail)
                ReviewRow(
                    label = stringResource(R.string.transfer_to),
                    value = if (review.isOwnAccount) uiString(R.string.details_your_account, recipientLine) else recipientLine,
                )
                request.category?.let { ReviewRow(stringResource(R.string.transfer_category), it.label()) }
                request.description?.let { ReviewRow(stringResource(R.string.review_note), it) }
                ReviewRow(stringResource(R.string.review_fee), stringResource(R.string.review_fee_none), showDivider = false)
            }
        }

        LoadingButton(
            text = stringResource(if (submitError != null) R.string.action_try_again else R.string.review_confirm),
            loading = isSubmitting,
            onClick = { onIntent(TransferIntent.Confirm) },
            modifier = Modifier.testTag("review_confirm"),
        )
        TextButton(
            onClick = { onIntent(TransferIntent.BackToForm) },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("review_back"),
        ) { Text(stringResource(if (request.destination is TransferDestination.OwnAccount) R.string.review_edit else R.string.action_back)) }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        if (showDivider) HorizontalDivider()
    }
}