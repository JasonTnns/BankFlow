package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(email: String, password: String): AppResult<User> {
        val cleanEmail = email.trim().lowercase()
        val fields = linkedMapOf<String, List<String>>()

        if (cleanEmail.isEmpty()) fields["email"] = listOf("Email is required")
        if (password.isEmpty()) fields["password"] = listOf("Password is required")

        if (fields.isNotEmpty()) return AppResult.Failure(AppError.Validation(fieldErrors = fields))
        return authRepository.login(cleanEmail, password)
    }
}