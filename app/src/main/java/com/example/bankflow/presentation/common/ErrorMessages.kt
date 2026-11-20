package com.example.bankflow.presentation.common

import androidx.compose.runtime.Composable
import com.example.bankflow.R
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.usecase.AddBeneficiaryUseCase

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
    AppError.BeneficiaryLimitReached ->
        UiText.Res(R.string.error_beneficiary_limit_reached, listOf(AddBeneficiaryUseCase.MAX_BENEFICIARIES))
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

fun FieldIssue.toText(): UiText = when (this) {
    FieldIssue.OwnAccount -> UiText.Res(R.string.issue_own_account)
    FieldIssue.EmailRequired -> UiText.Res(R.string.issue_email_required)
    FieldIssue.EmailInvalid -> UiText.Res(R.string.issue_email_invalid)
    FieldIssue.PasswordRequired -> UiText.Res(R.string.issue_password_required)
    is FieldIssue.PasswordTooShort -> UiText.Res(R.string.issue_password_too_short, listOf(min))
    FieldIssue.PasswordNeedsLetterAndNumber -> UiText.Res(R.string.issue_password_needs_letter_number)
    FieldIssue.PasswordTooLong -> UiText.Res(R.string.issue_password_too_long)
    FieldIssue.NameRequired -> UiText.Res(R.string.issue_name_required)
    is FieldIssue.NameTooLong -> UiText.Res(R.string.issue_name_too_long, listOf(max))
    FieldIssue.AmountRequired -> UiText.Res(R.string.issue_amount_required)
    FieldIssue.AmountInvalid -> UiText.Res(R.string.issue_amount_invalid)
    FieldIssue.AmountNotPositive -> UiText.Res(R.string.issue_amount_not_positive)
    FieldIssue.AmountTooLarge -> UiText.Res(R.string.issue_amount_too_large)
    FieldIssue.AmountMaxBelowMin -> UiText.Res(R.string.issue_amount_max_below_min)
    is FieldIssue.InsufficientFunds -> UiText.Res(R.string.issue_insufficient_funds, listOf(available))
    FieldIssue.AccountNumberInvalid -> UiText.Res(R.string.issue_account_number_invalid)
    FieldIssue.DestinationRequired -> UiText.Res(R.string.issue_destination_required)
    FieldIssue.DestinationNotFound -> UiText.Res(R.string.issue_destination_not_found)
    FieldIssue.DestinationIsSource -> UiText.Res(R.string.issue_destination_is_source)
    is FieldIssue.DescriptionTooLong -> UiText.Res(R.string.issue_description_too_long, listOf(max))
    FieldIssue.NicknameRequired -> UiText.Res(R.string.issue_nickname_required)
    is FieldIssue.NicknameTooLong -> UiText.Res(R.string.issue_nickname_too_long, listOf(max))
    FieldIssue.Invalid -> UiText.Res(R.string.issue_invalid)
}

fun AppError?.fieldText(field: String): UiText? {
    val validation = this as? AppError.Validation ?: return null
    validation.issues[field]?.firstOrNull()?.let { return it.toText() }
    return validation.fieldErrors[field]?.firstOrNull()?.let { UiText.Plain(it) }
}