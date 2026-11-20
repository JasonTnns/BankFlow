package com.example.bankflow.core.network

import com.example.bankflow.domain.common.FieldIssue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerIssueMapperTest {

    private fun params(name: String, value: Int): JsonObject = buildJsonObject { put(name, value) }

    private fun map(field: String, code: String, params: JsonObject? = null) = ServerIssueMapper.map(field, code, params)

    @Test
    fun `server field names are routed to the form's field names`() {
        assertEquals("amount", ServerIssueMapper.formField("amountMinor"))
        assertEquals("destination", ServerIssueMapper.formField("destinationAccountNumber"))
        assertEquals("destination", ServerIssueMapper.formField("destinationAccountId"))
        assertEquals("password", ServerIssueMapper.formField("password"))
        assertEquals("accountNumber", ServerIssueMapper.formField("accountNumber"))
    }

    @Test
    fun `sign-in and registration fields`() {
        assertEquals(FieldIssue.EmailRequired, map("email", "REQUIRED"))
        assertEquals(FieldIssue.EmailInvalid, map("email", "INVALID_EMAIL"))
        assertEquals(FieldIssue.PasswordRequired, map("password", "REQUIRED"))
        assertEquals(FieldIssue.PasswordNeedsLetterAndNumber, map("password", "PASSWORD_NEEDS_LETTER_AND_NUMBER"))
        assertEquals(FieldIssue.PasswordTooLong, map("password", "PASSWORD_TOO_LONG"))
        assertEquals(FieldIssue.NameRequired, map("fullName", "REQUIRED"))
    }

    @Test
    fun `limits carry the server's numbers`() {
        assertEquals(FieldIssue.PasswordTooShort(10), map("password", "TOO_SHORT", params("min", 10)))
        assertEquals(FieldIssue.NameTooLong(60), map("fullName", "TOO_LONG", params("max", 60)))
        assertEquals(FieldIssue.NicknameTooLong(30), map("nickname", "TOO_LONG", params("max", 30)))
        assertEquals(FieldIssue.DescriptionTooLong(200), map("description", "TOO_LONG", params("max", 200)))
    }

    @Test
    fun `a limit without its number falls back to the documented default`() {
        assertEquals(FieldIssue.PasswordTooShort(8), map("password", "TOO_SHORT"))
        assertEquals(FieldIssue.NameTooLong(100), map("fullName", "TOO_LONG"))
        assertEquals(FieldIssue.NicknameTooLong(40), map("nickname", "TOO_LONG"))
        assertEquals(FieldIssue.DescriptionTooLong(140), map("description", "TOO_LONG"))
    }

    @Test
    fun `a number that is not a number is ignored`() {
        val odd = buildJsonObject { put("min", "eight") }

        assertEquals(FieldIssue.PasswordTooShort(8), map("password", "TOO_SHORT", odd))
    }

    @Test
    fun `transfer amounts, by the server's field name`() {
        assertEquals(FieldIssue.AmountRequired, map("amountMinor", "REQUIRED"))
        assertEquals(FieldIssue.AmountNotPositive, map("amountMinor", "TOO_SMALL", params("min", 0)))
        assertEquals(FieldIssue.AmountTooLarge, map("amountMinor", "TOO_LARGE", params("max", 100_000_000)))
        assertEquals(FieldIssue.AmountInvalid, map("amountMinor", "NOT_INTEGER"))
        assertEquals(FieldIssue.AmountInvalid, map("amountMinor", "INVALID_TYPE"))
    }

    @Test
    fun `destinations`() {
        assertEquals(FieldIssue.DestinationRequired, map("destinationAccountId", "DESTINATION_CONFLICT"))
        assertEquals(FieldIssue.DestinationRequired, map("destinationAccountId", "INVALID_UUID"))
        assertEquals(FieldIssue.AccountNumberInvalid, map("destinationAccountNumber", "INVALID_FORMAT"))
    }

    @Test
    fun `the save-recipient form's account number`() {
        assertEquals(FieldIssue.AccountNumberInvalid, map("accountNumber", "INVALID_FORMAT"))
        assertEquals(FieldIssue.OwnAccount, map("accountNumber", "OWN_ACCOUNT"))
        assertEquals(FieldIssue.NicknameRequired, map("nickname", "REQUIRED"))
    }

    @Test
    fun `anything unknown is left to the server's own text`() {
        assertNull(map("email", "BRAND_NEW_CODE"))
        assertNull(map("password", "OWN_ACCOUNT"))
        assertNull(map("themePreference", "INVALID_OPTION"))
        assertNull(map("somethingElse", "REQUIRED"))
    }
}