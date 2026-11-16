package com.example.bankflow.data.repository

import com.example.bankflow.data.remote.BeneficiaryRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.repository.BeneficiaryRepository
import org.koin.core.annotation.Single

@Single
class BeneficiaryRepositoryImpl(
    private val remote: BeneficiaryRemoteDataSource,
) : BeneficiaryRepository {

    override suspend fun getBeneficiaries(): AppResult<List<Beneficiary>> = remote.getBeneficiaries()

    override suspend fun addBeneficiary(nickname: String, accountNumber: String): AppResult<Beneficiary> =
        remote.addBeneficiary(nickname, accountNumber)

    override suspend fun deleteBeneficiary(id: String): AppResult<Unit> = remote.deleteBeneficiary(id)
}