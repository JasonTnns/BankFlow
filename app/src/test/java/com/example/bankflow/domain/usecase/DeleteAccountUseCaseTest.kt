package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.testutil.StubProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAccountUseCaseTest {

    private val repository = StubProfileRepository()
    private val useCase = DeleteAccountUseCase(repository)

    @Test
    fun `an empty password is rejected without a request`() = runTest {
        val result = useCase("")

        val error = (result as AppResult.Failure).error as AppError.Validation
        assertEquals(listOf(FieldIssue.PasswordRequired), error.issues["password"])
        assertTrue(repository.deleteCalls.isEmpty())
    }

    @Test
    fun `the password is sent exactly as typed, spaces included`() = runTest {
        useCase("  Pass w0rd  ")

        assertEquals(listOf("  Pass w0rd  "), repository.deleteCalls)
    }

    @Test
    fun `server errors pass through unchanged`() = runTest {
        repository.deleteError = AppError.PasswordIncorrect

        assertEquals(AppError.PasswordIncorrect, (useCase("wrong") as AppResult.Failure).error)
    }
}