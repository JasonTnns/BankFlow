package com.example.bankflow.presentation.auth.login

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.usecase.LoginUseCase
import com.example.bankflow.presentation.common.withoutFieldError
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


@KoinViewModel
class LoginViewModel(
    private val login: LoginUseCase,
) : MviViewModel<LoginState, LoginIntent, NoEffect>(LoginState()) {

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged ->
                setState { copy(email = intent.value, error = error.withoutFieldError("email")) }
            is LoginIntent.PasswordChanged ->
                setState { copy(password = intent.value, error = error.withoutFieldError("password")) }
            LoginIntent.TogglePasswordVisibility ->
                setState { copy(passwordVisible = !passwordVisible) }
            LoginIntent.Submit -> submit()
        }
    }

    private fun submit() {
        if (currentState.isLoading) return
        val email = currentState.email
        val password = currentState.password

        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = login(email, password)
            setState {
                copy(isLoading = false, error = (result as? AppResult.Failure)?.error)
            }
        }
    }
}