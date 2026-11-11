package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class GetAccountsUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(): AppResult<List<Account>> = accountRepository.getAccounts()

    suspend operator fun invoke(accountId: String): AppResult<Account> =
        accountRepository.getAccount(accountId)
}