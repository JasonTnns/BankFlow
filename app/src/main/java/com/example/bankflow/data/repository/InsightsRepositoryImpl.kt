package com.example.bankflow.data.repository

import com.example.bankflow.data.remote.InsightsRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.SpendingSummary
import com.example.bankflow.domain.repository.InsightsRepository
import org.koin.core.annotation.Single
import java.time.Instant


@Single
class InsightsRepositoryImpl(
    private val remote: InsightsRemoteDataSource
): InsightsRepository {
    override suspend fun getSummary(
        from: Instant,
        to: Instant,
        currency: String
    ): AppResult<SpendingSummary> = remote.getSummary(from, to, currency)
}