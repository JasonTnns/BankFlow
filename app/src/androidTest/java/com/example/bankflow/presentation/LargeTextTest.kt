package com.example.bankflow.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.presentation.auth.login.LoginContent
import com.example.bankflow.presentation.auth.login.LoginState
import com.example.bankflow.presentation.auth.register.RegisterContent
import com.example.bankflow.presentation.auth.register.RegisterState
import com.example.bankflow.presentation.dashboard.DashboardContent
import com.example.bankflow.presentation.dashboard.DashboardState
import com.example.bankflow.presentation.insights.InsightsContent
import com.example.bankflow.presentation.insights.InsightsState
import com.example.bankflow.presentation.profile.ProfileContent
import com.example.bankflow.presentation.profile.ProfileState
import com.example.bankflow.presentation.security.LockScreen
import com.example.bankflow.presentation.transfer.TransferContent
import com.example.bankflow.presentation.transfer.TransferState
import com.example.bankflow.presentation.transfer.TransferStep
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class LargeTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(fontScale: Float = 2f, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BankFlowTheme { content() }
            }
        }
    }

    private val form = TransferState(
        accounts = listOf(UI_ACCOUNT, UI_SAVINGS),
        isLoadingAccounts = false,
        sourceAccountId = "acc-1",
    )

    @Test
    fun loginKeepsItsButtonReachable() {
        show { LoginContent(LoginState(error = AppError.InvalidCredentials), onIntent = {}, onNavigateToRegister = {}) }

        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun registrationKeepsItsButtonReachable() {
        show { RegisterContent(RegisterState(), onIntent = {}, onNavigateToLogin = {}) }

        composeRule.onNodeWithTag("register_submit").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun theTransferFormKeepsContinueReachable() {
        show { TransferContent(form, onIntent = {}, onDone = {}) }

        composeRule.onNodeWithTag("transfer_continue").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theReviewKeepsConfirmReachable() {
        show { TransferContent(form.copy(step = TransferStep.REVIEW, review = UI_REVIEW), onIntent = {}, onDone = {}) }

        composeRule.onNodeWithTag("review_confirm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSuccessScreenKeepsDoneReachable() {
        show {
            TransferContent(
                form.copy(step = TransferStep.SUCCESS, review = UI_REVIEW, result = UI_RESULT),
                onIntent = {},
                onDone = {},
            )
        }

        composeRule.onNodeWithTag("success_done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDashboardStillShowsItsAccounts() {
        show {
            DashboardContent(
                state = DashboardState(dashboard = UI_DASHBOARD),
                userName = "Alice Tester",
                onIntent = {},
                onAccountClick = {},
            )
        }

        composeRule.onNodeWithText("Everyday Checking").assertIsDisplayed()
    }

    @Test
    fun insightsStillReachesItsLastCategory() {
        val october = YearMonth.of(2026, 10)
        show {
            InsightsContent(
                state = InsightsState(month = october, currentMonth = october, summary = uiSummary()),
                onIntent = {},
            )
        }

        composeRule.onNodeWithTag("insights_list").performScrollToNode(hasTestTag("category_BILLS"))
        composeRule.onNodeWithTag("category_BILLS").assertIsDisplayed()
    }

    @Test
    fun profileKeepsLogOutReachable() {
        show {
            ProfileContent(
                state = ProfileState(profile = uiProfile()),
                onIntent = {},
                onOpenRecipients = {},
                onLogout = {},
                appVersion = "1.0",
            )
        }

        composeRule.onNodeWithTag("profile_logout").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLockScreenKeepsBothButtonsReachableEvenWithAnError() {
        show {
            LockScreen(
                failure = AuthFailure.LOCKED_OUT,
                isPrompting = false,
                onUnlock = {},
                onLogout = {},
                promptOnShow = false,
            )
        }

        composeRule.onNodeWithTag("lock_unlock").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lock_logout").performScrollTo().assertIsDisplayed()
    }
}