package com.example.bankflow.presentation.accounts.filter

import com.example.bankflow.R
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.formatDate
import com.example.bankflow.presentation.common.labelRes
import java.time.ZoneId
import java.util.Locale

enum class FilterKind { DIRECTION, CATEGORY, DATE_RANGE, AMOUNT_RANGE }

data class ActiveFilterChip(val kind: FilterKind, val label: UiText)

fun TransactionFilter.activeChips(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    currency: String = Money.DEFAULT_CURRENCY,
): List<ActiveFilterChip> = buildList {
    direction?.let { add(ActiveFilterChip(FilterKind.DIRECTION, UiText.Res(it.labelRes()))) }
    category?.let { add(ActiveFilterChip(FilterKind.CATEGORY, UiText.Res(it.labelRes()))) }

    if (from != null || to != null) {
        val fromText = from?.formatDate(zone, locale)
        val toText = to?.formatDate(zone, locale)
        val label = when {
            fromText != null && toText != null ->
                if (fromText == toText) UiText.Plain(fromText) else UiText.Res(R.string.date_range_between, listOf(fromText, toText))
            fromText != null -> UiText.Res(R.string.date_range_from, listOf(fromText))
            else -> UiText.Res(R.string.date_range_until, listOf(toText.orEmpty()))
        }
        add(ActiveFilterChip(FilterKind.DATE_RANGE, label))
    }

    if (minAmountMinor != null || maxAmountMinor != null) {
        val min = minAmountMinor?.let { Money(it, currency) }
        val max = maxAmountMinor?.let { Money(it, currency) }
        val label = when {
            min != null && max != null -> UiText.Res(R.string.amount_range_between, listOf(min, max))
            min != null -> UiText.Res(R.string.amount_range_min, listOf(min))
            else -> UiText.Res(R.string.amount_range_max, listOf(requireNotNull(max)))
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