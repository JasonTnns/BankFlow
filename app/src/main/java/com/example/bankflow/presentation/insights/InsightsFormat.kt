package com.example.bankflow.presentation.insights

import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.model.percentChange
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

fun YearMonth.title(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

fun YearMonth.monthName(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("LLLL", locale))

fun shareLabel(percent: Int): String = if (percent <= 0) "<1%" else "$percent%"

fun comparisonText(
    current: SpendingSummary,
    previous: SpendingSummary?,
    previousMonth: YearMonth,
    locale: Locale = Locale.getDefault(),
): String? {
    if (previous == null) return null
    val change = percentChange(current.spent, previous.spent) ?: return null
    val name = previousMonth.monthName(locale)
    return when {
        change > 0 -> "$change% more than $name"
        change < 0 -> "${-change}% less than $name"
        else -> "Same as $name"
    }
}