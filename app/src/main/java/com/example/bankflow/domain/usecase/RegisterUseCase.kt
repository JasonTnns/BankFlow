package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository

class RegisterUseCase(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(email: String, password: String, fullName: String): AppResult<User> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = fullName.trim()
        val fields = linkedMapOf<String, List<String>>()

        when {
            cleanEmail.isEmpty() -> fields["email"] = listOf("Email is required")
            cleanEmail.length > 254 || !EMAIL.matches(cleanEmail) ->
                fields["email"] = listOf("Enter a valid email address")
        }

        val passwordProblems = buildList {
            if (password.length < MIN_PASSWORD_LENGTH) add("Password must be at least $MIN_PASSWORD_LENGTH characters")
            if (password.none { it.isLetter() } || password.none { it.isDigit() }) {
                add("Password must contain a letter and a number")
            }
            if (password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) add("Password is too long")
        }
        if (passwordProblems.isNotEmpty()) fields["password"] = passwordProblems

        when {
            cleanName.isEmpty() -> fields["fullName"] = listOf("Full name is required")
            cleanName.length > 100 -> fields["fullName"] = listOf("Full name is too long")
        }

        if (fields.isNotEmpty()) return AppResult.Failure(AppError.Validation(fieldErrors = fields))
        return authRepository.register(cleanEmail, password, cleanName)
    }

    private companion object {
        val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_BYTES = 72
    }
}