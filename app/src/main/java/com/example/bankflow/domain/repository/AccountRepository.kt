package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Recipient
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.model.Transaction

interface AccountRepository {

    suspend fun getDashboard(): AppResult<Dashboard>

    suspend fun getAccounts(): AppResult<List<Account>>

    suspend fun getAccount(accountId: String): AppResult<Account>

    suspend fun getTransaction(accountId: String, transferId: String): AppResult<Transaction>

    suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int
    ): AppResult<TransactionPage>

    suspend fun lookupRecipient(accountNumber: String): AppResult<Recipient>
}