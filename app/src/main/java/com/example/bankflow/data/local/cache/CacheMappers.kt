package com.example.bankflow.data.local.cache

import com.example.bankflow.data.remote.dto.AccountDto
import com.example.bankflow.data.remote.dto.CounterpartyDto
import com.example.bankflow.data.remote.dto.DashboardDto
import com.example.bankflow.data.remote.dto.TotalDto
import com.example.bankflow.data.remote.dto.TransactionDto
import com.example.bankflow.data.remote.dto.TransactionPageDto
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.TransactionPage

fun Account.toDto() = AccountDto(
    id = id,
    name = name,
    type = type.name,
    accountNumber = accountNumber,
    currency = balance.currency,
    balanceMinor = balance.minorUnits,
    status = status.name,
    createdAt = createdAt.toString(),
)

fun Transaction.toDto() = TransactionDto(
    transferId = transferId,
    accountId = accountId,
    direction = direction.name,
    amountMinor = amount.minorUnits,
    balanceAfterMinor = balanceAfter.minorUnits,
    currency = amount.currency,
    status = status.name,
    kind = kind.name,
    category = category.name,
    description = description,
    createdAt = createdAt.toString(),
    counterparty = CounterpartyDto(
        name = counterparty.name,
        accountNumberMasked = counterparty.accountNumberMasked,
        isOwnAccount = counterparty.isOwnAccount,
    ),
)

fun TransactionPage.toDto() = TransactionPageDto(items = items.map { it.toDto() }, nextCursor = nextCursor)

fun Dashboard.toDto() = DashboardDto(
    accounts = accounts.map { it.toDto() },
    totals = totals.map { TotalDto(currency = it.currency, balanceMinor = it.minorUnits) },
    recentTransactions = recentTransactions.map { it.toDto() },
)