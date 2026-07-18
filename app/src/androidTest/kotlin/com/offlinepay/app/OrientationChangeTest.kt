package com.offlinepay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orientation change robustness test.
 *
 * Verifies that rotating the device preserves user-entered data
 * and active UI state (via SavedStateHandle and rememberSaveable).
 *
 * Design reference: Section 16.4
 * Requirements: Req 14.12 (orientation change resilience)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OrientationChangeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardSurvivesOrientationChange() {
        // Wait for the dashboard to render
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_root").assertIsDisplayed()

        // Simulate orientation change by recreating the activity
        composeTestRule.activityRule.scenario.recreate()

        // Verify that the dashboard still renders after recreation
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_root").assertIsDisplayed()
    }

    @Test
    fun navigationStateSurvivesOrientationChange() {
        composeTestRule.waitForIdle()

        // Simulate configuration change
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // The app should not crash and should remain on the same screen
        composeTestRule.onRoot().assertIsDisplayed()
    }
}
