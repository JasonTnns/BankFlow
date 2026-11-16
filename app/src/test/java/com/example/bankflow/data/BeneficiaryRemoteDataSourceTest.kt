package com.example.bankflow.data

import com.example.bankflow.data.remote.BeneficiaryRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.AccountStatus
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeneficiaryRemoteDataSourceTest {

    private val signedIn = storedSession("token", "refresh")

    private fun source(handler: io.ktor.client.engine.mock.MockRequestHandler) =
        BeneficiaryRemoteDataSource(testClient(FakeSessionStore(signedIn), handler))

    private val beneficiaryJson = """
        {"id":"b1","nickname":"Mum","accountNumber":"200000000001","accountNumberMasked":"••••0001",
         "displayName":"Bob D.","currency":"USD","status":"FROZEN","createdAt":"2026-10-01T00:00:00.000Z"}
    """.trimIndent()

    @Test
    fun `the list is mapped, including a frozen account`() = runBlocking {
        val result = source { json("""{"beneficiaries":[$beneficiaryJson]}""") }.getBeneficiaries()

        val recipient = (result as AppResult.Success).value.single()
        assertEquals("Mum", recipient.nickname)
        assertEquals("200000000001", recipient.accountNumber)
        assertEquals(AccountStatus.FROZEN, recipient.status)
        assertEquals(false, recipient.isActive)
    }

    @Test
    fun `adding sends the nickname and number and maps the answer`() = runBlocking {
        var body: String? = null
        var path: String? = null
        val source = source { request ->
            path = request.url.encodedPath
            body = (request.body as TextContent).text
            json("""{"beneficiary":$beneficiaryJson}""", HttpStatusCode.Created)
        }

        val result = source.addBeneficiary("Mum", "200000000001")

        assertTrue(path!!.endsWith("/beneficiaries"))
        assertTrue(body!!.contains("\"nickname\":\"Mum\""))
        assertTrue(body!!.contains("\"accountNumber\":\"200000000001\""))
        assertEquals("b1", (result as AppResult.Success).value.id)
    }

    @Test
    fun `a duplicate maps to BeneficiaryExists`() = runBlocking {
        val result = source { json(errorJson("BENEFICIARY_EXISTS"), HttpStatusCode.Conflict) }
            .addBeneficiary("Mum", "200000000001")

        assertEquals(AppError.BeneficiaryExists, (result as AppResult.Failure).error)
    }

    @Test
    fun `an unknown number maps to NotFound`() = runBlocking {
        val result = source { json(errorJson("NOT_FOUND"), HttpStatusCode.NotFound) }
            .addBeneficiary("Ghost", "999999999999")

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
    }

    @Test
    fun `deleting uses DELETE on the recipient's own path and accepts an empty answer`() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val result = source { request ->
            method = request.method
            path = request.url.encodedPath
            respond("", HttpStatusCode.NoContent)
        }.deleteBeneficiary("b1")

        assertEquals(HttpMethod.Delete, method)
        assertTrue(path!!.endsWith("/beneficiaries/b1"))
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `deleting a recipient that does not exist maps to NotFound`() = runBlocking {
        val result = source { json(errorJson("NOT_FOUND"), HttpStatusCode.NotFound) }.deleteBeneficiary("nope")

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
    }
}