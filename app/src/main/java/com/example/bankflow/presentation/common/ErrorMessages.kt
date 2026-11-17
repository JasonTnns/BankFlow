package com.example.bankflow.presentation.common

import androidx.compose.runtime.Composable
import com.example.bankflow.R
import com.example.bankflow.domain.common.AppError

fun AppError.userText(): UiText = when (this) {
    AppError.NoConnection -> UiText.Res(R.string.error_no_connection)
    AppError.Timeout -> UiText.Res(R.string.error_timeout)
    is AppError.Server -> UiText.Res(R.string.error_server)
    AppError.RateLimited -> UiText.Res(R.string.error_rate_limited)
    AppError.SessionExpired -> UiText.Res(R.string.error_session_expired)
    AppError.InvalidCredentials -> UiText.Res(R.string.error_invalid_credentials)
    AppError.EmailTaken -> UiText.Res(R.string.error_email_taken)
    is AppError.Validation -> message?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }
        ?: UiText.Res(R.string.error_validation)
    AppError.NotFound -> UiText.Res(R.string.error_not_found)
    AppError.BeneficiaryExists -> UiText.Res(R.string.error_beneficiary_exists)
    AppError.InsufficientFunds -> UiText.Res(R.string.error_insufficient_funds)
    is AppError.LimitExceeded -> remaining
        ?.let { UiText.Res(R.string.error_limit_exceeded_with_remaining, listOf(it)) }
        ?: UiText.Res(R.string.error_limit_exceeded)
    AppError.SameAccount -> UiText.Res(R.string.error_same_account)
    AppError.AccountInactive -> UiText.Res(R.string.error_account_inactive)
    AppError.CurrencyMismatch -> UiText.Res(R.string.error_currency_mismatch)
    AppError.IdempotencyConflict -> UiText.Res(R.string.error_idempotency_conflict)
    is AppError.Unknown -> UiText.Res(R.string.error_unknown)
}

@Composable
fun AppError.userMessage(): String = userText().resolve()
fun AppError?.fieldError(field: String): String? =
    (this as? AppError.Validation)?.fieldErrors?.get(field)?.firstOrNull()