package com.example.bankflow.domain.model

import java.time.Instant

data class Counterparty(
    val name: String,
    val accountNumberMasked: String?,
    val isOwnAccount: Boolean
)

data class Transaction(
    val transferId: String,
    val accountId: String,
    val direction: Direction,
    val amount: `Money.kt`,
    val balanceAfter: `Money.kt`,
    val status: TransferStatus,
    val kind: TransferKind,
    val category: Category,
    val description: String?,
    val createdAt: Instant,
    val counterparty: Counterparty
) {
    val key: String get() = "$transferId:$accountId"
}

data class TransactionFilter(
    val direction: Direction? = null,
    val category: Category? = null,
    val search: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null
) {
    val isActive: Boolean
        get() = this != TransactionFilter()
}

data class TransactionPage(
    val items: List<Transaction>,
    val nextCursor: String?
)