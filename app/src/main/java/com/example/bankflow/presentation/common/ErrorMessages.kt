package com.example.bankflow.presentation.common

import com.example.bankflow.domain.common.AppError
import java.util.Locale

fun AppError.userMessage(locale: Locale = Locale.getDefault()): String = when (this) {
    AppError.NoConnection -> "No internet connection. Check your network and try again."
    AppError.Timeout -> "The request took too long. Please try again."
    is AppError.Server -> "Something went wrong on our side. Please try again."
    AppError.RateLimited -> "Too many attempts. Please wait a moment and try again."
    AppError.SessionExpired -> "Your session has expired. Please sign in again."
    AppError.InvalidCredentials -> "Incorrect email or password."
    AppError.EmailTaken -> "An account with this email already exists."
    is AppError.Validation -> message?.takeIf { it.isNotBlank() } ?: "Please check the highlighted fields."
    AppError.NotFound -> "We couldn't find that."
    AppError.InsufficientFunds -> "You don't have enough funds for this transfer."
    is AppError.LimitExceeded -> remaining
        ?.let { "This is over your daily limit. You can still send up to ${it.format(locale)} today." }
        ?: "This transfer is over your daily limit."
    AppError.SameAccount -> "Choose two different accounts."
    AppError.AccountInactive -> "One of the accounts can't be used right now."
    AppError.CurrencyMismatch -> "Both accounts must use the same currency."
    AppError.IdempotencyConflict -> "This transfer was already submitted with different details. Start a new one."
    is AppError.Unknown -> "Something unexpected happened. Please try again."
}

fun AppError?.fieldError(field: String): String? =
    (this as? AppError.Validation)?.fieldErrors?.get(field)?.firstOrNull()