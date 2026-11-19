package com.example.bankflow.presentation.transfer

import com.example.bankflow.domain.usecase.AddBeneficiaryUseCase
import com.example.bankflow.domain.usecase.GetBeneficiariesUseCase
import androidx.lifecycle.viewModelScope
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.common.IdGenerator
import com.example.bankflow.domain.common.isOutcomeUnknown
import com.example.bankflow.domain.common.toAsciiDigits
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.usecase.GetAccountsUseCase
import com.example.bankflow.domain.usecase.LookupRecipientUseCase
import com.example.bankflow.domain.usecase.TransferMoneyUseCase
import com.example.bankflow.presentation.common.format
import com.example.bankflow.presentation.common.withoutFieldError
import com.example.bankflow.presentation.mvi.MviViewModel
import com.example.bankflow.presentation.mvi.NoEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant

@KoinViewModel
class TransferViewModel(
    private val getAccounts: GetAccountsUseCase,
    private val lookupRecipient: LookupRecipientUseCase,
    private val transferMoney: TransferMoneyUseCase,
    private val getBeneficiaries: GetBeneficiariesUseCase,
    private val addBeneficiary: AddBeneficiaryUseCase,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    changes: AccountChangeNotifier,
) : MviViewModel<TransferState, TransferIntent, NoEffect>(TransferState()) {

    private var unknownAttempt: Pair<TransferRequest, Instant>? = null
    private var accountsJob: Job? = null

    init {
        loadAccounts()
        viewModelScope.launch { changes.changes.collect { loadAccounts(silent = true) } }
    }

    override fun handleIntent(intent: TransferIntent) {
        when (intent) {
            TransferIntent.RetryLoadAccounts -> loadAccounts()
            TransferIntent.RefreshRecipients -> loadRecipients()
            is TransferIntent.RecipientSelected -> setState {
                copy(
                    destinationMode = DestinationMode.OTHER_PERSON,
                    accountNumber = intent.beneficiary.accountNumber,
                    formError = formError.withoutFieldError("destination"),
                )
            }
            TransferIntent.SaveRecipient -> saveRecipient()

            is TransferIntent.SourceSelected -> setState {
                copy(
                    sourceAccountId = intent.accountId,
                    ownDestinationId = ownDestinationId?.takeIf { it != intent.accountId },
                    formError = null,
                )
            }
            is TransferIntent.DestinationModeChanged -> setState {
                copy(destinationMode = intent.mode, formError = formError.withoutFieldError("destination"))
            }
            is TransferIntent.OwnDestinationSelected -> setState {
                copy(ownDestinationId = intent.accountId, formError = formError.withoutFieldError("destination"))
            }
            is TransferIntent.AccountNumberChanged -> setState {
                copy(
                    accountNumber = intent.text.filter { it.isDigit() }.take(ACCOUNT_NUMBER_LENGTH),
                    formError = formError.withoutFieldError("destination"),
                )
            }
            is TransferIntent.AmountChanged -> setState {
                copy(amountText = sanitizeAmount(intent.text), formError = formError.withoutFieldError("amount"))
            }
            is TransferIntent.CategorySelected -> setState { copy(category = intent.category) }
            is TransferIntent.NoteChanged -> setState {
                copy(note = intent.text.take(MAX_NOTE_LENGTH), formError = formError.withoutFieldError("description"))
            }

            TransferIntent.Continue -> prepareReview()
            TransferIntent.BackToForm -> setState { copy(step = TransferStep.FORM, review = null, submitError = null) }
            TransferIntent.Confirm -> confirm()
            TransferIntent.EditAfterFailure -> setState {
                copy(step = TransferStep.FORM, failure = null, review = null, submitError = null)
            }
            TransferIntent.StartNew -> startNew()
        }
    }


    private fun loadAccounts(silent: Boolean = false) {
        accountsJob?.cancel()
        if (!silent) setState { copy(isLoadingAccounts = true, accountsError = null) }

        accountsJob = viewModelScope.launch {
            when (val result = getAccounts()) {
                is AppResult.Success -> setState {
                    val loaded = result.value
                    val source = sourceAccountId?.takeIf { id -> loaded.any { it.id == id } }
                        ?: loaded.firstOrNull { it.isActive }?.id
                    copy(
                        accounts = loaded,
                        sourceAccountId = source,
                        ownDestinationId = ownDestinationId?.takeIf { id -> id != source && loaded.any { it.id == id } },
                        isLoadingAccounts = false,
                        accountsError = null,
                    )
                }
                is AppResult.Failure -> if (!silent) setState {
                    copy(isLoadingAccounts = false, accountsError = result.error)
                }
            }
        }
    }

    private fun startNew() {
        unknownAttempt = null
        setState {
            TransferState(
                accounts = accounts,
                isLoadingAccounts = false,
                sourceAccountId = accounts.firstOrNull { it.isActive }?.id,
                beneficiaries = beneficiaries,
            )
        }
        loadAccounts(silent = true)
    }

    private fun loadRecipients() {
        viewModelScope.launch {
            val result = getBeneficiaries()
            if (result is AppResult.Success) setState { copy(beneficiaries = result.value) }
        }
    }

    private fun saveRecipient() {
        val snapshot = currentState
        val number = snapshot.unsavedRecipientNumber ?: return
        val name = snapshot.review?.recipientName ?: return
        if (snapshot.saveRecipient == SaveRecipientState.Saving) return

        setState { copy(saveRecipient = SaveRecipientState.Saving) }
        viewModelScope.launch {
            when (val result = addBeneficiary(name, number)) {
                is AppResult.Success -> setState {
                    copy(
                        beneficiaries = beneficiaries.filterNot { it.id == result.value.id } + result.value,
                        saveRecipient = SaveRecipientState.Saved,
                    )
                }
                is AppResult.Failure ->
                    if (result.error == AppError.BeneficiaryExists) {
                        // Saved on another device in the meantime: the goal is reached.
                        setState { copy(saveRecipient = SaveRecipientState.Saved) }
                        loadRecipients()
                    } else {
                        setState { copy(saveRecipient = SaveRecipientState.Failed(result.error)) }
                    }
            }
        }
    }


    private fun prepareReview() {
        val snapshot = currentState
        if (snapshot.isPreparing || snapshot.isSubmitting || snapshot.step != TransferStep.FORM) return
        val sourceId = snapshot.sourceAccountId ?: return

        setState { copy(isPreparing = true, formError = null) }
        viewModelScope.launch {
            when (val outcome = buildReview(snapshot, sourceId)) {
                is AppResult.Success -> setState {
                    copy(isPreparing = false, step = TransferStep.REVIEW, review = outcome.value, submitError = null)
                }
                is AppResult.Failure -> setState { copy(isPreparing = false, formError = outcome.error) }
            }
        }
    }

    private suspend fun buildReview(s: TransferState, sourceId: String): AppResult<TransferReview> {
        val cached = s.accounts.firstOrNull { it.id == sourceId }
            ?: return AppResult.Failure(AppError.Validation())

        val amount = Money.parse(s.amountText, cached.balance.currency)
            ?: return failure("destination", FieldIssue.DestinationRequired)

        val destination = when (s.destinationMode) {
            DestinationMode.OWN_ACCOUNT -> TransferDestination.OwnAccount(s.ownDestinationId.orEmpty())
            DestinationMode.OTHER_PERSON -> TransferDestination.AccountNumber(s.accountNumber)
        }
        val draft = TransferRequest(
            idempotencyKey = idGenerator.newId(),
            sourceAccountId = cached.id,
            destination = destination,
            amount = amount,
            category = s.category,
            description = s.note,
        )

        (transferMoney.validate(draft, cached) as? AppError.Validation)?.let { return AppResult.Failure(it) }

        val source = when (val fresh = getAccounts(sourceId)) {
            is AppResult.Success -> fresh.value
            is AppResult.Failure -> return fresh
        }
        transferMoney.validate(draft, source)?.let { return AppResult.Failure(it.forForm(source)) }

        val request = reuseKeyIfSameAttempt(transferMoney.normalize(draft))

        // Who is receiving the money?
        return when (val target = request.destination) {
            is TransferDestination.OwnAccount -> {
                val account = s.accounts.firstOrNull { it.id == target.accountId }
                    ?: return failure("destination", "Choose a destination account")
                if (!account.isActive) {
                    AppResult.Failure(AppError.AccountInactive)
                } else {
                    AppResult.Success(
                        TransferReview(request, source, account.name, account.maskedNumber, isOwnAccount = true),
                    )
                }
            }
            is TransferIntent.AccountNumberChanged -> setState {
                copy(
                    accountNumber = intent.text.toAsciiDigits().filter { it in '0'..'9' }.take(ACCOUNT_NUMBER_LENGTH),
                    formError = formError.withoutFieldError("destination"),
                )
            }
        }
    }

    private fun AppError.forForm(source: Account): AppError = when (this) {
        AppError.SameAccount -> validation("destination", FieldIssue.DestinationIsSource)
        AppError.InsufficientFunds -> validation("amount", FieldIssue.InsufficientFunds(source.balance))
        else -> this
    }


    private fun reuseKeyIfSameAttempt(request: TransferRequest): TransferRequest {
        val (previous, sentAt) = unknownAttempt ?: return request
        val recent = Duration.between(sentAt, clock.instant()) <= UNKNOWN_ATTEMPT_WINDOW
        val sameTransfer = previous.copy(idempotencyKey = request.idempotencyKey) == request
        return if (recent && sameTransfer) request.copy(idempotencyKey = previous.idempotencyKey) else request
    }


    private fun confirm() {
        val snapshot = currentState
        val review = snapshot.review ?: return
        if (snapshot.step != TransferStep.REVIEW || snapshot.isSubmitting) return

        setState { copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = transferMoney(review.request, review.source)) {
                is AppResult.Success -> {
                    unknownAttempt = null
                    setState { copy(isSubmitting = false, step = TransferStep.SUCCESS, result = result.value) }
                }
                is AppResult.Failure -> {
                    val error = result.error
                    if (error.isOutcomeUnknown) {
                        unknownAttempt = review.request to clock.instant()
                        setState { copy(isSubmitting = false, submitError = error) }
                    } else {
                        unknownAttempt = null
                        setState { copy(isSubmitting = false, step = TransferStep.FAILED, failure = error) }
                    }
                }
            }
        }
    }


    private fun validation(field: String, issue: FieldIssue) =
        AppError.Validation(issues = mapOf(field to listOf(issue)))

    private fun failure(field: String, issue: FieldIssue) = AppResult.Failure(validation(field, issue))

    private fun sanitizeAmount(text: String): String {
        val cleaned = text.toAsciiDigits().replace(',', '.').filter { it in '0'..'9' || it == '.' }
        val firstDot = cleaned.indexOf('.')
        val singleDot = if (firstDot < 0) {
            cleaned
        } else {
            cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
        }
        return singleDot.take(MAX_AMOUNT_LENGTH)
    }

    companion object {
        const val ACCOUNT_NUMBER_LENGTH = 12
        const val MAX_NOTE_LENGTH = 140
        const val MAX_AMOUNT_LENGTH = 15
        val UNKNOWN_ATTEMPT_WINDOW: Duration = Duration.ofMinutes(15)
    }
}