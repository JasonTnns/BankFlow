package com.example.bankflow.presentation.security

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.AuthFailure
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
    private val clock: Clock,
) : MviViewModel<AppLockState, AppLockIntent, NoEffect>(AppLockState()) {

    private var stoppedAt: Instant? = null
    private var sessionSeen = false

    init {
        viewModelScope.launch {
            settings.enabled.collect { isEnabled ->
                setState {
                    copy(enabled = isEnabled, locked = if (this.enabled == false && isEnabled) false else locked)
                }
            }
        }
        viewModelScope.launch {
            authRepository.currentUser.map { it != null }.distinctUntilChanged().collect { isSignedIn ->
                val coldStart = !sessionSeen
                sessionSeen = true
                setState { copy(signedIn = isSignedIn, locked = coldStart && isSignedIn, failure = null) }
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
            AppLockIntent.UnlockSucceeded -> setState { copy(locked = false, failure = null) }
            is AppLockIntent.UnlockFailed ->
                if (intent.reason != AuthFailure.CANCELLED) setState { copy(failure = intent.reason) }
        }
    }

    companion object {
        val LOCK_AFTER: Duration = Duration.ofSeconds(30)
    }
}