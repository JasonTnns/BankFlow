package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.presentation.security.LockScreen
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var unlocks = 0
    private var logouts = 0

    private fun show(failure: AuthFailure? = null, isPrompting: Boolean = false, promptOnShow: Boolean = false) {
        composeRule.setContent {
            BankFlowTheme {
                LockScreen(
                    failure = failure,
                    isPrompting = isPrompting,
                    onUnlock = { unlocks++ },
                    onLogout = { logouts++ },
                    promptOnShow = promptOnShow,
                )
            }
        }
    }

    @Test
    fun explainsWhyTheAppIsLockedAndOffersUnlockAndLogout() {
        show()

        composeRule.onNodeWithText("BankFlow is locked").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_unlock").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_logout").assertIsDisplayed()
    }

    @Test
    fun theUnlockButtonAsksForTheSystemPrompt() {
        show()

        composeRule.onNodeWithTag("lock_unlock").performClick()

        assertEquals(1, unlocks)
    }

    @Test
    fun thePromptOpensByItselfWhenTheScreenAppears() {
        show(promptOnShow = true)
        composeRule.waitForIdle()

        assertEquals(1, unlocks)
    }

    @Test
    fun whilePromptingTheUnlockButtonIsDisabled() {
        show(isPrompting = true)

        composeRule.onNodeWithTag("lock_unlock").assertIsNotEnabled()
    }

    @Test
    fun aLockoutExplainsItAndLogoutStaysAvailable() {
        show(failure = AuthFailure.LOCKED_OUT)

        composeRule.onNodeWithTag("lock_error").assertIsDisplayed()
        composeRule.onNodeWithText("Too many attempts", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("lock_logout").performClick()

        assertEquals(1, logouts)
    }

    @Test
    fun anUnavailableDeviceSendsTheUserToLogOut() {
        show(failure = AuthFailure.UNAVAILABLE)

        composeRule.onNodeWithText("Log out and sign in with your password", substring = true).assertIsDisplayed()
    }

    @Test
    fun aGenericErrorAsksToTryAgain() {
        show(failure = AuthFailure.ERROR)

        composeRule.onNodeWithText("We couldn't verify you. Please try again.").assertIsDisplayed()
    }
}