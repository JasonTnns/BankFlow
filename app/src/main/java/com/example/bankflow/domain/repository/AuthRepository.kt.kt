package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.User
import kotlinx.coroutines.flow.Flow

interface `AuthRepository.kt` {

    val currentUser: Flow<User?>

    suspend fun register(email: String, password: String, fullName: String): AppResult<User>

    suspend fun login(email: String, password: String): AppResult<User>

    suspend fun logout()
}