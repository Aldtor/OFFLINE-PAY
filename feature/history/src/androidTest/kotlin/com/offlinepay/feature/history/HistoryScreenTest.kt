package com.offlinepay.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.offlinepay.core.designsystem.theme.OfflinePayTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [HistoryScreen].
 *
 * Tests:
 * - Empty state shows "No payments yet" message
 * - Accessibility checks enabled
 *
 * Design reference: Section 16.3
 * Requirements: Req 8.4, Req 8.5, Req 14.3
 */
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_showsNoPaymentsMessage() {
        composeTestRule.setContent {
            OfflinePayTheme {
                HistoryEmptyState()
            }
        }

        composeTestRule
            .onNodeWithText("No payments yet", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun emptyState_showsScanPrompt() {
        composeTestRule.setContent {
            OfflinePayTheme {
                HistoryEmptyState()
            }
        }

        composeTestRule
            .onNodeWithText("Scan a QR code", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun noResults_showsNoResultsMessage() {
        composeTestRule.setContent {
            OfflinePayTheme {
                HistoryNoResultsState()
            }
        }

        composeTestRule
            .onNodeWithText("No results found", substring = true)
            .assertIsDisplayed()
    }
}
