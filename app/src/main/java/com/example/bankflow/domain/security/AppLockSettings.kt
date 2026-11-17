package com.example.bankflow.domain.security

import kotlinx.coroutines.flow.Flow

interface AppLockSettings {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}