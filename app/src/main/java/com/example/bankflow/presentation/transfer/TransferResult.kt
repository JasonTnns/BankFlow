package com.example.bankflow.presentation.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.presentation.common.format

@Composable
fun TransferSuccessContent(
    result: TransferResult,
    review: TransferReview?,
    onDone: () -> Unit,
    onAnother: () -> Unit,
    modifier: Modifier = Modifier,
    saveRecipient: SaveRecipientState = SaveRecipientState.Idle,
    canSaveRecipient: Boolean = false,
    onSaveRecipient: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(72.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text("Transfer sent", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                text = result.transfer.amount.format(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag("success_amount"),
            )
            review?.let {
                Text("to ${it.recipientName}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ResultRow("New balance", result.sourceAccountBalance.format(), tag = "success_balance")
                ResultRow("Reference", result.transfer.id, tag = "success_reference", monospace = true, showDivider = false)
            }
        }
        review?.let {
            SaveRecipientPrompt(
                recipientName = it.recipientName,
                state = saveRecipient,
                canSave = canSaveRecipient,
                onSave = onSaveRecipient,
            )
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("success_done")) { Text("Done") }
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().testTag("success_another")) {
            Text("Make another transfer")
        }
    }
}

@Composable
fun TransferFailureContent(
    error: AppError,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text("Transfer not sent", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                text = error.transferFailureMessage(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("failure_message"),
            )
            Text(
                text = "No money was moved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("failure_edit")) { Text("Edit transfer") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("failure_close")) { Text("Close") }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    tag: String,
    monospace: Boolean = false,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = if (monospace) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 16.dp).testTag(tag),
            )
        }
        if (showDivider) HorizontalDivider()
    }
}