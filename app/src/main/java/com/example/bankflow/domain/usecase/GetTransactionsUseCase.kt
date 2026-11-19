package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Cached
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class GetTransactionsUseCase(
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(
        accountId: String,
        filter: TransactionFilter = TransactionFilter(),
        cursor: String? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): AppResult<TransactionPage> {
        suspend fun cached(accountId: String): Cached<TransactionPage>? =
            accountRepository.getCachedTransactions(accountId)
        val issues = linkedMapOf<String, List<FieldIssue>>()

        if (filter.from != null && filter.to != null && filter.from.isAfter(filter.to)) {
            issues["dateRange"] = listOf(FieldIssue.Invalid)
        }
        if (filter.minAmountMinor != null && filter.maxAmountMinor != null &&
            filter.minAmountMinor > filter.maxAmountMinor
        ) {
            issues["amountRange"] = listOf(FieldIssue.AmountMaxBelowMin)
        }
        if ((filter.search?.trim()?.length ?: 0) > MAX_SEARCH_LENGTH) issues["search"] = listOf(FieldIssue.Invalid)
        if (issues.isNotEmpty()) return AppResult.Failure(AppError.Validation(issues = issues))

        val cleanFilter = filter.copy(search = filter.search?.trim()?.takeIf { it.isNotEmpty() })
        return accountRepository.getTransactions(
            accountId = accountId,
            filter = cleanFilter,
            cursor = cursor,
            limit = limit.coerceIn(1, MAX_PAGE_SIZE),
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        private const val MAX_SEARCH_LENGTH = 100
    }
}