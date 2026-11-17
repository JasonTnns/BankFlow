package com.example.bankflow.domain.security

import kotlinx.coroutines.flow.StateFlow

enum class VaultState {
    UNKNOWN,

    EMPTY,

    LOCKED,

    OPEN,
}

enum class UnlockResult {
    SUCCESS,

    AUTHENTICATION_REQUIRED,

    KEY_INVALIDATED,
}

open class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)


interface SessionVault {
    val state: StateFlow<VaultState>


    suspend fun unlock(): UnlockResult

    suspend fun protect()

    suspend fun unprotect()
}