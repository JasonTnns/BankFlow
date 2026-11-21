package com.example.bankflow.presentation.common

import com.example.bankflow.R
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.usecase.AddBeneficiaryUseCase
import com.example.bankflow.presentation.transfer.transferFailureText
import com.example.bankflow.presentation.transfer.unknownOutcomeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    private val everyError = listOf(
        AppError.PasswordIncorrect,
        AppError.BeneficiaryLimitReached,
        AppError.NoConnection, AppError.Timeout, AppError.Server(500), AppError.RateLimited,
        AppError.SessionExpired, AppError.InvalidCredentials, AppError.EmailTaken,
        AppError.Validation(), AppError.NotFound, AppError.BeneficiaryExists, AppError.InsufficientFunds,
        AppError.LimitExceeded(), AppError.SameAccount, AppError.AccountInactive,
        AppError.CurrencyMismatch, AppError.IdempotencyConflict, AppError.Unknown(),
    )

    @Test
    fun `every error has its own message`() {
        everyError.forEach { error ->
            val text = error.userText()
            assertTrue("$error", text is UiText.Res && text.id != 0)
        }
    }

    @Test
    fun `limit exceeded tells the user how much they can still send`() {
        val text = AppError.LimitExceeded(remaining = Money(50_000)).userText()

        assertEquals(UiText.Res(R.string.error_limit_exceeded_with_remaining, listOf(Money(50_000))), text)
    }

    @Test
    fun `limit exceeded without details still makes sense`() {
        assertEquals(UiText.Res(R.string.error_limit_exceeded), AppError.LimitExceeded().userText())
    }

    @Test
    fun `raw server messages are never leaked, only validation text our own rules produce`() {
        assertEquals(UiText.Res(R.string.error_server), AppError.Server(502).userText())
        assertEquals(UiText.Res(R.string.error_unknown), AppError.Unknown("stack trace here").userText())
        assertEquals(UiText.Res(R.string.error_validation), AppError.Validation().userText())
        assertEquals(UiText.Res(R.string.error_validation), AppError.Validation(message = "  ").userText())
        assertEquals(UiText.Plain("Password is too short"), AppError.Validation(message = "Password is too short").userText())
    }

    @Test
    fun `field errors can be read per field`() {
        val error: AppError = AppError.Validation(fieldErrors = mapOf("email" to listOf("Enter a valid email address")))

        assertEquals("Enter a valid email address", error.fieldError("email"))
        assertNull(error.fieldError("password"))
        assertNull((AppError.NoConnection as AppError).fieldError("email"))
        assertNull(null.fieldError("email"))
    }

    @Test
    fun `a rejected transfer is explained, and malformed details get their own message`() {
        assertEquals(UiText.Res(R.string.error_insufficient_funds), AppError.InsufficientFunds.transferFailureText())
        assertEquals(UiText.Res(R.string.transfer_failure_validation), AppError.Validation().transferFailureText())
    }

    @Test
    fun `an unknown outcome says it could not be confirmed and that retrying is safe`() {
        fun lead(error: AppError) = (error.unknownOutcomeText() as UiText.Res).args.single()

        assertEquals(UiText.Res(R.string.unknown_outcome_lost_connection), lead(AppError.NoConnection))
        assertEquals(UiText.Res(R.string.unknown_outcome_slow), lead(AppError.Timeout))
        assertEquals(UiText.Res(R.string.unknown_outcome_generic), lead(AppError.Server(502)))
        assertEquals(R.string.unknown_outcome_format, (AppError.Timeout.unknownOutcomeText() as UiText.Res).id)
    }

    @Test
    fun `a field's message is our own rule first, then the server's words`() {
        val both: AppError = AppError.Validation(
            fieldErrors = mapOf("email" to listOf("Server says no"), "name" to listOf("Server name")),
            issues = mapOf("email" to listOf(FieldIssue.EmailInvalid)),
        )

        assertEquals(UiText.Res(R.string.issue_email_invalid), both.fieldText("email"))
        assertEquals(UiText.Plain("Server name"), both.fieldText("name"))
        assertNull(both.fieldText("password"))
        assertNull((AppError.NoConnection as AppError).fieldText("email"))
        assertNull(null.fieldText("email"))
    }

    @Test
    fun `every field issue has its own message`() {
        val everyIssue = listOf(
            FieldIssue.OwnAccount,
            FieldIssue.EmailRequired, FieldIssue.EmailInvalid, FieldIssue.PasswordRequired,
            FieldIssue.PasswordTooShort(8), FieldIssue.PasswordNeedsLetterAndNumber, FieldIssue.PasswordTooLong,
            FieldIssue.NameRequired, FieldIssue.NameTooLong(100), FieldIssue.AmountRequired, FieldIssue.AmountInvalid,
            FieldIssue.AmountNotPositive, FieldIssue.AmountTooLarge, FieldIssue.AmountMaxBelowMin,
            FieldIssue.InsufficientFunds(Money(100)), FieldIssue.AccountNumberInvalid, FieldIssue.DestinationRequired,
            FieldIssue.DestinationNotFound, FieldIssue.DestinationIsSource, FieldIssue.DescriptionTooLong(140),
            FieldIssue.NicknameRequired, FieldIssue.NicknameTooLong(40), FieldIssue.Invalid,
        )

        val ids = everyIssue.map { (it.toText() as UiText.Res).id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the recipient limit names the number`() {
        assertEquals(
            UiText.Res(R.string.error_beneficiary_limit_reached, listOf(AddBeneficiaryUseCase.MAX_BENEFICIARIES)),
            AppError.BeneficiaryLimitReached.userText(),
        )
    }
}