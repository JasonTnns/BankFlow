package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.repository.BeneficiaryRepository
import org.koin.core.annotation.Factory

@Factory
class DeleteBeneficiaryUseCase(
    private val repository: BeneficiaryRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = repository.deleteBeneficiary(id)
}