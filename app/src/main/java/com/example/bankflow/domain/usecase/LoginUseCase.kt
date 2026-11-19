package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.AuthRepository
import org.koin.core.annotation.Factory

@Factory
class LoginUseCase(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(email: String, password: String): AppResult<User> {
        val cleanEmail = email.trim().lowercase()
        val issues = linkedMapOf<String, List<FieldIssue>>()

        if (cleanEmail.isEmpty()) issues["email"] = listOf(FieldIssue.EmailRequired)
        if (password.isEmpty()) issues["password"] = listOf(FieldIssue.PasswordRequired)

        if (issues.isNotEmpty()) return AppResult.Failure(AppError.Validation(issues = issues))
        return authRepository.login(cleanEmail, password)
    }
}