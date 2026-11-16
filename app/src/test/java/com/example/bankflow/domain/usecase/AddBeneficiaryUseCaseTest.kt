package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.testutil.StubBeneficiaryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBeneficiaryUseCaseTest {

    private val repository = StubBeneficiaryRepository()
    private val useCase = AddBeneficiaryUseCase(repository)

    private fun fieldsOf(result: AppResult<*>) =
        ((result as AppResult.Failure).error as AppError.Validation).fieldErrors.keys

    @Test
    fun `a valid recipient is saved with trimmed values`() = runTest {
        val result = useCase("  Mum  ", " 200000000001 ")

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("Mum" to "200000000001"), repository.added)
    }

    @Test
    fun `a blank or oversized name is rejected without a network call`() = runTest {
        assertEquals(setOf("nickname"), fieldsOf(useCase("   ", "200000000001")))
        assertEquals(setOf("nickname"), fieldsOf(useCase("x".repeat(41), "200000000001")))
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `account numbers must be exactly twelve digits`() = runTest {
        listOf("", "12345", "20000000000a", "2000000000012", "2000 0000 0001").forEach { number ->
            assertEquals("'$number'", setOf("accountNumber"), fieldsOf(useCase("Mum", number)))
        }
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `every problem is reported together`() = runTest {
        assertEquals(setOf("nickname", "accountNumber"), fieldsOf(useCase("", "abc")))
    }

    @Test
    fun `server errors pass through unchanged`() = runTest {
        repository.addResult = AppResult.Failure(AppError.BeneficiaryExists)

        assertEquals(AppError.BeneficiaryExists, (useCase("Mum", "200000000001") as AppResult.Failure).error)
    }
}