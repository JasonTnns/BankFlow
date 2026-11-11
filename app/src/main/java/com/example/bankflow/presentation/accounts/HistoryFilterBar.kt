package com.example.bankflow.presentation.accounts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.bankflow.presentation.accounts.filter.ActiveFilterChip
import com.example.bankflow.presentation.accounts.filter.FilterKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterBar(
    searchText: String,
    chips: List<ActiveFilterChip>,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (FilterKind) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f).testTag("history_search"),
                placeholder = { Text("Search transactions") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
            IconButton(onClick = onOpenFilters, modifier = Modifier.testTag("filter_button")) {
                BadgedBox(badge = { if (chips.isNotEmpty()) Badge { Text(chips.size.toString()) } }) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = if (chips.isEmpty()) "Filters" else "Filters, ${chips.size} active",
                    )
                }
            }
        }

        if (chips.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(chips, key = { it.kind }) { chip ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveFilter(chip.kind) },
                        label = { Text(chip.label) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        modifier = Modifier
                            .semantics { contentDescription = "Remove filter: ${chip.label}" }
                            .testTag("chip_${chip.kind.name}"),
                    )
                }
                item(key = "clear_all") {
                    TextButton(onClick = onClearAll, modifier = Modifier.testTag("clear_all")) { Text("Clear all") }
                }
            }
        }
    }
}