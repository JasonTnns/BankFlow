package com.example.bankflow.presentation.common

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ErrorMessagesTest {

    @Test
    fun `every error has a non-blank message`() {
        val errors = listOf(
            AppError.NoConnection, AppError.Timeout, AppError.Server(500), AppError.RateLimited,
            AppError.SessionExpired, AppError.InvalidCredentials, AppError.EmailTaken,
            AppError.Validation(), AppError.NotFound, AppError.InsufficientFunds,
            AppError.LimitExceeded(), AppError.SameAccount, AppError.AccountInactive,
            AppError.CurrencyMismatch, AppError.IdempotencyConflict, AppError.Unknown(),
            AppError.BeneficiaryExists,
        )
        errors.forEach { assertTrue("$it", it.userMessage(Locale.US).isNotBlank()) }
    }

    @Test
    fun `limit exceeded tells the user how much they can still send`() {
        val message = AppError.LimitExceeded(remaining = Money(50_000)).userMessage(Locale.US)
        assertTrue(message, message.contains("$500.00"))
    }

    @Test
    fun `limit exceeded without details still makes sense`() {
        assertEquals("This transfer is over your daily limit.", AppError.LimitExceeded().userMessage(Locale.US))
    }

    @Test
    fun `raw server messages are never leaked, only validation text we control`() {
        assertEquals("Something went wrong on our side. Please try again.", AppError.Server(502).userMessage())
        assertEquals("Please check the highlighted fields.", AppError.Validation().userMessage())
    }

    @Test
    fun `field errors can be read per field`() {
        val error: AppError = AppError.Validation(fieldErrors = mapOf("email" to listOf("Enter a valid email address")))

        assertEquals("Enter a valid email address", error.fieldError("email"))
        assertNull(error.fieldError("password"))
        assertNull((AppError.NoConnection as AppError).fieldError("email"))
        assertNull(null.fieldError("email"))
    }
}