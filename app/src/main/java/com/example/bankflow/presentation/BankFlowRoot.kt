package com.example.bankflow.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.common.findActivity
import com.example.bankflow.presentation.navigation.AppNavHost
import com.example.bankflow.presentation.security.AppLockIntent
import com.example.bankflow.presentation.security.AppLockViewModel
import com.example.bankflow.presentation.security.LockScreen
import com.example.bankflow.presentation.security.SecureWindowEffect
import com.example.bankflow.presentation.security.showAuthPrompt
import com.example.bankflow.presentation.security.toIntent
import com.example.bankflow.presentation.session.SessionState
import com.example.bankflow.presentation.session.SessionViewModel
import com.example.bankflow.ui.theme.BankFlowTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BankFlowRoot(
    sessionViewModel: SessionViewModel = koinViewModel(),
    lockViewModel: AppLockViewModel = koinViewModel(),
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val lock by lockViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isPrompting by remember { mutableStateOf(false) }

    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lockViewModel.onIntent(AppLockIntent.Stopped) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { lockViewModel.onIntent(AppLockIntent.Started) }

    SecureWindowEffect(secure = session is SessionState.SignedIn)

    val themePreference = (session as? SessionState.SignedIn)?.user?.themePreference ?: ThemePreference.SYSTEM

    BankFlowTheme(themePreference = themePreference) {
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            when {
                !lock.isReady -> RootSplash()
                lock.showLock -> LockScreen(
                    failure = lock.failure,
                    isPrompting = isPrompting,
                    onUnlock = {
                        val activity = context.findActivity() as? FragmentActivity
                        if (activity != null && !isPrompting) {
                            isPrompting = true
                            activity.showAuthPrompt(promptTitle, promptSubtitle) { outcome ->
                                isPrompting = false
                                lockViewModel.onIntent(outcome.toIntent())
                            }
                        }
                    },
                    onLogout = sessionViewModel::logout,
                )
                else -> AppNavHost(session = session, onLogout = sessionViewModel::logout)
            }
        }
    }
}

@Composable
private fun RootSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}