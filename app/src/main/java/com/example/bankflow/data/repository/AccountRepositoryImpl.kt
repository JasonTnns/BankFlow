package com.example.bankflow.data.repository

import com.example.bankflow.data.local.cache.CacheStore
import com.example.bankflow.data.local.cache.toDto
import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.data.remote.dto.DashboardDto
import com.example.bankflow.data.remote.dto.TransactionPageDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Cached
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Recipient
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Single

class AccountRepositoryImpl(
    private val remote: BankRemoteDataSource,
    private val cache: CacheStore,
) : AccountRepository {

    override suspend fun getDashboard(): AppResult<Dashboard> {
        val owner = cache.currentOwner()
        val result = remote.getDashboard()
        if (result is AppResult.Success) {
            cache.write(owner, DASHBOARD_KEY, DashboardDto.serializer(), result.value.toDto())
        }
        return result
    }

    override suspend fun getAccounts(): AppResult<List<Account>> = remote.getAccounts()

    override suspend fun getAccount(accountId: String): AppResult<Account> = remote.getAccount(accountId)

    override suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int,
    ): AppResult<TransactionPage> {
        val cacheable = cursor == null && !filter.isActive
        val owner = if (cacheable) cache.currentOwner() else null
        val result = remote.getTransactions(accountId, filter, cursor, limit)
        if (cacheable && result is AppResult.Success) {
            cache.write(owner, historyKey(accountId), TransactionPageDto.serializer(), result.value.toDto())
        }
        return result
    }

    override suspend fun getTransaction(accountId: String, transferId: String): AppResult<Transaction> =
        remote.getTransaction(accountId, transferId)

    override suspend fun lookupRecipient(accountNumber: String): AppResult<Recipient> =
        remote.lookupRecipient(accountNumber)


    override suspend fun getCachedDashboard(): Cached<Dashboard>? =
        readCached(DASHBOARD_KEY, DashboardDto.serializer()) { it.toDomain() }

    override suspend fun getCachedAccount(accountId: String): Cached<Account>? {
        val saved = getCachedDashboard() ?: return null
        val account = saved.value.accounts.firstOrNull { it.id == accountId } ?: return null
        return Cached(account, saved.savedAt)
    }

    override suspend fun getCachedTransactions(accountId: String): Cached<TransactionPage>? =
        readCached(historyKey(accountId), TransactionPageDto.serializer()) { it.toDomain() }

    private suspend fun <D, T> readCached(key: String, serializer: KSerializer<D>, map: (D) -> T): Cached<T>? {
        val saved = cache.read(key, serializer) ?: return null
        return try {
            Cached(map(saved.value), saved.savedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val DASHBOARD_KEY = "dashboard"
        fun historyKey(accountId: String) = "history:$accountId"
    }
}