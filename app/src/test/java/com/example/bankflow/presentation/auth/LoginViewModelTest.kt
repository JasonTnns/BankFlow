package com.example.bankflow.presentation.auth

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.LoginUseCase
import com.example.bankflow.presentation.auth.login.LoginIntent
import com.example.bankflow.presentation.auth.login.LoginViewModel
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubAuthRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = StubAuthRepository()
    private val viewModel = LoginViewModel(LoginUseCase(repository))

    private fun fill(email: String = "Alice@Example.com ", password: String = "Passw0rd123") {
        viewModel.onIntent(LoginIntent.EmailChanged(email))
        viewModel.onIntent(LoginIntent.PasswordChanged(password))
    }

    @Test
    fun `typing updates the form`() {
        fill(email = "a@b.com", password = "secret")

        assertEquals("a@b.com", viewModel.state.value.email)
        assertEquals("secret", viewModel.state.value.password)
    }

    @Test
    fun `the password can be shown and hidden`() {
        assertFalse(viewModel.state.value.passwordVisible)
        viewModel.onIntent(LoginIntent.TogglePasswordVisibility)
        assertTrue(viewModel.state.value.passwordVisible)
        viewModel.onIntent(LoginIntent.TogglePasswordVisibility)
        assertFalse(viewModel.state.value.passwordVisible)
    }

    @Test
    fun `submitting sends the cleaned email and ends loading`() {
        fill()

        viewModel.onIntent(LoginIntent.Submit)

        assertEquals(listOf("alice@example.com" to "Passw0rd123"), repository.loginCalls)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `while the request is in flight the form shows loading`() {
        repository.gate = CompletableDeferred()
        fill()

        viewModel.onIntent(LoginIntent.Submit)
        assertTrue(viewModel.state.value.isLoading)

        repository.gate!!.complete(Unit)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `tapping Submit repeatedly sends one request`() {
        repository.gate = CompletableDeferred()
        fill()

        repeat(3) { viewModel.onIntent(LoginIntent.Submit) }

        assertEquals(1, repository.loginCalls.size)
        repository.gate!!.complete(Unit)
    }

    @Test
    fun `wrong credentials show a banner and stop loading`() {
        repository.loginResult = AppResult.Failure(AppError.InvalidCredentials)
        fill()

        viewModel.onIntent(LoginIntent.Submit)

        assertEquals(AppError.InvalidCredentials, viewModel.state.value.banner)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `an empty form shows field errors without calling the server`() {
        viewModel.onIntent(LoginIntent.Submit)

        assertNotNull(viewModel.state.value.emailError)
        assertNotNull(viewModel.state.value.passwordError)
        assertNull("field errors are not banners", viewModel.state.value.banner)
        assertTrue(repository.loginCalls.isEmpty())
    }

    @Test
    fun `editing a field clears only that field's error`() {
        viewModel.onIntent(LoginIntent.Submit)

        viewModel.onIntent(LoginIntent.EmailChanged("a@b.com"))

        assertNull(viewModel.state.value.emailError)
        assertNotNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `a banner error clears when the user starts typing, and they can retry after being offline`() {
        repository.loginResult = AppResult.Failure(AppError.NoConnection)
        fill()
        viewModel.onIntent(LoginIntent.Submit)
        assertEquals(AppError.NoConnection, viewModel.state.value.banner)

        viewModel.onIntent(LoginIntent.PasswordChanged("Passw0rd123!"))
        assertNull(viewModel.state.value.banner)

        repository.loginResult = AppResult.Success(com.example.bankflow.testutil.TEST_USER)
        viewModel.onIntent(LoginIntent.Submit)
        assertEquals(2, repository.loginCalls.size)
        assertNull(viewModel.state.value.error)
    }
}