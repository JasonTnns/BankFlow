package com.example.bankflow.presentation.security

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.AuthOutcome
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.domain.security.BiometricSupport
import com.example.bankflow.domain.security.SessionVault
import com.example.bankflow.domain.security.VaultException
import com.example.bankflow.presentation.mvi.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class SecurityViewModel(
    private val settings: AppLockSettings,
    private val biometrics: BiometricSupport,
    private val vault: SessionVault,
) : MviViewModel<SecurityState, SecurityIntent, SecurityEffect>(
    SecurityState(availability = biometrics.availability()),
) {

    init {
        viewModelScope.launch { settings.enabled.collect { value -> setState { copy(enabled = value) } } }
    }

    override fun handleIntent(intent: SecurityIntent) {
        when (intent) {
            SecurityIntent.Refresh -> setState { copy(availability = biometrics.availability()) }
            is SecurityIntent.Toggle -> toggle(intent.enable)
            is SecurityIntent.AuthResult -> onAuthResult(intent)
        }
    }

    private fun toggle(enable: Boolean) {
        val state = currentState
        if (state.isSaving || enable == state.enabled) return

        val availability = biometrics.availability()
        setState { copy(availability = availability, failure = null) }

        when {
            availability == BiometricAvailability.AVAILABLE -> sendEffect(SecurityEffect.Authenticate(enable))
            enable -> setState { copy(failure = AuthFailure.UNAVAILABLE) }
            else -> apply(false)
        }
    }

    private fun onAuthResult(result: SecurityIntent.AuthResult) {
        when (val outcome = result.outcome) {
            AuthOutcome.Success -> apply(result.enable)
            is AuthOutcome.Failed ->
                if (outcome.reason != AuthFailure.CANCELLED) setState { copy(failure = outcome.reason) }
        }
    }

    private fun apply(enable: Boolean) {
        setState { copy(isSaving = true, failure = null) }
        viewModelScope.launch {
            try {
                if (enable) enableLock() else disableLock()
                setState { copy(isSaving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: VaultException) {
                setState { copy(isSaving = false, failure = AuthFailure.UNAVAILABLE) }
            } catch (e: Exception) {
                setState { copy(isSaving = false, failure = AuthFailure.ERROR) }
            }
        }
    }

    private suspend fun enableLock() {
        vault.protect()
        try {
            settings.setEnabled(true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                vault.unprotect()
            } catch (undoError: Exception) {
                if (undoError is CancellationException) throw undoError
            }
            throw e
        }
    }

    private suspend fun disableLock() {
        vault.unprotect()
        settings.setEnabled(false)
    }
}