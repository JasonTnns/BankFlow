package com.example.bankflow.presentation.security

import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.AuthOutcome
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.presentation.mvi.UiEffect
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class SecurityState(
    val enabled: Boolean = false,
    val availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
    val isSaving: Boolean = false,
    val failure: AuthFailure? = null,
) : UiState {
    val canToggle: Boolean get() = !isSaving && (availability == BiometricAvailability.AVAILABLE || enabled)
}

sealed interface SecurityIntent : UiIntent {
    data object Refresh : SecurityIntent
    data class Toggle(val enable: Boolean) : SecurityIntent

    data class AuthResult(val enable: Boolean, val outcome: AuthOutcome) : SecurityIntent
}

sealed interface SecurityEffect : UiEffect {
    data class Authenticate(val enable: Boolean) : SecurityEffect
}