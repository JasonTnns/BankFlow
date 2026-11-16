package com.example.bankflow.data.remote.mapper

import com.example.bankflow.data.remote.dto.AccountDto
import com.example.bankflow.data.remote.dto.BeneficiaryDto
import com.example.bankflow.data.remote.dto.DashboardDto
import com.example.bankflow.data.remote.dto.ProfileDto
import com.example.bankflow.data.remote.dto.RecipientDto
import com.example.bankflow.data.remote.dto.SessionDto
import com.example.bankflow.data.remote.dto.SpendingSummaryDto
import com.example.bankflow.data.remote.dto.TransactionDto
import com.example.bankflow.data.remote.dto.TransactionPageDto
import com.example.bankflow.data.remote.dto.TransferDto
import com.example.bankflow.data.remote.dto.TransferRequestDto
import com.example.bankflow.data.remote.dto.TransferResultDto
import com.example.bankflow.data.remote.dto.UserDto
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.AuthSession
import com.example.bankflow.domain.model.AuthTokens
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.CategorySpend
import com.example.bankflow.domain.model.Counterparty
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.Recipient
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.domain.model.User
import java.time.Instant


private inline fun <reified E : Enum<E>> String.toEnumOr(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

fun UserDto.toDomain() = User(
    id = id,
    email = email,
    fullName = fullName,
    themePreference = themePreference.toEnumOr(ThemePreference.SYSTEM),
)

fun SessionDto.toDomain() = AuthSession(
    tokens = AuthTokens(accessToken, refreshToken, expiresInSeconds),
    user = user.toDomain(),
)

fun AccountDto.toDomain() = Account(
    id = id,
    name = name,
    type = enumValueOf<AccountType>(type),
    accountNumber = accountNumber,
    balance = Money(balanceMinor, currency),
    status = enumValueOf<AccountStatus>(status),
    createdAt = Instant.parse(createdAt),
)

fun TransactionDto.toDomain() = Transaction(
    transferId = transferId,
    accountId = accountId,
    direction = enumValueOf<Direction>(direction),
    amount = Money(amountMinor, currency),
    balanceAfter = Money(balanceAfterMinor, currency),
    status = enumValueOf<TransferStatus>(status),
    kind = enumValueOf<TransferKind>(kind),
    category = category.toEnumOr(Category.OTHER),
    description = description,
    createdAt = Instant.parse(createdAt),
    counterparty = Counterparty(
        name = counterparty.name,
        accountNumberMasked = counterparty.accountNumberMasked,
        isOwnAccount = counterparty.isOwnAccount,
    ),
)

fun TransactionPageDto.toDomain() = TransactionPage(
    items = items.map { it.toDomain() },
    nextCursor = nextCursor,
)

fun DashboardDto.toDomain() = Dashboard(
    accounts = accounts.map { it.toDomain() },
    totals = totals.map { Money(it.balanceMinor, it.currency) },
    recentTransactions = recentTransactions.map { it.toDomain() },
)

fun TransferDto.toDomain() = Transfer(
    id = id,
    kind = enumValueOf<TransferKind>(kind),
    status = enumValueOf<TransferStatus>(status),
    failureReason = failureReason,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    amount = Money(amountMinor, currency),
    category = category.toEnumOr(Category.OTHER),
    description = description,
    createdAt = Instant.parse(createdAt),
    completedAt = completedAt?.let { Instant.parse(it) },
    direction = direction?.let { enumValueOf<Direction>(it) },
)

fun TransferResultDto.toDomain() = TransferResult(
    transfer = transfer.toDomain(),
    sourceAccountBalance = Money(sourceAccountBalanceMinor, transfer.currency),
)

fun TransferRequest.toDto() = TransferRequestDto(
    sourceAccountId = sourceAccountId,
    destinationAccountId = (destination as? TransferDestination.OwnAccount)?.accountId,
    destinationAccountNumber = (destination as? TransferDestination.AccountNumber)?.number,
    amountMinor = amount.minorUnits,
    category = category?.name,
    description = description,
)

fun RecipientDto.toDomain() = Recipient(
    displayName = displayName,
    accountNumberMasked = accountNumberMasked,
    currency = currency,
    status = enumValueOf<AccountStatus>(status),
    isOwnAccount = isOwnAccount,
)

fun BeneficiaryDto.toDomain() = Beneficiary(
    id = id,
    nickname = nickname,
    accountNumber = accountNumber,
    accountNumberMasked = accountNumberMasked,
    displayName = displayName,
    currency = currency,
    status = enumValueOf<AccountStatus>(status),
    createdAt = Instant.parse(createdAt),
)

fun SpendingSummaryDto.toDomain() = SpendingSummary(
    from = Instant.parse(from),
    to = Instant.parse(to),
    income = Money(incomeMinor, currency),
    spent = Money(spentMinor, currency),
    net = Money(netMinor, currency),
    transactionCount = transactionCount,
    byCategory = byCategory.map {
        CategorySpend(
            category = it.category.toEnumOr(Category.OTHER),
            spent = Money(it.spentMinor, currency),
            count = it.count,
        )
    },
)

fun ProfileDto.toDomain() = Profile(
    user = User(
        id = id,
        email = email,
        fullName = fullName,
        themePreference = themePreference.toEnumOr(ThemePreference.SYSTEM),
    ),
    dailyTransferLimit = Money(dailyTransferLimitMinor),
    memberSince = Instant.parse(createdAt),
)