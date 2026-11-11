package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.TransactionFilter
import com.example.bankflow.presentation.accounts.AccountDetailsContent
import com.example.bankflow.presentation.accounts.AccountDetailsIntent
import com.example.bankflow.presentation.accounts.AccountDetailsState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDetailsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<AccountDetailsIntent>()
    private var backClicks = 0

    private fun show(state: AccountDetailsState) {
        composeRule.setContent {
            BankFlowTheme {
                AccountDetailsContent(
                    state = state,
                    onIntent = { intents += it },
                    onBack = { backClicks++ },
                )
            }
        }
    }

    private fun loaded(
        count: Int = 3,
        nextCursor: String? = null,
        extra: AccountDetailsState.() -> AccountDetailsState = { this },
    ) = AccountDetailsState(
        account = UI_ACCOUNT,
        items = (1..count).map { uiTransaction("t$it", name = "Merchant $it") },
        nextCursor = nextCursor,
        hasLoaded = true,
    ).extra()

    @Test
    fun showsTheAccountHeaderAndItsTransactions() {
        show(loaded())

        composeRule.onNodeWithText("Everyday Checking").assertIsDisplayed()
        composeRule.onNodeWithText("$1,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("Merchant 1").assertIsDisplayed()
    }

    @Test
    fun theBackButtonWorks() {
        show(loaded())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun anAccountWithNoActivityShowsAnEmptyMessage() {
        show(loaded(count = 0))

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }

    @Test
    fun anEmptyFilteredListOffersToClearTheFilter() {
        show(loaded(count = 0, extra = { copy(filter = TransactionFilter(direction = Direction.IN)) }))

        composeRule.onNodeWithText("No matching transactions").assertIsDisplayed()
        composeRule.onNodeWithText("Clear filters").performClick()

        assertEquals(listOf(AccountDetailsIntent.ClearFilter), intents)
    }

    @Test
    fun aFailedFirstLoadShowsAnErrorAndRetries() {
        show(AccountDetailsState(account = UI_ACCOUNT, loadError = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(AccountDetailsIntent.Refresh), intents)
    }

    @Test
    fun aFailedPageShowsARetryRowAtTheEnd() {
        show(loaded(nextCursor = "c1", extra = { copy(loadMoreError = AppError.NoConnection) }))

        composeRule.onNodeWithText("Retry").performClick()

        assertTrue(intents.contains(AccountDetailsIntent.RetryLoadMore))
    }

    @Test
    fun reachingTheEndShowsAFinishedMessage() {
        show(loaded(nextCursor = null))

        composeRule.onNodeWithText("You're all caught up").assertIsDisplayed()
    }

    @Test
    fun scrollingNearTheEndAsksForTheNextPage() {
        show(loaded(count = 40, nextCursor = "c1"))
        assertTrue("nothing is requested while the user is at the top", intents.isEmpty())

        composeRule.onNodeWithTag("history_list").performScrollToIndex(42)
        composeRule.waitForIdle()

        assertTrue(intents.contains(AccountDetailsIntent.LoadMore))
    }

    @Test
    fun tappingATransactionReportsIt() {
        val clicked = mutableListOf<String>()
        composeRule.setContent {
            BankFlowTheme {
                AccountDetailsContent(
                    state = loaded(),
                    onIntent = { intents += it },
                    onBack = {},
                    onTransactionClick = { clicked += it.transferId },
                )
            }
        }

        composeRule.onNodeWithText("Merchant 2").performClick()

        assertEquals(listOf("t2"), clicked)
    }

    @Test
    fun typingInTheSearchBoxAndRemovingAChipSendIntents() {
        show(loaded(extra = { copy(filter = TransactionFilter(direction = Direction.OUT)) }))

        composeRule.onNodeWithTag("history_search").performTextInput("rent")
        composeRule.onNodeWithTag("chip_DIRECTION").performClick()

        assertEquals(
            listOf(
                AccountDetailsIntent.SearchChanged("rent"),
                AccountDetailsIntent.ApplyFilter(TransactionFilter()),
            ),
            intents,
        )
    }
}