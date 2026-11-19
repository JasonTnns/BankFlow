package com.example.bankflow.presentation.insights

import com.example.bankflow.R
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.model.percentChange
import com.example.bankflow.presentation.common.UiText
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

fun YearMonth.title(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

fun YearMonth.monthName(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("LLLL", locale))

fun shareLabel(percent: Int): UiText =
    if (percent <= 0) UiText.Res(R.string.share_less_than_one) else UiText.Res(R.string.share_percent, listOf(percent))

fun comparisonText(
    current: SpendingSummary,
    previous: SpendingSummary?,
    previousMonth: YearMonth,
    locale: Locale = Locale.getDefault(),
): UiText? {
    if (previous == null) return null
    val change = percentChange(current.spent, previous.spent) ?: return null
    val name = previousMonth.monthName(locale)
    return when {
        change > 0 -> UiText.Res(R.string.insights_comparison_more, listOf(change, name))
        change < 0 -> UiText.Res(R.string.insights_comparison_less, listOf(-change, name))
        else -> UiText.Res(R.string.insights_comparison_same, listOf(name))
    }
}