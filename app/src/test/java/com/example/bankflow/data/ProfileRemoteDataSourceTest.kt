package com.example.bankflow.data

import com.example.bankflow.data.remote.ProfileRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.ThemePreference
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import io.ktor.http.HttpStatusCode

class ProfileRemoteDataSourceTest {

    private val signedIn = storedSession("token", "refresh")

    private fun source(handler: MockRequestHandler) =
        ProfileRemoteDataSource(testClient(FakeSessionStore(signedIn), handler))

    private fun profileJson(theme: String = "DARK") = """
        {"profile":{"id":"user-1","email":"alice@example.com","fullName":"Alice Renamed","themePreference":"$theme",
         "dailyTransferLimitMinor":500000,"createdAt":"2026-01-15T10:00:00.000Z"}}
    """.trimIndent()

    @Test
    fun `the profile is read from me and mapped`() = runBlocking {
        var path: String? = null
        val result = source { request ->
            path = request.url.encodedPath
            json(profileJson())
        }.getProfile()

        val profile = (result as AppResult.Success).value
        assertTrue(path!!.endsWith("/me"))
        assertEquals("Alice Renamed", profile.user.fullName)
        assertEquals("alice@example.com", profile.user.email)
        assertEquals(ThemePreference.DARK, profile.user.themePreference)
        assertEquals(Money(500_000), profile.dailyTransferLimit)
    }

    @Test
    fun `an unknown theme falls back to the system theme`() = runBlocking {
        val result = source { json(profileJson(theme = "NEON")) }.getProfile()

        assertEquals(ThemePreference.SYSTEM, (result as AppResult.Success).value.user.themePreference)
    }

    @Test
    fun `an update is a PATCH that sends only what changed`() = runBlocking {
        var method: HttpMethod? = null
        var body: String? = null
        val source = source { request ->
            method = request.method
            body = (request.body as TextContent).text
            json(profileJson())
        }

        source.updateProfile(fullName = "Alice Renamed", themePreference = null)

        assertEquals(HttpMethod.Patch, method)
        assertTrue(body!!.contains("\"fullName\":\"Alice Renamed\""))
        assertFalse("an unchanged field must be left out, not sent as null", body!!.contains("themePreference"))
        assertFalse(body!!.contains("null"))
    }

    @Test
    fun `a theme-only update leaves the name out`() = runBlocking {
        var body: String? = null
        source { request ->
            body = (request.body as TextContent).text
            json(profileJson())
        }.updateProfile(fullName = null, themePreference = ThemePreference.DARK)

        assertTrue(body!!.contains("\"themePreference\":\"DARK\""))
        assertFalse(body!!.contains("fullName"))
    }

    @Test
    fun `a rejected name maps to per-field messages`() = runBlocking {
        val result = source {
            json(
                errorJson("VALIDATION_ERROR", "Some fields are invalid", """{"fields":{"fullName":["Full name is too long"]}}"""),
                HttpStatusCode.BadRequest,
            )
        }.updateProfile(fullName = "x", themePreference = null)

        val error = (result as AppResult.Failure).error as AppError.Validation
        assertEquals(mapOf("fullName" to listOf("Full name is too long")), error.fieldErrors)
    }

    @Test
    fun `a network failure becomes NoConnection`() = runBlocking {
        val result = source { throw IOException("offline") }.getProfile()

        assertEquals(AppError.NoConnection, (result as AppResult.Failure).error)
    }
}