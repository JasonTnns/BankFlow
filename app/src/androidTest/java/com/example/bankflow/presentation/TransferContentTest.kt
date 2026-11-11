package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.TransferDestination
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.presentation.transfer.DestinationMode
import com.example.bankflow.presentation.transfer.TransferContent
import com.example.bankflow.presentation.transfer.TransferIntent
import com.example.bankflow.presentation.transfer.TransferReview
import com.example.bankflow.presentation.transfer.TransferState
import com.example.bankflow.presentation.transfer.TransferStep
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TransferContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<TransferIntent>()
    private var dones = 0

    private fun show(state: TransferState) {
        composeRule.setContent {
            BankFlowTheme {
                TransferContent(state = state, onIntent = { intents += it }, onDone = { dones++ })
            }
        }
    }

    private val form = TransferState(
        accounts = listOf(UI_ACCOUNT, UI_SAVINGS),
        isLoadingAccounts = false,
        sourceAccountId = "acc-1",
    )

    private val review = TransferReview(
        request = TransferRequest(
            idempotencyKey = "key-1",
            sourceAccountId = "acc-1",
            destination = TransferDestination.AccountNumber("200000000001"),
            amount = Money(2_500),
            category = Category.FOOD,
            description = "Lunch",
        ),
        source = UI_ACCOUNT,
        recipientName = "Bob D.",
        recipientDetail = "\u2022\u2022\u2022\u20220001",
        isOwnAccount = false,
    )

    private val result = TransferResult(
        transfer = Transfer(
            id = "transfer-42",
            kind = TransferKind.TRANSFER,
            status = TransferStatus.COMPLETED,
            failureReason = null,
            sourceAccountId = "acc-1",
            destinationAccountId = "dest",
            amount = Money(2_500),
            category = Category.FOOD,
            description = "Lunch",
            createdAt = Instant.now(),
            completedAt = Instant.now(),
        ),
        sourceAccountBalance = Money(97_500),
    )


    @Test
    fun theFormShowsAccountsFieldsAndTheAvailableBalance() {
        show(form)

        composeRule.onNodeWithText("Everyday Checking").assertIsDisplayed()
        composeRule.onNodeWithText("High-Yield Savings").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_number").assertIsDisplayed()
        composeRule.onNodeWithText("Available: $1,000.00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_continue").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theChosenSourceIsMarkedAndChangingItSendsAnIntent() {
        show(form)

        composeRule.onNodeWithText("Everyday Checking").assertIsSelected()
        composeRule.onNodeWithText("High-Yield Savings").performClick()

        assertEquals(listOf(TransferIntent.SourceSelected("acc-2")), intents)
    }

    @Test
    fun typingSendsIntents() {
        show(form)

        composeRule.onNodeWithTag("transfer_number").performTextInput("200000000001")
        composeRule.onNodeWithTag("transfer_amount").performScrollTo().performTextInput("25")

        assertEquals(
            listOf(TransferIntent.AccountNumberChanged("200000000001"), TransferIntent.AmountChanged("25")),
            intents,
        )
    }

    @Test
    fun fieldErrorsShowUnderTheirFields() {
        show(
            form.copy(
                formError = AppError.Validation(
                    fieldErrors = mapOf(
                        "amount" to listOf("Enter an amount"),
                        "destination" to listOf("No account found with that number"),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("No account found with that number").assertIsDisplayed()
        composeRule.onNodeWithText("Enter an amount").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_error").assertDoesNotExist()
    }

    @Test
    fun aNetworkProblemShowsInTheBanner() {
        show(form.copy(formError = AppError.NoConnection))

        composeRule.onNodeWithTag("transfer_error").assertIsDisplayed()
    }

    @Test
    fun switchingToMyAccountsShowsOnlyTheOtherAccounts() {
        show(form.copy(destinationMode = DestinationMode.OWN_ACCOUNT))

        composeRule.onNodeWithTag("transfer_number").assertDoesNotExist()
        composeRule.onAllNodesWithTextCount("High-Yield Savings", expected = 2)
    }

    @Test
    fun withOneAccountThereIsNothingToMoveBetween() {
        show(form.copy(accounts = listOf(UI_ACCOUNT), destinationMode = DestinationMode.OWN_ACCOUNT))

        composeRule.onNodeWithTag("no_own_accounts").assertIsDisplayed()
    }

    @Test
    fun theModeButtonsSendIntents() {
        show(form)

        composeRule.onNodeWithTag("mode_own").performClick()

        assertEquals(listOf(TransferIntent.DestinationModeChanged(DestinationMode.OWN_ACCOUNT)), intents)
    }

    @Test
    fun whilePreparingTheFormIsLocked() {
        show(form.copy(isPreparing = true, amountText = "25", accountNumber = "200000000001"))

        composeRule.onNodeWithTag("transfer_continue").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("transfer_amount").assertIsNotEnabled()
    }

    @Test
    fun accountsThatFailedToLoadOfferARetry() {
        show(TransferState(isLoadingAccounts = false, accountsError = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(TransferIntent.RetryLoadAccounts), intents)
    }


    @Test
    fun theReviewShowsExactlyWhatWillBeSent() {
        show(form.copy(step = TransferStep.REVIEW, review = review))

        composeRule.onNodeWithTag("review_amount").assertTextEquals("$25.00")
        composeRule.onNodeWithTag("review_recipient").assertTextEquals("to Bob D.")
        composeRule.onNodeWithText("Everyday Checking \u00B7 \u2022\u2022\u2022\u20220001").assertIsDisplayed()
        composeRule.onNodeWithText("Lunch").assertIsDisplayed()
    }

    @Test
    fun confirmAndBackSendTheirIntents() {
        show(form.copy(step = TransferStep.REVIEW, review = review))

        composeRule.onNodeWithTag("review_confirm").performClick()
        composeRule.onNodeWithTag("review_back").performClick()

        assertEquals(listOf(TransferIntent.Confirm, TransferIntent.BackToForm), intents)
    }

    @Test
    fun anUnknownOutcomeSaysSoAndOffersASafeRetry() {
        show(form.copy(step = TransferStep.REVIEW, review = review, submitError = AppError.Timeout))

        composeRule.onNodeWithTag("review_error").assertIsDisplayed()
        composeRule.onNodeWithText("it won't be sent twice", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(TransferIntent.Confirm), intents)
    }

    @Test
    fun whileSendingNothingCanBePressed() {
        show(form.copy(step = TransferStep.REVIEW, review = review, isSubmitting = true))

        composeRule.onNodeWithTag("review_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_back").assertIsNotEnabled()
        composeRule.onNodeWithTag("review_top_back").assertIsNotEnabled()
    }


    @Test
    fun successShowsTheServersBalanceAndTheReference() {
        show(form.copy(step = TransferStep.SUCCESS, review = review, result = result))

        composeRule.onNodeWithText("Transfer sent").assertIsDisplayed()
        composeRule.onNodeWithTag("success_amount").assertTextEquals("$25.00")
        composeRule.onNodeWithTag("success_balance").assertTextEquals("$975.00")
        composeRule.onNodeWithTag("success_reference").assertTextEquals("transfer-42")
    }

    @Test
    fun successHasDoneAndAnotherTransfer() {
        show(form.copy(step = TransferStep.SUCCESS, review = review, result = result))

        composeRule.onNodeWithTag("success_another").performScrollTo().performClick()
        composeRule.onNodeWithTag("success_done").performScrollTo().performClick()

        assertEquals(listOf(TransferIntent.StartNew, TransferIntent.StartNew), intents)
        assertEquals(1, dones)
    }

    @Test
    fun aRejectedTransferSaysWhyAndThatNoMoneyMoved() {
        show(
            form.copy(
                step = TransferStep.FAILED,
                review = review,
                failure = AppError.LimitExceeded(remaining = Money(50_000)),
            ),
        )

        composeRule.onNodeWithText("Transfer not sent").assertIsDisplayed()
        composeRule.onNodeWithTag("failure_message").assertIsDisplayed()
        composeRule.onNodeWithText("No money was moved.").assertIsDisplayed()
    }

    @Test
    fun aRejectedTransferCanBeEditedOrClosed() {
        show(form.copy(step = TransferStep.FAILED, review = review, failure = AppError.InsufficientFunds))

        composeRule.onNodeWithTag("failure_edit").performClick()
        composeRule.onNodeWithTag("failure_close").performClick()

        assertEquals(listOf(TransferIntent.EditAfterFailure, TransferIntent.StartNew), intents)
        assertEquals(1, dones)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(
        text: String,
        expected: Int,
    ) {
        val actual = onAllNodesWithText(text).fetchSemanticsNodes().size
        assertEquals("nodes with text '$text'", expected, actual)
    }
}