package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Beneficiary

interface BeneficiaryRepository {
    suspend fun getBeneficiaries(): AppResult<List<Beneficiary>>

    suspend fun addBeneficiary(nickname: String, accountNumber: String): AppResult<Beneficiary>

    suspend fun deleteBeneficiary(id: String): AppResult<Unit>
}