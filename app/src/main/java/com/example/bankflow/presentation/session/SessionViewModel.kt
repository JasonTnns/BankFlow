package com.example.bankflow.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed interface SessionState {
    data object Loading: SessionState
    data class SignedIn(val user: User) : SessionState
    data object SignedOut: SessionState
}

@KoinViewModel
class SessionViewModel(
    authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
): ViewModel() {

    val state: StateFlow<SessionState> = authRepository.currentUser
        .map { user -> if (user != null) SessionState.SignedIn(user) else SessionState.SignedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }
}