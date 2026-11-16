package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
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
        if (fullName == null && themePreference == null) {
            return AppResult.Failure(AppError.Validation(message = "Nothing to update"))
        }

        val cleanName = fullName?.trim()
        if (cleanName != null) {
            val problem = when {
                cleanName.isEmpty() -> "Full name is required"
                cleanName.length > MAX_NAME_LENGTH -> "Full name is too long (max $MAX_NAME_LENGTH characters)"
                else -> null
            }
            if (problem != null) {
                return AppResult.Failure(AppError.Validation(fieldErrors = mapOf("fullName" to listOf(problem))))
            }
        }

        return repository.updateProfile(cleanName, themePreference)
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}