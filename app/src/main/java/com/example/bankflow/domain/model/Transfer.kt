package com.example.bankflow.domain.model

import java.time.Instant

enum class TransferKind { TRANSFER, DEPOSIT }

enum class TransferStatus { PENDING, COMPLETED, FAILED, REVERSED }

enum class Direction { IN, OUT }

enum class Category { FOOD, TRANSPORT, SHOPPING, BILLS, ENTERTAINMENT, HEALTH, INCOME, TRANSFER, OTHER }

sealed interface TransferDestination {
    data class OwnAccount(val accountId: String) : TransferDestination

    data class AccountNumber(val number: String) : TransferDestination
}

data class TransferRequest(
    val idempotencyKey: String,
    val sourceAccountId: String,
    val destination: TransferDestination,
    val amount: Money,
    val category: Category? = null,
    val description: String? = null
)

data class Transfer(
    val id: String,
    val kind: TransferKind,
    val status: TransferStatus,
    val failureReason: String?,
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: Money,
    val category: Category,
    val description: String?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val direction: Direction? = null
)

data class TransferResult(
    val transfer: Transfer,
    val sourceAccountBalance: Money
)