package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import org.koin.core.annotation.Factory

@Factory
class RegisterUseCase(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(email: String, password: String, fullName: String): AppResult<User> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = fullName.trim()
        val issues = linkedMapOf<String, List<FieldIssue>>()

        when {
            cleanEmail.isEmpty() -> issues["email"] = listOf(FieldIssue.EmailRequired)
            cleanEmail.length > 254 || !EMAIL.matches(cleanEmail) -> issues["email"] = listOf(FieldIssue.EmailInvalid)
        }

        val passwordProblems = buildList<FieldIssue> {
            if (password.length < MIN_PASSWORD_LENGTH) add(FieldIssue.PasswordTooShort(MIN_PASSWORD_LENGTH))
            if (password.none { it.isLetter() } || password.none { it.isDigit() }) {
                add(FieldIssue.PasswordNeedsLetterAndNumber)
            }
            if (password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) add(FieldIssue.PasswordTooLong)
        }
        if (passwordProblems.isNotEmpty()) issues["password"] = passwordProblems

        when {
            cleanName.isEmpty() -> issues["fullName"] = listOf(FieldIssue.NameRequired)
            cleanName.length > MAX_NAME_LENGTH -> issues["fullName"] = listOf(FieldIssue.NameTooLong(MAX_NAME_LENGTH))
        }

        if (issues.isNotEmpty()) return AppResult.Failure(AppError.Validation(issues = issues))
        return authRepository.register(cleanEmail, password, cleanName)
    }

    private companion object {
        val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_BYTES = 72
        const val MAX_NAME_LENGTH = 100
    }
}