package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.repository.AuthRepository

class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}