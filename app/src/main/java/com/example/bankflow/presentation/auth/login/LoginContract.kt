package com.example.bankflow.presentation.auth.login

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.common.fieldError
import com.example.bankflow.presentation.common.toBanner
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class LoginState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val emailError: String? get() = error.fieldError("email")
    val passwordError: String? get() = error.fieldError("password")
    val banner: AppError? get() = error.toBanner()
}

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object TogglePasswordVisibility : LoginIntent
    data object Submit : LoginIntent
}