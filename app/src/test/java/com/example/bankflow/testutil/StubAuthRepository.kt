package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.Transaction
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

val TEST_USER = User("u1", "alice@example.com", "Alice Tester", ThemePreference.SYSTEM)

class StubAuthRepository : AuthRepository {
    private val user = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = user

    var loginResult: AppResult<User> = AppResult.Success(TEST_USER)
    var registerResult: AppResult<User> = AppResult.Success(TEST_USER)

    var gate: CompletableDeferred<Unit>? = null

    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>() // email, password, name

    override suspend fun login(email: String, password: String): AppResult<User> {
        loginCalls += email to password
        gate?.await()
        return loginResult
    }

    override suspend fun register(email: String, password: String, fullName: String): AppResult<User> {
        registerCalls += Triple(email, password, fullName)
        gate?.await()
        return registerResult
    }

    override suspend fun logout() {
        user.value = null
    }

    var transactionResult: AppResult<Transaction> = AppResult.Success(testTransaction("t1"))
    var transactionGate: CompletableDeferred<Unit>? = null
    val transactionRequests = mutableListOf<Pair<String, String>>() // accountId to transferId

    suspend fun getTransaction(accountId: String, transferId: String): AppResult<Transaction> {
        transactionRequests += accountId to transferId
        transactionGate?.await()
        return transactionResult
    }
}