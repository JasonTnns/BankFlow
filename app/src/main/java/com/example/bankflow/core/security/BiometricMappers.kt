package com.example.bankflow.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.BiometricAvailability

val ALLOWED_AUTHENTICATORS: Int =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun mapBiometricStatus(status: Int): BiometricAvailability = when (status) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
    else -> BiometricAvailability.UNAVAILABLE
}

fun mapAuthError(errorCode: Int): AuthFailure = when (errorCode) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthFailure.CANCELLED

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthFailure.LOCKED_OUT

    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthFailure.UNAVAILABLE

    else -> AuthFailure.ERROR
}