package com.example.bankflow.presentation.beneficiaries

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.fieldError
import com.example.bankflow.presentation.common.fieldText
import com.example.bankflow.presentation.common.toText
import com.example.bankflow.presentation.common.userText
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

data class AddRecipientForm(
    val nickname: String = "",
    val accountNumber: String = "",
    val isSaving: Boolean = false,
    val error: AppError? = null,
) {
    val nicknameError: UiText? get() = error.fieldText("nickname")

    val accountNumberError: UiText?
        get() = error.fieldText("accountNumber") ?: when (error) {
            AppError.NotFound -> FieldIssue.DestinationNotFound.toText()
            AppError.BeneficiaryExists -> AppError.BeneficiaryExists.userText()
            else -> null
        }

    val banner: AppError?
        get() = when (val e = error) {
            null, AppError.NotFound, AppError.BeneficiaryExists -> null
            is AppError.Validation -> if (e.fields.isEmpty()) e else null
            else -> e
        }
}

data class BeneficiariesState(
    val items: List<Beneficiary> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: AppError? = null,
    val addForm: AddRecipientForm? = null,
    val pendingDelete: Beneficiary? = null,
    val isDeleting: Boolean = false,
    val actionError: AppError? = null,
) : UiState {
    val isEmpty: Boolean get() = hasLoaded && items.isEmpty()
}

sealed interface BeneficiariesIntent : UiIntent {
    data object Retry : BeneficiariesIntent

    data object OpenAdd : BeneficiariesIntent
    data object DismissAdd : BeneficiariesIntent
    data class NicknameChanged(val text: String) : BeneficiariesIntent
    data class NumberChanged(val text: String) : BeneficiariesIntent
    data object SaveNew : BeneficiariesIntent

    data class AskDelete(val beneficiary: Beneficiary) : BeneficiariesIntent
    data object CancelDelete : BeneficiariesIntent
    data object ConfirmDelete : BeneficiariesIntent
}