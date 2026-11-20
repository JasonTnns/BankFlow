package com.example.bankflow.data

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationMappingTest {

    private val signedIn = storedSession("token", "refresh")

    private fun errorFor(status: HttpStatusCode, body: String): AppError = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { json(body, status) })
        (bank.getAccounts() as AppResult.Failure).error
    }

    private fun validation(details: String?) =
        errorFor(HttpStatusCode.BadRequest, errorJson("VALIDATION_ERROR", "Some fields are invalid", details)) as AppError.Validation

    @Test
    fun `known codes become our own issues, with the server's numbers`() {
        val error = validation(
            """{"fields":{"password":["Password must be at least 8 characters","Password must contain a letter and a number"]},
                "issues":{"password":[{"code":"TOO_SHORT","params":{"min":8}},{"code":"PASSWORD_NEEDS_LETTER_AND_NUMBER"}]}}""",
        )

        assertEquals(listOf(FieldIssue.PasswordTooShort(8), FieldIssue.PasswordNeedsLetterAndNumber), error.issues["password"])
        assertTrue("nothing is left over to show in English", error.fieldErrors.isEmpty())
    }

    @Test
    fun `server field names land on the form's fields`() {
        val error = validation(
            """{"fields":{"amountMinor":["x"],"destinationAccountNumber":["y"]},
                "issues":{"amountMinor":[{"code":"TOO_SMALL","params":{"min":0}}],
                          "destinationAccountNumber":[{"code":"INVALID_FORMAT"}]}}""",
        )

        assertEquals(listOf(FieldIssue.AmountNotPositive), error.issues["amount"])
        assertEquals(listOf(FieldIssue.AccountNumberInvalid), error.issues["destination"])
        assertNull(error.issues["amountMinor"])
    }

    @Test
    fun `an unknown code keeps the server's own words for that problem`() {
        val error = validation(
            """{"fields":{"email":["Server words"]},"issues":{"email":[{"code":"BRAND_NEW_CODE"}]}}""",
        )

        assertEquals(mapOf("email" to listOf("Server words")), error.fieldErrors)
        assertTrue(error.issues.isEmpty())
    }

    @Test
    fun `a server that sends no codes at all still gets its messages shown, on the form's field`() {
        val error = validation("""{"fields":{"amountMinor":["Too small","Not whole"]}}""")

        assertEquals(mapOf("amount" to listOf("Too small", "Not whole")), error.fieldErrors)
        assertTrue(error.issues.isEmpty())
    }

    @Test
    fun `known and unknown problems on one field are kept apart, each in its own form`() {
        val error = validation(
            """{"fields":{"password":["Too short","Something odd"]},
                "issues":{"password":[{"code":"TOO_SHORT","params":{"min":8}},{"code":"BRAND_NEW_CODE"}]}}""",
        )

        assertEquals(listOf(FieldIssue.PasswordTooShort(8)), error.issues["password"])
        assertEquals(listOf("Something odd"), error.fieldErrors["password"])
    }

    @Test
    fun `damaged codes fall back to the messages instead of failing`() {
        val error = validation("""{"fields":{"email":["Bad email"]},"issues":"not an object"}""")

        assertEquals(mapOf("email" to listOf("Bad email")), error.fieldErrors)
    }

    @Test
    fun `the server's generic English message is never shown, the localized fallback is`() {
        val error = validation("""{"fields":{},"issues":{}}""")

        assertNull(error.message)
        assertTrue(error.fields.isEmpty())
    }

    @Test
    fun `a validation error with no details at all is still a validation error`() {
        val error = validation(null)

        assertTrue(error.fields.isEmpty())
    }

    @Test
    fun `reaching the recipient limit is its own error`() {
        val error = errorFor(HttpStatusCode.UnprocessableEntity, errorJson("BENEFICIARY_LIMIT_REACHED"))

        assertEquals(AppError.BeneficiaryLimitReached, error)
    }
}