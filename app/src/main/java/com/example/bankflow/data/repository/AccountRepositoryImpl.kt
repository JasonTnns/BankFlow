package com.example.bankflow.data.repository

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Single
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.Recipient

@Single
class AccountRepositoryImpl(
    private val remote: BankRemoteDataSource,
) : AccountRepository {
    override suspend fun getDashboard(): AppResult<Dashboard> = remote.getDashboard()

    override suspend fun getAccounts(): AppResult<List<Account>> = remote.getAccounts()

    override suspend fun getAccount(accountId: String): AppResult<Account> = remote.getAccount(accountId)

    override suspend fun getTransaction(accountId: String, transferId: String): AppResult<Transaction> =
        remote.getTransaction(accountId, transferId)

    override suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int
    ): AppResult<TransactionPage> = remote.getTransactions(accountId, filter, cursor, limit)


    override suspend fun lookupRecipient(accountNumber: String): AppResult<Recipient> =
        remote.lookupRecipient(accountNumber)

}