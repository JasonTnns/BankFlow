package com.example.bankflow.data

import com.example.bankflow.data.remote.AuthRemoteDataSource
import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.data.repository.AuthRepositoryImpl
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AuthRepositoryTest {

    private fun repository(store: FakeSessionStore, client: io.ktor.client.HttpClient) =
        AuthRepositoryImpl(AuthRemoteDataSource(client), store, client, deviceName = "Test phone")

    @Test
    fun `login stores the session and publishes the user`() = runBlocking {
        val store = FakeSessionStore()
        val repository = repository(store, testClient(store) { json(sessionJson("a1", "r1")) })

        val result = repository.login("alice@example.com", "Passw0rd123")

        assertEquals("Alice", (result as AppResult.Success).value.fullName)
        assertEquals("a1", store.current()?.accessToken)
        assertEquals("r1", store.current()?.refreshToken)
        assertEquals("alice@example.com", repository.currentUser.first()?.email)
    }

    @Test
    fun `register stores the session too`() = runBlocking {
        val store = FakeSessionStore()
        val repository = repository(store, testClient(store) { json(sessionJson("a1", "r1"), HttpStatusCode.Created) })

        val result = repository.register("alice@example.com", "Passw0rd123", "Alice")

        assertEquals("alice@example.com", (result as AppResult.Success).value.email)
        assertEquals("a1", store.current()?.accessToken)
    }

    @Test
    fun `a failed login stores nothing`() = runBlocking {
        val store = FakeSessionStore()
        val repository = repository(store, testClient(store) {
            json(errorJson("INVALID_CREDENTIALS"), HttpStatusCode.Unauthorized)
        })

        val result = repository.login("alice@example.com", "wrong")

        assertEquals(AppError.InvalidCredentials, (result as AppResult.Failure).error)
        assertNull(store.current())
        assertNull(repository.currentUser.first())
    }

    @Test
    fun `a failed login never refreshes or replaces an existing session`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val refreshCalls = AtomicInteger()
        val repository = repository(store, testClient(store) { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh")) {
                refreshCalls.incrementAndGet()
                json(sessionJson("new", "new-refresh"))
            } else {
                json(errorJson("INVALID_CREDENTIALS"), HttpStatusCode.Unauthorized)
            }
        })

        val result = repository.login("alice@example.com", "wrong")

        assertEquals(AppError.InvalidCredentials, (result as AppResult.Failure).error)
        assertEquals(0, refreshCalls.get())
        assertEquals("old", store.current()?.accessToken)
    }

    @Test
    fun `logout tells the server with the current token and clears the session`() = runBlocking {
        val store = FakeSessionStore()
        var logoutAuthorization: String? = null
        val repository = repository(store, testClient(store) { request ->
            if (request.url.encodedPath.endsWith("/auth/logout")) {
                logoutAuthorization = request.headers[HttpHeaders.Authorization]
                respond("", HttpStatusCode.NoContent)
            } else {
                json(sessionJson("a1", "r1"))
            }
        })
        repository.login("alice@example.com", "Passw0rd123")

        repository.logout()

        assertEquals("Bearer a1", logoutAuthorization)
        assertNull(store.current())
        assertNull(repository.currentUser.first())
    }

    @Test
    fun `logout clears the session even when the server cannot be reached`() = runBlocking {
        val store = FakeSessionStore()
        val repository = repository(store, testClient(store) { request ->
            if (request.url.encodedPath.endsWith("/auth/logout")) throw IOException("offline")
            json(sessionJson("a1", "r1"))
        })
        repository.login("alice@example.com", "Passw0rd123")

        repository.logout()

        assertNull("the user must never be stuck signed in", store.current())
        assertNull(repository.currentUser.first())
    }

    @Test
    fun `a second user on the same install never sends the first user's token`() = runBlocking {
        val store = FakeSessionStore()
        val logins = AtomicInteger()
        var lastDashboardAuthorization: String? = null
        val client = testClient(store) { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/login") ->
                    if (logins.incrementAndGet() == 1) json(sessionJson("a1", "r1")) else json(sessionJson("b1", "r2"))
                path.endsWith("/auth/logout") -> respond("", HttpStatusCode.NoContent)
                else -> {
                    lastDashboardAuthorization = request.headers[HttpHeaders.Authorization]
                    json(DASHBOARD_JSON)
                }
            }
        }
        val repository = repository(store, client)
        val bank = BankRemoteDataSource(client)

        repository.login("alice@example.com", "Passw0rd123")
        bank.getDashboard()
        assertEquals("Bearer a1", lastDashboardAuthorization)

        repository.logout()
        repository.login("bob@example.com", "Passw0rd123")
        bank.getDashboard()

        assertEquals("Bearer b1", lastDashboardAuthorization)
    }
}