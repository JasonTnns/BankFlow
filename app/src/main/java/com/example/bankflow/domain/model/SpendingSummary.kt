package com.example.bankflow.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class CategorySpend(
    val category: Category,
    val spent: Money,
    val count: Int,
)

data class SpendingSummary(
    val from: Instant,
    val to: Instant,
    val income: Money,
    val spent: Money,
    val net: Money,
    val transactionCount: Int,
    val byCategory: List<CategorySpend>,
) {
    val isEmpty: Boolean get() = transactionCount == 0

    fun sharePercent(category: CategorySpend): Int {
        if (spent.minorUnits <= 0) return 0
        return BigDecimal(category.spent.minorUnits)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(spent.minorUnits), 0, RoundingMode.HALF_UP)
            .toInt()
    }
}

fun percentChange(current: Money, previous: Money): Int? {
    if (previous.minorUnits <= 0) return null
    return BigDecimal(current.minorUnits - previous.minorUnits)
        .multiply(BigDecimal(100))
        .divide(BigDecimal(previous.minorUnits), 0, RoundingMode.HALF_UP)
        .toInt()
}