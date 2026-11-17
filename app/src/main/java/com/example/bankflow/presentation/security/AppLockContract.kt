package com.example.bankflow.presentation.security

import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.AuthOutcome
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState


data class AppLockState(
    val signedIn: Boolean? = null,
    val enabled: Boolean? = null,
    val vaultState: VaultState = VaultState.UNKNOWN,
    val locked: Boolean = false,
    val failure: AuthFailure? = null,
) : UiState {
    val vaultLocked: Boolean get() = vaultState == VaultState.LOCKED

    val isReady: Boolean
        get() = vaultState != VaultState.UNKNOWN &&
                (vaultLocked || signedIn == false || (signedIn == true && enabled != null))

    val showLock: Boolean get() = vaultLocked || (signedIn == true && enabled == true && locked)
}

sealed interface AppLockIntent : UiIntent {
    data object Stopped : AppLockIntent

    data object Started : AppLockIntent

    data object UnlockSucceeded : AppLockIntent
    data class UnlockFailed(val reason: AuthFailure) : AppLockIntent
}

fun AuthOutcome.toIntent(): AppLockIntent = when (this) {
    AuthOutcome.Success -> AppLockIntent.UnlockSucceeded
    is AuthOutcome.Failed -> AppLockIntent.UnlockFailed(reason)
}