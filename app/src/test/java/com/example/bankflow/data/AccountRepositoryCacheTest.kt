package com.example.bankflow.data

import com.example.bankflow.data.remote.BankRemoteDataSource
import com.example.bankflow.data.repository.AccountRepositoryImpl
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.testutil.FakeCacheStore
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AccountRepositoryCacheTest {

    private val cache = FakeCacheStore()
    private val pageJson = """{"items":[$TRANSACTION_JSON],"nextCursor":"c1"}"""

    private fun repository(handler: MockRequestHandler) = AccountRepositoryImpl(
        remote = BankRemoteDataSource(testClient(FakeSessionStore(storedSession("token", "refresh")), handler)),
        cache = cache,
    )

    private suspend fun AccountRepositoryImpl.history(filter: TransactionFilter = TransactionFilter(), cursor: String? = null) =
        getTransactions("acc-1", filter, cursor, 20)

    @Test
    fun `a fresh dashboard is saved and can be read back`() = runBlocking {
        val repository = repository { json(DASHBOARD_JSON) }

        repository.getDashboard()

        val saved = repository.getCachedDashboard()!!
        assertEquals(Money(100_000), saved.value.accounts.single().balance)
        assertEquals(Instant.parse("2026-10-29T10:00:00Z"), saved.savedAt)
    }

    @Test
    fun `a failed refresh keeps the earlier copy`() = runBlocking {
        var fail = false
        val repository = repository { if (fail) json(errorJson("INTERNAL_ERROR"), HttpStatusCode.InternalServerError) else json(DASHBOARD_JSON) }
        repository.getDashboard()
        fail = true

        val result = repository.getDashboard()

        assertEquals(true, result is AppResult.Failure)
        assertNotNull(repository.getCachedDashboard())
        assertEquals(1, cache.writes)
    }

    @Test
    fun `nothing is saved until something succeeds`() = runBlocking {
        assertNull(repository { json(DASHBOARD_JSON) }.getCachedDashboard())
    }

    @Test
    fun `the first unfiltered page of history is saved, with its category fallback applied`() = runBlocking {
        val repository = repository { json(pageJson) }

        repository.history()

        val saved = repository.getCachedTransactions("acc-1")!!
        assertEquals("c1", saved.value.nextCursor)
        assertEquals(Category.OTHER, saved.value.items.single().category)
    }

    @Test
    fun `filtered and later pages are always live, never saved`() = runBlocking {
        val repository = repository { json(pageJson) }

        repository.history(filter = TransactionFilter(search = "rent"))
        repository.history(cursor = "c1")

        assertEquals(0, cache.writes)
        assertNull(repository.getCachedTransactions("acc-1"))
    }

    @Test
    fun `each account has its own saved history`() = runBlocking {
        val repository = repository { json(pageJson) }

        repository.history()

        assertNotNull(repository.getCachedTransactions("acc-1"))
        assertNull(repository.getCachedTransactions("acc-2"))
    }

    @Test
    fun `an account header comes from the saved dashboard`() = runBlocking {
        val repository = repository { json(DASHBOARD_JSON) }
        repository.getDashboard()

        assertEquals("Everyday Checking", repository.getCachedAccount("acc-1")?.value?.name)
        assertNull(repository.getCachedAccount("someone-else"))
    }

    @Test
    fun `an answer that arrives after the user changed is not saved under the new user`() = runBlocking {
        val repository = repository {
            cache.owner = "user-2"
            json(DASHBOARD_JSON)
        }

        repository.getDashboard()

        assertEquals(0, cache.writes)
        assertNull(repository.getCachedDashboard())
    }

    @Test
    fun `a saved copy that no longer maps to today's models counts as a miss`() = runBlocking {
        val repository = repository { json(DASHBOARD_JSON) }
        cache.entries["user-1" to "dashboard"] = """
            {"accounts":[{"id":"a","name":"n","type":"WEIRD","accountNumber":"1","currency":"USD",
              "balanceMinor":1,"status":"ACTIVE","createdAt":"2026-10-29T10:00:00.000Z"}],
             "totals":[],"recentTransactions":[]}
        """.trimIndent() to Instant.parse("2026-10-29T10:00:00Z")

        assertNull(repository.getCachedDashboard())
    }
}