package com.example.bankflow.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.navigation.AppNavHost
import com.example.bankflow.presentation.session.SessionState
import com.example.bankflow.presentation.session.SessionViewModel
import com.example.bankflow.ui.theme.BankFlowTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun BankFlowRoot(sessionViewModel: SessionViewModel = koinViewModel()) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    val themePreference = (session as? SessionState.SignedIn)?.user?.themePreference
        ?: ThemePreference.SYSTEM

    BankFlowTheme(themePreference = themePreference) {
        AppNavHost(session = session, onLogout = sessionViewModel::logout)
    }
}