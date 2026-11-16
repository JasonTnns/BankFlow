package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.repository.BeneficiaryRepository
import org.koin.core.annotation.Factory

@Factory
class GetBeneficiariesUseCase(
    private val repository: BeneficiaryRepository,
) {
    suspend operator fun invoke(): AppResult<List<Beneficiary>> = repository.getBeneficiaries()
}