package com.example.bankflow.presentation.accounts.filter

import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.formatDate
import com.example.bankflow.presentation.common.label
import java.time.ZoneId
import java.util.Locale

enum class FilterKind { DIRECTION, CATEGORY, DATE_RANGE, AMOUNT_RANGE }

data class ActiveFilterChip(val kind: FilterKind, val label: String)

fun TransactionFilter.activeChips(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    currency: String = Money.DEFAULT_CURRENCY,
): List<ActiveFilterChip> = buildList {
    direction?.let { add(ActiveFilterChip(FilterKind.DIRECTION, it.label())) }
    category?.let { add(ActiveFilterChip(FilterKind.CATEGORY, it.label())) }

    if (from != null || to != null) {
        val fromText = from?.formatDate(zone, locale)
        val toText = to?.formatDate(zone, locale)
        val label = when {
            fromText != null && toText != null ->
                if (fromText == toText) fromText else "$fromText \u2013 $toText"
            fromText != null -> "From $fromText"
            else -> "Until $toText"
        }
        add(ActiveFilterChip(FilterKind.DATE_RANGE, label))
    }

    if (minAmountMinor != null || maxAmountMinor != null) {
        val min = minAmountMinor?.let { Money(it, currency).format(locale) }
        val max = maxAmountMinor?.let { Money(it, currency).format(locale) }
        val label = when {
            min != null && max != null -> "$min \u2013 $max"
            min != null -> "At least $min"
            else -> "Up to $max"
        }
        add(ActiveFilterChip(FilterKind.AMOUNT_RANGE, label))
    }
}

fun TransactionFilter.without(kind: FilterKind): TransactionFilter = when (kind) {
    FilterKind.DIRECTION -> copy(direction = null)
    FilterKind.CATEGORY -> copy(category = null)
    FilterKind.DATE_RANGE -> copy(from = null, to = null)
    FilterKind.AMOUNT_RANGE -> copy(minAmountMinor = null, maxAmountMinor = null)
}