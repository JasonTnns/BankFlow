package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.Beneficiary
import com.example.bankflow.domain.repository.BeneficiaryRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant

fun testBeneficiary(
    id: String = "b-1",
    nickname: String = "Bob",
    number: String = "200000000001",
    status: AccountStatus = AccountStatus.ACTIVE,
) = Beneficiary(
    id = id,
    nickname = nickname,
    accountNumber = number,
    accountNumberMasked = "\u2022\u2022\u2022\u2022" + number.takeLast(4),
    displayName = "Bob D.",
    currency = "USD",
    status = status,
    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
)

class StubBeneficiaryRepository : BeneficiaryRepository {
    var listResult: AppResult<List<Beneficiary>> = AppResult.Success(emptyList())
    var addResult: AppResult<Beneficiary>? = null
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

    var gate: CompletableDeferred<Unit>? = null

    var listCalls = 0
    val added = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()

    override suspend fun getBeneficiaries(): AppResult<List<Beneficiary>> {
        listCalls++
        return listResult
    }

    override suspend fun addBeneficiary(nickname: String, accountNumber: String): AppResult<Beneficiary> {
        added += nickname to accountNumber
        gate?.await()
        return addResult ?: AppResult.Success(testBeneficiary("b-new", nickname, accountNumber))
    }

    override suspend fun deleteBeneficiary(id: String): AppResult<Unit> {
        deleted += id
        gate?.await()
        return deleteResult
    }
}