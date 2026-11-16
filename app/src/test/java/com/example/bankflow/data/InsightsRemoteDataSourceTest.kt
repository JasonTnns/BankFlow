package com.example.bankflow.data

import com.example.bankflow.data.remote.InsightsRemoteDataSource
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class InsightsRemoteDataSourceTest {

    private val signedIn = storedSession("token", "refresh")

    private fun source(handler: io.ktor.client.engine.mock.MockRequestHandler) =
        InsightsRemoteDataSource(testClient(FakeSessionStore(signedIn), handler))

    private val summaryJson = """
        {"summary":{"from":"2026-10-01T00:00:00.000Z","to":"2026-11-01T00:00:00.000Z","currency":"USD",
          "incomeMinor":100000,"spentMinor":7500,"netMinor":92500,"transactionCount":4,
          "byCategory":[{"category":"FOOD","spentMinor":6500,"count":2},
                        {"category":"SOMETHING_NEW","spentMinor":1000,"count":1}]}}
    """.trimIndent()

    @Test
    fun `the range and currency are sent as query parameters`() = runBlocking {
        var url: io.ktor.http.Url? = null
        val source = source { request ->
            url = request.url
            json(summaryJson)
        }

        source.getSummary(Instant.parse("2026-09-30T21:00:00Z"), Instant.parse("2026-10-31T21:00:00Z"), "USD")

        assertTrue(url!!.encodedPath.endsWith("/insights/summary"))
        assertEquals("2026-09-30T21:00:00Z", url!!.parameters["from"])
        assertEquals("2026-10-31T21:00:00Z", url!!.parameters["to"])
        assertEquals("USD", url!!.parameters["currency"])
    }

    @Test
    fun `the summary is mapped, with money in the summary's currency and unknown categories tolerated`() = runBlocking {
        val result = source { json(summaryJson) }
            .getSummary(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "USD")

        val summary = (result as AppResult.Success).value
        assertEquals(Money(100_000), summary.income)
        assertEquals(Money(7_500), summary.spent)
        assertEquals(Money(92_500), summary.net)
        assertEquals(4, summary.transactionCount)
        assertEquals(Category.FOOD, summary.byCategory[0].category)
        assertEquals(Money(6_500), summary.byCategory[0].spent)
        assertEquals(Category.OTHER, summary.byCategory[1].category)
    }

    @Test
    fun `a rejected range maps to a validation error`() = runBlocking {
        val result = source {
            json(errorJson("VALIDATION_ERROR", "Some fields are invalid"), HttpStatusCode.BadRequest)
        }.getSummary(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"), "USD")

        assertTrue((result as AppResult.Failure).error is AppError.Validation)
    }

    @Test
    fun `a network failure becomes NoConnection`() = runBlocking {
        val result = source { throw IOException("offline") }
            .getSummary(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "USD")

        assertEquals(AppError.NoConnection, (result as AppResult.Failure).error)
    }
}