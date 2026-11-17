package com.example.bankflow.testutil

import com.example.bankflow.data.security.AuthenticationRequiredException
import com.example.bankflow.data.security.KeyInvalidatedException
import com.example.bankflow.data.security.KeyWrapper
import com.example.bankflow.data.security.StoredKey
import com.example.bankflow.data.security.VaultKeys
import com.example.bankflow.data.security.VaultLockedException
import com.example.bankflow.data.security.VaultStorage
import com.example.bankflow.domain.security.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class FakeVaultStorage : VaultStorage {
    var stored: StoredKey? = null
    var failOnRead = false
    var failOnWrite = false
    var writes = 0

    override suspend fun read(): StoredKey? {
        if (failOnRead) throw IOException("cannot read")
        return stored
    }

    override suspend fun write(key: StoredKey) {
        if (failOnWrite) throw IOException("cannot write")
        writes++
        stored = key
    }

    override suspend fun clear() {
        stored = null
    }
}

class FakeKeyWrapper(
    private val name: String,
    private val requiresAuth: Boolean = false,
) : KeyWrapper {
    var authenticated = false
    var invalidated = false
    var failWrap = false
    var destroyed = 0

    override fun wrap(key: ByteArray): ByteArray {
        if (failWrap) throw IllegalStateException("cannot create the key")
        return name.toByteArray() + key.reversedArray()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (invalidated) throw KeyInvalidatedException()
        if (requiresAuth && !authenticated) throw AuthenticationRequiredException()
        val prefix = name.toByteArray()
        require(wrapped.size > prefix.size && wrapped.copyOfRange(0, prefix.size).contentEquals(prefix)) {
            "damaged"
        }
        return wrapped.copyOfRange(prefix.size, wrapped.size).reversedArray()
    }

    override fun destroy() {
        destroyed++
    }
}

class FakeVaultKeys(initial: VaultState = VaultState.OPEN) : VaultKeys {
    val stateFlow = MutableStateFlow(initial)
    override val state: StateFlow<VaultState> = stateFlow

    var key: ByteArray? = if (initial == VaultState.OPEN) ByteArray(32) { it.toByte() } else null
    var ensureOpenCalls = 0
    var resets = 0

    override suspend fun ensureOpen() {
        ensureOpenCalls++
        when (stateFlow.value) {
            VaultState.LOCKED -> throw VaultLockedException()
            VaultState.EMPTY, VaultState.UNKNOWN -> {
                key = ByteArray(32) { (it + 1).toByte() }
                stateFlow.value = VaultState.OPEN
            }
            VaultState.OPEN -> Unit
        }
    }

    override fun currentKey(): ByteArray? = key

    override suspend fun reset() {
        resets++
        key = null
        stateFlow.value = VaultState.EMPTY
    }
}