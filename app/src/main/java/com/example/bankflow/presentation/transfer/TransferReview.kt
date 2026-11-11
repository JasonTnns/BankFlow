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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.auth.LoadingButton
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.label

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
            Text("You're sending", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = request.amount.format(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag("review_amount"),
            )
            Text(
                text = "to ${review.recipientName}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("review_recipient"),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ReviewRow("From", "${review.source.name} \u00B7 ${review.source.maskedNumber}")
                ReviewRow(
                    label = "To",
                    value = buildString {
                        append(review.recipientName)
                        append(" \u00B7 ")
                        append(review.recipientDetail)
                        if (review.isOwnAccount) append(" (your account)")
                    },
                )
                request.category?.let { ReviewRow("Category", it.label()) }
                request.description?.let { ReviewRow("Note", it) }
                ReviewRow("Fee", "None", showDivider = false)
            }
        }

        LoadingButton(
            text = if (submitError != null) "Try again" else "Confirm and send",
            loading = isSubmitting,
            onClick = { onIntent(TransferIntent.Confirm) },
            modifier = Modifier.testTag("review_confirm"),
        )
        TextButton(
            onClick = { onIntent(TransferIntent.BackToForm) },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("review_back"),
        ) { Text(if (request.destination is TransferDestination.OwnAccount) "Edit transfer" else "Back") }
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