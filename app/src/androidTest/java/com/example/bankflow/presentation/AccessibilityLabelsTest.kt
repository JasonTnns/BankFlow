package com.example.bankflow.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.presentation.accounts.AccountDetailsContent
import com.example.bankflow.presentation.accounts.AccountDetailsState
import com.example.bankflow.presentation.auth.login.LoginContent
import com.example.bankflow.presentation.auth.login.LoginState
import com.example.bankflow.presentation.auth.register.RegisterContent
import com.example.bankflow.presentation.auth.register.RegisterState
import com.example.bankflow.presentation.beneficiaries.BeneficiariesContent
import com.example.bankflow.presentation.beneficiaries.BeneficiariesState
import com.example.bankflow.presentation.dashboard.DashboardContent
import com.example.bankflow.presentation.dashboard.DashboardState
import com.example.bankflow.presentation.insights.InsightsContent
import com.example.bankflow.presentation.insights.InsightsState
import com.example.bankflow.presentation.profile.DeleteAccountForm
import com.example.bankflow.presentation.profile.ProfileContent
import com.example.bankflow.presentation.profile.ProfileState
import com.example.bankflow.presentation.security.LockScreen
import com.example.bankflow.presentation.security.SecurityContent
import com.example.bankflow.presentation.security.SecurityState
import com.example.bankflow.presentation.transfer.TransferContent
import com.example.bankflow.presentation.transfer.TransferState
import com.example.bankflow.presentation.transfer.TransferStep
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.YearMonth
import java.util.Locale

/**
 * Every control you can tap must tell a screen reader what it is: text or a description. An icon button
 * with neither is announced as just "button". Checked on every screen, in English and in Arabic.
 */
@RunWith(Parameterized::class)
class AccessibilityLabelsTest(private val screen: String, private val languageTag: String) {

    companion object {
        private val screens = listOf(
            "login", "register", "dashboard", "history", "transfer-form", "transfer-review", "transfer-success",
            "transfer-failure", "insights", "profile", "profile-delete-dialog", "recipients", "lock", "security",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0} in {1}")
        fun data(): List<Array<Any>> = screens.flatMap { name -> listOf("en", "ar").map { arrayOf<Any>(name, it) } }
    }

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val defaultLocale = DefaultLocaleRule(Locale.forLanguageTag(languageTag))

    private val october = YearMonth.of(2026, 10)
    private val form = TransferState(
        accounts = listOf(UI_ACCOUNT, UI_SAVINGS),
        isLoadingAccounts = false,
        sourceAccountId = "acc-1",
        beneficiaries = listOf(uiBeneficiary("b1", "Mum")),
    )

    @Composable
    private fun Screen() = when (screen) {
        "login" -> LoginContent(LoginState(error = AppError.InvalidCredentials), onIntent = {}, onNavigateToRegister = {})
        "register" -> RegisterContent(RegisterState(), onIntent = {}, onNavigateToLogin = {})
        "dashboard" -> DashboardContent(DashboardState(dashboard = UI_DASHBOARD), "Alice Tester", onIntent = {}, onAccountClick = {})
        "history" -> AccountDetailsContent(
            state = AccountDetailsState(
                account = UI_ACCOUNT,
                items = listOf(uiTransaction("t1"), uiTransaction("t2")),
                hasLoaded = true,
            ),
            onIntent = {},
            onBack = {},
        )
        "transfer-form" -> TransferContent(form, onIntent = {}, onDone = {})
        "transfer-review" -> TransferContent(form.copy(step = TransferStep.REVIEW, review = UI_REVIEW), onIntent = {}, onDone = {})
        "transfer-success" -> TransferContent(
            form.copy(step = TransferStep.SUCCESS, review = UI_REVIEW, result = UI_RESULT),
            onIntent = {},
            onDone = {},
        )
        "transfer-failure" -> TransferContent(
            form.copy(step = TransferStep.FAILED, review = UI_REVIEW, failure = AppError.InsufficientFunds),
            onIntent = {},
            onDone = {},
        )
        "insights" -> InsightsContent(InsightsState(month = october, currentMonth = october, summary = uiSummary()), onIntent = {})
        "profile" -> ProfileContent(ProfileState(profile = uiProfile()), {}, {}, {}, appVersion = "1.0")
        "profile-delete-dialog" -> ProfileContent(
            ProfileState(profile = uiProfile(), deleteForm = DeleteAccountForm()), {}, {}, {}, appVersion = "1.0",
        )
        "recipients" -> BeneficiariesContent(
            BeneficiariesState(items = listOf(uiBeneficiary("b1", "Mum")), hasLoaded = true),
            onIntent = {},
            onBack = {},
        )
        "lock" -> LockScreen(AuthFailure.LOCKED_OUT, isPrompting = false, onUnlock = {}, onLogout = {}, promptOnShow = false)
        "security" -> SecurityContent(SecurityState(), onToggle = {})
        else -> error("unknown screen $screen")
    }

    @Test
    fun everyControlHasAName() {
        composeRule.setContent {
            WithLocale(Locale.forLanguageTag(languageTag)) { BankFlowTheme { Screen() } }
        }
        composeRule.waitForIdle()

        val unlabeled = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { node ->
            val config = node.config
            val hasText = config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true
            val hasDescription = config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true
            val isTextField = config.contains(SemanticsProperties.EditableText) // labelled by its own label
            !hasText && !hasDescription && !isTextField
        }

        assertTrue(
            "$screen ($languageTag): controls with no name at ${unlabeled.map { it.boundsInRoot }}",
            unlabeled.isEmpty(),
        )
    }
}