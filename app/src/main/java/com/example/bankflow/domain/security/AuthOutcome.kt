package com.example.bankflow.domain.security

enum class AuthFailure { CANCELLED, LOCKED_OUT, UNAVAILABLE, ERROR }

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Failed(val reason: AuthFailure) : AuthOutcome
}

enum class BiometricAvailability { AVAILABLE, NONE_ENROLLED, NO_HARDWARE, UNAVAILABLE }