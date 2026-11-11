package com.example.bankflow.presentation.auth.register

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.common.fieldError
import com.example.bankflow.presentation.common.toBanner
import com.example.bankflow.presentation.common.userMessage
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val fullNameError: String? get() = error.fieldError("fullName")

    val emailError: String?
        get() = error.fieldError("email")
            ?: if (error == AppError.EmailTaken) AppError.EmailTaken.userMessage() else null

    val passwordError: String? get() = error.fieldError("password")
    val banner: AppError? get() = error.toBanner()
}

sealed interface RegisterIntent : UiIntent {
    data class FullNameChanged(val value: String) : RegisterIntent
    data class EmailChanged(val value: String) : RegisterIntent
    data class PasswordChanged(val value: String) : RegisterIntent
    data object TogglePasswordVisibility : RegisterIntent
    data object Submit : RegisterIntent
}