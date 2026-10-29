package com.example.bankflow.domain.common

import com.example.bankflow.domain.model.Money

sealed interface AppError {

    val isRetryable: Boolean get() = false

    data object NoConnection : AppError { override val isRetryable = true }
    data object Timeout : AppError { override val isRetryable = true }
    data class Server(val httpStatus: Int? = null) : AppError { override val isRetryable = true }
    data object RateLimited : AppError { override val isRetryable = true }


    data object SessionExpired : AppError
    data object InvalidCredentials : AppError
    data object EmailTaken : AppError

    data class Validation(
        val message: String? = null,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : AppError
    data object NotFound : AppError

    data object InsufficientFunds : AppError
    data class LimitExceeded(
        val limit: Money? = null,
        val usedToday: Money? = null,
        val remaining: Money? = null,
    ) : AppError
    data object SameAccount : AppError
    data object AccountInactive : AppError
    data object CurrencyMismatch : AppError
    data object IdempotencyConflict : AppError

    data class Unknown(val message: String? = null) : AppError
}