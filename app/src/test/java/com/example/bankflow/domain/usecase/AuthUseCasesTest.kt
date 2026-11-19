package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.testutil.StubAccountRepository
import com.example.bankflow.testutil.StubAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AuthUseCasesTest {

    private val auth = StubAuthRepository()
    private val register = RegisterUseCase(auth)
    private val login = LoginUseCase(auth)

    private fun issues(result: AppResult<*>) = ((result as AppResult.Failure).error as AppError.Validation).issues

    @Test
    fun `an empty registration form reports every field, with codes and not sentences`() = runTest {
        val found = issues(register("", "", ""))

        assertEquals(listOf(FieldIssue.EmailRequired), found["email"])
        assertEquals(
            listOf(FieldIssue.PasswordTooShort(8), FieldIssue.PasswordNeedsLetterAndNumber),
            found["password"],
        )
        assertEquals(listOf(FieldIssue.NameRequired), found["fullName"])
        assertTrue(auth.registerCalls.isEmpty())
    }

    @Test
    fun `a malformed email, a password without a number, and an overlong password`() = runTest {
        assertEquals(listOf(FieldIssue.EmailInvalid), issues(register("not-an-email", "Passw0rd123", "Alice"))["email"])
        assertEquals(
            listOf(FieldIssue.PasswordNeedsLetterAndNumber),
            issues(register("a@b.com", "abcdefgh", "Alice"))["password"],
        )
        assertTrue(FieldIssue.PasswordTooLong in issues(register("a@b.com", "a1" + "x".repeat(80), "Alice"))["password"].orEmpty())
    }

    @Test
    fun `a name of 100 characters is fine and 101 is not`() = runTest {
        assertTrue(register("a@b.com", "Passw0rd123", "x".repeat(100)) is AppResult.Success)
        assertEquals(
            listOf(FieldIssue.NameTooLong(100)),
            issues(register("a@b.com", "Passw0rd123", "x".repeat(101)))["fullName"],
        )
    }

    @Test
    fun `login only needs something typed`() = runTest {
        val found = issues(login("", ""))

        assertEquals(listOf(FieldIssue.EmailRequired), found["email"])
        assertEquals(listOf(FieldIssue.PasswordRequired), found["password"])
        assertTrue(login("a@b.com", "x") is AppResult.Success)
    }

    @Test
    fun `contradictory history filters are rejected before any request`() = runTest {
        val accounts = StubAccountRepository()
        val useCase = GetTransactionsUseCase(accounts)

        val result = useCase(
            "acc-1",
            com.example.bankflow.domain.model.TransactionFilter(
                from = Instant.parse("2026-11-01T00:00:00Z"),
                to = Instant.parse("2026-10-01T00:00:00Z"),
                minAmountMinor = 500,
                maxAmountMinor = 100,
            ),
        )

        assertEquals(setOf("dateRange", "amountRange"), ((result as AppResult.Failure).error as AppError.Validation).fields)
        assertTrue(accounts.transactionCalls.isEmpty())
    }
}