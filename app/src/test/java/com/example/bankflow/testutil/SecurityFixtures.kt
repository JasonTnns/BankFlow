package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.domain.security.BiometricSupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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