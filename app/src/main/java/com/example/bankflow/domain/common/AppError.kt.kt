package com.example.bankflow.domain.common

import com.example.bankflow.domain.model.`Money.kt`

sealed interface `AppError.kt` {

    val isRetryable: Boolean get() = false

    data object NoConnection : `AppError.kt` { override val isRetryable = true }
    data object Timeout : `AppError.kt` { override val isRetryable = true }
    data class Server(val httpStatus: Int? = null) : `AppError.kt` { override val isRetryable = true }
    data object RateLimited : `AppError.kt` { override val isRetryable = true }

    data object SessionExpired : `AppError.kt`
    data object InvalidCredentials : `AppError.kt`
    data object EmailTaken : `AppError.kt`

    data class Validation(
        val message: String? = null,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : `AppError.kt`
    data object NotFound : `AppError.kt`

    data object InsufficientFunds : `AppError.kt`
    data class LimitExceeded(
        val limit: Money? = null,
        val usedToday: Money? = null,
        val remaining: Money? = null,
    ) : `AppError.kt`
    data object SameAccount : `AppError.kt`
    data object AccountInactive : `AppError.kt`
    data object CurrencyMismatch : `AppError.kt`
    data object IdempotencyConflict : `AppError.kt`

    data class Unknown(val message: String? = null) : `AppError.kt`
}