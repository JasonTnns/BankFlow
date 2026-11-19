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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.example.bankflow.R
import com.example.bankflow.presentation.common.uiString
import com.example.bankflow.presentation.components.rememberReduceMotion

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
    val reduceMotion = rememberReduceMotion()
    val iconScale = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(stringResource(R.string.success_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                text = result.transfer.amount.format(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag("success_amount"),
            )
            review?.let {
                Text(uiString(R.string.review_to, it.recipientName), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ResultRow(stringResource(R.string.success_new_balance), result.sourceAccountBalance.format(), tag = "success_balance")
                ResultRow(stringResource(R.string.details_reference), result.transfer.id, tag = "success_reference", monospace = true, showDivider = false)
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
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("success_done")) { Text(stringResource(R.string.action_done)) }
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().testTag("success_another")) {
            Text(stringResource(R.string.success_another))
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
            Text(stringResource(R.string.failure_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                text = error.transferFailureMessage(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("failure_message"),
            )
            Text(
                text = stringResource(R.string.failure_no_money),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("failure_edit")) { Text(stringResource(R.string.review_edit)) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("failure_close")) { Text(stringResource(R.string.action_close)) }
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