package com.example.bankflow.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.BiometricAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricMappingTest {

    @Test
    fun `device status maps to what the settings screen can explain`() {
        assertEquals(BiometricAvailability.AVAILABLE, mapBiometricStatus(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(BiometricAvailability.NONE_ENROLLED, mapBiometricStatus(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(BiometricAvailability.NO_HARDWARE, mapBiometricStatus(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        ).forEach { assertEquals("$it", BiometricAvailability.UNAVAILABLE, mapBiometricStatus(it)) }
    }

    @Test
    fun `dismissing the prompt is a cancellation, not an error`() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        ).forEach { assertEquals("$it", AuthFailure.CANCELLED, mapAuthError(it)) }
    }

    @Test
    fun `too many attempts is a lockout`() {
        assertEquals(AuthFailure.LOCKED_OUT, mapAuthError(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(AuthFailure.LOCKED_OUT, mapAuthError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
    }

    @Test
    fun `missing hardware or credentials is unavailable`() {
        listOf(
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        ).forEach { assertEquals("$it", AuthFailure.UNAVAILABLE, mapAuthError(it)) }
    }

    @Test
    fun `anything else is a generic error`() {
        assertEquals(AuthFailure.ERROR, mapAuthError(BiometricPrompt.ERROR_TIMEOUT))
        assertEquals(AuthFailure.ERROR, mapAuthError(9999))
    }

    @Test
    fun `the device PIN is always an accepted way in`() {
        assertTrue(ALLOWED_AUTHENTICATORS and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0)
    }
}