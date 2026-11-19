package com.example.bankflow.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.presentation.accounts.HistoryFilterBar
import com.example.bankflow.presentation.accounts.filter.ActiveFilterChip
import com.example.bankflow.presentation.accounts.filter.FilterKind
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryFilterBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val searches = mutableListOf<String>()
    private val removed = mutableListOf<FilterKind>()
    private var opened = 0
    private var cleared = 0

    private fun show(searchText: String = "", chips: List<ActiveFilterChip> = emptyList()) {
        composeRule.setContent {
            BankFlowTheme {
                HistoryFilterBar(
                    searchText = searchText,
                    chips = chips,
                    onSearchChange = { searches += it },
                    onOpenFilters = { opened++ },
                    onRemoveFilter = { removed += it },
                    onClearAll = { cleared++ },
                )
            }
        }
    }

    private val sampleChips = listOf(
        ActiveFilterChip(FilterKind.DIRECTION, UiText.Plain("Money out")),
        ActiveFilterChip(FilterKind.CATEGORY, UiText.Plain("Food")),
    )

    @Test
    fun withNoFiltersThereAreNoChipsAndNoClearAll() {
        show()

        composeRule.onNodeWithTag("history_search").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_DIRECTION").assertDoesNotExist()
        composeRule.onNodeWithTag("clear_all").assertDoesNotExist()
    }

    @Test
    fun typingSendsTheText() {
        show()

        composeRule.onNodeWithTag("history_search").performTextInput("rent")

        assertEquals(listOf("rent"), searches)
    }

    @Test
    fun theClearButtonEmptiesTheSearch() {
        show(searchText = "rent")

        composeRule.onNodeWithContentDescription("Clear search").performClick()

        assertEquals(listOf(""), searches)
    }

    @Test
    fun theFilterButtonOpensTheSheet() {
        show()

        composeRule.onNodeWithTag("filter_button").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun activeFiltersShowAsChipsThatRemoveThemselves() {
        show(chips = sampleChips)

        composeRule.onNodeWithText("Money out").assertIsDisplayed()
        composeRule.onNodeWithText("Food").assertIsDisplayed()

        composeRule.onNodeWithTag("chip_CATEGORY").performClick()

        assertEquals(listOf(FilterKind.CATEGORY), removed)
    }

    @Test
    fun clearAllIsOfferedWhileFiltersAreActive() {
        show(chips = sampleChips)

        composeRule.onNodeWithTag("clear_all").performClick()

        assertEquals(1, cleared)
    }
}