package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.profile.DeleteAccountForm
import com.example.bankflow.presentation.profile.NameEdit
import com.example.bankflow.presentation.profile.ProfileContent
import com.example.bankflow.presentation.profile.ProfileIntent
import com.example.bankflow.presentation.profile.ProfileState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ProfileIntent>()
    private var recipientClicks = 0
    private var logouts = 0

    private fun show(state: ProfileState) {
        composeRule.setContent {
            BankFlowTheme {
                ProfileContent(
                    state = state,
                    onIntent = { intents += it },
                    onOpenRecipients = { recipientClicks++ },
                    onLogout = { logouts++ },
                    appVersion = "1.0",
                )
            }
        }
    }

    private val loaded = ProfileState(profile = uiProfile())


    @Test
    fun showsTheNameEmailAndAccountDetails() {
        show(loaded)

        composeRule.onNodeWithTag("profile_name").assertTextEquals("Alice Demo")
        composeRule.onNodeWithTag("profile_email").assertTextEquals("alice@example.com")
        composeRule.onNodeWithTag("row_limit").performScrollTo().assertTextEquals("$5,000.00")
        composeRule.onNodeWithTag("row_since").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Your email and transfer limit can't be changed here.", substring = false)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun showsTheAppVersionAndTheDemoNote() {
        show(loaded)

        composeRule.onNodeWithTag("row_version").performScrollTo().assertTextEquals("1.0")
        composeRule.onNodeWithText("BankFlow is a demo app", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSavedRecipientsRowOpensTheRecipientsScreen() {
        show(loaded)

        composeRule.onNodeWithTag("profile_recipients").performScrollTo().performClick()

        assertEquals(1, recipientClicks)
    }


    @Test
    fun showsALoadingIndicatorBeforeTheFirstData() {
        show(ProfileState(isLoading = true))

        composeRule.onNodeWithContentDescription("Loading profile").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_name").assertDoesNotExist()
    }

    @Test
    fun aFailedFirstLoadOffersARetry() {
        show(ProfileState(loadError = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(ProfileIntent.Refresh), intents)
    }


    @Test
    fun theCurrentThemeIsSelected() {
        show(ProfileState(profile = uiProfile(theme = ThemePreference.DARK)))

        composeRule.onNodeWithTag("theme_DARK").performScrollTo().assertIsSelected()
    }

    @Test
    fun choosingAThemeSendsAnIntent() {
        show(loaded)

        composeRule.onNodeWithTag("theme_LIGHT").performScrollTo().performClick()

        assertEquals(listOf(ProfileIntent.SelectTheme(ThemePreference.LIGHT)), intents)
    }

    @Test
    fun aThemeChangeInFlightMovesTheSelectionAndLocksTheOptions() {
        show(loaded.copy(pendingTheme = ThemePreference.DARK))

        composeRule.onNodeWithTag("theme_DARK").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("theme_LIGHT").assertIsNotEnabled()
        composeRule.onNodeWithTag("profile_edit_name").assertIsNotEnabled()
    }

    @Test
    fun aRefusedThemeChangeShowsWhy() {
        show(loaded.copy(themeError = AppError.NoConnection))

        composeRule.onNodeWithTag("theme_error").performScrollTo().assertIsDisplayed()
    }


    @Test
    fun theEditButtonOpensTheEditor() {
        show(loaded)

        composeRule.onNodeWithTag("profile_edit_name").performClick()

        assertEquals(listOf(ProfileIntent.OpenNameEdit), intents)
    }

    @Test
    fun theEditorSendsWhatIsTypedAndSaves() {
        show(loaded.copy(nameEdit = NameEdit(value = "Alice Demo")))

        composeRule.onNodeWithTag("name_field").performTextInput("!")
        composeRule.onNodeWithTag("name_save").performClick()

        assertEquals(listOf(ProfileIntent.NameChanged("Alice Demo!"), ProfileIntent.SaveName), intents)
    }

    @Test
    fun theEditorShowsAFieldErrorUnderTheField() {
        show(
            loaded.copy(
                nameEdit = NameEdit(
                    value = "",
                    error = AppError.Validation(fieldErrors = mapOf("fullName" to listOf("Full name is required"))),
                ),
            ),
        )

        composeRule.onNodeWithText("Full name is required").assertIsDisplayed()
        composeRule.onNodeWithTag("name_error").assertDoesNotExist()
    }

    @Test
    fun aNetworkProblemShowsInTheEditorBanner() {
        show(loaded.copy(nameEdit = NameEdit(value = "Alice", error = AppError.NoConnection)))

        composeRule.onNodeWithTag("name_error").assertIsDisplayed()
    }

    @Test
    fun whileSavingTheEditorIsLocked() {
        show(loaded.copy(nameEdit = NameEdit(value = "Alice", isSaving = true)))

        composeRule.onNodeWithTag("name_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("name_cancel").assertIsNotEnabled()
        composeRule.onNodeWithTag("name_field").assertIsNotEnabled()
    }

    @Test
    fun cancellingTheEditorSendsDismiss() {
        show(loaded.copy(nameEdit = NameEdit(value = "Alice")))

        composeRule.onNodeWithTag("name_cancel").performClick()

        assertEquals(listOf(ProfileIntent.DismissNameEdit), intents)
    }


    @Test
    fun logoutAsksForConfirmationFirst() {
        show(loaded)

        composeRule.onNodeWithTag("profile_logout").performScrollTo().performClick()

        composeRule.onNodeWithText("Log out?").assertIsDisplayed()
        assertEquals("nothing happens until the user confirms", 0, logouts)
    }

    @Test
    fun confirmingLogsOut() {
        show(loaded)
        composeRule.onNodeWithTag("profile_logout").performScrollTo().performClick()

        composeRule.onNodeWithTag("logout_confirm").performClick()

        assertEquals(1, logouts)
        composeRule.onNodeWithText("Log out?").assertDoesNotExist()
    }

    @Test
    fun cancellingKeepsTheUserSignedIn() {
        show(loaded)
        composeRule.onNodeWithTag("profile_logout").performScrollTo().performClick()

        composeRule.onNodeWithTag("logout_cancel").performClick()

        assertEquals(0, logouts)
        composeRule.onNodeWithText("Log out?").assertDoesNotExist()
    }

    // ---------------------------------------------------------------- deleting the account

    @Test
    fun deleteAccountOpensTheDialogFirst() {
        show(loaded)

        composeRule.onNodeWithTag("profile_delete").performScrollTo().performClick()

        assertEquals(listOf(ProfileIntent.OpenDelete), intents)
    }

    @Test
    fun theDialogExplainsWhatHappensAndSendsThePasswordAndConfirmation() {
        show(loaded.copy(deleteForm = DeleteAccountForm()))

        composeRule.onNodeWithText("This closes your accounts", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("delete_password").performTextInput("Passw0rd123")
        composeRule.onNodeWithTag("delete_account_confirm").performClick()

        assertEquals(
            listOf(ProfileIntent.DeletePasswordChanged("Passw0rd123"), ProfileIntent.ConfirmDelete),
            intents,
        )
    }

    @Test
    fun aWrongPasswordShowsUnderTheField() {
        show(loaded.copy(deleteForm = DeleteAccountForm(
            password = "x",
            error = AppError.PasswordIncorrect
        )
        ))

        composeRule.onNodeWithText("Incorrect password.").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_error").assertDoesNotExist()
    }

    @Test
    fun aNetworkProblemShowsInTheBanner() {
        show(loaded.copy(deleteForm = DeleteAccountForm(error = AppError.NoConnection)))

        composeRule.onNodeWithTag("delete_error").assertIsDisplayed()
    }

    @Test
    fun whileDeletingTheDialogIsLocked() {
        show(loaded.copy(deleteForm = DeleteAccountForm(password = "x", isDeleting = true)))

        composeRule.onNodeWithTag("delete_account_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("delete_account_cancel").assertIsNotEnabled()
        composeRule.onNodeWithTag("delete_password").assertIsNotEnabled()
    }
}