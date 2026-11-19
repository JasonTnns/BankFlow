package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Recipient
import com.example.bankflow.domain.repository.AccountRepository
import org.koin.core.annotation.Factory

@Factory
class LookupRecipientUseCase(
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(accountNumber: String): AppResult<Recipient> {
        val clean = accountNumber.trim()
        if (!ACCOUNT_NUMBER.matches(clean)) {
            return AppResult.Failure(
                AppError.Validation(issues = mapOf("destination" to listOf(FieldIssue.AccountNumberInvalid))),
            )
        }
        return accountRepository.lookupRecipient(clean)
    }

    private companion object {
        val ACCOUNT_NUMBER = Regex("""^\d{12}$""")
    }
}