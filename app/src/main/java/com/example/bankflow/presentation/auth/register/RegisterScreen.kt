package com.example.bankflow.presentation.auth.register

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.presentation.auth.AuthTextField
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.auth.LoadingButton
import com.example.bankflow.presentation.auth.PasswordField
import com.example.bankflow.presentation.common.resolve
import com.example.bankflow.presentation.common.userMessage
import org.koin.androidx.compose.koinViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterContent(state = state, onIntent = viewModel::onIntent, onNavigateToLogin = onNavigateToLogin)
}

@Composable
fun RegisterContent(
    state: RegisterState,
    onIntent: (RegisterIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
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
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            state.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("auth_error")) }

            AuthTextField(
                value = state.fullName,
                onValueChange = { onIntent(RegisterIntent.FullNameChanged(it)) },
                label = stringResource(R.string.register_full_name),
                modifier = Modifier.testTag("register_name"),
                enabled = !state.isLoading,
                errorText = state.fullNameError?.resolve(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )

            AuthTextField(
                forceLtr = true,
                value = state.email,
                onValueChange = { onIntent(RegisterIntent.EmailChanged(it)) },
                label = stringResource(R.string.auth_email),
                modifier = Modifier.testTag("register_email"),
                enabled = !state.isLoading,
                errorText = state.emailError?.resolve(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
            )

            PasswordField(
                value = state.password,
                onValueChange = { onIntent(RegisterIntent.PasswordChanged(it)) },
                visible = state.passwordVisible,
                onToggleVisibility = { onIntent(RegisterIntent.TogglePasswordVisibility) },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onIntent(RegisterIntent.Submit)
                }),
                modifier = Modifier.testTag("register_password"),
                enabled = !state.isLoading,
                errorText = state.passwordError?.resolve(),
                supportingText = stringResource(R.string.register_password_hint),
                )

            LoadingButton(
                text = stringResource(R.string.register_action_create),
                loading = state.isLoading,
                onClick = { onIntent(RegisterIntent.Submit) },
                modifier = Modifier.testTag("register_submit"),
            )

            TextButton(onClick = onNavigateToLogin, enabled = !state.isLoading) {
                Text(stringResource(R.string.register_action_sign_in))
            }
        }
    }
}