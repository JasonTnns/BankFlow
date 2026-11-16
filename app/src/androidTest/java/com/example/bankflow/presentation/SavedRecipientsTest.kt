package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.model.AccountStatus
import com.example.bankflow.presentation.transfer.DestinationMode
import com.example.bankflow.presentation.transfer.SaveRecipientState
import com.example.bankflow.presentation.transfer.TransferContent
import com.example.bankflow.presentation.transfer.TransferIntent
import com.example.bankflow.presentation.transfer.TransferState
import com.example.bankflow.presentation.transfer.TransferStep
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedRecipientsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<TransferIntent>()
    private var manageClicks = 0

    private fun show(state: TransferState) {
        composeRule.setContent {
            BankFlowTheme {
                TransferContent(
                    state = state,
                    onIntent = { intents += it },
                    onDone = {},
                    onManageRecipients = { manageClicks++ },
                )
            }
        }
    }

    private val form = TransferState(
        accounts = listOf(UI_ACCOUNT, UI_SAVINGS),
        isLoadingAccounts = false,
        sourceAccountId = "acc-1",
    )
    private val mum = uiBeneficiary("b1", "Mum")
    private val success = form.copy(step = TransferStep.SUCCESS, review = UI_REVIEW, result = UI_RESULT)


    @Test
    fun savedRecipientsAreOfferedAsChips() {
        show(form.copy(beneficiaries = listOf(mum)))

        composeRule.onNodeWithText("Mum").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingAChipChoosesThatRecipient() {
        show(form.copy(beneficiaries = listOf(mum)))

        composeRule.onNodeWithTag("recipient_chip_b1").performScrollTo().performClick()

        assertEquals(listOf(TransferIntent.RecipientSelected(mum)), intents)
    }

    @Test
    fun theChipMatchingTheTypedNumberIsSelected() {
        show(form.copy(beneficiaries = listOf(mum), accountNumber = "200000000001"))

        composeRule.onNodeWithTag("recipient_chip_b1").performScrollTo().assertIsSelected()
    }

    @Test
    fun aFrozenRecipientCannotBeChosen() {
        show(form.copy(beneficiaries = listOf(uiBeneficiary("b2", "Landlord", status = AccountStatus.FROZEN))))

        composeRule.onNodeWithTag("recipient_chip_b2").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun withNoRecipientsThereIsAHintAndAWayToAddOne() {
        show(form)

        composeRule.onNodeWithText("People you save will appear here", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("manage_recipients").performScrollTo().performClick()

        assertEquals(1, manageClicks)
    }

    @Test
    fun manageLeadsToTheRecipientsScreen() {
        show(form.copy(beneficiaries = listOf(mum)))

        composeRule.onNodeWithTag("manage_recipients").performScrollTo()
        composeRule.onNodeWithText("Manage").performClick()

        assertEquals(1, manageClicks)
    }

    @Test
    fun theyAreHiddenWhenMovingMoneyBetweenYourOwnAccounts() {
        show(form.copy(beneficiaries = listOf(mum), destinationMode = DestinationMode.OWN_ACCOUNT))

        composeRule.onNodeWithTag("recipient_chip_b1").assertDoesNotExist()
    }


    @Test
    fun aNewRecipientCanBeSavedFromTheSuccessScreen() {
        show(success)

        composeRule.onNodeWithTag("save_recipient").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save Bob D. as a recipient").assertIsDisplayed()
        composeRule.onNodeWithTag("save_recipient").performClick()

        assertEquals(listOf(TransferIntent.SaveRecipient), intents)
    }

    @Test
    fun anAlreadySavedRecipientIsNotOffered() {
        show(success.copy(beneficiaries = listOf(uiBeneficiary("b1", "Bob"))))

        composeRule.onNodeWithTag("save_recipient").assertDoesNotExist()
    }

    @Test
    fun afterSavingTheScreenConfirmsIt() {
        show(
            success.copy(
                beneficiaries = listOf(uiBeneficiary("b1", "Bob")),
                saveRecipient = SaveRecipientState.Saved,
            ),
        )

        composeRule.onNodeWithTag("recipient_saved").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save_recipient").assertDoesNotExist()
    }

    @Test
    fun aFailedSaveSaysSoAndOffersAnotherTry() {
        show(success.copy(saveRecipient = SaveRecipientState.Failed(AppError.NoConnection)))

        composeRule.onNodeWithTag("save_recipient_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save_recipient").assertIsDisplayed()
    }

    @Test
    fun whileSavingTheButtonIsDisabled() {
        show(success.copy(saveRecipient = SaveRecipientState.Saving))

        composeRule.onNodeWithTag("save_recipient").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun anOwnAccountTransferOffersNoSaving() {
        show(success.copy(review = UI_REVIEW.copy(isOwnAccount = true)))

        composeRule.onNodeWithTag("save_recipient").assertDoesNotExist()
    }
}