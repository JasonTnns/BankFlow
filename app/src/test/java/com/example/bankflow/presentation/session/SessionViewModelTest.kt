package com.example.bankflow.presentation.session

import app.cash.turbine.test
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.usecase.LogoutUseCase
import com.example.bankflow.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val alice = User("u1", "alice@example.com", "Alice", ThemePreference.SYSTEM)

    private class FakeAuthRepository(initial: User?) : AuthRepository {
        val user = MutableStateFlow(initial)
        var logoutCalls = 0
        override val currentUser: Flow<User?> = user
        override suspend fun register(email: String, password: String, fullName: String): AppResult<User> =
            error("not used")
        override suspend fun login(email: String, password: String): AppResult<User> = error("not used")
        override suspend fun logout() {
            logoutCalls++
            user.value = null
        }
    }

    @Test
    fun `a stored session starts the app signed in`() = runTest {
        val viewModel = SessionViewModel(FakeAuthRepository(alice), LogoutUseCase(FakeAuthRepository(alice)))

        viewModel.state.test {
            assertEquals(SessionState.SignedIn(alice), expectMostRecentItem())
        }
    }

    @Test
    fun `no stored session starts the app signed out`() = runTest {
        val repository = FakeAuthRepository(null)
        val viewModel = SessionViewModel(repository, LogoutUseCase(repository))

        viewModel.state.test {
            assertEquals(SessionState.SignedOut, expectMostRecentItem())
        }
    }

    @Test
    fun `logging out moves the app to signed out`() = runTest {
        val repository = FakeAuthRepository(alice)
        val viewModel = SessionViewModel(repository, LogoutUseCase(repository))

        viewModel.state.test {
            assertEquals(SessionState.SignedIn(alice), expectMostRecentItem())

            viewModel.logout()

            assertEquals(SessionState.SignedOut, awaitItem())
        }
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `a session that dies on its own (failed refresh) also signs the app out`() = runTest {
        val repository = FakeAuthRepository(alice)
        val viewModel = SessionViewModel(repository, LogoutUseCase(repository))

        viewModel.state.test {
            assertEquals(SessionState.SignedIn(alice), expectMostRecentItem())

            repository.user.value = null

            assertEquals(SessionState.SignedOut, awaitItem())
        }
    }
}