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
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus
import java.time.Instant

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