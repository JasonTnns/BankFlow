package com.example.bankflow.domain.model

import java.time.Instant

data class Beneficiary(
    val id: String,
    val nickname: String,
    val accountNumber: String,
    val accountNumberMasked: String,
    val displayName: String,
    val currency: String,
    val status: AccountStatus,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = status == AccountStatus.ACTIVE
}