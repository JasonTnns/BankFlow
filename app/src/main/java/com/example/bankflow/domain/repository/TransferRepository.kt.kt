package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult

interface `TransferRepository.kt` {

    suspend fun transfer(request: TransferRequest): AppResult<TransferResult>

    suspend fun getTransfer(transferid: String): AppResult<Transfer>
}