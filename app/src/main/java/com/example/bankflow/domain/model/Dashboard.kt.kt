package com.example.bankflow.domain.model

data class `Dashboard.kt`(
    val accounts: List<Account>,
    val totals: List<Money>,
    val recentTransactions: List<Transaction>
)