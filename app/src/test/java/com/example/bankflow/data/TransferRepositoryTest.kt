package com.example.bankflow.data

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.data.repository.TransferRepositoryImpl
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.testutil.FakeAccountChangeNotifier
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransferRepositoryTest {

    private val notifier = FakeAccountChangeNotifier()

    private fun repository(handler: MockRequestHandler) = TransferRepositoryImpl(
        remote = BankRemoteDataSource(testClient(FakeSessionStore(storedSession("token", "refresh")), handler)),
        changes = notifier,
    )

    private val request = TransferRequest(
        idempotencyKey = "key-1",
        sourceAccountId = "acc-1",
        destination = TransferDestination.AccountNumber("200000000001"),
        amount = Money(2_500),
    )

    private val transferJson = """
        {"transfer":{"id":"t-1","kind":"TRANSFER","status":"COMPLETED","failureReason":null,
          "sourceAccountId":"acc-1","destinationAccountId":"acc-2","amountMinor":2500,"currency":"USD",
          "category":"TRANSFER","description":null,"createdAt":"2026-10-29T10:00:00.000Z",
          "completedAt":"2026-10-29T10:00:00.000Z"},"sourceAccountBalanceMinor":97500}
    """.trimIndent()

    @Test
    fun `a completed transfer tells the rest of the app that balances changed`() = runBlocking {
        val result = repository { json(transferJson, HttpStatusCode.Created) }.transfer(request)

        assertEquals(Money(97_500), (result as AppResult.Success).value.sourceAccountBalance)
        assertEquals(1, notifier.notifications)
    }

    @Test
    fun `a replayed transfer also notifies, which is harmless`() = runBlocking {
        repository { json(transferJson, HttpStatusCode.OK) }.transfer(request)

        assertEquals(1, notifier.notifications)
    }

    @Test
    fun `a definite rejection changes nothing, so nothing is announced`() = runBlocking {
        val result = repository { json(errorJson("INSUFFICIENT_FUNDS"), HttpStatusCode.UnprocessableEntity) }
            .transfer(request)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, notifier.notifications)
    }

    @Test
    fun `an unknown outcome announces a change, because the transfer may have gone through`() = runBlocking {
        repository { throw IOException("connection dropped") }.transfer(request)

        assertEquals(1, notifier.notifications)
    }

    @Test
    fun `a server error also announces a change`() = runBlocking {
        repository { json(errorJson("INTERNAL_ERROR"), HttpStatusCode.InternalServerError) }.transfer(request)

        assertEquals(1, notifier.notifications)
    }
}