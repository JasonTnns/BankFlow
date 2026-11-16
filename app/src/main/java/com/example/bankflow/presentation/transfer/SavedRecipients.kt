@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.bankflow.presentation.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.presentation.common.userMessage

@Composable
fun SavedRecipients(
    beneficiaries: List<Beneficiary>,
    selectedNumber: String,
    enabled: Boolean,
    onSelect: (Beneficiary) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved recipients",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            TextButton(onClick = onManage, modifier = Modifier.testTag("manage_recipients")) {
                Text(if (beneficiaries.isEmpty()) "Add" else "Manage")
            }
        }

        if (beneficiaries.isEmpty()) {
            Text(
                text = "People you save will appear here for one-tap transfers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                beneficiaries.forEach { beneficiary ->
                    FilterChip(
                        selected = beneficiary.accountNumber == selectedNumber,
                        onClick = { onSelect(beneficiary) },
                        enabled = enabled && beneficiary.isActive,
                        label = { Text(beneficiary.nickname) },
                        modifier = Modifier.testTag("recipient_chip_${beneficiary.id}"),
                    )
                }
            }
        }
    }
}

@Composable
fun SaveRecipientPrompt(
    recipientName: String,
    state: SaveRecipientState,
    canSave: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state == SaveRecipientState.Saved -> Text(
            text = "$recipientName is saved to your recipients",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .testTag("recipient_saved")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        !canSave -> Unit
        else -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is SaveRecipientState.Failed) {
                Text(
                    text = state.error.userMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .testTag("save_recipient_error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            OutlinedButton(
                onClick = onSave,
                enabled = state != SaveRecipientState.Saving,
                modifier = Modifier.fillMaxWidth().testTag("save_recipient"),
            ) { Text(if (state == SaveRecipientState.Saving) "Saving\u2026" else "Save $recipientName as a recipient") }
        }
    }
}