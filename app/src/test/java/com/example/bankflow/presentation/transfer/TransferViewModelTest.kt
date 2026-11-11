package com.example.bankflow.presentation.transfer

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.usecase.GetAccountsUseCase
import com.example.bankflow.domain.usecase.LookupRecipientUseCase
import com.example.bankflow.domain.usecase.TransferMoneyUseCase
import com.example.bankflow.testutil.FakeAccountChangeNotifier
import com.example.bankflow.testutil.MainDispatcherRule
import com.example.bankflow.testutil.SequentialIdGenerator
import com.example.bankflow.testutil.StubAccountRepository
import com.example.bankflow.testutil.StubTransferRepository
import com.example.bankflow.testutil.TEST_ACCOUNT
import com.example.bankflow.testutil.TEST_RECIPIENT
import com.example.bankflow.testutil.TEST_SAVINGS
import com.example.bankflow.testutil.TestClock
import com.example.bankflow.testutil.successResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class TransferViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val accounts = StubAccountRepository()
    private val transfers = StubTransferRepository()
    private val clock = TestClock()
    private val notifier = FakeAccountChangeNotifier()

    init {
        accounts.accountsResult = AppResult.Success(listOf(TEST_ACCOUNT, TEST_SAVINGS))
    }

    private fun viewModel() = TransferViewModel(
        getAccounts = GetAccountsUseCase(accounts),
        lookupRecipient = LookupRecipientUseCase(accounts),
        transferMoney = TransferMoneyUseCase(transfers),
        idGenerator = SequentialIdGenerator(),
        clock = clock,
        changes = notifier,
    )

    private fun TransferViewModel.fillOtherPerson(amount: String = "25", number: String = "200000000001") {
        onIntent(TransferIntent.AccountNumberChanged(number))
        onIntent(TransferIntent.AmountChanged(amount))
    }

    private fun TransferViewModel.review(amount: String = "25") {
        fillOtherPerson(amount)
        onIntent(TransferIntent.Continue)
        assertEquals(TransferStep.REVIEW, state.value.step)
    }


    @Test
    fun `loads the accounts and picks the first active one as the source`() {
        val state = viewModel().state.value

        assertEquals(listOf(TEST_ACCOUNT, TEST_SAVINGS), state.accounts)
        assertEquals("acc-1", state.sourceAccountId)
        assertFalse(state.isLoadingAccounts)
        assertEquals(listOf(TEST_SAVINGS), state.ownDestinationOptions)
    }

    @Test
    fun `a failed account load shows the error, and retry recovers`() {
        accounts.accountsResult = AppResult.Failure(AppError.NoConnection)
        val viewModel = viewModel()
        assertEquals(AppError.NoConnection, viewModel.state.value.accountsError)

        accounts.accountsResult = AppResult.Success(listOf(TEST_ACCOUNT))
        viewModel.onIntent(TransferIntent.RetryLoadAccounts)

        assertNull(viewModel.state.value.accountsError)
        assertEquals("acc-1", viewModel.state.value.sourceAccountId)
    }

    @Test
    fun `a balance change elsewhere refreshes the account list quietly`() {
        val viewModel = viewModel()
        accounts.accountsResult = AppResult.Success(listOf(TEST_ACCOUNT.copy(balance = Money(5_000)), TEST_SAVINGS))

        notifier.notifyChanged()

        assertEquals(Money(5_000), viewModel.state.value.sourceAccount?.balance)
        assertFalse(viewModel.state.value.isLoadingAccounts)
    }


    @Test
    fun `account numbers keep only digits and at most twelve`() {
        val viewModel = viewModel()

        viewModel.onIntent(TransferIntent.AccountNumberChanged("2000 0000-0001 99"))

        assertEquals("200000000001", viewModel.state.value.accountNumber)
    }

    @Test
    fun `amounts keep digits and a single decimal point, and accept a comma`() {
        val viewModel = viewModel()

        viewModel.onIntent(TransferIntent.AmountChanged("12,5x"))
        assertEquals("12.5", viewModel.state.value.amountText)

        viewModel.onIntent(TransferIntent.AmountChanged("1.2.3"))
        assertEquals("1.23", viewModel.state.value.amountText)
    }

    @Test
    fun `editing a field clears only that field's error`() {
        val viewModel = viewModel()
        viewModel.onIntent(TransferIntent.Continue)
        assertNotNull(viewModel.state.value.amountError)

        viewModel.onIntent(TransferIntent.AmountChanged("25"))

        assertNull(viewModel.state.value.amountError)
    }

    @Test
    fun `choosing another source removes it from the own-account destinations`() {
        val viewModel = viewModel()
        viewModel.onIntent(TransferIntent.OwnDestinationSelected("acc-2"))

        viewModel.onIntent(TransferIntent.SourceSelected("acc-2"))

        assertEquals("acc-2", viewModel.state.value.sourceAccountId)
        assertNull(viewModel.state.value.ownDestinationId)
        assertEquals(listOf(TEST_ACCOUNT), viewModel.state.value.ownDestinationOptions)
    }


    @Test
    fun `a missing amount is reported at once, without any network call`() {
        val viewModel = viewModel()
        viewModel.onIntent(TransferIntent.AccountNumberChanged("200000000001"))

        viewModel.onIntent(TransferIntent.Continue)

        assertEquals(TransferStep.FORM, viewModel.state.value.step)
        assertNotNull(viewModel.state.value.amountError)
        assertTrue(accounts.accountRequests.isEmpty())
        assertTrue(accounts.recipientLookups.isEmpty())
    }

    @Test
    fun `a malformed account number is reported at once, without any network call`() {
        val viewModel = viewModel()
        viewModel.fillOtherPerson(number = "12345")

        viewModel.onIntent(TransferIntent.Continue)

        assertNotNull(viewModel.state.value.destinationError)
        assertTrue(accounts.accountRequests.isEmpty())
        assertTrue(accounts.recipientLookups.isEmpty())
    }

    @Test
    fun `a valid form reaches the review with a fresh balance, the recipient's name and a key`() {
        val viewModel = viewModel()
        viewModel.fillOtherPerson(amount = "25")
        viewModel.onIntent(TransferIntent.NoteChanged("  Lunch  "))

        viewModel.onIntent(TransferIntent.Continue)

        val state = viewModel.state.value
        val review = state.review!!
        assertEquals(TransferStep.REVIEW, state.step)
        assertFalse(state.isPreparing)
        assertEquals("key-1", review.request.idempotencyKey)
        assertEquals(Money(2_500), review.request.amount)
        assertEquals(TransferDestination.AccountNumber("200000000001"), review.request.destination)
        assertEquals("Lunch", review.request.description)
        assertEquals("Bob D.", review.recipientName)
        assertFalse(review.isOwnAccount)
        assertEquals(listOf("acc-1"), accounts.accountRequests)
        assertEquals(listOf("200000000001"), accounts.recipientLookups)
    }

    @Test
    fun `affordability is judged on the fresh balance, not the one on screen`() {
        val viewModel = viewModel()
        accounts.accountResult = AppResult.Success(TEST_ACCOUNT.copy(balance = Money(1_000))) // spent elsewhere
        viewModel.fillOtherPerson(amount = "25")

        viewModel.onIntent(TransferIntent.Continue)

        assertEquals(TransferStep.FORM, viewModel.state.value.step)
        assertNotNull(viewModel.state.value.amountError)
        assertTrue(accounts.recipientLookups.isEmpty())
    }

    @Test
    fun `if the fresh balance cannot be loaded the user sees a retryable error`() {
        val viewModel = viewModel()
        accounts.accountResult = AppResult.Failure(AppError.NoConnection)
        viewModel.fillOtherPerson()

        viewModel.onIntent(TransferIntent.Continue)

        assertEquals(TransferStep.FORM, viewModel.state.value.step)
        assertEquals(AppError.NoConnection, viewModel.state.value.banner)
        assertFalse(viewModel.state.value.isPreparing)
    }

    @Test
    fun `an unknown account number is reported under the account number field`() {
        accounts.recipientResult = AppResult.Failure(AppError.NotFound)
        val viewModel = viewModel()
        viewModel.fillOtherPerson()

        viewModel.onIntent(TransferIntent.Continue)

        assertEquals("No account found with that number", viewModel.state.value.destinationError)
        assertEquals(TransferStep.FORM, viewModel.state.value.step)
    }

    @Test
    fun `a frozen recipient account is refused before the review`() {
        accounts.recipientResult = AppResult.Success(TEST_RECIPIENT.copy(status = AccountStatus.FROZEN))
        val viewModel = viewModel()
        viewModel.fillOtherPerson()

        viewModel.onIntent(TransferIntent.Continue)

        assertEquals(AppError.AccountInactive, viewModel.state.value.banner)
    }

    @Test
    fun `sending to your own number is rejected as the same account`() {
        val viewModel = viewModel()
        viewModel.fillOtherPerson(number = TEST_ACCOUNT.accountNumber)

        viewModel.onIntent(TransferIntent.Continue)

        assertNotNull(viewModel.state.value.destinationError)
        assertTrue(accounts.recipientLookups.isEmpty())
    }

    @Test
    fun `moving money to your own account needs no lookup and names the account`() {
        val viewModel = viewModel()
        viewModel.onIntent(TransferIntent.DestinationModeChanged(DestinationMode.OWN_ACCOUNT))
        viewModel.onIntent(TransferIntent.OwnDestinationSelected("acc-2"))
        viewModel.onIntent(TransferIntent.AmountChanged("100"))

        viewModel.onIntent(TransferIntent.Continue)

        val review = viewModel.state.value.review!!
        assertEquals("High-Yield Savings", review.recipientName)
        assertTrue(review.isOwnAccount)
        assertEquals(TransferDestination.OwnAccount("acc-2"), review.request.destination)
        assertTrue(accounts.recipientLookups.isEmpty())
    }

    @Test
    fun `own-account mode without a chosen account asks for one`() {
        val viewModel = viewModel()
        viewModel.onIntent(TransferIntent.DestinationModeChanged(DestinationMode.OWN_ACCOUNT))
        viewModel.onIntent(TransferIntent.AmountChanged("100"))

        viewModel.onIntent(TransferIntent.Continue)

        assertNotNull(viewModel.state.value.destinationError)
    }


    @Test
    fun `confirming shows the success screen with the server's balance`() {
        transfers.handler = { AppResult.Success(successResult(it, newBalance = 97_500)) }
        val viewModel = viewModel()
        viewModel.review()

        viewModel.onIntent(TransferIntent.Confirm)

        val state = viewModel.state.value
        assertEquals(TransferStep.SUCCESS, state.step)
        assertEquals(Money(97_500), state.result?.sourceAccountBalance)
        assertFalse(state.isSubmitting)
        assertEquals("Bob D.", state.review?.recipientName)
    }

    @Test
    fun `tapping Confirm repeatedly sends one request`() {
        val viewModel = viewModel()
        viewModel.review()
        val gate = CompletableDeferred<Unit>().also { transfers.gate = it }

        repeat(3) { viewModel.onIntent(TransferIntent.Confirm) }

        assertTrue(viewModel.state.value.isSubmitting)
        assertEquals(1, transfers.requests.size)
        gate.complete(Unit)
        assertEquals(TransferStep.SUCCESS, viewModel.state.value.step)
    }

    @Test
    fun `going back is not possible mid-send, but works before it`() {
        val viewModel = viewModel()
        viewModel.review()
        viewModel.onIntent(TransferIntent.BackToForm)

        assertEquals(TransferStep.FORM, viewModel.state.value.step)
        assertNull(viewModel.state.value.review)
        assertEquals("25", viewModel.state.value.amountText)
    }


    @Test
    fun `retrying after a timeout reuses the same idempotency key`() {
        transfers.script(AppResult.Failure(AppError.Timeout), AppResult.Success(successResult(dummyRequest())))
        val viewModel = viewModel()
        viewModel.review()

        viewModel.onIntent(TransferIntent.Confirm)
        assertEquals(TransferStep.REVIEW, viewModel.state.value.step)
        assertEquals(AppError.Timeout, viewModel.state.value.submitError)

        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(listOf("key-1", "key-1"), transfers.requests.map { it.idempotencyKey })
        assertEquals(TransferStep.SUCCESS, viewModel.state.value.step)
    }

    @Test
    fun `every kind of unknown outcome keeps the user on review with a safe retry`() {
        listOf(AppError.NoConnection, AppError.Timeout, AppError.Server(502), AppError.Unknown("garbled")).forEach { error ->
            transfers.requests.clear()
            transfers.script(AppResult.Failure(error))
            val viewModel = viewModel()
            viewModel.review()

            viewModel.onIntent(TransferIntent.Confirm)

            assertEquals("$error", TransferStep.REVIEW, viewModel.state.value.step)
            assertEquals(error, viewModel.state.value.submitError)
        }
    }

    @Test
    fun `going back and confirming the identical transfer after an unknown outcome keeps the key`() {
        transfers.script(AppResult.Failure(AppError.NoConnection), AppResult.Success(successResult(dummyRequest())))
        val viewModel = viewModel()
        viewModel.review()
        viewModel.onIntent(TransferIntent.Confirm)

        viewModel.onIntent(TransferIntent.BackToForm)
        viewModel.onIntent(TransferIntent.Continue)
        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(listOf("key-1", "key-1"), transfers.requests.map { it.idempotencyKey })
    }

    @Test
    fun `changing the details after an unknown outcome is a different transfer with a new key`() {
        transfers.script(AppResult.Failure(AppError.NoConnection), AppResult.Success(successResult(dummyRequest())))
        val viewModel = viewModel()
        viewModel.review(amount = "25")
        viewModel.onIntent(TransferIntent.Confirm)

        viewModel.onIntent(TransferIntent.BackToForm)
        viewModel.onIntent(TransferIntent.AmountChanged("30"))
        viewModel.onIntent(TransferIntent.Continue)
        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(listOf("key-1", "key-2"), transfers.requests.map { it.idempotencyKey })
    }

    @Test
    fun `an old unknown attempt is forgotten, so a fresh identical transfer gets a new key`() {
        transfers.script(AppResult.Failure(AppError.Timeout), AppResult.Success(successResult(dummyRequest())))
        val viewModel = viewModel()
        viewModel.review()
        viewModel.onIntent(TransferIntent.Confirm)

        clock.advance(TransferViewModel.UNKNOWN_ATTEMPT_WINDOW + Duration.ofMinutes(1))
        viewModel.onIntent(TransferIntent.BackToForm)
        viewModel.onIntent(TransferIntent.Continue)
        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(listOf("key-1", "key-2"), transfers.requests.map { it.idempotencyKey })
    }


    @Test
    fun `a rejected transfer shows the failure screen, and no money is claimed to have moved`() {
        transfers.script(AppResult.Failure(AppError.InsufficientFunds))
        val viewModel = viewModel()
        viewModel.review()

        viewModel.onIntent(TransferIntent.Confirm)

        val state = viewModel.state.value
        assertEquals(TransferStep.FAILED, state.step)
        assertEquals(AppError.InsufficientFunds, state.failure)
        assertNull(state.result)
        assertNull(state.submitError)
    }

    @Test
    fun `a daily limit failure carries the amount that is still allowed`() {
        val limit = AppError.LimitExceeded(limit = Money(500_000), usedToday = Money(450_000), remaining = Money(50_000))
        transfers.script(AppResult.Failure(limit))
        val viewModel = viewModel()
        viewModel.review()

        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(limit, viewModel.state.value.failure)
    }

    @Test
    fun `after a rejection, editing keeps the form and the next attempt uses a NEW key`() {
        transfers.script(AppResult.Failure(AppError.LimitExceeded()), AppResult.Success(successResult(dummyRequest())))
        val viewModel = viewModel()
        viewModel.review()
        viewModel.onIntent(TransferIntent.Confirm)

        viewModel.onIntent(TransferIntent.EditAfterFailure)
        assertEquals(TransferStep.FORM, viewModel.state.value.step)
        assertEquals("200000000001", viewModel.state.value.accountNumber)
        assertNull(viewModel.state.value.failure)

        viewModel.onIntent(TransferIntent.Continue)
        viewModel.onIntent(TransferIntent.Confirm)

        assertEquals(listOf("key-1", "key-2"), transfers.requests.map { it.idempotencyKey })
    }


    @Test
    fun `starting a new transfer clears the form but keeps the accounts`() {
        val viewModel = viewModel()
        viewModel.review()
        viewModel.onIntent(TransferIntent.Confirm)

        viewModel.onIntent(TransferIntent.StartNew)

        val state = viewModel.state.value
        assertEquals(TransferStep.FORM, state.step)
        assertEquals("", state.amountText)
        assertEquals("", state.accountNumber)
        assertNull(state.result)
        assertNull(state.review)
        assertEquals(2, state.accounts.size)
        assertEquals("acc-1", state.sourceAccountId)
    }

    private fun dummyRequest() = com.example.bankflow.domain.model.TransferRequest(
        idempotencyKey = "x",
        sourceAccountId = "acc-1",
        destination = TransferDestination.AccountNumber("200000000001"),
        amount = Money(2_500),
    )
}