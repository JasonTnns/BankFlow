package com.example.bankflow.data

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AuthRefreshTest {

    private fun isRefresh(path: String) = path.endsWith("/auth/refresh")

    @Test
    fun `an expired access token is refreshed and the request is retried transparently`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val client = testClient(store) { request ->
            when {
                isRefresh(request.url.encodedPath) -> json(sessionJson("new", "new-refresh"))
                request.headers[HttpHeaders.Authorization] == "Bearer new" -> json(DASHBOARD_JSON)
                else -> json(errorJson("TOKEN_EXPIRED"), HttpStatusCode.Unauthorized)
            }
        }

        val result = BankRemoteDataSource(client).getDashboard()

        assertTrue("expected success but was $result", result is AppResult.Success)
        val saved = store.current()!!
        assertEquals("new", saved.accessToken)
        assertEquals("new-refresh", saved.refreshToken)
    }

    @Test
    fun `parallel requests with an expired token trigger exactly one refresh`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val refreshCalls = AtomicInteger()
        val client = testClient(store) { request ->
            when {
                isRefresh(request.url.encodedPath) -> {
                    refreshCalls.incrementAndGet()
                    json(sessionJson("new", "new-refresh"))
                }
                request.headers[HttpHeaders.Authorization] == "Bearer new" -> json(DASHBOARD_JSON)
                else -> json(errorJson("TOKEN_EXPIRED"), HttpStatusCode.Unauthorized)
            }
        }
        val bank = BankRemoteDataSource(client)

        val results = (1..5).map { async { bank.getDashboard() } }.awaitAll()

        assertTrue(results.all { it is AppResult.Success })
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `a dead refresh token ends the session and clears the store`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val client = testClient(store) { request ->
            when {
                isRefresh(request.url.encodedPath) ->
                    json(errorJson("INVALID_REFRESH_TOKEN"), HttpStatusCode.Unauthorized)
                else -> json(errorJson("TOKEN_EXPIRED"), HttpStatusCode.Unauthorized)
            }
        }

        val result = BankRemoteDataSource(client).getDashboard()

        assertEquals(AppError.SessionExpired, (result as AppResult.Failure).error)
        assertNull("the app must be signed out", store.current())
    }

    @Test
    fun `losing the network during a refresh does NOT sign the user out`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val client = testClient(store) { request ->
            if (isRefresh(request.url.encodedPath)) throw IOException("offline")
            json(errorJson("TOKEN_EXPIRED"), HttpStatusCode.Unauthorized)
        }

        val result = BankRemoteDataSource(client).getDashboard()

        assertEquals(AppError.NoConnection, (result as AppResult.Failure).error)
        assertNotNull("the session must survive", store.current())
    }

    @Test
    fun `a server error during a refresh is retryable and keeps the session`() = runBlocking {
        val store = FakeSessionStore(storedSession("old", "old-refresh"))
        val client = testClient(store) { request ->
            if (isRefresh(request.url.encodedPath)) {
                json(errorJson("INTERNAL_ERROR"), HttpStatusCode.InternalServerError)
            } else {
                json(errorJson("TOKEN_EXPIRED"), HttpStatusCode.Unauthorized)
            }
        }

        val result = BankRemoteDataSource(client).getDashboard()

        assertTrue((result as AppResult.Failure).error is AppError.Server)
        assertNotNull(store.current())
    }

    @Test
    fun `a signed-out user gets SessionExpired without any refresh attempt`() = runBlocking {
        val refreshCalls = AtomicInteger()
        val client = testClient(FakeSessionStore(null)) { request ->
            if (isRefresh(request.url.encodedPath)) refreshCalls.incrementAndGet()
            json(errorJson("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
        }

        val result = BankRemoteDataSource(client).getDashboard()

        assertEquals(AppError.SessionExpired, (result as AppResult.Failure).error)
        assertEquals(0, refreshCalls.get())
    }
}