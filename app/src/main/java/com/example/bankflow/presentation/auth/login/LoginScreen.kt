package com.example.bankflow.presentation.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.presentation.auth.AuthTextField
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.auth.LoadingButton
import com.example.bankflow.presentation.auth.PasswordField
import com.example.bankflow.presentation.common.userMessage
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(state = state, onIntent = viewModel::onIntent, onNavigateToRegister = onNavigateToRegister)
}

@Composable
fun LoginContent(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = "BankFlow",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Sign in to your accounts",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            state.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("auth_error")) }

            AuthTextField(
                value = state.email,
                onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
                label = "Email",
                modifier = Modifier.testTag("login_email"),
                enabled = !state.isLoading,
                errorText = state.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
            )

            PasswordField(
                value = state.password,
                onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
                visible = state.passwordVisible,
                onToggleVisibility = { onIntent(LoginIntent.TogglePasswordVisibility) },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onIntent(LoginIntent.Submit)
                }),
                modifier = Modifier.testTag("login_password"),
                enabled = !state.isLoading,
                errorText = state.passwordError,
            )

            LoadingButton(
                text = "Sign in",
                loading = state.isLoading,
                onClick = { onIntent(LoginIntent.Submit) },
                modifier = Modifier.testTag("login_submit"),
            )

            TextButton(onClick = onNavigateToRegister, enabled = !state.isLoading) {
                Text("New here? Create an account")
            }
        }
    }
}