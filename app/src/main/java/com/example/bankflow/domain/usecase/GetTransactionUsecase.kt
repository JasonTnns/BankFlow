package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class GetTransactionUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(accountId: String, transferId: String): AppResult<Transaction> =
        accountRepository.getTransaction(accountId, transferId)
}