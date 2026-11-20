package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
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
        val issues = linkedMapOf<String, List<FieldIssue>>()

        when {
            cleanNickname.isEmpty() -> issues["nickname"] = listOf(FieldIssue.NicknameRequired)
            cleanNickname.length > MAX_NICKNAME_LENGTH ->
                issues["nickname"] = listOf(FieldIssue.NicknameTooLong(MAX_NICKNAME_LENGTH))
        }
        if (!ACCOUNT_NUMBER.matches(cleanNumber)) issues["accountNumber"] = listOf(FieldIssue.AccountNumberInvalid)

        if (issues.isNotEmpty()) return AppResult.Failure(AppError.Validation(issues = issues))
        return repository.addBeneficiary(cleanNickname, cleanNumber)
    }

    companion object {
        const val MAX_BENEFICIARIES = 50
        const val MAX_NICKNAME_LENGTH = 40
        private val ACCOUNT_NUMBER = Regex("""^\d{12}$""")
    }
}