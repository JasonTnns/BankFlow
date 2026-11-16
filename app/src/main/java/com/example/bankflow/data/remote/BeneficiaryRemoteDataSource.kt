package com.example.bankflow.data.remote

import com.example.bankflow.core.network.apiCall
import com.example.bankflow.core.network.apiCallUnit
import com.example.bankflow.data.remote.dto.AddBeneficiaryRequestDto
import com.example.bankflow.data.remote.dto.BeneficiariesResponseDto
import com.example.bankflow.data.remote.dto.BeneficiaryResponseDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Beneficiary
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class BeneficiaryRemoteDataSource(private val client: HttpClient) {

    suspend fun getBeneficiaries(): AppResult<List<Beneficiary>> =
        apiCall<BeneficiariesResponseDto, List<Beneficiary>>(
            request = { client.get("beneficiaries") },
            map = { dto -> dto.beneficiaries.map { it.toDomain() } },
        )

    suspend fun addBeneficiary(nickname: String, accountNumber: String): AppResult<Beneficiary> =
        apiCall<BeneficiaryResponseDto, Beneficiary>(
            request = {
                client.post("beneficiaries") {
                    contentType(ContentType.Application.Json)
                    setBody(AddBeneficiaryRequestDto(nickname, accountNumber))
                }
            },
            map = { it.beneficiary.toDomain() },
        )

    suspend fun deleteBeneficiary(id: String): AppResult<Unit> =
        apiCallUnit { client.delete("beneficiaries/$id") }
}