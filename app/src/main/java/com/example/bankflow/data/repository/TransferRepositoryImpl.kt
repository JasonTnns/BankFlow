package com.example.bankflow.data.repository

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.repository.TransferRepository
import org.koin.core.annotation.Single

@Single
class TransferRepositoryImpl(
    private val remote: BankRemoteDataSource,
) : TransferRepository {

    override suspend fun transfer(request: TransferRequest): AppResult<TransferResult> =
        remote.transfer(request)

    override suspend fun getTransfer(transferId: String): AppResult<Transfer> =
        remote.getTransfer(transferId)
}