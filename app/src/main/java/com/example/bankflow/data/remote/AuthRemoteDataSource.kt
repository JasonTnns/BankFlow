package com.example.bankflow.data.remote

import com.example.bankflow.core.network.apiCall
import com.example.bankflow.core.network.apiCallUnit
import com.example.bankflow.data.remote.dto.LoginRequestDto
import com.example.bankflow.data.remote.dto.RegisterRequestDto
import com.example.bankflow.data.remote.dto.SessionDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class AuthRemoteDataSource(private val client: HttpClient) {

    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        deviceName: String? = null,
    ): AppResult<AuthSession> = apiCall<SessionDto, AuthSession>(
        request = {
            client.post("auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequestDto(email, password, fullName, deviceName))
            }
        },
        map = { it.toDomain() },
    )

    suspend fun login(
        email: String,
        password: String,
        deviceName: String? = null,
    ): AppResult<AuthSession> = apiCall<SessionDto, AuthSession>(
        request = {
            client.post("auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequestDto(email, password, deviceName))
            }
        },
        map = { it.toDomain() },
    )

    suspend fun logout(): AppResult<Unit> = apiCallUnit { client.post("auth/logout") }
}