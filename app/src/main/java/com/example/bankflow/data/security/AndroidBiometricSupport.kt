package com.example.bankflow.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import com.example.bankflow.core.security.ALLOWED_AUTHENTICATORS
import com.example.bankflow.core.security.mapBiometricStatus
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.domain.security.BiometricSupport
import org.koin.core.annotation.Single

@Single
class AndroidBiometricSupport(private val context: Context) : BiometricSupport {
    override fun availability(): BiometricAvailability =
        mapBiometricStatus(BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS))
}