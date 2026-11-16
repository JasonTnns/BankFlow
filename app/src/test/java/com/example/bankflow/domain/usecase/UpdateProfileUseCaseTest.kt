package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.testutil.StubProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProfileUseCaseTest {

    private val repository = StubProfileRepository()
    private val useCase = UpdateProfileUseCase(repository)

    private fun fieldsOf(result: AppResult<*>) =
        ((result as AppResult.Failure).error as AppError.Validation).fieldErrors.keys

    @Test
    fun `asking for no change is an error, without a request`() = runTest {
        val result = useCase()

        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `the name is trimmed before it is sent`() = runTest {
        useCase(fullName = "  Alice Renamed  ")

        assertEquals(listOf("Alice Renamed" to null), repository.updates)
    }

    @Test
    fun `a blank name is rejected without a request`() = runTest {
        assertEquals(setOf("fullName"), fieldsOf(useCase(fullName = "   ")))
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a name of 100 characters is fine, 101 is not`() = runTest {
        assertTrue(useCase(fullName = "x".repeat(100)) is AppResult.Success)
        assertEquals(setOf("fullName"), fieldsOf(useCase(fullName = "x".repeat(101))))
    }

    @Test
    fun `a theme alone leaves the name out of the request`() = runTest {
        useCase(themePreference = ThemePreference.DARK)

        assertEquals(listOf(null to ThemePreference.DARK), repository.updates)
    }

    @Test
    fun `both can be changed together`() = runTest {
        useCase(fullName = "Alice 2", themePreference = ThemePreference.LIGHT)

        assertEquals(listOf("Alice 2" to ThemePreference.LIGHT), repository.updates)
    }

    @Test
    fun `server errors pass through unchanged`() = runTest {
        repository.updateError = AppError.NoConnection

        assertEquals(AppError.NoConnection, (useCase(fullName = "Alice 2") as AppResult.Failure).error)
    }
}