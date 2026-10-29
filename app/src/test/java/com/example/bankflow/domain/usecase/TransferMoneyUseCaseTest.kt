package com.example.bankflow.domain.usecase

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Account
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.domain.repository.TransferRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransferMoneyUseCaseTest {

    private val repository = FakeTransferRepository()
    private val useCase = TransferMoneyUseCase(repository)


    @Test
    fun `valid transfer is sent and the server's result is returned unchanged`() = runTest {
        val result = useCase(request(amount = 25_00), account(balance = 100_000))

        assertTrue(result is AppResult.Success)
        assertEquals(97_500L, (result as AppResult.Success).value.sourceAccountBalance.minorUnits)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun `the idempotency key is passed through untouched`() = runTest {
        useCase(request(key = "key-123"), account())
        assertEquals("key-123", repository.requests.single().idempotencyKey)
    }

    @Test
    fun `an amount equal to the balance is allowed`() = runTest {
        val result = useCase(request(amount = 100_000), account(balance = 100_000))
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `text fields are trimmed and a blank description becomes null`() = runTest {
        useCase(
            request(
                destination = TransferDestination.AccountNumber(" 200000000001 "),
                description = "   ",
            ),
            account(),
        )

        val sent = repository.requests.single()
        assertEquals(TransferDestination.AccountNumber("200000000001"), sent.destination)
        assertNull(sent.description)
    }

    @Test
    fun `transfer to one of the user's own accounts is allowed`() = runTest {
        val result = useCase(
            request(destination = TransferDestination.OwnAccount("acc-2")),
            account(),
        )
        assertTrue(result is AppResult.Success)
    }


    @Test
    fun `zero and negative amounts are rejected without calling the server`() = runTest {
        listOf(0L, -100L).forEach { minor ->
            assertValidationFailure(useCase(request(amount = minor), account()), "amount")
        }
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `an amount above the per-transfer maximum is rejected`() = runTest {
        val result = useCase(request(amount = Money.MAX_TRANSFER_MINOR + 1), account(balance = Long.MAX_VALUE / 2))
        assertValidationFailure(result, "amount")
    }

    @Test
    fun `an amount in another currency is rejected`() = runTest {
        val euros = request().copy(amount = Money(500, "EUR"))
        assertValidationFailure(useCase(euros, account()), "amount")
    }


    @Test
    fun `account numbers must be exactly twelve digits`() = runTest {
        listOf("12345678901", "1234567890123", "20000000000a", "", "2000 0000 0001").forEach { number ->
            val result = useCase(
                request(destination = TransferDestination.AccountNumber(number)),
                account(),
            )
            assertValidationFailure(result, "destination")
        }
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `sending to the same account is rejected, by id or by number`() = runTest {
        val byId = useCase(request(destination = TransferDestination.OwnAccount("acc-1")), account(id = "acc-1"))
        val byNumber = useCase(
            request(destination = TransferDestination.AccountNumber("100000000001")),
            account(number = "100000000001"),
        )

        assertEquals(AppError.SameAccount, (byId as AppResult.Failure).error)
        assertEquals(AppError.SameAccount, (byNumber as AppResult.Failure).error)
        assertTrue(repository.requests.isEmpty())
    }


    @Test
    fun `insufficient funds is caught locally`() = runTest {
        val result = useCase(request(amount = 100_001), account(balance = 100_000))

        assertEquals(AppError.InsufficientFunds, (result as AppResult.Failure).error)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `a frozen source account is rejected`() = runTest {
        val result = useCase(request(), account(status = AccountStatus.FROZEN))
        assertEquals(AppError.AccountInactive, (result as AppResult.Failure).error)
    }


    @Test
    fun `a description over 140 characters is rejected`() = runTest {
        val result = useCase(request(description = "x".repeat(141)), account())
        assertValidationFailure(result, "description")
    }

    @Test
    fun `a missing idempotency key is rejected`() = runTest {
        assertValidationFailure(useCase(request(key = " "), account()), "idempotencyKey")
    }

    @Test
    fun `a request for a different source account than the one given is rejected`() = runTest {
        val result = useCase(request(sourceId = "other"), account(id = "acc-1"))
        assertValidationFailure(result, "sourceAccountId")
    }

    @Test
    fun `several problems are reported together`() = runTest {
        val result = useCase(
            request(amount = 0, destination = TransferDestination.AccountNumber("abc")),
            account(),
        )
        val fields = ((result as AppResult.Failure).error as AppError.Validation).fieldErrors.keys
        assertEquals(setOf("amount", "destination"), fields)
    }


    @Test
    fun `server failures are passed through unchanged`() = runTest {
        val limit = AppError.LimitExceeded(limit = Money(500_000), usedToday = Money(450_000), remaining = Money(50_000))
        repository.response = AppResult.Failure(limit)

        val result = useCase(request(), account())

        assertEquals(limit, (result as AppResult.Failure).error)
    }


    private fun assertValidationFailure(result: AppResult<*>, field: String) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected a validation failure but was $result", error is AppError.Validation)
        assertTrue(
            "Expected a message for '$field' but got ${(error as AppError.Validation).fieldErrors}",
            error.fieldErrors.containsKey(field),
        )
    }

    private fun account(
        id: String = "acc-1",
        number: String = "100000000001",
        balance: Long = 100_000,
        status: AccountStatus = AccountStatus.ACTIVE,
    ) = Account(
        id = id,
        name = "Everyday Checking",
        type = AccountType.CHECKING,
        accountNumber = number,
        balance = Money(balance),
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun request(
        amount: Long = 2_500,
        key: String = "key-1",
        sourceId: String = "acc-1",
        destination: TransferDestination = TransferDestination.AccountNumber("200000000001"),
        description: String? = null,
    ) = TransferRequest(
        idempotencyKey = key,
        sourceAccountId = sourceId,
        destination = destination,
        amount = Money(amount),
        description = description,
    )

    private class FakeTransferRepository : TransferRepository {
        val requests = mutableListOf<TransferRequest>()

        var response: AppResult<TransferResult>? = null

        override suspend fun transfer(request: TransferRequest): AppResult<TransferResult> {
            requests += request
            return response ?: AppResult.Success(
                TransferResult(
                    transfer = Transfer(
                        id = "transfer-1",
                        kind = TransferKind.TRANSFER,
                        status = TransferStatus.COMPLETED,
                        failureReason = null,
                        sourceAccountId = request.sourceAccountId,
                        destinationAccountId = "dest-1",
                        amount = request.amount,
                        category = Category.TRANSFER,
                        description = request.description,
                        createdAt = Instant.parse("2026-10-29T10:00:00Z"),
                        completedAt = Instant.parse("2026-10-29T10:00:00Z"),
                    ),
                    sourceAccountBalance = Money(100_000 - request.amount.minorUnits),
                ),
            )
        }

        override suspend fun getTransfer(transferId: String): AppResult<Transfer> =
            AppResult.Failure(AppError.NotFound)
    }
}