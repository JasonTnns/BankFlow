@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bankflow.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bankflow.BuildConfig
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.auth.ErrorBanner
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.formatDate
import com.example.bankflow.presentation.common.userMessage
import com.example.bankflow.presentation.components.ErrorState
import com.example.bankflow.presentation.components.InlineError
import com.example.bankflow.presentation.components.SectionTitle
import org.koin.androidx.compose.koinViewModel


@Composable
fun ProfileScreen(
    onOpenRecipients: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(ProfileIntent.Refresh) }

    ProfileContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenRecipients = onOpenRecipients,
        onLogout = onLogout,
        appVersion = BuildConfig.VERSION_NAME,
    )
}

@Composable
fun ProfileContent(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    onOpenRecipients: () -> Unit,
    onLogout: () -> Unit,
    appVersion: String,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val profile = state.profile
            val loadError = state.loadError
            when {
                profile != null -> ProfileBody(
                    profile = profile,
                    state = state,
                    onIntent = onIntent,
                    onOpenRecipients = onOpenRecipients,
                    onLogoutClick = { confirmLogout = true },
                    appVersion = appVersion,
                )
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .semantics { contentDescription = "Loading profile" },
                )
                loadError != null -> ErrorState(
                    error = loadError,
                    onRetry = { onIntent(ProfileIntent.Refresh) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    state.nameEdit?.let { edit -> NameDialog(edit = edit, isUpdating = state.isUpdating, onIntent = onIntent) }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to see your accounts.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("logout_confirm"),
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }, modifier = Modifier.testTag("logout_cancel")) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ProfileBody(
    profile: Profile,
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    onOpenRecipients: () -> Unit,
    onLogoutClick: () -> Unit,
    appVersion: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("profile_list"),
    ) {
        ProfileHeader(profile)

        SectionTitle("Account")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                InfoRow(
                    label = "Name",
                    value = profile.user.fullName,
                    tag = "row_name",
                    trailing = {
                        TextButton(
                            onClick = { onIntent(ProfileIntent.OpenNameEdit) },
                            enabled = !state.isUpdating,
                            modifier = Modifier.testTag("profile_edit_name"),
                        ) { Text("Edit") }
                    },
                )
                InfoRow("Email", profile.user.email, tag = "row_email")
                InfoRow("Member since", profile.memberSince.formatDate(), tag = "row_since")
                InfoRow("Daily transfer limit", profile.dailyTransferLimit.format(), tag = "row_limit", showDivider = false)
            }
        }
        Text(
            text = "Your email and transfer limit can't be changed here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SectionTitle("Appearance")
        state.themeError?.let { error ->
            InlineError(error, onRetry = null, modifier = Modifier.testTag("theme_error"))
        }
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                ThemePreference.entries.forEachIndexed { index, theme ->
                    ThemeOption(
                        theme = theme,
                        selected = state.selectedTheme == theme,
                        enabled = !state.isUpdating,
                        onSelect = { onIntent(ProfileIntent.SelectTheme(theme)) },
                    )
                    if (index < ThemePreference.entries.lastIndex) HorizontalDivider()
                }
            }
        }

        SectionTitle("Payments")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenRecipients)
                    .padding(16.dp)
                    .testTag("profile_recipients"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Saved recipients", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "The people you send money to",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        SectionTitle("About")
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            InfoRow("Version", appVersion, tag = "row_version", showDivider = false)
        }
        Text(
            text = "BankFlow is a demo app. No real money is involved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        OutlinedButton(
            onClick = onLogoutClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("profile_logout"),
        ) { Text("Log out") }
    }
}

@Composable
private fun ProfileHeader(profile: Profile) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(profile.user.fullName),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = profile.user.fullName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("profile_name").semantics { heading() },
        )
        Text(
            text = profile.user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("profile_email"),
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    tag: String,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (trailing == null) 12.dp else 4.dp)
                .then(if (trailing == null) Modifier.semantics(mergeDescendants = true) { } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 16.dp).testTag(tag),
                )
                trailing?.invoke()
            }
        }
        if (showDivider) HorizontalDivider()
    }
}

@Composable
private fun ThemeOption(
    theme: ThemePreference,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_${theme.name}")
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(theme.themeLabel(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = theme.themeDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameDialog(edit: NameEdit, isUpdating: Boolean, onIntent: (ProfileIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!edit.isSaving) onIntent(ProfileIntent.DismissNameEdit) },
        title = { Text("Edit name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                edit.banner?.let { ErrorBanner(it.userMessage(), Modifier.testTag("name_error")) }
                OutlinedTextField(
                    value = edit.value,
                    onValueChange = { onIntent(ProfileIntent.NameChanged(it)) },
                    modifier = Modifier.testTag("name_field"),
                    label = { Text("Full name") },
                    singleLine = true,
                    enabled = !edit.isSaving,
                    isError = edit.fieldError != null,
                    supportingText = edit.fieldError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onIntent(ProfileIntent.SaveName) }),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(ProfileIntent.SaveName) },
                enabled = !isUpdating,
                modifier = Modifier.testTag("name_save"),
            ) { Text(if (edit.isSaving) "Saving\u2026" else "Save") }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(ProfileIntent.DismissNameEdit) },
                enabled = !edit.isSaving,
                modifier = Modifier.testTag("name_cancel"),
            ) { Text("Cancel") }
        },
    )
}