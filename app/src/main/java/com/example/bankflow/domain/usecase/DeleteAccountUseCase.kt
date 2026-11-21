package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.repository.ProfileRepository
import org.koin.core.annotation.Factory

@Factory
class DeleteAccountUseCase(
    private val repository: ProfileRepository,
) {

    suspend operator fun invoke(password: String): AppResult<Unit> {
        // Deliberately not trimmed: a password may legitimately start or end with a space.
        if (password.isEmpty()) {
            return AppResult.Failure(AppError.Validation(issues = mapOf("password" to listOf(FieldIssue.PasswordRequired))))
        }
        return repository.deleteAccount(password)
    }
}