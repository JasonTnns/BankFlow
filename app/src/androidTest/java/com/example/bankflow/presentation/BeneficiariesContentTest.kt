package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.presentation.beneficiaries.AddRecipientForm
import com.example.bankflow.presentation.beneficiaries.BeneficiariesContent
import com.example.bankflow.presentation.beneficiaries.BeneficiariesIntent
import com.example.bankflow.presentation.beneficiaries.BeneficiariesState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeneficiariesContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<BeneficiariesIntent>()
    private var backClicks = 0

    private fun show(state: BeneficiariesState) {
        composeRule.setContent {
            BankFlowTheme {
                BeneficiariesContent(state = state, onIntent = { intents += it }, onBack = { backClicks++ })
            }
        }
    }

    private val mum = uiBeneficiary("b1", "Mum")
    private val landlord = uiBeneficiary("b2", "Landlord", "300000000001", AccountStatus.FROZEN)
    private val loaded = BeneficiariesState(items = listOf(mum, landlord), hasLoaded = true)

    @Test
    fun listsRecipientsWithTheirOwnerAndMaskedNumber() {
        show(loaded)

        composeRule.onNodeWithText("Mum").assertIsDisplayed()
        composeRule.onNodeWithText("Bob D. \u00B7 \u2022\u2022\u2022\u20220001").assertIsDisplayed()
    }

    @Test
    fun aFrozenAccountIsMarkedUnavailable() {
        show(loaded)

        composeRule.onNodeWithText("Unavailable", substring = true).assertIsDisplayed()
    }

    @Test
    fun theDeleteButtonAsksFirst() {
        show(loaded)

        composeRule.onNodeWithContentDescription("Delete Mum").performClick()

        assertEquals(listOf(BeneficiariesIntent.AskDelete(mum)), intents)
    }

    @Test
    fun theAddButtonOpensTheDialog() {
        show(loaded)

        composeRule.onNodeWithTag("add_recipient").performClick()

        assertEquals(listOf(BeneficiariesIntent.OpenAdd), intents)
    }

    @Test
    fun withNoRecipientsTheEmptyStateOffersToAddOne() {
        show(BeneficiariesState(hasLoaded = true))

        composeRule.onNodeWithText("No saved recipients").assertIsDisplayed()
        composeRule.onNodeWithTag("add_recipient").assertDoesNotExist()
        composeRule.onNodeWithText("Add recipient").performClick()

        assertEquals(listOf(BeneficiariesIntent.OpenAdd), intents)
    }

    @Test
    fun aFailedLoadOffersARetry() {
        show(BeneficiariesState(loadError = AppError.NoConnection))

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(BeneficiariesIntent.Retry), intents)
    }

    @Test
    fun aFailedDeleteShowsAnErrorAndKeepsTheList() {
        show(loaded.copy(actionError = AppError.NoConnection))

        composeRule.onNodeWithText("Mum").assertIsDisplayed()
        composeRule.onNodeWithText("No internet connection", substring = true).assertIsDisplayed()
    }

    @Test
    fun theBackButtonWorks() {
        show(loaded)

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backClicks)
    }


    @Test
    fun theAddDialogSendsWhatIsTypedAndSaves() {
        show(loaded.copy(addForm = AddRecipientForm()))

        composeRule.onNodeWithTag("add_nickname").performTextInput("Dad")
        composeRule.onNodeWithTag("add_number").performTextInput("200000000001")
        composeRule.onNodeWithTag("add_save").performClick()

        assertEquals(
            listOf(
                BeneficiariesIntent.NicknameChanged("Dad"),
                BeneficiariesIntent.NumberChanged("200000000001"),
                BeneficiariesIntent.SaveNew,
            ),
            intents,
        )
    }

    @Test
    fun theAddDialogShowsAnUnknownNumberUnderItsField() {
        show(loaded.copy(addForm = AddRecipientForm(accountNumber = "999999999999", error = AppError.NotFound)))

        composeRule.onNodeWithText("No account found with that number").assertIsDisplayed()
        composeRule.onNodeWithTag("add_error").assertDoesNotExist()
    }

    @Test
    fun theAddDialogShowsFieldErrorsAndNetworkProblemsSeparately() {
        show(
            loaded.copy(
                addForm = AddRecipientForm(
                    error = AppError.Validation(fieldErrors = mapOf("nickname" to listOf("Give this recipient a name"))),
                ),
            ),
        )
        composeRule.onNodeWithText("Give this recipient a name").assertIsDisplayed()
        composeRule.onNodeWithTag("add_error").assertDoesNotExist()
    }

    @Test
    fun aNetworkProblemShowsInTheDialogBanner() {
        show(loaded.copy(addForm = AddRecipientForm(error = AppError.NoConnection)))

        composeRule.onNodeWithTag("add_error").assertIsDisplayed()
    }

    @Test
    fun whileSavingTheDialogIsLocked() {
        show(loaded.copy(addForm = AddRecipientForm(nickname = "Dad", accountNumber = "200000000001", isSaving = true)))

        composeRule.onNodeWithTag("add_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("add_nickname").assertIsNotEnabled()
    }


    @Test
    fun theDeleteDialogNamesTheRecipientAndConfirmsOrCancels() {
        show(loaded.copy(pendingDelete = mum))

        composeRule.onNodeWithText("Delete recipient?").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_confirm").performClick()
        composeRule.onNodeWithTag("delete_cancel").performClick()

        assertEquals(listOf(BeneficiariesIntent.ConfirmDelete, BeneficiariesIntent.CancelDelete), intents)
    }

    @Test
    fun whileDeletingTheDialogCannotBeActedOn() {
        show(loaded.copy(pendingDelete = mum, isDeleting = true))

        composeRule.onNodeWithTag("delete_confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("delete_cancel").assertIsNotEnabled()
    }
}