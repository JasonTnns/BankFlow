package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.security.AuthFailure
import com.example.bankflow.domain.security.BiometricAvailability
import com.example.bankflow.presentation.security.SecurityContent
import com.example.bankflow.presentation.security.SecurityState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val toggles = mutableListOf<Boolean>()

    private fun show(state: SecurityState) {
        composeRule.setContent {
            BankFlowTheme { SecurityContent(state = state, onToggle = { toggles += it }) }
        }
    }

    @Test
    fun showsTheSettingAndWhatItDoes() {
        show(SecurityState())

        composeRule.onNodeWithText("Unlock with biometrics or device PIN").assertIsDisplayed()
        composeRule.onNodeWithText("Ask for your fingerprint", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("security_switch").assertIsOff()
    }

    @Test
    fun reflectsWhenItIsOn() {
        show(SecurityState(enabled = true))

        composeRule.onNodeWithTag("security_switch").assertIsOn()
    }

    @Test
    fun tappingAsksToSwitchItToTheOppositeState() {
        show(SecurityState(enabled = false))

        composeRule.onNodeWithTag("security_switch").performClick()

        assertEquals(listOf(true), toggles)
    }

    @Test
    fun withNothingSetUpItExplainsHowToFixThat() {
        show(SecurityState(availability = BiometricAvailability.NONE_ENROLLED))

        composeRule.onNodeWithText("Set up a screen lock", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("security_switch").assertIsNotEnabled()
    }

    @Test
    fun onADeviceWithoutHardwareItCannotBeUsed() {
        show(SecurityState(availability = BiometricAvailability.NO_HARDWARE))

        composeRule.onNodeWithText("can't verify your identity", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("security_switch").assertIsNotEnabled()
    }

    @Test
    fun anExistingLockCanStillBeSwitchedOffWhenVerificationIsGone() {
        show(SecurityState(enabled = true, availability = BiometricAvailability.NONE_ENROLLED))

        composeRule.onNodeWithTag("security_switch").performClick()

        assertEquals(listOf(false), toggles)
    }

    @Test
    fun whileSavingTheSwitchIsLocked() {
        show(SecurityState(isSaving = true))

        composeRule.onNodeWithTag("security_switch").assertIsNotEnabled()
    }

    @Test
    fun aFailureIsExplained() {
        show(SecurityState(failure = AuthFailure.LOCKED_OUT))

        composeRule.onNodeWithTag("security_error").assertIsDisplayed()
    }

    @Test
    fun noFailureNoMessage() {
        show(SecurityState())

        composeRule.onNodeWithTag("security_error").assertDoesNotExist()
    }
}