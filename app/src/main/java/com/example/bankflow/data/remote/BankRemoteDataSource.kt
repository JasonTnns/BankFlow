package com.example.bankflow.data.remote

import com.example.bankflow.core.network.apiCall
import com.example.bankflow.data.remote.dto.AccountResponseDto
import com.example.bankflow.data.remote.dto.AccountsResponseDto
import com.example.bankflow.data.remote.dto.DashboardDto
import com.example.bankflow.data.remote.dto.TransactionPageDto
import com.example.bankflow.data.remote.dto.TransferEnvelopeDto
import com.example.bankflow.data.remote.dto.TransferResultDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.data.remote.mapper.toDto
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class BankRemoteDataSource(private val client: HttpClient) {

    suspend fun getDashboard(): AppResult<Dashboard> =
        apiCall<DashboardDto, Dashboard>(
            request = { client.get("dashboard") },
            map = { it.toDomain() },
        )

    suspend fun getAccounts(): AppResult<List<Account>> =
        apiCall<AccountsResponseDto, List<Account>>(
            request = { client.get("accounts") },
            map = { dto -> dto.accounts.map { it.toDomain() } },
        )

    suspend fun getAccount(accountId: String): AppResult<Account> =
        apiCall<AccountResponseDto, Account>(
            request = { client.get("accounts/$accountId") },
            map = { it.account.toDomain() },
        )

    suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int,
    ): AppResult<TransactionPage> =
        apiCall<TransactionPageDto, TransactionPage>(
            request = {
                client.get("accounts/$accountId/transactions") {
                    parameter("limit", limit)
                    parameter("cursor", cursor)
                    parameter("direction", filter.direction?.name)
                    parameter("category", filter.category?.name)
                    parameter("search", filter.search)
                    parameter("from", filter.from?.toString())
                    parameter("to", filter.to?.toString())
                    parameter("minAmountMinor", filter.minAmountMinor)
                    parameter("maxAmountMinor", filter.maxAmountMinor)
                }
            },
            map = { it.toDomain() },
        )

    suspend fun transfer(request: TransferRequest): AppResult<TransferResult> =
        apiCall<TransferResultDto, TransferResult>(
            request = {
                client.post("transfers") {
                    header("Idempotency-Key", request.idempotencyKey)
                    contentType(ContentType.Application.Json)
                    setBody(request.toDto())
                }
            },
            map = { it.toDomain() },
        )

    suspend fun getTransfer(transferId: String): AppResult<Transfer> =
        apiCall<TransferEnvelopeDto, Transfer>(
            request = { client.get("transfers/$transferId") },
            map = { it.transfer.toDomain() },
        )
}