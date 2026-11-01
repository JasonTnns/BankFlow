package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
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

    private fun validate(request: TransferRequest, source: Account): AppError? {
        val fields = linkedMapOf<String, List<String>>()

        if (request.idempotencyKey.isBlank()) {
            fields["idempotencyKey"] = listOf("Missing idempotency key")
        }
        if (request.sourceAccountId != source.id) {
            fields["sourceAccountId"] = listOf("Source account does not match")
        }

        val amount = request.amount
        when {
            amount.currency != source.balance.currency ->
                fields["amount"] = listOf("Amount currency must match the account")
            amount.minorUnits <= 0 ->
                fields["amount"] = listOf("Enter an amount greater than zero")
            amount.minorUnits > Money.MAX_TRANSFER_MINOR ->
                fields["amount"] = listOf("Amount is above the maximum for one transfer")
        }

        when (val destination = request.destination) {
            is TransferDestination.AccountNumber ->
                if (!ACCOUNT_NUMBER.matches(destination.number.trim())) {
                    fields["destination"] = listOf("Account number must be 12 digits")
                }
            is TransferDestination.OwnAccount ->
                if (destination.accountId.isBlank()) {
                    fields["destination"] = listOf("Choose a destination account")
                }
        }

        val description = request.description?.trim()
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            fields["description"] = listOf("Description is too long (max $MAX_DESCRIPTION_LENGTH characters)")
        }

        if (fields.isNotEmpty()) return AppError.Validation(fieldErrors = fields)

        val sameAccount = when (val destination = request.destination) {
            is TransferDestination.OwnAccount -> destination.accountId == source.id
            is TransferDestination.AccountNumber -> destination.number.trim() == source.accountNumber
        }
        if (sameAccount) return AppError.SameAccount
        if (!source.isActive) return AppError.AccountInactive
        if (source.balance < amount) return AppError.InsufficientFunds
        return null
    }

    private fun normalize(request: TransferRequest): TransferRequest = request.copy(
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