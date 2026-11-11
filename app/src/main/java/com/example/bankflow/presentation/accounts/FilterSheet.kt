package com.example.bankflow.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.presentation.accounts.filter.FilterDraft
import com.example.bankflow.presentation.accounts.filter.localDateToPickerMillis
import com.example.bankflow.presentation.accounts.filter.pickerMillisToLocalDate
import com.example.bankflow.presentation.common.label
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: TransactionFilter,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var draft by remember { mutableStateOf(FilterDraft.from(filter, zone)) }
    var errors by remember { mutableStateOf(FilterDraft.Errors()) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FilterSheetContent(
            draft = draft,
            errors = errors,
            onDraftChange = {
                draft = it
                errors = FilterDraft.Errors()
            },
            onPickDates = { showDatePicker = true },
            onReset = {
                draft = FilterDraft()
                errors = FilterDraft.Errors()
            },
            onApply = {
                when (val result = draft.toFilter(zone)) {
                    is FilterDraft.Result.Valid -> onApply(result.filter)
                    is FilterDraft.Result.Invalid -> errors = result.errors
                }
            },
        )
    }

    if (showDatePicker) {
        DateRangeDialog(
            initialFrom = draft.fromDate,
            initialTo = draft.toDate,
            onConfirm = { from, to ->
                draft = draft.copy(fromDate = from, toDate = to)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    draft: FilterDraft,
    errors: FilterDraft.Errors,
    onDraftChange: (FilterDraft) -> Unit,
    onPickDates: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            TextButton(onClick = onReset, modifier = Modifier.testTag("filter_reset")) { Text("Reset") }
        }

        SheetSection("Type") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.direction == null,
                    onClick = { onDraftChange(draft.copy(direction = null)) },
                    label = { Text("Any type") },
                )
                Direction.entries.forEach { direction ->
                    FilterChip(
                        selected = draft.direction == direction,
                        onClick = { onDraftChange(draft.copy(direction = direction)) },
                        label = { Text(direction.label()) },
                    )
                }
            }
        }

        SheetSection("Category") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = draft.category == null,
                    onClick = { onDraftChange(draft.copy(category = null)) },
                    label = { Text("All categories") },
                )
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = draft.category == category,
                        onClick = { onDraftChange(draft.copy(category = category)) },
                        label = { Text(category.label()) },
                    )
                }
            }
        }

        SheetSection("Date range") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickDates, modifier = Modifier.testTag("filter_dates")) {
                    Text(dateRangeText(draft.fromDate, draft.toDate) ?: "Any date")
                }
                if (draft.fromDate != null || draft.toDate != null) {
                    IconButton(onClick = { onDraftChange(draft.copy(fromDate = null, toDate = null)) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear date range")
                    }
                }
            }
        }

        SheetSection("Amount") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.minAmountText,
                    onValueChange = { onDraftChange(draft.copy(minAmountText = it)) },
                    modifier = Modifier.weight(1f).testTag("filter_min"),
                    label = { Text("Minimum") },
                    singleLine = true,
                    isError = errors.minAmount != null,
                    supportingText = errors.minAmount?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = draft.maxAmountText,
                    onValueChange = { onDraftChange(draft.copy(maxAmountText = it)) },
                    modifier = Modifier.weight(1f).testTag("filter_max"),
                    label = { Text("Maximum") },
                    singleLine = true,
                    isError = errors.maxAmount != null,
                    supportingText = errors.maxAmount?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
        }

        Button(onClick = onApply, modifier = Modifier.fillMaxWidth().testTag("filter_apply")) {
            Text("Show results")
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
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

private fun dateRangeText(from: LocalDate?, to: LocalDate?): String? {
    if (from == null && to == null) return null
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val fromText = from?.format(formatter)
    val toText = to?.format(formatter)
    return when {
        fromText != null && toText != null -> if (fromText == toText) fromText else "$fromText \u2013 $toText"
        fromText != null -> "From $fromText"
        else -> "Until $toText"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (from: LocalDate?, to: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.let(::localDateToPickerMillis),
        initialSelectedEndDateMillis = initialTo?.let(::localDateToPickerMillis),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.let(::pickerMillisToLocalDate)
                    val end = state.selectedEndDateMillis?.let(::pickerMillisToLocalDate) ?: start
                    onConfirm(start, end)
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}