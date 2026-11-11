package com.example.bankflow.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.auth.register.RegisterContent
import com.example.bankflow.presentation.auth.register.RegisterIntent
import com.example.bankflow.presentation.auth.register.RegisterState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<RegisterIntent>()
    private var loginClicks = 0

    private fun show(state: RegisterState = RegisterState()) {
        composeRule.setContent {
            BankFlowTheme {
                RegisterContent(
                    state = state,
                    onIntent = { intents += it },
                    onNavigateToLogin = { loginClicks++ },
                )
            }
        }
    }

    @Test
    fun showsTheThreeFieldsAndThePasswordRule() {
        show()

        composeRule.onNodeWithTag("register_name").assertIsDisplayed()
        composeRule.onNodeWithTag("register_email").assertIsDisplayed()
        composeRule.onNodeWithTag("register_password").assertIsDisplayed()
        composeRule.onNodeWithText("At least 8 characters, with a letter and a number").assertIsDisplayed()
    }

    @Test
    fun typingSendsIntentsForEachField() {
        show()

        composeRule.onNodeWithTag("register_name").performTextInput("Alice")
        composeRule.onNodeWithTag("register_email").performTextInput("a@b.com")

        assertEquals(
            listOf(RegisterIntent.FullNameChanged("Alice"), RegisterIntent.EmailChanged("a@b.com")),
            intents,
        )
    }

    @Test
    fun anEmailThatIsTakenShowsUnderTheEmailFieldAndNotAsABanner() {
        show(RegisterState(error = AppError.EmailTaken))

        composeRule.onNodeWithText("An account with this email already exists.").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_error").assertDoesNotExist()
    }

    @Test
    fun aPasswordErrorReplacesTheHint() {
        show(
            RegisterState(
                error = AppError.Validation(
                    fieldErrors = mapOf("password" to listOf("Password must be at least 8 characters")),
                ),
            ),
        )

        composeRule.onNodeWithText("Password must be at least 8 characters").assertIsDisplayed()
        composeRule.onNodeWithText("At least 8 characters, with a letter and a number").assertDoesNotExist()
    }

    @Test
    fun aNetworkErrorShowsInTheBanner() {
        show(RegisterState(error = AppError.NoConnection))

        composeRule.onNodeWithTag("auth_error").assertIsDisplayed()
    }

    @Test
    fun whileLoadingTheButtonIsDisabled() {
        show(RegisterState(isLoading = true))

        composeRule.onNodeWithTag("register_submit").assertIsNotEnabled()
    }

    @Test
    fun theSignInLinkNavigatesBack() {
        show()

        composeRule.onNodeWithText("Already have an account? Sign in").performClick()

        assertEquals(1, loginClicks)
    }
}