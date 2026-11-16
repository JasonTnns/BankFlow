package com.example.bankflow.data

import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.remote.ProfileRemoteDataSource
import com.example.bankflow.data.remote.dto.UserDto
import com.example.bankflow.data.repository.ProfileRepositoryImpl
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {

    private val profileJson = """
        {"profile":{"id":"user-1","email":"alice@example.com","fullName":"Alice Renamed","themePreference":"DARK",
         "dailyTransferLimitMinor":500000,"createdAt":"2026-01-15T10:00:00.000Z"}}
    """.trimIndent()

    private fun repository(store: SessionStore, handler: MockRequestHandler) =
        ProfileRepositoryImpl(ProfileRemoteDataSource(testClient(store, handler)), store)

    @Test
    fun `reading the profile brings the stored session in line`() = runBlocking {
        val store = FakeSessionStore(storedSession("access", "refresh"))

        repository(store) { json(profileJson) }.getProfile()

        val user = store.current()!!.user
        assertEquals("Alice Renamed", user.fullName)
        assertEquals("DARK", user.themePreference)
    }

    @Test
    fun `an update does too, and the tokens are left alone`() = runBlocking {
        val store = FakeSessionStore(storedSession("access", "refresh"))

        val result = repository(store) { json(profileJson) }.updateProfile("Alice Renamed", ThemePreference.DARK)

        assertTrue(result is AppResult.Success)
        val saved = store.current()!!
        assertEquals("Alice Renamed", saved.user.fullName)
        assertEquals("DARK", saved.user.themePreference)
        assertEquals("access", saved.accessToken)
        assertEquals("refresh", saved.refreshToken)
    }

    @Test
    fun `a failed update leaves the session as it was`() = runBlocking {
        val store = FakeSessionStore(storedSession("access", "refresh"))

        val result = repository(store) { json(errorJson("INTERNAL_ERROR"), HttpStatusCode.InternalServerError) }
            .updateProfile("Alice Renamed", null)

        assertTrue(result is AppResult.Failure)
        assertEquals("Alice", store.current()!!.user.fullName)
        assertEquals("SYSTEM", store.current()!!.user.themePreference)
    }

    @Test
    fun `an answer that arrives after logout cannot bring a session back`() = runBlocking {
        val store = FakeSessionStore(null)

        val result = repository(store) { json(profileJson) }.getProfile()

        assertTrue(result is AppResult.Success)
        assertNull(store.current())
    }

    @Test
    fun `a problem writing the local copy does not fail the call`() = runBlocking {
        val failing = object : SessionStore by FakeSessionStore(storedSession("access", "refresh")) {
            override suspend fun updateUser(transform: (UserDto) -> UserDto) {
                throw IllegalStateException("storage unavailable")
            }
        }

        val result = repository(failing) { json(profileJson) }.updateProfile("Alice Renamed", null)

        assertTrue("the server change succeeded", result is AppResult.Success)
    }
}