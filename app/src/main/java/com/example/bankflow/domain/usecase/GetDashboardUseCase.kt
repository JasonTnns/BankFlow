package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Cached
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class GetDashboardUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(): AppResult<Dashboard> = accountRepository.getDashboard()

    suspend fun cached(): Cached<Dashboard>? = accountRepository.getCachedDashboard()
}