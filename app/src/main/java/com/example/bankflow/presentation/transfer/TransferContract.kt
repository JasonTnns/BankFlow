package com.example.bankflow.presentation.transfer

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.fieldError
import com.example.bankflow.presentation.common.fieldText
import com.example.bankflow.presentation.common.toBanner
import com.example.bankflow.presentation.mvi.UiIntent
import com.example.bankflow.presentation.mvi.UiState

enum class TransferStep { FORM, REVIEW, SUCCESS, FAILED }

enum class DestinationMode { OTHER_PERSON, OWN_ACCOUNT }

data class TransferReview(
    val request: TransferRequest,
    val source: Account,
    val recipientName: String,
    val recipientDetail: String,
    val isOwnAccount: Boolean,
)

sealed interface SaveRecipientState {
    data object Idle : SaveRecipientState
    data object Saving : SaveRecipientState
    data object Saved : SaveRecipientState
    data class Failed(val error: AppError) : SaveRecipientState
}

data class TransferState(
    val step: TransferStep = TransferStep.FORM,

    val accounts: List<Account> = emptyList(),
    val isLoadingAccounts: Boolean = true,
    val accountsError: AppError? = null,

    val beneficiaries: List<Beneficiary> = emptyList(),

    val sourceAccountId: String? = null,
    val destinationMode: DestinationMode = DestinationMode.OTHER_PERSON,
    val ownDestinationId: String? = null,
    val accountNumber: String = "",
    val amountText: String = "",
    val category: Category = Category.TRANSFER,
    val note: String = "",
    val isPreparing: Boolean = false,
    val formError: AppError? = null,

    val review: TransferReview? = null,
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,

    val result: TransferResult? = null,
    val failure: AppError? = null,
    val saveRecipient: SaveRecipientState = SaveRecipientState.Idle,
) : UiState {
    val sourceAccount: Account? get() = accounts.firstOrNull { it.id == sourceAccountId }

    val ownDestinationOptions: List<Account>
        get() = accounts.filter { it.id != sourceAccountId && it.isActive }

    val unsavedRecipientNumber: String?
        get() {
            val paid = review ?: return null
            if (paid.isOwnAccount) return null
            val number = (paid.request.destination as? TransferDestination.AccountNumber)?.number ?: return null
            return number.takeIf { candidate -> beneficiaries.none { it.accountNumber == candidate } }
        }

    val amountError: UiText? get() = formError.fieldText("amount")
    val destinationError: UiText? get() = formError.fieldText("destination")
    val noteError: UiText? get() = formError.fieldText("description")
    val banner: AppError? get() = formError.toBanner()
}

sealed interface TransferIntent : UiIntent {
    data object RetryLoadAccounts : TransferIntent

    data object RefreshRecipients : TransferIntent
    data class RecipientSelected(val beneficiary: Beneficiary) : TransferIntent

    data class SourceSelected(val accountId: String) : TransferIntent
    data class DestinationModeChanged(val mode: DestinationMode) : TransferIntent
    data class OwnDestinationSelected(val accountId: String) : TransferIntent
    data class AccountNumberChanged(val text: String) : TransferIntent
    data class AmountChanged(val text: String) : TransferIntent
    data class CategorySelected(val category: Category) : TransferIntent
    data class NoteChanged(val text: String) : TransferIntent

    data object Continue : TransferIntent
    data object BackToForm : TransferIntent

    data object Confirm : TransferIntent

    data object EditAfterFailure : TransferIntent

    data object SaveRecipient : TransferIntent

    data object StartNew : TransferIntent
}