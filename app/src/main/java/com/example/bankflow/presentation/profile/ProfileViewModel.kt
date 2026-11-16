package com.example.bankflow.presentation.profile

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.usecase.GetProfileUseCase
import com.example.bankflow.domain.usecase.UpdateProfileUseCase
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ProfileViewModel(
    private val getProfile: GetProfileUseCase,
    private val updateProfile: UpdateProfileUseCase,
) : MviViewModel<ProfileState, ProfileIntent, NoEffect>(ProfileState(isLoading = true)) {

    private var loadJob: Job? = null

    private var changeCount = 0

    init {
        load()
    }

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Refresh -> load()
            is ProfileIntent.SelectTheme -> selectTheme(intent.theme)

            ProfileIntent.OpenNameEdit -> currentState.profile?.let { profile ->
                setState { copy(nameEdit = NameEdit(value = profile.user.fullName)) }
            }
            is ProfileIntent.NameChanged -> setState {
                copy(nameEdit = nameEdit?.copy(value = intent.text.take(MAX_NAME_LENGTH), error = null))
            }
            ProfileIntent.SaveName -> saveName()
            ProfileIntent.DismissNameEdit -> setState { copy(nameEdit = null) }
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return // one request at a time

        val changesAtStart = changeCount
        if (currentState.profile == null) setState { copy(isLoading = true, loadError = null) }

        loadJob = viewModelScope.launch {
            val result = getProfile()
            if (changeCount != changesAtStart) return@launch

            when (result) {
                is AppResult.Success -> setState { copy(profile = result.value, isLoading = false, loadError = null) }
                is AppResult.Failure -> setState {
                    copy(isLoading = false, loadError = if (profile == null) result.error else null)
                }
            }
        }
    }


    private fun selectTheme(theme: ThemePreference) {
        val state = currentState
        val profile = state.profile ?: return
        if (state.isUpdating || theme == profile.user.themePreference) return

        setState { copy(pendingTheme = theme, themeError = null) }
        viewModelScope.launch {
            when (val result = updateProfile(themePreference = theme)) {
                is AppResult.Success -> {
                    changeCount++
                    setState { copy(profile = result.value, pendingTheme = null) }
                }
                is AppResult.Failure -> setState { copy(pendingTheme = null, themeError = result.error) }
            }
        }
    }


    private fun saveName() {
        val state = currentState
        val edit = state.nameEdit ?: return
        val profile = state.profile ?: return
        if (state.isUpdating) return

        if (edit.value.trim() == profile.user.fullName) {
            setState { copy(nameEdit = null) }
            return
        }

        setState { copy(nameEdit = nameEdit?.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            when (val result = updateProfile(fullName = edit.value)) {
                is AppResult.Success -> {
                    changeCount++
                    setState { copy(profile = result.value, nameEdit = null) }
                }
                is AppResult.Failure -> setState {
                    copy(nameEdit = nameEdit?.copy(isSaving = false, error = result.error))
                }
            }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = UpdateProfileUseCase.MAX_NAME_LENGTH
    }
}