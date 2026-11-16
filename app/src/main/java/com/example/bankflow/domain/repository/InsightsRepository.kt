package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.SpendingSummary
import java.time.Instant

interface InsightsRepository {
    suspend fun getSummary(from: Instant, to: Instant, currency: String): AppResult<SpendingSummary>
}