package com.example.bankflow.data.remote

import com.example.bankflow.core.network.apiCall
import com.example.bankflow.data.remote.dto.SpendingSummaryResponseDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.SpendingSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.koin.core.annotation.Single
import java.time.Instant

@Single
class InsightsRemoteDataSource(private val client: HttpClient) {

    suspend fun getSummary(from: Instant, to: Instant, currency: String): AppResult<SpendingSummary> =
        apiCall<SpendingSummaryResponseDto, SpendingSummary>(
            request = {
                client.get("insights/summary") {
                    parameter("from", from.toString())
                    parameter("to", to.toString())
                    parameter("currency", currency)
                }
            },
            map = { it.summary.toDomain() },
        )
}