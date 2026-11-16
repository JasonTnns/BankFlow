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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.presentation.auth.ErrorBanner
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
                title = { Text("Saved recipients") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasLoaded && !state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { onIntent(BeneficiariesIntent.OpenAdd) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add recipient") },
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
                        .semantics { contentDescription = "Loading recipients" },
                )
                loadError != null && !state.hasLoaded -> ErrorState(
                    error = loadError,
                    onRetry = { onIntent(BeneficiariesIntent.Retry) },
                    modifier = Modifier.align(Alignment.Center),
                )
                state.isEmpty -> EmptyState(
                    title = "No saved recipients",
                    message = "Save the people you pay often, and send them money in one tap.",
                    actionLabel = "Add recipient",
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
            Text("${beneficiary.displayName} \u00B7 ${beneficiary.accountNumberMasked}$unavailable")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${beneficiary.nickname}")
            }
        },
        modifier = Modifier.testTag("recipient_${beneficiary.id}"),
    )
}

@Composable
private fun AddRecipientDialog(form: AddRecipientForm, onIntent: (BeneficiariesIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(BeneficiariesIntent.DismissAdd) },
        title = { Text("Add recipient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                form.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("add_error")) }
                OutlinedTextField(
                    value = form.nickname,
                    onValueChange = { onIntent(BeneficiariesIntent.NicknameChanged(it)) },
                    modifier = Modifier.testTag("add_nickname"),
                    label = { Text("Name, e.g. Mum") },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.nicknameError != null,
                    supportingText = form.nicknameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = form.accountNumber,
                    onValueChange = { onIntent(BeneficiariesIntent.NumberChanged(it)) },
                    modifier = Modifier.testTag("add_number"),
                    label = { Text("Account number") },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.accountNumberError != null,
                    supportingText = { Text(form.accountNumberError ?: "12 digits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(BeneficiariesIntent.SaveNew) },
                enabled = !form.isSaving,
                modifier = Modifier.testTag("add_save"),
            ) { Text(if (form.isSaving) "Saving\u2026" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(BeneficiariesIntent.DismissAdd) }) { Text("Cancel") }
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
        title = { Text("Delete recipient?") },
        text = {
            Text(
                "${target.nickname} (${target.displayName}) will be removed from your saved recipients. " +
                        "You can add them again later.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(BeneficiariesIntent.ConfirmDelete) },
                enabled = !isDeleting,
                modifier = Modifier.testTag("delete_confirm"),
            ) { Text("Delete") }
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