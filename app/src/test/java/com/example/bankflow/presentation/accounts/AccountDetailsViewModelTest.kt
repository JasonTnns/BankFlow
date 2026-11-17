package com.example.bankflow.presentation.accounts

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Cached
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.domain.model.TransactionPage
import com.example.bankflow.domain.usecase.GetAccountsUseCase
import com.example.bankflow.domain.usecase.GetTransactionsUseCase
import com.example.bankflow.testutil.FakeAccountChangeNotifier
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.StubAccountRepository
import com.example.bankflow.testutil.TEST_ACCOUNT
import com.example.bankflow.testutil.testPage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant

class AccountDetailsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val notifier = FakeAccountChangeNotifier()

    private val repository = StubAccountRepository()

    private val pages = mutableMapOf<String?, AppResult<TransactionPage>>()

    private val gates = mutableMapOf<String?, CompletableDeferred<Unit>>()

    init {
        repository.transactionsHandler = { call ->
            gates[call.cursor]?.await()
            pages[call.cursor] ?: AppResult.Success(TransactionPage(emptyList(), null))
        }
    }

    private fun viewModel() = AccountDetailsViewModel(
        accountId = "acc-1",
        getAccounts = GetAccountsUseCase(repository),
        getTransactions = GetTransactionsUseCase(repository),
        changes = notifier
    )

    private fun AccountDetailsViewModel.ids() = state.value.items.map { it.transferId }

    private fun twoPages() {
        pages[null] = AppResult.Success(testPage("t1", "t2", next = "c1"))
        pages["c1"] = AppResult.Success(testPage("t3", next = null))
    }


    @Test
    fun `opening the screen loads the account and the first page`() {
        twoPages()
        val viewModel = viewModel()

        val state = viewModel.state.value
        assertEquals(TEST_ACCOUNT, state.account)
        assertEquals(listOf("t1", "t2"), viewModel.ids())
        assertEquals("c1", state.nextCursor)
        assertTrue(state.hasLoaded)
        assertFalse(state.isInitialLoading)
        assertEquals(null, repository.transactionCalls.single().cursor)
        assertEquals("acc-1", repository.transactionCalls.single().accountId)
    }

    @Test
    fun `an account with no activity is empty, not an error`() {
        val state = viewModel().state.value

        assertTrue(state.isEmpty)
        assertTrue(state.endReached)
        assertNull(state.loadError)
    }

    @Test
    fun `a failed first load shows the error, and Refresh recovers`() {
        pages[null] = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        assertEquals(AppError.NoConnection, viewModel.state.value.loadError)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertFalse(viewModel.state.value.hasLoaded)

        pages[null] = AppResult.Success(testPage("t1"))
        viewModel.onIntent(AccountDetailsIntent.Refresh)

        assertEquals(listOf("t1"), viewModel.ids())
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `a failed account header does not break the list`() {
        repository.accountResult = AppResult.Failure(AppError.Timeout)
        pages[null] = AppResult.Success(testPage("t1"))

        val viewModel = viewModel()

        assertNull(viewModel.state.value.account)
        assertEquals(listOf("t1"), viewModel.ids())
    }


    @Test
    fun `loading more appends the next page and reaches the end`() {
        twoPages()
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        assertEquals(listOf("t1", "t2", "t3"), viewModel.ids())
        assertTrue(viewModel.state.value.endReached)
        assertFalse(viewModel.state.value.isLoadingMore)
        assertEquals(listOf(null, "c1"), repository.transactionCalls.map { it.cursor })
    }

    @Test
    fun `LoadMore does nothing once the end is reached`() {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        viewModel.onIntent(AccountDetailsIntent.LoadMore)
        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        assertEquals(2, repository.transactionCalls.size)
    }

    @Test
    fun `scrolling fast sends one request for the next page`() {
        twoPages()
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { gates["c1"] = it }

        repeat(5) { viewModel.onIntent(AccountDetailsIntent.LoadMore) }

        assertTrue(viewModel.state.value.isLoadingMore)
        assertEquals(2, repository.transactionCalls.size)
        gate.complete(Unit)
        assertEquals(listOf("t1", "t2", "t3"), viewModel.ids())
    }

    @Test
    fun `a failed page keeps the list, and only an explicit retry tries again`() {
        twoPages()
        pages["c1"] = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        assertEquals(listOf("t1", "t2"), viewModel.ids())
        assertEquals(AppError.NoConnection, viewModel.state.value.loadMoreError)
        assertFalse(viewModel.state.value.isLoadingMore)

        repeat(3) { viewModel.onIntent(AccountDetailsIntent.LoadMore) }
        assertEquals(2, repository.transactionCalls.size)

        pages["c1"] = AppResult.Success(testPage("t3"))
        viewModel.onIntent(AccountDetailsIntent.RetryLoadMore)

        assertEquals(listOf("t1", "t2", "t3"), viewModel.ids())
        assertNull(viewModel.state.value.loadMoreError)
    }

    @Test
    fun `a repeated row from the server is not shown twice`() {
        pages[null] = AppResult.Success(testPage("t1", "t2", next = "c1"))
        pages["c1"] = AppResult.Success(testPage("t2", "t3"))
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        assertEquals(listOf("t1", "t2", "t3"), viewModel.ids())
    }


    @Test
    fun `refresh keeps the list visible, then replaces it`() {
        twoPages()
        val viewModel = viewModel()
        pages[null] = AppResult.Success(testPage("n1", next = null))
        val gate = CompletableDeferred<Unit>().also { gates[null] = it }

        viewModel.onIntent(AccountDetailsIntent.Refresh)

        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isInitialLoading)
        assertEquals(listOf("t1", "t2"), viewModel.ids())

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(listOf("n1"), viewModel.ids())
        assertEquals(null, viewModel.state.value.nextCursor)
    }

    @Test
    fun `a failed refresh keeps the old list and reports the error`() {
        twoPages()
        val viewModel = viewModel()
        pages[null] = AppResult.Failure(AppError.Timeout)

        viewModel.onIntent(AccountDetailsIntent.Refresh)

        assertEquals(listOf("t1", "t2"), viewModel.ids())
        assertEquals(AppError.Timeout, viewModel.state.value.loadError)
        assertTrue(viewModel.state.value.hasLoaded)
    }


    @Test
    fun `a new filter clears the list and loads page one with that filter`() {
        twoPages()
        val viewModel = viewModel()
        val outgoing = TransactionFilter(direction = Direction.OUT)
        pages[null] = AppResult.Success(testPage("f1"))
        val gate = CompletableDeferred<Unit>().also { gates[null] = it }

        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(outgoing))

        assertEquals(outgoing, viewModel.state.value.filter)
        assertTrue("old rows must not sit under the new filter", viewModel.state.value.items.isEmpty())
        assertTrue(viewModel.state.value.isInitialLoading)

        gate.complete(Unit)
        assertEquals(listOf("f1"), viewModel.ids())
        assertEquals(outgoing, repository.transactionCalls.last().filter)
        assertNull(repository.transactionCalls.last().cursor)
    }

    @Test
    fun `applying the same filter again does not reload`() {
        twoPages()
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(TransactionFilter()))

        assertEquals(1, repository.transactionCalls.size)
    }

    @Test
    fun `clearing filters returns to the unfiltered list`() {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(TransactionFilter(search = "rent")))

        viewModel.onIntent(AccountDetailsIntent.ClearFilter)

        assertEquals(TransactionFilter(), viewModel.state.value.filter)
        assertEquals(TransactionFilter(), repository.transactionCalls.last().filter)
    }

    @Test
    fun `a slow page for the old filter can never overwrite the new list`() {
        twoPages()
        val viewModel = viewModel()
        val oldPageGate = CompletableDeferred<Unit>().also { gates["c1"] = it }
        viewModel.onIntent(AccountDetailsIntent.LoadMore)

        pages[null] = AppResult.Success(testPage("f1"))
        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(TransactionFilter(direction = Direction.IN)))
        oldPageGate.complete(Unit)
        assertEquals(listOf("f1"), viewModel.ids())
        assertFalse(viewModel.state.value.isLoadingMore)
    }


    @Test
    fun `typing waits for a pause before searching`() = runTest {
        twoPages()
        val viewModel = viewModel()

        listOf("re", "ren", "rent").forEach { viewModel.onIntent(AccountDetailsIntent.SearchChanged(it)) }

        assertEquals("the box updates instantly", "rent", viewModel.state.value.searchText)
        advanceTimeBy(AccountDetailsViewModel.SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals("nothing is sent while the user is still typing", 1, repository.transactionCalls.size)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, repository.transactionCalls.size)
        assertEquals("rent", repository.transactionCalls.last().filter.search)
        assertNull(repository.transactionCalls.last().cursor)
    }

    @Test
    fun `a blank search removes the search filter`() = runTest {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("rent"))
        advanceUntilIdle()
        assertEquals("rent", viewModel.state.value.filter.search)

        viewModel.onIntent(AccountDetailsIntent.SearchChanged("   "))
        advanceUntilIdle()

        assertNull(viewModel.state.value.filter.search)
        assertEquals(3, repository.transactionCalls.size)
    }

    @Test
    fun `search text is limited to 100 characters`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.SearchChanged("x".repeat(150)))

        assertEquals(100, viewModel.state.value.searchText.length)
    }

    @Test
    fun `clearing filters also empties the search box and cancels a pending search`() = runTest {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("ren"))

        viewModel.onIntent(AccountDetailsIntent.ClearFilter)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.searchText)
        assertTrue(repository.transactionCalls.none { it.filter.search != null })
    }

    @Test
    fun `typing the same search again after clearing still searches`() = runTest {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("rent"))
        advanceUntilIdle()

        viewModel.onIntent(AccountDetailsIntent.ClearFilter)
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("rent"))
        advanceUntilIdle()

        assertEquals("rent", viewModel.state.value.filter.search)
    }

    @Test
    fun `sheet filters keep the search the user typed, even before its pause is over`() = runTest {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("rent"))

        viewModel.onIntent(AccountDetailsIntent.ApplySheetFilter(TransactionFilter(direction = Direction.OUT)))

        assertEquals(
            TransactionFilter(direction = Direction.OUT, search = "rent"),
            viewModel.state.value.filter,
        )
        advanceUntilIdle()
        assertEquals("the pending search adds no second reload", 2, repository.transactionCalls.size)
    }

    @Test
    fun `removing a chip keeps the search`() = runTest {
        twoPages()
        val viewModel = viewModel()
        viewModel.onIntent(AccountDetailsIntent.SearchChanged("rent"))
        advanceUntilIdle()
        viewModel.onIntent(AccountDetailsIntent.ApplySheetFilter(TransactionFilter(direction = Direction.OUT)))

        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(viewModel.state.value.filter.copy(direction = null)))

        assertEquals(TransactionFilter(search = "rent"), viewModel.state.value.filter)
        assertEquals("rent", viewModel.state.value.searchText)
    }

    @Test
    fun `a completed transfer reloads the history and keeps the list visible meanwhile`() {
        twoPages()
        val viewModel = viewModel()
        pages[null] = AppResult.Success(testPage("new", "t1", "t2", next = null))

        notifier.notifyChanged()

        assertEquals(listOf("new", "t1", "t2"), viewModel.ids())
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(2, repository.transactionCalls.size)
    }

    private val savedAt = Instant.parse("2026-10-29T09:00:00Z")

    @Test
    fun `saved history shows at once while the fresh page loads, then is replaced`() {
        repository.cachedHistory = Cached(testPage("old1", "old2", next = "c0"), savedAt)
        pages[null] = AppResult.Success(testPage("n1", next = null))
        val gate = CompletableDeferred<Unit>().also { gates[null] = it }

        val viewModel = viewModel()

        assertEquals(listOf("old1", "old2"), viewModel.ids())
        assertEquals(savedAt, viewModel.state.value.savedAt)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isInitialLoading)

        gate.complete(Unit)

        assertEquals(listOf("n1"), viewModel.ids())
        assertNull(viewModel.state.value.savedAt)
    }

    @Test
    fun `the saved account header is used until the live one arrives`() {
        repository.cachedAccount = Cached(TEST_ACCOUNT, savedAt)
        repository.accountResult = AppResult.Failure(AppError.NoConnection)

        assertEquals(TEST_ACCOUNT, viewModel().state.value.account)
    }

    @Test
    fun `a failed refresh keeps the saved list, marked as saved`() {
        repository.cachedHistory = Cached(testPage("old1"), savedAt)
        pages[null] = AppResult.Failure(AppError.NoConnection)

        val viewModel = viewModel()

        assertEquals(listOf("old1"), viewModel.ids())
        assertEquals(savedAt, viewModel.state.value.savedAt)
        assertEquals(AppError.NoConnection, viewModel.state.value.loadError)
    }

    @Test
    fun `a new filter drops the saved marker, because the list is no longer the saved one`() {
        repository.cachedHistory = Cached(testPage("old1"), savedAt)
        pages[null] = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()

        viewModel.onIntent(AccountDetailsIntent.ApplyFilter(TransactionFilter(direction = Direction.OUT)))

        assertNull(viewModel.state.value.savedAt)
        assertTrue(viewModel.state.value.items.isEmpty())
    }
}