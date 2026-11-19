package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.presentation.accounts.FilterSheetContent
import com.example.bankflow.presentation.accounts.filter.FilterDraft
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FilterSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val drafts = mutableListOf<FilterDraft>()
    private var applies = 0
    private var resets = 0
    private var datePicks = 0

    private fun show(draft: FilterDraft = FilterDraft(), errors: FilterDraft.Errors = FilterDraft.Errors()) {
        composeRule.setContent {
            BankFlowTheme {
                FilterSheetContent(
                    draft = draft,
                    errors = errors,
                    onDraftChange = { drafts += it },
                    onPickDates = { datePicks++ },
                    onReset = { resets++ },
                    onApply = { applies++ },
                )
            }
        }
    }

    @Test
    fun theCurrentDraftIsShownAsSelected() {
        show(FilterDraft(direction = Direction.OUT, category = Category.FOOD))

        composeRule.onNodeWithText("Money out").assertIsSelected()
        composeRule.onNodeWithText("Food").assertIsSelected()
        composeRule.onNodeWithText("Any type").assertIsDisplayed()
    }

    @Test
    fun choosingATypeOrCategoryEditsTheDraft() {
        show()

        composeRule.onNodeWithText("Money in").performClick()
        composeRule.onNodeWithText("Bills").performScrollTo().performClick()

        assertEquals(
            listOf(FilterDraft(direction = Direction.IN), FilterDraft(category = Category.BILLS)),
            drafts,
        )
    }

    @Test
    fun typingAnAmountEditsTheDraft() {
        show()

        composeRule.onNodeWithTag("filter_min").performScrollTo().performTextInput("25")

        assertEquals(listOf(FilterDraft(minAmountText = "25")), drafts)
    }

    @Test
    fun amountErrorsAreShownUnderTheirFields() {
        FilterDraft.Errors(minAmount = FieldIssue.AmountInvalid)
        composeRule.onNodeWithText("Enter an amount like 25 or 25.50").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDateButtonShowsTheChosenRangeAndOpensThePicker() {
        show(FilterDraft(fromDate = LocalDate.of(2026, 10, 1), toDate = LocalDate.of(2026, 10, 1)))

        composeRule.onNodeWithTag("filter_dates").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dates").performClick()

        assertEquals(1, datePicks)
    }

    @Test
    fun resetAndApplyAreSeparateActions() {
        show()

        composeRule.onNodeWithTag("filter_reset").performClick()
        composeRule.onNodeWithTag("filter_apply").performScrollTo().performClick()

        assertEquals(1, resets)
        assertEquals(1, applies)
    }
}