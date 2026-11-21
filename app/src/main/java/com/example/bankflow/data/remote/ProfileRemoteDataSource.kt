package com.example.bankflow.data.remote

import com.example.bankflow.core.network.apiCall
import com.example.bankflow.core.network.apiCallUnit
import com.example.bankflow.data.remote.dto.DeleteAccountRequestDto
import com.example.bankflow.data.remote.dto.ProfileResponseDto
import com.example.bankflow.data.remote.dto.UpdateProfileRequestDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class ProfileRemoteDataSource(private val client: HttpClient) {

    suspend fun getProfile(): AppResult<Profile> =
        apiCall<ProfileResponseDto, Profile>(
            request = { client.get("me") },
            map = { it.profile.toDomain() },
        )

    suspend fun updateProfile(fullName: String?, themePreference: ThemePreference?): AppResult<Profile> =
        apiCall<ProfileResponseDto, Profile>(
            request = {
                client.patch("me") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateProfileRequestDto(fullName = fullName, themePreference = themePreference?.name))
                }
            },
            map = { it.profile.toDomain() },
        )

    suspend fun deleteAccount(password: String): AppResult<Unit> = apiCallUnit {
        client.delete("me") {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequestDto(password))
        }
    }
}