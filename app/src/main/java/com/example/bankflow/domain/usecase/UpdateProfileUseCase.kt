package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.repository.ProfileRepository
import org.koin.core.annotation.Factory

@Factory
class UpdateProfileUseCase(
    private val repository: ProfileRepository,
) {

    suspend operator fun invoke(
        fullName: String? = null,
        themePreference: ThemePreference? = null,
    ): AppResult<Profile> {
        if (fullName == null && themePreference == null) return AppResult.Failure(AppError.Validation())

        val cleanName = fullName?.trim()
        if (cleanName != null) {
            val issue = when {
                cleanName.isEmpty() -> FieldIssue.NameRequired
                cleanName.length > MAX_NAME_LENGTH -> FieldIssue.NameTooLong(MAX_NAME_LENGTH)
                else -> null
            }
            if (issue != null) {
                return AppResult.Failure(AppError.Validation(issues = mapOf("fullName" to listOf(issue))))
            }
        }

        return repository.updateProfile(cleanName, themePreference)
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}