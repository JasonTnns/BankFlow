package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.CategorySpend
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.model.ZoneProvider
import com.example.bankflow.domain.repository.InsightsRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

fun testSummary(
    from: Instant = Instant.parse("2026-10-01T00:00:00Z"),
    to: Instant = Instant.parse("2026-11-01T00:00:00Z"),
    income: Long = 100_000,
    categories: List<Pair<Category, Long>> = listOf(Category.FOOD to 6_500, Category.BILLS to 1_000),
    transactionCount: Int = 5,
): SpendingSummary {
    val spent = categories.sumOf { it.second }
    return SpendingSummary(
        from = from,
        to = to,
        income = Money(income),
        spent = Money(spent),
        net = Money(income - spent),
        transactionCount = transactionCount,
        byCategory = categories.map { (category, amount) -> CategorySpend(category, Money(amount), count = 1) },
    )
}

class FixedZoneProvider(var zone: ZoneId = ZoneOffset.UTC) : ZoneProvider {
    override fun zone(): ZoneId = zone
}

class StubInsightsRepository : InsightsRepository {
    data class Call(val from: Instant, val to: Instant, val currency: String)

    val calls = mutableListOf<Call>()
    val gates = mutableMapOf<Instant, CompletableDeferred<Unit>>()
    var handler: (Call) -> AppResult<SpendingSummary> =
        { call -> AppResult.Success(testSummary(from = call.from, to = call.to)) }

    override suspend fun getSummary(from: Instant, to: Instant, currency: String): AppResult<SpendingSummary> {
        val call = Call(from, to, currency)
        calls += call
        gates[from]?.await()
        return handler(call)
    }
}