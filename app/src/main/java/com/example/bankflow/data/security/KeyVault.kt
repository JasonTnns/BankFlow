package com.example.bankflow.data.security

import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.SessionVault
import com.example.bankflow.domain.security.UnlockResult
import com.example.bankflow.domain.security.VaultException
import com.example.bankflow.domain.security.VaultState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom


class KeyVault(
    private val storage: VaultStorage,
    private val plain: KeyWrapper,
    private val protectedWrapper: KeyWrapper,
    private val settings: AppLockSettings,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: SecureRandom = SecureRandom(),
) : SessionVault, VaultKeys {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(VaultState.UNKNOWN)

    @Volatile
    private var key: ByteArray? = null

    override val state: StateFlow<VaultState> = _state.asStateFlow()

    override fun currentKey(): ByteArray? = key

    fun start(scope: CoroutineScope): Job = scope.launch { exclusive { } }


    override suspend fun ensureOpen() = exclusive {
        when (_state.value) {
            VaultState.OPEN -> Unit
            VaultState.LOCKED -> throw VaultLockedException()
            else -> createKey()
        }
    }

    override suspend fun reset() = exclusive { resetNow() }


    override suspend fun unlock(): UnlockResult = exclusive {
        when (_state.value) {
            VaultState.OPEN, VaultState.EMPTY -> UnlockResult.SUCCESS
            VaultState.UNKNOWN -> UnlockResult.KEY_INVALIDATED
            VaultState.LOCKED -> unwrapProtectedKey()
        }
    }

    override suspend fun protect() = exclusive {
        val dek = key ?: throw VaultException("The key is not available")
        if (orNull { storage.read() }?.mode == KeyMode.PROTECTED) return@exclusive

        val wrapped = try {
            protectedWrapper.wrap(dek)
        } catch (e: Exception) {
            throw VaultException("Could not create a protected key", e)
        }
        storage.write(StoredKey(KeyMode.PROTECTED, wrapped))
        plain.destroy()
    }

    override suspend fun unprotect() = exclusive {
        val dek = key ?: throw VaultException("The key is not available")
        if (orNull { storage.read() }?.mode == KeyMode.PLAIN) return@exclusive

        val wrapped = try {
            plain.wrap(dek)
        } catch (e: Exception) {
            throw VaultException("Could not store the key", e)
        }
        storage.write(StoredKey(KeyMode.PLAIN, wrapped))
        protectedWrapper.destroy()
    }


    private suspend fun <T> exclusive(block: suspend () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            initializeIfNeeded()
            block()
        }
    }

    private suspend fun initializeIfNeeded() {
        if (_state.value != VaultState.UNKNOWN) return

        val stored = orNull { storage.read() }
        if (stored == null) {
            _state.value = VaultState.EMPTY
            return
        }
        when (stored.mode) {
            KeyMode.PROTECTED -> _state.value = VaultState.LOCKED
            KeyMode.PLAIN -> try {
                key = plain.unwrap(stored.wrapped)
                _state.value = VaultState.OPEN
            } catch (e: Exception) {
                resetNow()
            }
        }
    }

    private suspend fun createKey() {
        val dek = ByteArray(KEY_BYTES).also(random::nextBytes)
        val wantsProtection = orNull { settings.enabled.first() } ?: false
        val (mode, wrapped) = wrapNew(dek, wantsProtection)

        storage.write(StoredKey(mode, wrapped))
        key = dek
        _state.value = VaultState.OPEN
    }


    private fun wrapNew(dek: ByteArray, protect: Boolean): Pair<KeyMode, ByteArray> {
        if (protect) {
            try {
                return KeyMode.PROTECTED to protectedWrapper.wrap(dek)
            } catch (e: Exception) {
            }
        }
        return KeyMode.PLAIN to plain.wrap(dek)
    }

    private suspend fun unwrapProtectedKey(): UnlockResult {
        val stored = orNull { storage.read() }
        if (stored == null || stored.mode != KeyMode.PROTECTED) {
            resetNow()
            return UnlockResult.KEY_INVALIDATED
        }
        return try {
            key = protectedWrapper.unwrap(stored.wrapped)
            _state.value = VaultState.OPEN
            UnlockResult.SUCCESS
        } catch (e: AuthenticationRequiredException) {
            UnlockResult.AUTHENTICATION_REQUIRED
        } catch (e: Exception) {
            resetNow()
            UnlockResult.KEY_INVALIDATED
        }
    }

    private suspend fun resetNow() {
        key?.fill(0)
        key = null
        orNull { storage.clear() }
        plain.destroy()
        protectedWrapper.destroy()
        _state.value = VaultState.EMPTY
    }

    private inline fun <T> orNull(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}