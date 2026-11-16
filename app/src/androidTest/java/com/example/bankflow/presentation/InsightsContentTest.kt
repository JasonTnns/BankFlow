package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.insights.InsightsContent
import com.example.bankflow.presentation.insights.InsightsIntent
import com.example.bankflow.presentation.insights.InsightsState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class InsightsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<InsightsIntent>()

    private fun show(state: InsightsState) {
        composeRule.setContent {
            BankFlowTheme { InsightsContent(state = state, onIntent = { intents += it }) }
        }
    }

    private val october = YearMonth.of(2026, 10)
    private val loaded = InsightsState(
        month = october,
        currentMonth = october,
        summary = uiSummary(),
        previous = uiSummary(categories = listOf(Category_FOOD_ONLY)),
    )

    private companion object {
        val Category_FOOD_ONLY = com.example.bankflow.domain.model.Category.FOOD to 5_000L
    }

    @Test
    fun showsTheMonthAndItsTotals() {
        show(loaded)

        composeRule.onNodeWithTag("month_title").assertTextEquals("October 2026")
        composeRule.onNodeWithTag("insights_spent").assertTextEquals("$75.00")
        composeRule.onNodeWithTag("insights_income").assertTextEquals("$1,000.00")
        composeRule.onNodeWithTag("insights_net").assertTextEquals("+$925.00")
    }

    @Test
    fun comparesWithLastMonth() {
        show(loaded)

        composeRule.onNodeWithTag("insights_comparison").assertTextEquals("50% more than September")
    }

    @Test
    fun withoutLastMonthThereIsNoComparisonLine() {
        show(loaded.copy(previous = null))

        composeRule.onNodeWithTag("insights_comparison").assertDoesNotExist()
    }

    @Test
    fun listsCategoriesBiggestFirstWithTheirShare() {
        show(loaded)

        composeRule.onNodeWithTag("category_FOOD").assertIsDisplayed()
        composeRule.onNodeWithTag("category_BILLS").assertIsDisplayed()
        composeRule.onNodeWithText("87% of spending").assertIsDisplayed()
        composeRule.onNodeWithText("13% of spending").assertIsDisplayed()
    }

    @Test
    fun theWholeRowIsOneSpokenSentence() {
        show(loaded)

        composeRule
            .onNodeWithContentDescription("Food, $65.00, 87% of spending, 1 transaction")
            .assertIsDisplayed()
    }

    @Test
    fun aMonthWithIncomeButNoSpendingSaysSo() {
        show(loaded.copy(summary = uiSummary(categories = emptyList(), transactionCount = 1)))

        composeRule.onNodeWithTag("insights_no_spending").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_spent").assertTextEquals("$0.00")
    }

    @Test
    fun aMonthWithNoActivityIsAnEmptyState() {
        show(loaded.copy(summary = uiSummary(categories = emptyList(), income = 0, transactionCount = 0)))

        composeRule.onNodeWithText("No activity in October 2026").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_spent").assertDoesNotExist()
    }

    @Test
    fun nextIsDisabledOnTheCurrentMonth() {
        show(loaded)

        composeRule.onNodeWithTag("month_next").assertIsNotEnabled()
    }

    @Test
    fun theMonthButtonsSendIntents() {
        show(loaded.copy(month = YearMonth.of(2026, 9)))

        composeRule.onNodeWithTag("month_previous").performClick()
        composeRule.onNodeWithTag("month_next").performClick()

        assertEquals(listOf(InsightsIntent.PreviousMonth, InsightsIntent.NextMonth), intents)
    }

    @Test
    fun showsALoadingPlaceholderBeforeTheFirstData() {
        show(InsightsState(month = october, currentMonth = october, isLoading = true))

        composeRule.onNodeWithContentDescription("Loading insights").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_spent").assertDoesNotExist()
    }

    @Test
    fun aFailedFirstLoadOffersARetry() {
        show(InsightsState(month = october, currentMonth = october, error = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(InsightsIntent.Refresh), intents)
    }

    @Test
    fun aFailedRefreshKeepsTheNumbersAndOffersRetry() {
        show(loaded.copy(error = AppError.Timeout))

        composeRule.onNodeWithTag("insights_spent").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(InsightsIntent.Refresh), intents)
    }
}