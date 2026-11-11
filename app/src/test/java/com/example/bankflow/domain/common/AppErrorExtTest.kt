package com.example.bankflow.domain.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorExtTest {

    @Test
    fun `when the server may have acted, the outcome is unknown`() {
        listOf(
            AppError.NoConnection, AppError.Timeout, AppError.Server(502), AppError.RateLimited, AppError.Unknown("garbled reply"),
        ).forEach { assertTrue("$it", it.isOutcomeUnknown) }
    }

    @Test
    fun `when the server answered definitively, it is not`() {
        listOf(
            AppError.InsufficientFunds, AppError.LimitExceeded(), AppError.SameAccount, AppError.AccountInactive,
            AppError.CurrencyMismatch, AppError.IdempotencyConflict, AppError.NotFound, AppError.Validation(),
            AppError.SessionExpired, AppError.InvalidCredentials, AppError.EmailTaken,
        ).forEach { assertFalse("$it", it.isOutcomeUnknown) }
    }
}