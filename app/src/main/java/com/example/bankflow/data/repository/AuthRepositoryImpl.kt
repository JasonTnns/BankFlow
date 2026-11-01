package com.example.bankflow.data.repository

import com.example.bankflow.core.network.clearCachedTokens
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.local.session.StoredSession
import com.example.bankflow.data.remote.AuthRemoteDataSource
import com.example.bankflow.data.remote.dto.UserDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.AuthSession
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val sessionStore: SessionStore,
    private val client: HttpClient,
    deviceName: String? = null,
) : AuthRepository {

    private val deviceName: String? = deviceName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }

    override val currentUser: Flow<User?> = sessionStore.session.map { it?.user?.toDomain() }

    override suspend fun register(email: String, password: String, fullName: String): AppResult<User> =
        remote.register(email, password, fullName, deviceName).persist()

    override suspend fun login(email: String, password: String): AppResult<User> =
        remote.login(email, password, deviceName).persist()

    override suspend fun logout() {
        try {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { remote.logout() }
        } finally {
            withContext(NonCancellable) {
                sessionStore.clear()
                client.clearCachedTokens()
            }
        }
    }

    private suspend fun AppResult<AuthSession>.persist(): AppResult<User> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success -> try {
            sessionStore.save(value.toStored())
            client.clearCachedTokens()
            AppResult.Success(value.user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown("Could not store the session securely"))
        }
    }

    private fun AuthSession.toStored() = StoredSession(
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        user = UserDto(
            id = user.id,
            email = user.email,
            fullName = user.fullName,
            themePreference = user.themePreference.name,
        ),
    )

    private companion object {
        const val LOGOUT_TIMEOUT_MS = 5_000L
    }
}