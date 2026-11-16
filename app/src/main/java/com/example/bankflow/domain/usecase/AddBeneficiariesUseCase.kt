package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.repository.BeneficiaryRepository
import org.koin.core.annotation.Factory

@Factory
class AddBeneficiaryUseCase(
    private val repository: BeneficiaryRepository,
) {

    suspend operator fun invoke(nickname: String, accountNumber: String): AppResult<Beneficiary> {
        val cleanNickname = nickname.trim()
        val cleanNumber = accountNumber.trim()
        val fields = linkedMapOf<String, List<String>>()

        when {
            cleanNickname.isEmpty() -> fields["nickname"] = listOf("Give this recipient a name")
            cleanNickname.length > MAX_NICKNAME_LENGTH ->
                fields["nickname"] = listOf("Name is too long (max $MAX_NICKNAME_LENGTH characters)")
        }
        if (!ACCOUNT_NUMBER.matches(cleanNumber)) {
            fields["accountNumber"] = listOf("Account number must be 12 digits")
        }

        if (fields.isNotEmpty()) return AppResult.Failure(AppError.Validation(fieldErrors = fields))
        return repository.addBeneficiary(cleanNickname, cleanNumber)
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 40
        private val ACCOUNT_NUMBER = Regex("""^\d{12}$""")
    }
}