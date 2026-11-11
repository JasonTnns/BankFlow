package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Counterparty
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Recipient
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.domain.repository.AccountRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant

val TEST_ACCOUNT = Account(
    id = "acc-1",
    name = "Everyday Checking",
    type = AccountType.CHECKING,
    accountNumber = "100000000001",
    balance = Money(100_000),
    status = AccountStatus.ACTIVE,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
)

val TEST_SAVINGS = TEST_ACCOUNT.copy(
    id = "acc-2",
    name = "High-Yield Savings",
    type = AccountType.SAVINGS,
    accountNumber = "100000000002",
    balance = Money(250_000),
)

val TEST_RECIPIENT = Recipient(
    displayName = "Bob D.",
    accountNumberMasked = "\u2022\u2022\u2022\u20220001",
    currency = "USD",
    status = AccountStatus.ACTIVE,
    isOwnAccount = false,
)

fun testTransaction(
    id: String,
    direction: Direction = Direction.OUT,
    amount: Long = 1_000,
    description: String? = null,
    category: Category = Category.FOOD,
    createdAt: Instant = Instant.parse("2026-10-29T10:00:00Z"),
) = Transaction(
    transferId = id,
    accountId = "acc-1",
    direction = direction,
    amount = Money(amount),
    balanceAfter = Money(100_000),
    status = TransferStatus.COMPLETED,
    kind = TransferKind.TRANSFER,
    category = category,
    description = description,
    createdAt = createdAt,
    counterparty = Counterparty(name = "Fresh Market", accountNumberMasked = null, isOwnAccount = false),
)

fun testPage(vararg ids: String, next: String? = null) =
    TransactionPage(items = ids.map { testTransaction(it) }, nextCursor = next)

val TEST_DASHBOARD = Dashboard(
    accounts = listOf(TEST_ACCOUNT),
    totals = listOf(Money(100_000)),
    recentTransactions = listOf(
        testTransaction("t-welcome", Direction.IN, 100_000, "Welcome bonus", Category.INCOME),
    ),
)

class StubAccountRepository : AccountRepository {

    data class TransactionCall(
        val accountId: String,
        val filter: TransactionFilter,
        val cursor: String?,
        val limit: Int,
    )

    var dashboardResult: AppResult<Dashboard> = AppResult.Success(TEST_DASHBOARD)
    var accountsResult: AppResult<List<Account>> = AppResult.Success(listOf(TEST_ACCOUNT))
    var accountResult: AppResult<Account> = AppResult.Success(TEST_ACCOUNT)
    var transactionResult: AppResult<Transaction> = AppResult.Success(testTransaction("t1"))
    var recipientResult: AppResult<Recipient> = AppResult.Success(TEST_RECIPIENT)

    var dashboardGate: CompletableDeferred<Unit>? = null
    var dashboardCalls = 0

    var transactionGate: CompletableDeferred<Unit>? = null
    val transactionRequests = mutableListOf<Pair<String, String>>()

    val accountRequests = mutableListOf<String>()
    val recipientLookups = mutableListOf<String>()

    var transactionsHandler: suspend (TransactionCall) -> AppResult<TransactionPage> =
        { AppResult.Success(TransactionPage(emptyList(), null)) }
    val transactionCalls = mutableListOf<TransactionCall>()

    override suspend fun getDashboard(): AppResult<Dashboard> {
        dashboardCalls++
        dashboardGate?.await()
        return dashboardResult
    }

    override suspend fun getAccounts(): AppResult<List<Account>> = accountsResult

    override suspend fun getAccount(accountId: String): AppResult<Account> {
        accountRequests += accountId
        return accountResult
    }

    override suspend fun getTransactions(
        accountId: String,
        filter: TransactionFilter,
        cursor: String?,
        limit: Int,
    ): AppResult<TransactionPage> {
        val call = TransactionCall(accountId, filter, cursor, limit)
        transactionCalls += call
        return transactionsHandler(call)
    }

    override suspend fun getTransaction(accountId: String, transferId: String): AppResult<Transaction> {
        transactionRequests += accountId to transferId
        transactionGate?.await()
        return transactionResult
    }

    override suspend fun lookupRecipient(accountNumber: String): AppResult<Recipient> {
        recipientLookups += accountNumber
        return recipientResult
    }
}

fun notFound(): AppResult.Failure = AppResult.Failure(AppError.NotFound)