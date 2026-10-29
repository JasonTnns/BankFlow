package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage

interface `AccountRepository.kt` {

    suspend fun getDashboard(): AppResult<`Dashboard.kt`>

    suspend fun getAccounts(): AppResult<List<Account>>

    suspend fun getAccount(accountId: String): AppResult<Account>

    suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int
    ): AppResult<TransactionPage>
}