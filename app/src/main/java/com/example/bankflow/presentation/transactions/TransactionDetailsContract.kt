package com.example.bankflow.presentation.transactions

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class TransactionRef(val accountId: String, val transferId: String)

data class TransactionDetailsState(
    val transaction: Transaction? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
) : UiState

sealed interface TransactionDetailsIntent : UiIntent {
    data object Retry : TransactionDetailsIntent
}