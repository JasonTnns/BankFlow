package com.example.bankflow.presentation.auth

import com.example.bankflow.R
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.RegisterUseCase
import com.example.bankflow.presentation.auth.register.RegisterIntent
import com.example.bankflow.presentation.auth.register.RegisterViewModel
import com.example.bankflow.presentation.common.UiText
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

class RegisterViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = StubAuthRepository()
    private val viewModel = RegisterViewModel(RegisterUseCase(repository))

    private fun fill(
        name: String = "  Alice Tester ",
        email: String = " Alice@Example.com",
        password: String = "Passw0rd123",
    ) {
        viewModel.onIntent(RegisterIntent.FullNameChanged(name))
        viewModel.onIntent(RegisterIntent.EmailChanged(email))
        viewModel.onIntent(RegisterIntent.PasswordChanged(password))
    }

    @Test
    fun `a valid form registers with cleaned values`() {
        fill()

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals(
            listOf(Triple("alice@example.com", "Passw0rd123", "Alice Tester")),
            repository.registerCalls,
        )
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `an email that is already registered is shown under the email field, not in a banner`() {
        repository.registerResult = AppResult.Failure(AppError.EmailTaken)
        assertEquals(UiText.Res(R.string.error_email_taken), viewModel.state.value.emailError)
        fill()

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals("An account with this email already exists.", viewModel.state.value.emailError)
        assertNull(viewModel.state.value.banner)
    }

    @Test
    fun `editing the email clears the already-registered message`() {
        repository.registerResult = AppResult.Failure(AppError.EmailTaken)
        fill()
        viewModel.onIntent(RegisterIntent.Submit)

        viewModel.onIntent(RegisterIntent.EmailChanged("other@example.com"))

        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun `a weak password is rejected locally with a message under the password field`() {
        fill(password = "short")

        viewModel.onIntent(RegisterIntent.Submit)

        assertNotNull(viewModel.state.value.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun `an empty form reports every missing field`() {
        viewModel.onIntent(RegisterIntent.Submit)

        val state = viewModel.state.value
        assertNotNull(state.fullNameError)
        assertNotNull(state.emailError)
        assertNotNull(state.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun `field errors sent by the server are shown on their fields`() {
        repository.registerResult = AppResult.Failure(
            AppError.Validation(fieldErrors = mapOf("email" to listOf("Enter a valid email address"))),
        )
        assertEquals(UiText.Plain("Enter a valid email address"), viewModel.state.value.emailError)
        fill()

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals("Enter a valid email address", viewModel.state.value.emailError)
    }

    @Test
    fun `tapping Submit repeatedly registers once`() {
        repository.gate = CompletableDeferred()
        fill()

        repeat(3) { viewModel.onIntent(RegisterIntent.Submit) }

        assertEquals(1, repository.registerCalls.size)
        assertTrue(viewModel.state.value.isLoading)
        repository.gate!!.complete(Unit)
    }

    @Test
    fun `a network failure shows a banner`() {
        repository.registerResult = AppResult.Failure(AppError.NoConnection)
        fill()

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals(AppError.NoConnection, viewModel.state.value.banner)
    }
}