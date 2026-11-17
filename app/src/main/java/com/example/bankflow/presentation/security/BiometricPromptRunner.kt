package com.example.bankflow.presentation.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.bankflow.core.security.ALLOWED_AUTHENTICATORS
import com.example.bankflow.core.security.mapAuthError
import com.example.bankflow.domain.security.AuthOutcome

fun FragmentActivity.showAuthPrompt(title: String, subtitle: String?, onResult: (AuthOutcome) -> Unit) {
    var delivered = false
    fun deliver(outcome: AuthOutcome) {
        if (!delivered) {
            delivered = true
            onResult(outcome)
        }
    }

    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                deliver(AuthOutcome.Success)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                deliver(AuthOutcome.Failed(mapAuthError(errorCode)))
        },
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .apply { if (subtitle != null) setSubtitle(subtitle) }
        .build()

    prompt.authenticate(info)
}