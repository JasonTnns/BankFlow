package com.example.bankflow.data.local.session

import com.example.bankflow.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

interface SessionStore {
    val session: Flow<StoredSession?>

    suspend fun current(): StoredSession?
    suspend fun save(session: StoredSession)

    suspend fun updateUser(transform: (UserDto) -> UserDto)

    suspend fun clear()
}