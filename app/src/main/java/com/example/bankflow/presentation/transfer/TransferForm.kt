@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.bankflow.presentation.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.auth.LoadingButton
import com.example.bankflow.presentation.common.currencySymbol
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.label
import com.example.bankflow.presentation.common.userMessage
import com.example.bankflow.presentation.components.EmptyState
import com.example.bankflow.presentation.components.ErrorState

@Composable
fun TransferFormContent(
    state: TransferState,
    onIntent: (TransferIntent) -> Unit,
    modifier: Modifier = Modifier,
    onManageRecipients: () -> Unit = {},
) {
    val accountsError = state.accountsError
    when {
        state.isLoadingAccounts && state.accounts.isEmpty() -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading accounts" })
        }
        accountsError != null && state.accounts.isEmpty() -> ErrorState(
            error = accountsError,
            onRetry = { onIntent(TransferIntent.RetryLoadAccounts) },
            modifier = modifier,
        )
        state.accounts.isEmpty() -> EmptyState(
            title = "No accounts yet",
            message = "You need an account before you can send money.",
            modifier = modifier,
        )
        else -> FormBody(state, onIntent, onManageRecipients, modifier)    }
}

@Composable
private fun FormBody(
    state: TransferState,
    onIntent: (TransferIntent) -> Unit,
    onManageRecipients: () -> Unit,
    modifier: Modifier,
) {
    val busy = state.isPreparing
    val keyboard = LocalSoftwareKeyboardController.current
    val currency = state.sourceAccount?.balance?.currency ?: Money.DEFAULT_CURRENCY

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("transfer_error")) }

        FormSection("From") {
            state.accounts.forEach { account ->
                AccountChoice(
                    account = account,
                    selected = account.id == state.sourceAccountId,
                    enabled = !busy,
                    onSelect = { onIntent(TransferIntent.SourceSelected(account.id)) },
                )
            }
        }

        FormSection("To") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.destinationMode == DestinationMode.OTHER_PERSON,
                    onClick = { onIntent(TransferIntent.DestinationModeChanged(DestinationMode.OTHER_PERSON)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !busy,
                    modifier = Modifier.testTag("mode_other"),
                ) { Text("Someone else") }
                SegmentedButton(
                    selected = state.destinationMode == DestinationMode.OWN_ACCOUNT,
                    onClick = { onIntent(TransferIntent.DestinationModeChanged(DestinationMode.OWN_ACCOUNT)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !busy,
                    modifier = Modifier.testTag("mode_own"),
                ) { Text("My accounts") }
            }

            when (state.destinationMode) {
                DestinationMode.OTHER_PERSON -> OutlinedTextField(
                    value = state.accountNumber,
                    onValueChange = { onIntent(TransferIntent.AccountNumberChanged(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("transfer_number"),
                    label = { Text("Account number") },
                    singleLine = true,
                    enabled = !busy,
                    isError = state.destinationError != null,
                    supportingText = { Text(state.destinationError ?: "12 digits. You'll see the owner's name before sending.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                DestinationMode.OWN_ACCOUNT -> {
                    if (state.ownDestinationOptions.isEmpty()) {
                        Text(
                            text = "You need another active account to move money between your own accounts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("no_own_accounts"),
                        )
                    } else {
                        state.ownDestinationOptions.forEach { account ->
                            AccountChoice(
                                account = account,
                                selected = account.id == state.ownDestinationId,
                                enabled = !busy,
                                onSelect = { onIntent(TransferIntent.OwnDestinationSelected(account.id)) },
                            )
                        }
                    }
                    state.destinationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.destinationMode == DestinationMode.OTHER_PERSON) {
            SavedRecipients(
                beneficiaries = state.beneficiaries,
                selectedNumber = state.accountNumber,
                enabled = !busy,
                onSelect = { onIntent(TransferIntent.RecipientSelected(it)) },
                onManage = onManageRecipients,
            )
        }

        FormSection("Amount") {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { onIntent(TransferIntent.AmountChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("transfer_amount"),
                label = { Text("Amount") },
                prefix = { Text(currencySymbol(currency)) },
                singleLine = true,
                enabled = !busy,
                isError = state.amountError != null,
                supportingText = {
                    Text(state.amountError ?: state.sourceAccount?.let { "Available: ${it.balance.format()}" }.orEmpty())
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
        }

        FormSection("Category") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Category.entries.filter { it != Category.INCOME }.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onIntent(TransferIntent.CategorySelected(category)) },
                        label = { Text(category.label()) },
                        enabled = !busy,
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = { onIntent(TransferIntent.NoteChanged(it)) },
            modifier = Modifier.fillMaxWidth().testTag("transfer_note"),
            label = { Text("Note (optional)") },
            enabled = !busy,
            isError = state.noteError != null,
            supportingText = { Text(state.noteError ?: "${state.note.length}/${TransferViewModel.MAX_NOTE_LENGTH}") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        LoadingButton(
            text = "Continue",
            loading = busy,
            onClick = {
                keyboard?.hide()
                onIntent(TransferIntent.Continue)
            },
            modifier = Modifier.testTag("transfer_continue"),
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun AccountChoice(
    account: Account,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleSmall)
                Text(account.maskedNumber, style = MaterialTheme.typography.bodySmall)
            }
            Text(account.balance.format(), style = MaterialTheme.typography.titleSmall)
        }
    }
}