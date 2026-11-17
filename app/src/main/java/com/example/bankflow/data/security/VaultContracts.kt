package com.example.bankflow.data.security

import com.example.bankflow.domain.security.VaultException
import com.example.bankflow.domain.security.VaultState
import kotlinx.coroutines.flow.StateFlow

interface VaultKeys {
    val state: StateFlow<VaultState>

    suspend fun ensureOpen()

    fun currentKey(): ByteArray?

    suspend fun reset()
}

enum class KeyMode { PLAIN, PROTECTED }

class StoredKey(val mode: KeyMode, val wrapped: ByteArray)

interface VaultStorage {
    suspend fun read(): StoredKey?
    suspend fun write(key: StoredKey)
    suspend fun clear()
}

interface KeyWrapper {
    fun wrap(key: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray

    fun destroy()
}

class VaultLockedException : VaultException("The vault is locked")
class AuthenticationRequiredException : Exception("Fresh authentication is required")
class KeyInvalidatedException : Exception("The protected key can no longer be used")