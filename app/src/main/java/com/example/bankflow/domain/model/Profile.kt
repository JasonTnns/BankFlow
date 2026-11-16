package com.example.bankflow.domain.model

import java.time.Instant

data class Profile(
    val user: User,
    val dailyTransferLimit: Money,
    val memberSince: Instant,
)