package com.example.bankflow.data.repository

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.isOutcomeUnknown
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.repository.TransferRepository
import org.koin.core.annotation.Single


@Single
class TransferRepositoryImpl(
    private val remote: BankRemoteDataSource,
    private val changes: AccountChangeNotifier,
) : TransferRepository {

    override suspend fun transfer(request: TransferRequest): AppResult<TransferResult> {
        val result = remote.transfer(request)
        val balancesMayHaveChanged = when (result) {
            is AppResult.Success -> true
            is AppResult.Failure -> result.error.isOutcomeUnknown
        }
        if (balancesMayHaveChanged) changes.notifyChanged()
        return result
    }

    override suspend fun getTransfer(transferId: String): AppResult<Transfer> =
        remote.getTransfer(transferId)
}