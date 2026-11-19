package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.repository.TransferRepository
import org.koin.core.annotation.Factory

@Factory
class TransferMoneyUseCase(
    private val transferRepository: TransferRepository,
) {

    suspend operator fun invoke(
        request: TransferRequest,
        sourceAccount: Account,
    ): AppResult<TransferResult> {
        validate(request, sourceAccount)?.let { return AppResult.Failure(it) }
        return transferRepository.transfer(normalize(request))
    }

    fun validate(request: TransferRequest, source: Account): AppError? {
        val issues = linkedMapOf<String, List<FieldIssue>>()

        if (request.idempotencyKey.isBlank()) issues["idempotencyKey"] = listOf(FieldIssue.Invalid)
        if (request.sourceAccountId != source.id) issues["sourceAccountId"] = listOf(FieldIssue.Invalid)

        val amount = request.amount
        when {
            amount.currency != source.balance.currency -> issues["amount"] = listOf(FieldIssue.Invalid)
            amount.minorUnits <= 0 -> issues["amount"] = listOf(FieldIssue.AmountNotPositive)
            amount.minorUnits > Money.MAX_TRANSFER_MINOR -> issues["amount"] = listOf(FieldIssue.AmountTooLarge)
        }

        when (val destination = request.destination) {
            is TransferDestination.AccountNumber ->
                if (!ACCOUNT_NUMBER.matches(destination.number.trim())) {
                    issues["destination"] = listOf(FieldIssue.AccountNumberInvalid)
                }
            is TransferDestination.OwnAccount ->
                if (destination.accountId.isBlank()) issues["destination"] = listOf(FieldIssue.DestinationRequired)
        }

        val description = request.description?.trim()
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            issues["description"] = listOf(FieldIssue.DescriptionTooLong(MAX_DESCRIPTION_LENGTH))
        }

        if (issues.isNotEmpty()) return AppError.Validation(issues = issues)

        val sameAccount = when (val destination = request.destination) {
            is TransferDestination.OwnAccount -> destination.accountId == source.id
            is TransferDestination.AccountNumber -> destination.number.trim() == source.accountNumber
        }
        if (sameAccount) return AppError.SameAccount
        if (!source.isActive) return AppError.AccountInactive
        if (source.balance < amount) return AppError.InsufficientFunds
        return null
    }

    fun normalize(request: TransferRequest): TransferRequest = request.copy(
        destination = when (val destination = request.destination) {
            is TransferDestination.AccountNumber -> TransferDestination.AccountNumber(destination.number.trim())
            is TransferDestination.OwnAccount -> destination
        },
        description = request.description?.trim()?.takeIf { it.isNotEmpty() },
    )

    private companion object {
        val ACCOUNT_NUMBER = Regex("""^\d{12}$""")
        const val MAX_DESCRIPTION_LENGTH = 140
    }
}