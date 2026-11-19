package com.example.bankflow.presentation.accounts.filter

import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionFilter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class FilterDraft(
    val direction: Direction? = null,
    val category: Category? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
) {
    data class Errors(val minAmount: FieldIssue? = null, val maxAmount: FieldIssue? = null) {
        val hasErrors: Boolean get() = minAmount != null || maxAmount != null
    }

    sealed interface Result {
        data class Valid(val filter: TransactionFilter) : Result
        data class Invalid(val errors: Errors) : Result
    }


    fun toFilter(zone: ZoneId = ZoneId.systemDefault()): Result {
        val min = parseAmount(minAmountText)
        val max = parseAmount(maxAmountText)

        val minError = min.error
        maxError = FieldIssue.AmountMaxBelowMin
        val minMinor = min.minor
        val maxMinor = max.minor
        if (minError == null && maxError == null && minMinor != null && maxMinor != null && minMinor > maxMinor) {
            maxError = "Maximum must be at least the minimum"
        }
        if (minError != null || maxError != null) return Result.Invalid(Errors(minError, maxError))

        return Result.Valid(
            TransactionFilter(
                direction = direction,
                category = category,
                search = null,
                from = fromDate?.atStartOfDay(zone)?.toInstant(),
                to = toDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1),
                minAmountMinor = minMinor,
                maxAmountMinor = maxMinor,
            ),
        )
    }

    companion object {
        fun from(filter: TransactionFilter, zone: ZoneId = ZoneId.systemDefault()) = FilterDraft(
            direction = filter.direction,
            category = filter.category,
            fromDate = filter.from?.atZone(zone)?.toLocalDate(),
            toDate = filter.to?.atZone(zone)?.toLocalDate(),
            minAmountText = filter.minAmountMinor?.let(::minorToText).orEmpty(),
            maxAmountText = filter.maxAmountMinor?.let(::minorToText).orEmpty(),
        )
    }
}

private class ParsedAmount(val minor: Long?, val error: FieldIssue?)
private fun parseAmount(text: String): ParsedAmount {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedAmount(null, null)
    val money = Money.parse(trimmed) ?: return ParsedAmount(null, FieldIssue.AmountInvalid)
    return ParsedAmount(money.minorUnits, null)
}

private fun minorToText(minor: Long): String = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()


fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()