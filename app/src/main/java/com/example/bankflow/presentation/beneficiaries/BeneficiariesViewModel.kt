package com.example.bankflow.presentation.beneficiaries

import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.toAsciiDigits
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.usecase.AddBeneficiaryUseCase
import com.example.bankflow.domain.usecase.DeleteBeneficiaryUseCase
import com.example.bankflow.domain.usecase.GetBeneficiariesUseCase
import com.example.bankflow.presentation.common.withoutFieldError
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class BeneficiariesViewModel(
    private val getBeneficiaries: GetBeneficiariesUseCase,
    private val addBeneficiary: AddBeneficiaryUseCase,
    private val deleteBeneficiary: DeleteBeneficiaryUseCase,
) : MviViewModel<BeneficiariesState, BeneficiariesIntent, NoEffect>(BeneficiariesState()) {

    init {
        load()
    }

    override fun handleIntent(intent: BeneficiariesIntent) {
        when (intent) {
            BeneficiariesIntent.Retry -> load()

            BeneficiariesIntent.OpenAdd -> setState { copy(addForm = AddRecipientForm(), actionError = null) }
            BeneficiariesIntent.DismissAdd -> setState { copy(addForm = null) }
            is BeneficiariesIntent.NicknameChanged -> editForm {
                copy(nickname = intent.text.take(MAX_NICKNAME_LENGTH), error = error.withoutFieldError("nickname"))
            }
            is BeneficiariesIntent.NumberChanged -> editForm {
                copy(
                    accountNumber = intent.text.toAsciiDigits().filter { it in '0'..'9' }.take(ACCOUNT_NUMBER_LENGTH),
                    error = error.withoutNumberError(),
                )
            }
            BeneficiariesIntent.SaveNew -> saveNew()

            is BeneficiariesIntent.AskDelete -> setState { copy(pendingDelete = intent.beneficiary, actionError = null) }
            BeneficiariesIntent.CancelDelete -> setState { copy(pendingDelete = null) }
            BeneficiariesIntent.ConfirmDelete -> confirmDelete()
        }
    }

    private fun load() {
        if (currentState.isLoading) return
        setState { copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = getBeneficiaries()) {
                is AppResult.Success -> setState { copy(items = result.value, hasLoaded = true, isLoading = false) }
                is AppResult.Failure -> setState { copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun saveNew() {
        val form = currentState.addForm ?: return
        if (form.isSaving) return

        editForm { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = addBeneficiary(form.nickname, form.accountNumber)) {
                is AppResult.Success -> setState { copy(items = (items + result.value).sorted(), addForm = null) }
                is AppResult.Failure -> editForm { copy(isSaving = false, error = result.error) }
            }
        }
    }

    private fun confirmDelete() {
        val target = currentState.pendingDelete ?: return
        if (currentState.isDeleting) return

        setState { copy(isDeleting = true, actionError = null) }
        viewModelScope.launch {
            val result = deleteBeneficiary(target.id)
            val gone = result is AppResult.Success || (result as? AppResult.Failure)?.error == AppError.NotFound
            if (gone) {
                setState { copy(items = items.filterNot { it.id == target.id }, pendingDelete = null, isDeleting = false) }
            } else {
                setState {
                    copy(pendingDelete = null, isDeleting = false, actionError = (result as AppResult.Failure).error)
                }
            }
        }
    }

    private fun editForm(edit: AddRecipientForm.() -> AddRecipientForm) = setState { copy(addForm = addForm?.edit()) }

    private fun AppError?.withoutNumberError(): AppError? = when (this) {
        AppError.NotFound, AppError.BeneficiaryExists -> null
        else -> withoutFieldError("accountNumber")
    }

    private fun List<Beneficiary>.sorted(): List<Beneficiary> =
        sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nickname })

    private companion object {
        const val MAX_NICKNAME_LENGTH = 40
        const val ACCOUNT_NUMBER_LENGTH = 12
    }
}