package com.example.bankflow.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.presentation.accounts.HistoryFilterBar
import com.example.bankflow.presentation.insights.InsightsContent
import com.example.bankflow.presentation.insights.InsightsState
import com.example.bankflow.presentation.profile.ProfileContent
import com.example.bankflow.presentation.profile.ProfileState
import com.example.bankflow.ui.theme.BankFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class TouchTargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isTouchSized() =
        assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

    @Test
    fun theMonthButtonsAreBigEnough() {
        val october = YearMonth.of(2026, 10)
        composeRule.setContent {
            BankFlowTheme {
                InsightsContent(InsightsState(month = october, currentMonth = october, summary = uiSummary()), onIntent = {})
            }
        }

        composeRule.onNodeWithTag("month_previous").isTouchSized()
        composeRule.onNodeWithTag("month_next").isTouchSized()
    }

    @Test
    fun theFilterButtonAndSearchClearAreBigEnough() {
        composeRule.setContent {
            BankFlowTheme {
                HistoryFilterBar(
                    searchText = "rent",
                    chips = emptyList(),
                    onSearchChange = {},
                    onOpenFilters = {},
                    onRemoveFilter = {},
                    onClearAll = {},
                )
            }
        }

        composeRule.onNodeWithTag("filter_button").isTouchSized()
        composeRule.onNodeWithContentDescription("Clear search").isTouchSized()
    }

    @Test
    fun theProfileOptionsAreBigEnough() {
        composeRule.setContent {
            BankFlowTheme {
                ProfileContent(
                    state = ProfileState(profile = uiProfile(theme = ThemePreference.SYSTEM)),
                    onIntent = {},
                    onOpenRecipients = {},
                    onLogout = {},
                    appVersion = "1.0",
                )
            }
        }

        ThemePreference.entries.forEach { theme ->
            composeRule.onNodeWithTag("theme_${theme.name}").performScrollTo().isTouchSized()
        }
        composeRule.onNodeWithTag("profile_recipients").performScrollTo().isTouchSized()
        composeRule.onNodeWithTag("profile_logout").performScrollTo().isTouchSized()
    }
}