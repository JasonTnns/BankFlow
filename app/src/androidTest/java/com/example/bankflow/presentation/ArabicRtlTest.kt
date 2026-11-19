package com.example.bankflow.presentation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bankflow.R
import com.example.bankflow.presentation.auth.login.LoginContent
import com.example.bankflow.presentation.auth.login.LoginState
import com.example.bankflow.presentation.insights.InsightsContent
import com.example.bankflow.presentation.insights.InsightsState
import com.example.bankflow.presentation.transfer.TransferContent
import com.example.bankflow.presentation.transfer.TransferState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ArabicRtlTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val arabicDefault = DefaultLocaleRule(ARABIC)

    private fun show(locale: Locale, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            WithLocale(locale) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BankFlowTheme { content() }
                }
            }
        }
    }

    private val october = YearMonth.of(2026, 10)


    @Test
    fun theLoginScreenIsInArabic() {
        show(ARABIC) { LoginContent(LoginState(), onIntent = {}, onNavigateToRegister = {}) }

        composeRule.onNodeWithText("تسجيل الدخول").assertIsDisplayed()
        composeRule.onNodeWithText("جديد هنا؟ أنشئ حسابًا").assertIsDisplayed()
    }

    @Test
    fun theTransferFormIsInArabicAndKeepsItsButtonReachableAtLargeText() {
        show(ARABIC, fontScale = 2f) {
            TransferContent(
                state = TransferState(
                    accounts = listOf(UI_ACCOUNT, UI_SAVINGS),
                    isLoadingAccounts = false,
                    sourceAccountId = "acc-1",
                ),
                onIntent = {},
                onDone = {},
            )
        }

        composeRule.onNodeWithText("إرسال أموال").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_continue").performScrollTo().assertIsDisplayed()
    }


    private fun monthButtonsMirrored(locale: Locale): Boolean {
        show(locale) {
            InsightsContent(
                state = InsightsState(month = october, currentMonth = october, summary = uiSummary()),
                onIntent = {},
            )
        }
        val previous = composeRule.onNodeWithTag("month_previous").getBoundsInRoot()
        val next = composeRule.onNodeWithTag("month_next").getBoundsInRoot()
        return previous.left > next.left
    }

    @Test
    fun theMonthButtonsSwapSidesInArabic() {
        assertTrue(monthButtonsMirrored(ARABIC))
    }

    @Test
    fun theMonthButtonsStayInPlaceInEnglish() {
        assertTrue(!monthButtonsMirrored(Locale.ENGLISH))
    }

    @Test
    fun thePasswordToggleMovesToTheLeftInArabic() {
        show(ARABIC) { LoginContent(LoginState(), onIntent = {}, onNavigateToRegister = {}) }

        val field = composeRule.onNodeWithTag("login_password").getBoundsInRoot()
        val toggle = composeRule.onNodeWithContentDescription("إظهار كلمة المرور").getBoundsInRoot()

        val fieldCenter = (field.left + field.right) / 2
        val toggleCenter = (toggle.left + toggle.right) / 2
        assertTrue("the trailing icon should be on the left in a right-to-left layout", toggleCenter < fieldCenter)
    }

    private fun arabicResources() = InstrumentationRegistry.getInstrumentation().targetContext
        .createConfigurationContext(Configuration().apply { setLocale(ARABIC) })
        .resources

    private fun transactions(count: Int) =
        arabicResources().getQuantityString(R.plurals.insights_transactions, count, count)

    private fun digits(count: Int) = String.format(ARABIC, "%d", count)

    @Test
    fun arabicPluralsUseTheRightForm() {
        assertEquals("معاملة واحدة", transactions(1))
        assertEquals("معاملتان", transactions(2))
        listOf(3, 5, 10, 103).forEach { assertEquals("${digits(it)} معاملات", transactions(it)) }
        listOf(11, 25, 99, 100, 101).forEach { assertEquals("${digits(it)} معاملة", transactions(it)) }
    }

    @Test
    fun theSavedDataAgeUsesTheDualForm() {
        val resources = arabicResources()

        assertEquals(
            "عرض بيانات محفوظة منذ دقيقتين",
            resources.getQuantityString(R.plurals.saved_data_minutes, 2, 2),
        )
        assertEquals(
            "عرض بيانات محفوظة منذ ${digits(7)} ساعات",
            resources.getQuantityString(R.plurals.saved_data_hours, 7, 7),
        )
    }
}