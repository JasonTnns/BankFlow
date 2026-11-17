package com.example.bankflow.presentation.security

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.SessionVault
import com.example.bankflow.domain.security.UnlockResult
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant


@KoinViewModel
class AppLockViewModel(
    settings: AppLockSettings,
    authRepository: AuthRepository,
    private val vault: SessionVault,
    private val clock: Clock,
) : MviViewModel<AppLockState, AppLockIntent, NoEffect>(AppLockState()) {

    private var stoppedAt: Instant? = null
    private var sessionSeen = false

    private var sawLockedVault = false

    init {
        viewModelScope.launch {
            settings.enabled.collect { isEnabled ->
                setState {
                    copy(enabled = isEnabled, locked = if (this.enabled == false && isEnabled) false else locked)
                }
            }
        }
        viewModelScope.launch {
            vault.state.collect { vaultState ->
                if (vaultState == VaultState.LOCKED) sawLockedVault = true
                setState { copy(vaultState = vaultState) }
            }
        }
        viewModelScope.launch {
            authRepository.currentUser.map { it != null }.distinctUntilChanged().collect { isSignedIn ->
                val coldStart = !sessionSeen
                sessionSeen = true
                setState {
                    copy(signedIn = isSignedIn, locked = coldStart && isSignedIn && !sawLockedVault, failure = null)
                }
            }
        }
    }

    override fun handleIntent(intent: AppLockIntent) {
        when (intent) {
            AppLockIntent.Stopped -> stoppedAt = clock.instant()
            AppLockIntent.Started -> {
                val stopped = stoppedAt
                stoppedAt = null
                val state = currentState
                if (stopped != null && state.enabled == true && state.signedIn == true &&
                    Duration.between(stopped, clock.instant()) >= LOCK_AFTER
                ) {
                    setState { copy(locked = true, failure = null) }
                }
            }
            AppLockIntent.UnlockSucceeded -> unlock()
            is AppLockIntent.UnlockFailed ->
                if (intent.reason != AuthFailure.CANCELLED) setState { copy(failure = intent.reason) }
        }
    }

    private fun unlock() {
        if (currentState.vaultState != VaultState.LOCKED) {
            setState { copy(locked = false, failure = null) }
            return
        }
        viewModelScope.launch {
            when (vault.unlock()) {
                UnlockResult.SUCCESS -> setState { copy(locked = false, failure = null) }
                UnlockResult.AUTHENTICATION_REQUIRED -> setState { copy(failure = AuthFailure.ERROR) }
                UnlockResult.KEY_INVALIDATED -> setState { copy(locked = false, failure = null) }
            }
        }
    }

    companion object {
        val LOCK_AFTER: Duration = Duration.ofSeconds(30)
    }
}