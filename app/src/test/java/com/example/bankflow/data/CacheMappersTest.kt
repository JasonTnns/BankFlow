package com.example.bankflow.data

import com.example.bankflow.data.local.cache.toDto
import com.example.bankflow.data.remote.mapper.toDomain
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.testutil.TEST_ACCOUNT
import com.example.bankflow.testutil.TEST_SAVINGS
import com.example.bankflow.testutil.testTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CacheMappersTest {

    private val transactions = listOf(
        testTransaction("t1", Direction.IN, 100_000, "Welcome bonus", Category.INCOME),
        testTransaction("t2", Direction.OUT, 2_550, null, Category.FOOD, Instant.parse("2026-10-29T10:00:00.123456Z")),
    )

    @Test
    fun `a dashboard survives the trip to the cache and back unchanged`() {
        val dashboard = Dashboard(
            accounts = listOf(TEST_ACCOUNT, TEST_SAVINGS),
            totals = listOf(Money(350_000), Money(1_000, "EUR")),
            recentTransactions = transactions,
        )

        assertEquals(dashboard, dashboard.toDto().toDomain())
    }

    @Test
    fun `a history page keeps its cursor and its precise timestamps`() {
        val page = TransactionPage(items = transactions, nextCursor = "abc")

        assertEquals(page, page.toDto().toDomain())
    }

    @Test
    fun `the last page keeps its missing cursor`() {
        val page = TransactionPage(items = transactions, nextCursor = null)

        assertEquals(page, page.toDto().toDomain())
    }
}