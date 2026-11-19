@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.beneficiaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.common.ltr
import com.example.bankflow.presentation.common.resolve
import com.example.bankflow.presentation.common.uiString
import com.example.bankflow.presentation.common.userMessage
import com.example.bankflow.presentation.components.EmptyState
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.InlineError
import org.koin.androidx.compose.koinViewModel

@Composable
fun BeneficiariesScreen(
    onBack: () -> Unit,
    viewModel: BeneficiariesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BeneficiariesContent(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun BeneficiariesContent(
    state: BeneficiariesState,
    onIntent: (BeneficiariesIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipients_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasLoaded && !state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { onIntent(BeneficiariesIntent.OpenAdd) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipients_add)) },
                    modifier = Modifier.testTag("add_recipient"),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val loadError = state.loadError
            when {
                state.isLoading && !state.hasLoaded -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .semantics { contentDescription = loadingText },
                )
                loadError != null && !state.hasLoaded -> ErrorState(
                    error = loadError,
                    onRetry = { onIntent(BeneficiariesIntent.Retry) },
                    modifier = Modifier.align(Alignment.Center),
                )
                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.recipients_empty_title),
                    message = stringResource(R.string.recipients_empty_message),
                    actionLabel = stringResource(R.string.recipients_add),
                    onAction = { onIntent(BeneficiariesIntent.OpenAdd) },
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("recipients_list"),
                    contentPadding = PaddingValues(bottom = 96.dp), // clear of the floating button
                ) {
                    state.actionError?.let { error ->
                        item(key = "action_error") { InlineError(error, onRetry = null) }
                    }
                    items(state.items, key = { it.id }) { beneficiary ->
                        RecipientRow(beneficiary, onDelete = { onIntent(BeneficiariesIntent.AskDelete(beneficiary)) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.addForm?.let { form -> AddRecipientDialog(form, onIntent) }
    state.pendingDelete?.let { target -> DeleteRecipientDialog(target, state.isDeleting, onIntent) }
}

@Composable
private fun RecipientRow(beneficiary: Beneficiary, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(beneficiary.nickname) },
        supportingContent = {
            val unavailable = if (beneficiary.isActive) "" else " \u00B7 Unavailable"
            Text(
                if (beneficiary.isActive) {
                    uiString(R.string.pair_dot, beneficiary.displayName, beneficiary.accountNumberMasked)
                } else {
                    uiString(R.string.recipients_row_unavailable, beneficiary.displayName, beneficiary.accountNumberMasked)
                },
            )        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = uiString(R.string.recipients_delete_description, beneficiary.nickname))
            }
        },
        modifier = Modifier.testTag("recipient_${beneficiary.id}"),
    )
}

@Composable
private fun AddRecipientDialog(form: AddRecipientForm, onIntent: (BeneficiariesIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(BeneficiariesIntent.DismissAdd) },
        title = { Text(stringResource(R.string.recipients_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                form.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("add_error")) }
                OutlinedTextField(
                    textStyle = LocalTextStyle.current.ltr(),
                    value = form.nickname,
                    onValueChange = { onIntent(BeneficiariesIntent.NicknameChanged(it)) },
                    modifier = Modifier.testTag("add_nickname"),
                    label = { Text(stringResource(R.string.recipients_name_label)) },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.nicknameError != null,
                    supportingText = form.nicknameError?.let { error -> { Text(error.resolve()) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = form.accountNumber,
                    onValueChange = { onIntent(BeneficiariesIntent.NumberChanged(it)) },
                    modifier = Modifier.testTag("add_number"),
                    label = { Text(stringResource(R.string.transfer_account_number)) },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.accountNumberError != null,
                    supportingText = { Text(form.accountNumberError?.resolve() ?: stringResource(R.string.recipients_number_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(BeneficiariesIntent.SaveNew) },
                enabled = !form.isSaving,
                modifier = Modifier.testTag("add_save"),
            ) { Text(stringResource(if (form.isSaving) R.string.action_saving else R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(BeneficiariesIntent.DismissAdd) }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DeleteRecipientDialog(
    target: Beneficiary,
    isDeleting: Boolean,
    onIntent: (BeneficiariesIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onIntent(BeneficiariesIntent.CancelDelete) },
        title = { Text(stringResource(R.string.recipients_delete_title)) },
        text = {
            Text(uiString(R.string.recipients_delete_message, target.nickname, target.displayName))
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(BeneficiariesIntent.ConfirmDelete) },
                enabled = !isDeleting,
                modifier = Modifier.testTag("delete_confirm"),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(BeneficiariesIntent.CancelDelete) },
                enabled = !isDeleting,
                modifier = Modifier.testTag("delete_cancel"),
            ) { Text("Cancel") }
        },
    )
}