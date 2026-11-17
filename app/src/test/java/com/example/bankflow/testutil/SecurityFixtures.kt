package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.domain.security.BiometricSupport
import com.example.bankflow.domain.security.SessionVault
import com.example.bankflow.domain.security.UnlockResult
import com.example.bankflow.domain.security.VaultState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class FakeAppLockSettings(initial: Boolean = false) : AppLockSettings {
    val state = MutableStateFlow(initial)
    var failOnWrite = false

    override val enabled: Flow<Boolean> = state

    override suspend fun setEnabled(enabled: Boolean) {
        if (failOnWrite) throw IOException("storage unavailable")
        state.value = enabled
    }
}

class FakeBiometricSupport(var current: BiometricAvailability = BiometricAvailability.AVAILABLE) : BiometricSupport {
    override fun availability(): BiometricAvailability = current
}

class MutableAuthRepository(initial: User? = TEST_USER) : AuthRepository {
    val user = MutableStateFlow(initial)
    override val currentUser: Flow<User?> = user

    override suspend fun register(email: String, password: String, fullName: String): AppResult<User> = error("not used")
    override suspend fun login(email: String, password: String): AppResult<User> = error("not used")
    override suspend fun logout() {
        user.value = null
    }
}

class FakeSessionVault(initial: VaultState = VaultState.OPEN) : SessionVault {
    val stateFlow = MutableStateFlow(initial)
    override val state: StateFlow<VaultState> = stateFlow

    var unlockResult = UnlockResult.SUCCESS
    var protectError: Exception? = null
    var unprotectError: Exception? = null
    var unlocks = 0
    var protects = 0
    var unprotects = 0

    override suspend fun unlock(): UnlockResult {
        unlocks++
        when (unlockResult) {
            UnlockResult.SUCCESS -> stateFlow.value = VaultState.OPEN
            UnlockResult.KEY_INVALIDATED -> stateFlow.value = VaultState.EMPTY
            UnlockResult.AUTHENTICATION_REQUIRED -> Unit
        }
        return unlockResult
    }

    override suspend fun protect() {
        protectError?.let { throw it }
        protects++
    }

    override suspend fun unprotect() {
        unprotectError?.let { throw it }
        unprotects++
    }
}