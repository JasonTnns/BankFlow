package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.repository.TransferRepository
import org.koin.core.annotation.Factory

@Factory
class GetTransferUseCase(
    private val transferRepository: TransferRepository,
) {
    suspend operator fun invoke(transferId: String): AppResult<Transfer> =
        transferRepository.getTransfer(transferId)
}