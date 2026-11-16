package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.repository.ProfileRepository
import org.koin.core.annotation.Factory

@Factory
class GetProfileUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): AppResult<Profile> = repository.getProfile()
}