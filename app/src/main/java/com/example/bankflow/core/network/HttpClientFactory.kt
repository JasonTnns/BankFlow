package com.example.bankflow.core.network

import android.util.Log
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.local.session.StoredSession
import com.example.bankflow.data.remote.dto.RefreshRequestDto
import com.example.bankflow.data.remote.dto.SessionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

object HttpClientFactory {

    private const val BEARER_PREFIX = "Bearer "

    fun create(
        baseUrl: String,
        sessionStore: SessionStore,
        enableLogging: Boolean = false,
        engine: HttpClientEngine = OkHttp.create(),
    ): HttpClient = HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) { json(NetworkJson) }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
            requestTimeoutMillis = 25_000
        }

        install(DefaultRequest) {
            url(baseUrl)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        if (enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("BankFlowHttp", message)
                    }
                }
            }
        }

        install(Auth) {
            bearer {
                loadTokens {
                    sessionStore.current()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }

                sendWithoutRequest { request ->
                    val path = request.url.encodedPath
                    !(path.endsWith("auth/login") || path.endsWith("auth/register") || path.endsWith("auth/refresh"))
                }

                refreshTokens {
                    val requestPath = response.request.url.encodedPath
                    if (requestPath.endsWith("auth/login") || requestPath.endsWith("auth/register")) {
                        return@refreshTokens null
                    }

                    val stored = sessionStore.current() ?: return@refreshTokens null

                    val usedToken = response.request.headers[HttpHeaders.Authorization]?.removePrefix(BEARER_PREFIX)
                    if (usedToken != null && usedToken != stored.accessToken) {
                        return@refreshTokens BearerTokens(stored.accessToken, stored.refreshToken)
                    }

                    client.refreshSession(sessionStore, stored.refreshToken) {
                        markAsRefreshTokenRequest()
                    }
                }
            }
        }
    }

    private suspend fun HttpClient.refreshSession(
        sessionStore: SessionStore,
        refreshToken: String,
        markAsRefreshRequest: HttpRequestBuilder.() -> Unit,
    ): BearerTokens? {
        val response: HttpResponse = post("auth/refresh") {
            markAsRefreshRequest()
            contentType(ContentType.Application.Json)
            setBody(RefreshRequestDto(refreshToken = refreshToken))
        }

        return when {
            response.status.isSuccess() -> {
                val session = response.body<SessionDto>()
                sessionStore.save(StoredSession(session.accessToken, session.refreshToken, session.user))
                BearerTokens(session.accessToken, session.refreshToken)
            }
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest -> {
                sessionStore.clear()
                null
            }
            //
            else -> throw RefreshFailedException(ApiErrorMapper.fromResponse(response))
        }
    }
}

fun HttpClient.clearCachedTokens() {
    authProvider<BearerAuthProvider>()?.clearToken()
}