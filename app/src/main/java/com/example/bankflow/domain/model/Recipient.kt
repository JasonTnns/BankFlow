package com.example.bankflow.domain.model

data class Recipient(
    val displayName: String,
    val accountNumberMasked: String,
    val currency: String,
    val status: AccountStatus,
    val isOwnAccount: Boolean,
)