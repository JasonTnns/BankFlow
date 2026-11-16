package com.example.bankflow.presentation

import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Counterparty
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.presentation.transfer.TransferReview
import java.time.Instant
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.model.CategorySpend
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.User

val UI_ACCOUNT = Account(
    id = "acc-1",
    name = "Everyday Checking",
    type = AccountType.CHECKING,
    accountNumber = "100000000001",
    balance = Money(100_000),
    status = AccountStatus.ACTIVE,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
)

fun uiTransaction(
    id: String,
    name: String = "Fresh Market",
    direction: Direction = Direction.OUT,
    amount: Long = 2_500,
) = Transaction(
    transferId = id,
    accountId = "acc-1",
    direction = direction,
    amount = Money(amount),
    balanceAfter = Money(100_000),
    status = TransferStatus.COMPLETED,
    kind = TransferKind.TRANSFER,
    category = Category.FOOD,
    description = null,
    createdAt = Instant.now(),
    counterparty = Counterparty(name, accountNumberMasked = null, isOwnAccount = false),
)

val UI_DASHBOARD = Dashboard(
    accounts = listOf(UI_ACCOUNT),
    totals = listOf(Money(100_000)),
    recentTransactions = listOf(uiTransaction("t1", "Fresh Market"), uiTransaction("t2", "Acme Payroll", Direction.IN, 320_000)),
)

val UI_SAVINGS = UI_ACCOUNT.copy(
    id = "acc-2",
    name = "High-Yield Savings",
    type = com.example.bankflow.domain.model.AccountType.SAVINGS,
    accountNumber = "100000000002",
    balance = Money(250_000),
)

fun uiBeneficiary(
    id: String,
    nickname: String,
    number: String = "200000000001",
    status: AccountStatus = AccountStatus.ACTIVE,
) = Beneficiary(
    id = id,
    nickname = nickname,
    accountNumber = number,
    accountNumberMasked = "\u2022\u2022\u2022\u2022" + number.takeLast(4),
    displayName = "Bob D.",
    currency = "USD",
    status = status,
    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
)

val UI_REVIEW = TransferReview(
    request = TransferRequest(
        idempotencyKey = "key-1",
        sourceAccountId = "acc-1",
        destination = TransferDestination.AccountNumber("200000000001"),
        amount = Money(2_500),
        category = Category.FOOD,
        description = "Lunch",
    ),
    source = UI_ACCOUNT,
    recipientName = "Bob D.",
    recipientDetail = "\u2022\u2022\u2022\u20220001",
    isOwnAccount = false,
)

val UI_RESULT = TransferResult(
    transfer = Transfer(
        id = "transfer-42",
        kind = TransferKind.TRANSFER,
        status = TransferStatus.COMPLETED,
        failureReason = null,
        sourceAccountId = "acc-1",
        destinationAccountId = "dest",
        amount = Money(2_500),
        category = Category.FOOD,
        description = "Lunch",
        createdAt = Instant.now(),
        completedAt = Instant.now(),
    ),
    sourceAccountBalance = Money(97_500),
)

fun uiSummary(
    categories: List<Pair<Category, Long>> = listOf(Category.FOOD to 6_500, Category.BILLS to 1_000),
    income: Long = 100_000,
    transactionCount: Int = 5,
): SpendingSummary {
    val spent = categories.sumOf { it.second }
    return SpendingSummary(
        from = Instant.parse("2026-10-01T00:00:00Z"),
        to = Instant.parse("2026-11-01T00:00:00Z"),
        income = Money(income),
        spent = Money(spent),
        net = Money(income - spent),
        transactionCount = transactionCount,
        byCategory = categories.map { (category, amount) ->
            CategorySpend(
                category,
                Money(amount),
                count = 1
            )
        },
    )
}

fun uiProfile(name: String = "Alice Demo", theme: ThemePreference = ThemePreference.SYSTEM) =
    Profile(
        user = User("u1", "alice@example.com", name, theme),
        dailyTransferLimit = Money(500_000),
        memberSince = Instant.parse("2026-01-15T10:00:00Z"),
    )