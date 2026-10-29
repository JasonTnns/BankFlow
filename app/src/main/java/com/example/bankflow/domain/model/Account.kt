package com.example.bankflow.domain.model

import java.time.Instant

enum class AccountType { CHECKING, SAVINGS }

enum class AccountStatus { ACTIVE, FROZEN, CLOSED }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val accountNumber: String,
    val balance: `Money.kt`,
    val status: AccountStatus,
    val createdAt: Instant
) {
    val isActive: Boolean get() = status == AccountStatus.ACTIVE
    val maskedNumber: String get() = "••••" + accountNumber.takeLast(4)
}