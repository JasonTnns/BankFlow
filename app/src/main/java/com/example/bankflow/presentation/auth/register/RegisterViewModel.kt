package com.example.bankflow.presentation.auth.register

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.RegisterUseCase
import com.example.bankflow.presentation.common.withoutFieldError
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class RegisterViewModel(
    private val register: RegisterUseCase,
) : MviViewModel<RegisterState, RegisterIntent, NoEffect>(RegisterState()) {

    override fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.FullNameChanged ->
                setState { copy(fullName = intent.value, error = error.withoutFieldError("fullName")) }
            is RegisterIntent.EmailChanged ->
                setState { copy(email = intent.value, error = error.clearEmailError()) }
            is RegisterIntent.PasswordChanged ->
                setState { copy(password = intent.value, error = error.withoutFieldError("password")) }
            RegisterIntent.TogglePasswordVisibility ->
                setState { copy(passwordVisible = !passwordVisible) }
            RegisterIntent.Submit -> submit()
        }
    }

    private fun submit() {
        if (currentState.isLoading) return
        val name = currentState.fullName
        val email = currentState.email
        val password = currentState.password

        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = register(email, password, name)
            setState {
                copy(isLoading = false, error = (result as? AppResult.Failure)?.error)
            }
        }
    }

    private fun AppError?.clearEmailError(): AppError? =
        if (this == AppError.EmailTaken) null else withoutFieldError("email")
}