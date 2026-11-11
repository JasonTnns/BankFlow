package com.example.bankflow.presentation.transactions

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.GetTransactionUseCase
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

@KoinViewModel
class TransactionDetailsViewModel(
    @InjectedParam private val ref: TransactionRef,
    private val getTransaction: GetTransactionUseCase,
) : MviViewModel<TransactionDetailsState, TransactionDetailsIntent, NoEffect>(TransactionDetailsState()) {

    init {
        load()
    }

    override fun handleIntent(intent: TransactionDetailsIntent) {
        when (intent) {
            TransactionDetailsIntent.Retry -> load()
        }
    }

    private fun load() {
        if (currentState.isLoading) return
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = getTransaction(ref.accountId, ref.transferId)) {
                is AppResult.Success -> setState { copy(transaction = result.value, isLoading = false, error = null) }
                is AppResult.Failure -> setState { copy(isLoading = false, error = result.error) }
            }
        }
    }
}