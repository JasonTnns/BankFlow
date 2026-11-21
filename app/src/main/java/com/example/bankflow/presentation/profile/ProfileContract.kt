package com.example.bankflow.presentation.profile

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.fieldError
import com.example.bankflow.presentation.common.fieldText
import com.example.bankflow.presentation.common.toBanner
import com.example.bankflow.presentation.common.userText
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class NameEdit(
    val value: String,
    val isSaving: Boolean = false,
    val error: AppError? = null,
) {
    val fieldError: UiText? get() = error.fieldText("fullName")
    val banner: AppError? get() = error.toBanner()
}

data class ProfileState(

    val deleteForm: DeleteAccountForm? = null,
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val loadError: AppError? = null,
    val pendingTheme: ThemePreference? = null,
    val themeError: AppError? = null,
    val nameEdit: NameEdit? = null,
) : UiState {
    val isUpdating: Boolean
        get() = pendingTheme != null || nameEdit?.isSaving == true || deleteForm?.isDeleting == true
    val selectedTheme: ThemePreference? get() = pendingTheme ?: profile?.user?.themePreference
}

sealed interface ProfileIntent : UiIntent {
    data object OpenDelete : ProfileIntent
    data class DeletePasswordChanged(val text: String) : ProfileIntent
    data object ConfirmDelete : ProfileIntent
    data object DismissDelete : ProfileIntent
    data object Refresh : ProfileIntent

    data class SelectTheme(val theme: ThemePreference) : ProfileIntent

    data object OpenNameEdit : ProfileIntent
    data class NameChanged(val text: String) : ProfileIntent
    data object SaveName : ProfileIntent
    data object DismissNameEdit : ProfileIntent
}

/** The "Delete account" dialog. A wrong password shows under the field; everything else in a banner. */
data class DeleteAccountForm(
    val password: String = "",
    val isDeleting: Boolean = false,
    val error: AppError? = null,
) {
    val passwordError: UiText?
        get() = when (error) {
            AppError.PasswordIncorrect -> AppError.PasswordIncorrect.userText()
            else -> error.fieldText("password")
        }

    val banner: AppError?
        get() = when (val e = error) {
            null, AppError.PasswordIncorrect -> null
            is AppError.Validation -> if (e.fields.isEmpty()) e else null
            else -> e
        }
}