package com.example.bankflow.data

import com.example.bankflow.core.network.HttpClientFactory
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.local.session.StoredSession
import com.example.bankflow.data.remote.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionStore(initial: StoredSession? = null) : SessionStore {
    private val state = MutableStateFlow(initial)
    override val session: Flow<StoredSession?> = state
    override suspend fun current(): StoredSession? = state.value
    override suspend fun save(session: StoredSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

fun testClient(store: SessionStore, handler: MockRequestHandler): HttpClient =
    HttpClientFactory.create(
        baseUrl = "http://localhost/api/v1/",
        sessionStore = store,
        enableLogging = false,
        engine = MockEngine(handler),
    )

fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

fun storedSession(access: String, refresh: String) = StoredSession(
    accessToken = access,
    refreshToken = refresh,
    user = UserDto("user-1", "alice@example.com", "Alice", "SYSTEM"),
)


fun errorJson(code: String, message: String = "message", details: String? = null): String =
    """{"error":{"code":"$code","message":"$message"${details?.let { ""","details":$it""" } ?: ""}}}"""

fun sessionJson(access: String, refresh: String): String =
    """{"accessToken":"$access","refreshToken":"$refresh","expiresInSeconds":900,
        "user":{"id":"user-1","email":"alice@example.com","fullName":"Alice","themePreference":"SYSTEM"}}"""

const val ACCOUNT_JSON =
    """{"id":"acc-1","name":"Everyday Checking","type":"CHECKING","accountNumber":"100000000001",
        "currency":"USD","balanceMinor":100000,"status":"ACTIVE","createdAt":"2026-10-29T10:00:00.000Z"}"""

const val TRANSACTION_JSON =
    """{"transferId":"t-1","accountId":"acc-1","direction":"IN","amountMinor":100000,"balanceAfterMinor":100000,
        "currency":"USD","status":"COMPLETED","kind":"DEPOSIT","category":"SOMETHING_NEW","description":"Welcome bonus",
        "createdAt":"2026-10-29T10:00:00.000Z",
        "counterparty":{"name":"Bank Reserve","accountNumberMasked":null,"isOwnAccount":false}}"""

const val DASHBOARD_JSON =
    """{"accounts":[$ACCOUNT_JSON],"totals":[{"currency":"USD","balanceMinor":100000}],"recentTransactions":[$TRANSACTION_JSON]}"""