package com.example.bankflow.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.auth.login.LoginContent
import com.example.bankflow.presentation.auth.login.LoginIntent
import com.example.bankflow.presentation.auth.login.LoginState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<LoginIntent>()
    private var registerClicks = 0

    private fun show(state: LoginState = LoginState()) {
        composeRule.setContent {
            BankFlowTheme {
                LoginContent(
                    state = state,
                    onIntent = { intents += it },
                    onNavigateToRegister = { registerClicks++ },
                )
            }
        }
    }

    @Test
    fun showsTheFormAndNoErrorInitially() {
        show()

        composeRule.onNodeWithTag("login_email").assertIsDisplayed()
        composeRule.onNodeWithTag("login_password").assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").assertIsEnabled()
        composeRule.onNodeWithTag("auth_error").assertDoesNotExist()
    }

    @Test
    fun typingSendsIntents() {
        show()

        composeRule.onNodeWithTag("login_email").performTextInput("alice@example.com")
        composeRule.onNodeWithTag("login_password").performTextInput("Passw0rd123")

        assertEquals(
            listOf(
                LoginIntent.EmailChanged("alice@example.com"),
                LoginIntent.PasswordChanged("Passw0rd123"),
            ),
            intents,
        )
    }

    @Test
    fun tappingSignInSendsSubmit() {
        show()

        composeRule.onNodeWithTag("login_submit").performClick()

        assertEquals(listOf(LoginIntent.Submit), intents)
    }

    @Test
    fun showsTheBannerForWrongCredentials() {
        show(LoginState(error = AppError.InvalidCredentials))

        composeRule.onNodeWithText("Incorrect email or password.").assertIsDisplayed()
    }

    @Test
    fun showsFieldErrorsUnderTheirFields() {
        show(
            LoginState(
                error = AppError.Validation(
                    fieldErrors = mapOf(
                        "email" to listOf("Email is required"),
                        "password" to listOf("Password is required"),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Email is required").assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_error").assertDoesNotExist()
    }

    @Test
    fun whileLoadingTheFormIsLockedSoNothingCanBeSentTwice() {
        show(LoginState(email = "a@b.com", password = "x", isLoading = true))

        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_email").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_password").assertIsNotEnabled()
    }

    @Test
    fun theShowPasswordButtonSendsAToggle() {
        show()

        composeRule.onNodeWithContentDescription("Show password").performClick()

        assertEquals(listOf(LoginIntent.TogglePasswordVisibility), intents)
    }

    @Test
    fun theButtonLabelFollowsThePasswordVisibility() {
        show(LoginState(passwordVisible = true))

        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    @Test
    fun theCreateAccountLinkNavigates() {
        show()

        composeRule.onNodeWithText("New here? Create an account").performClick()

        assertEquals(1, registerClicks)
    }
}