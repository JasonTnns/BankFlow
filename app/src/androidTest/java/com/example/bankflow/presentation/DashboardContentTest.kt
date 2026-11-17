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
import com.example.bankflow.domain.model.Dashboard
import com.example.bankflow.presentation.dashboard.DashboardIntent
import com.example.bankflow.presentation.dashboard.DashboardState
import com.example.bankflow.presentation.dashboard.DashboardContent
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DashboardContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<DashboardIntent>()
    private val clickedAccounts = mutableListOf<String>()
    private var logouts = 0

    private fun show(state: DashboardState) {
        composeRule.setContent {
            BankFlowTheme {
                DashboardContent(
                    state = state,
                    userName = "Alice Tester",
                    onIntent = { intents += it },
                    onAccountClick = { clickedAccounts += it },
                    onLogout = { logouts++ },
                )
            }
        }
    }

    @Test
    fun greetsTheUserAndShowsBalancesAccountsAndActivity() {
        show(DashboardState(dashboard = UI_DASHBOARD))

        composeRule.onNodeWithText("Hi, Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Total balance").assertIsDisplayed()
        composeRule.onNodeWithText("Everyday Checking").assertIsDisplayed()
        composeRule.onNodeWithText("Fresh Market").assertIsDisplayed()
        composeRule.onNodeWithText("Acme Payroll").assertIsDisplayed()
    }

    @Test
    fun tappingAnAccountReportsItsId() {
        show(DashboardState(dashboard = UI_DASHBOARD))

        composeRule.onNodeWithText("Everyday Checking").performClick()

        assertEquals(listOf("acc-1"), clickedAccounts)
    }

    @Test
    fun noActivityShowsAnEmptyMessage() {
        show(DashboardState(dashboard = Dashboard(listOf(UI_ACCOUNT), emptyList(), emptyList())))

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }

    @Test
    fun aFailedFirstLoadShowsAnErrorWithRetry() {
        show(DashboardState(error = AppError.NoConnection))

        composeRule.onNodeWithText("No internet connection. Check your network and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(DashboardIntent.Refresh), intents)
    }

    @Test
    fun aFailedRefreshKeepsTheOldDataAndOffersRetry() {
        show(DashboardState(dashboard = UI_DASHBOARD, error = AppError.Timeout))

        composeRule.onNodeWithText("Everyday Checking").assertIsDisplayed()
        composeRule.onNodeWithText("The request took too long. Please try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(DashboardIntent.Refresh), intents)
    }

    @Test
    fun theLogoutButtonIsReachable() {
        show(DashboardState(dashboard = UI_DASHBOARD))

        composeRule.onNodeWithContentDescription("Log out").performClick()

        assertEquals(1, logouts)
    }

    @Test
    fun savedDataIsLabelledAsSuchWithItsAge() {
        show(DashboardState(dashboard = UI_DASHBOARD, savedAt = Instant.now().minusSeconds(300)))

        composeRule.onNodeWithTag("saved_notice").assertTextEquals("Showing saved data from 5 min ago")
    }

    @Test
    fun freshDataHasNoSavedNotice() {
        show(DashboardState(dashboard = UI_DASHBOARD))

        composeRule.onNodeWithTag("saved_notice").assertDoesNotExist()
    }
}