package com.example.bankflow.data

import com.example.bankflow.data.remote.AuthRemoteDataSource
import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class RemoteDataSourceTest {

    private val signedIn = storedSession("token", "refresh")


    @Test
    fun `dashboard JSON maps to domain models, tolerating unknown categories`() = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { json(DASHBOARD_JSON) })

        val dashboard = (bank.getDashboard() as AppResult.Success).value

        assertEquals(Money(100_000), dashboard.accounts.single().balance)
        assertEquals(listOf(Money(100_000)), dashboard.totals)
        val transaction = dashboard.recentTransactions.single()
        assertEquals(Direction.IN, transaction.direction)
        assertEquals(Category.OTHER, transaction.category)
        assertNull(transaction.counterparty.accountNumberMasked)
    }

    @Test
    fun `transaction filters become query parameters and unset ones are omitted`() = runBlocking {
        var captured: io.ktor.http.Url? = null
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { request ->
            captured = request.url
            json("""{"items":[],"nextCursor":null}""")
        })

        bank.getTransactions(
            accountId = "acc-1",
            filter = TransactionFilter(
                direction = Direction.OUT,
                category = Category.FOOD,
                search = "cafe",
                from = Instant.parse("2026-10-01T00:00:00Z"),
            ),
            cursor = "abc",
            limit = 20,
        )

        val url = captured!!
        assertTrue(url.encodedPath.endsWith("/accounts/acc-1/transactions"))
        assertEquals("20", url.parameters["limit"])
        assertEquals("abc", url.parameters["cursor"])
        assertEquals("OUT", url.parameters["direction"])
        assertEquals("FOOD", url.parameters["category"])
        assertEquals("cafe", url.parameters["search"])
        assertEquals("2026-10-01T00:00:00Z", url.parameters["from"])
        assertNull(url.parameters["to"])
        assertNull(url.parameters["minAmountMinor"])
    }

    @Test
    fun `a transfer sends the idempotency key header and omits null fields from the body`() = runBlocking {
        var key: String? = null
        var body: String? = null
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { request ->
            key = request.headers["Idempotency-Key"]
            body = (request.body as io.ktor.http.content.TextContent).text
            json(
                """{"transfer":{"id":"t-1","kind":"TRANSFER","status":"COMPLETED","failureReason":null,
                    "sourceAccountId":"acc-1","destinationAccountId":"acc-2","amountMinor":2500,"currency":"USD",
                    "category":"TRANSFER","description":null,"createdAt":"2026-10-29T10:00:00.000Z",
                    "completedAt":"2026-10-29T10:00:00.000Z"},"sourceAccountBalanceMinor":97500}""",
                HttpStatusCode.Created,
            )
        })

        val result = bank.transfer(
            TransferRequest(
                idempotencyKey = "key-1",
                sourceAccountId = "acc-1",
                destination = TransferDestination.AccountNumber("200000000001"),
                amount = Money(2500),
            )
        )

        assertEquals("key-1", key)
        assertTrue(body!!.contains("\"destinationAccountNumber\":\"200000000001\""))
        assertTrue("null fields must be omitted, body was $body", !body!!.contains("null"))
        // The balance in the result is the SERVER's number.
        assertEquals(Money(97_500), (result as AppResult.Success).value.sourceAccountBalance)
    }


    private fun errorFor(status: HttpStatusCode, body: String): AppError = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { json(body, status) })
        (bank.getAccounts() as AppResult.Failure).error
    }

    @Test
    fun `business rule codes map to their typed errors`() {
        val unprocessable = HttpStatusCode.UnprocessableEntity
        assertEquals(AppError.InsufficientFunds, errorFor(unprocessable, errorJson("INSUFFICIENT_FUNDS")))
        assertEquals(AppError.SameAccount, errorFor(unprocessable, errorJson("SAME_ACCOUNT")))
        assertEquals(AppError.AccountInactive, errorFor(unprocessable, errorJson("ACCOUNT_INACTIVE")))
        assertEquals(AppError.CurrencyMismatch, errorFor(unprocessable, errorJson("CURRENCY_MISMATCH")))
        assertEquals(AppError.IdempotencyConflict, errorFor(HttpStatusCode.Conflict, errorJson("IDEMPOTENCY_CONFLICT")))
        assertEquals(AppError.NotFound, errorFor(HttpStatusCode.NotFound, errorJson("NOT_FOUND")))
        assertEquals(AppError.RateLimited, errorFor(HttpStatusCode.TooManyRequests, errorJson("RATE_LIMITED")))
    }

    @Test
    fun `limit exceeded carries the amounts from the details`() {
        val error = errorFor(
            HttpStatusCode.UnprocessableEntity,
            errorJson(
                "LIMIT_EXCEEDED",
                details = """{"limitMinor":500000,"usedTodayMinor":450000,"remainingMinor":50000}""",
            ),
        )
        assertEquals(
            AppError.LimitExceeded(limit = Money(500_000), usedToday = Money(450_000), remaining = Money(50_000)),
            error,
        )
    }

    @Test
    fun `validation errors carry per-field messages`() {
        val error = errorFor(
            HttpStatusCode.BadRequest,
            errorJson("VALIDATION_ERROR", "Some fields are invalid", """{"fields":{"amountMinor":["Too small","Not whole"]}}"""),
        ) as AppError.Validation

        assertEquals(mapOf("amountMinor" to listOf("Too small", "Not whole")), error.fieldErrors)
    }

    @Test
    fun `responses that are not from our API fall back to the HTTP status`() {
        assertTrue(errorFor(HttpStatusCode.BadGateway, "<html>Bad gateway</html>") is AppError.Server)
        assertEquals(AppError.NotFound, errorFor(HttpStatusCode.NotFound, ""))
        assertEquals(AppError.RateLimited, errorFor(HttpStatusCode.TooManyRequests, "slow down"))
    }

    @Test
    fun `a malformed success body becomes Unknown instead of crashing`() = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { json("""{"accounts":"oops"}""") })
        assertTrue((bank.getAccounts() as AppResult.Failure).error is AppError.Unknown)
    }

    @Test
    fun `a network failure becomes NoConnection`() = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { throw IOException("unreachable") })
        assertEquals(AppError.NoConnection, (bank.getAccounts() as AppResult.Failure).error)
    }


    @Test
    fun `wrong credentials map to InvalidCredentials and never touch a stored session`() = runBlocking {
        val auth = AuthRemoteDataSource(testClient(FakeSessionStore(null)) {
            json(errorJson("INVALID_CREDENTIALS"), HttpStatusCode.Unauthorized)
        })

        val result = auth.login("alice@example.com", "wrong")

        assertEquals(AppError.InvalidCredentials, (result as AppResult.Failure).error)
    }

    @Test
    fun `a duplicate email maps to EmailTaken`() = runBlocking {
        val auth = AuthRemoteDataSource(testClient(FakeSessionStore(null)) {
            json(errorJson("EMAIL_TAKEN"), HttpStatusCode.Conflict)
        })

        val result = auth.register("alice@example.com", "Passw0rd123", "Alice")

        assertEquals(AppError.EmailTaken, (result as AppResult.Failure).error)
    }

    @Test
    fun `login success maps tokens and user`() = runBlocking {
        val auth = AuthRemoteDataSource(testClient(FakeSessionStore(null)) { json(sessionJson("a", "r")) })

        val session = (auth.login("alice@example.com", "Passw0rd123") as AppResult.Success).value

        assertEquals("a", session.tokens.accessToken)
        assertEquals("r", session.tokens.refreshToken)
        assertEquals("Alice", session.user.fullName)
    }

    @Test
    fun `one transaction is loaded from its own path and mapped`() = runBlocking {
        var path: String? = null
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { request ->
            path = request.url.encodedPath
            json("""{"transaction":$TRANSACTION_JSON}""")
        })

        val transaction = (bank.getTransaction("acc-1", "t-1") as AppResult.Success).value

        assertTrue(path!!.endsWith("/accounts/acc-1/transactions/t-1"))
        assertEquals("t-1", transaction.transferId)
        assertEquals(Money(100_000), transaction.amount)
    }

    @Test
    fun `a transaction that is not found maps to NotFound`() = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) {
            json(errorJson("NOT_FOUND"), HttpStatusCode.NotFound)
        })

        assertEquals(AppError.NotFound, (bank.getTransaction("acc-1", "nope") as AppResult.Failure).error)
    }

    @Test
    fun `a recipient lookup sends the number and maps the answer`() = runBlocking {
        var number: String? = null
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) { request ->
            number = request.url.parameters["accountNumber"]
            json(
                """{"recipient":{"displayName":"Bob D.","accountNumberMasked":"••••0001",
                    "currency":"USD","status":"ACTIVE","isOwnAccount":false}}""",
            )
        })

        val recipient = (bank.lookupRecipient("200000000001") as AppResult.Success).value

        assertEquals("200000000001", number)
        assertEquals("Bob D.", recipient.displayName)
        assertEquals(com.example.bankflow.domain.model.AccountStatus.ACTIVE, recipient.status)
    }

    @Test
    fun `an unknown account number maps to NotFound`() = runBlocking {
        val bank = BankRemoteDataSource(testClient(FakeSessionStore(signedIn)) {
            json(errorJson("NOT_FOUND"), HttpStatusCode.NotFound)
        })

        assertEquals(AppError.NotFound, (bank.lookupRecipient("999999999999") as AppResult.Failure).error)
    }
}