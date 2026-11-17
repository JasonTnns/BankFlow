package com.example.bankflow.presentation.security

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.R
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.AuthOutcome
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.presentation.common.findActivity
import com.example.bankflow.presentation.components.SectionTitle
import com.example.bankflow.presentation.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

@Composable
fun SecuritySection(viewModel: SecurityViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enableTitle = stringResource(R.string.security_prompt_enable)
    val disableTitle = stringResource(R.string.security_prompt_disable)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(SecurityIntent.Refresh) }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SecurityEffect.Authenticate -> {
                val activity = context.findActivity() as? FragmentActivity
                if (activity == null) {
                    viewModel.onIntent(SecurityIntent.AuthResult(effect.enable, AuthOutcome.Failed(AuthFailure.ERROR)))
                } else {
                    activity.showAuthPrompt(if (effect.enable) enableTitle else disableTitle, null) { outcome ->
                        viewModel.onIntent(SecurityIntent.AuthResult(effect.enable, outcome))
                    }
                }
            }
        }
    }

    SecurityContent(state = state, onToggle = { viewModel.onIntent(SecurityIntent.Toggle(it)) })
}

@Composable
fun SecurityContent(
    state: SecurityState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.security_title))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.enabled,
                        enabled = state.canToggle,
                        role = Role.Switch,
                        onValueChange = onToggle,
                    )
                    .padding(16.dp)
                    .testTag("security_switch"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.security_unlock_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(state.availability.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = null, enabled = state.canToggle)
            }
        }
        state.failure?.let { failure ->
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("security_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@StringRes
private fun BiometricAvailability.descriptionRes(): Int = when (this) {
    BiometricAvailability.AVAILABLE -> R.string.security_unlock_available
    BiometricAvailability.NONE_ENROLLED -> R.string.security_unlock_none_enrolled
    BiometricAvailability.NO_HARDWARE -> R.string.security_unlock_no_hardware
    BiometricAvailability.UNAVAILABLE -> R.string.security_unlock_unavailable
}