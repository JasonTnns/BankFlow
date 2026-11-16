package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.repository.InsightsRepository
import org.koin.core.annotation.Factory
import java.time.YearMonth
import java.time.ZoneId

@Factory
class GetSpendingSummaryUseCase (
    private val repository: InsightsRepository
) {
    suspend operator fun invoke(
        month: YearMonth,
        zone: ZoneId,
        currency: String = Money.DEFAULT_CURRENCY,
    ): AppResult<SpendingSummary> {
        val from = month.atDay(1).atStartOfDay(zone).toInstant()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        return repository.getSummary(from, to, currency)
    }
}