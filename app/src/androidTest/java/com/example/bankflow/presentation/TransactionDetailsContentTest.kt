package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.presentation.transactions.TransactionDetailsContent
import com.example.bankflow.presentation.transactions.TransactionDetailsIntent
import com.example.bankflow.presentation.transactions.TransactionDetailsState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDetailsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<TransactionDetailsIntent>()
    private var backClicks = 0

    private fun show(state: TransactionDetailsState) {
        composeRule.setContent {
            BankFlowTheme {
                TransactionDetailsContent(state = state, onIntent = { intents += it }, onBack = { backClicks++ })
            }
        }
    }

    @Test
    fun showsTheAmountCounterpartyAndKeyDetails() {
        show(TransactionDetailsState(transaction = uiTransaction("t-42", "Fresh Market", Direction.OUT, 2_500)))

        composeRule.onNodeWithTag("details_amount").assertTextEquals("\u2212$25.00")
        composeRule.onNodeWithText("Fresh Market").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onNodeWithText("Balance after").assertIsDisplayed()
        composeRule.onNodeWithTag("details_reference").assertTextEquals("t-42")
    }

    @Test
    fun incomingMoneyIsSignedAndLabelledFrom() {
        show(TransactionDetailsState(transaction = uiTransaction("t-1", "Acme Payroll", Direction.IN, 320_000)))

        composeRule.onNodeWithTag("details_amount").assertTextEquals("+$3,200.00")
        composeRule.onNodeWithText("From").assertIsDisplayed()
    }

    @Test
    fun copyingTheReferenceConfirmsItWasCopied() {
        show(TransactionDetailsState(transaction = uiTransaction("t-42")))

        composeRule.onNodeWithTag("details_copy").assertTextEquals("Copy")
        composeRule.onNodeWithTag("details_copy").performClick()

        composeRule.onNodeWithTag("details_copy").assertTextEquals("Copied")
    }

    @Test
    fun showsALoadingIndicatorWithNoData() {
        show(TransactionDetailsState(isLoading = true))

        composeRule.onNodeWithContentDescription("Loading transaction").assertIsDisplayed()
    }

    @Test
    fun aFailureOffersRetry() {
        show(TransactionDetailsState(error = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(TransactionDetailsIntent.Retry), intents)
    }

    @Test
    fun aMissingTransactionOffersNoRetry() {
        show(TransactionDetailsState(error = AppError.NotFound))

        composeRule.onNodeWithText("We couldn't find that.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun theBackButtonWorks() {
        show(TransactionDetailsState(transaction = uiTransaction("t-1")))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backClicks)
    }
}