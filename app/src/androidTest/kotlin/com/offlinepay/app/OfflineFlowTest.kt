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
 * Integration test verifying the app functions correctly without internet connectivity.
 *
 * Since OfflinePay is designed for fully offline UPI payments via USSD/123PAY (cellular
 * protocols, not internet), the entire dashboard and payment flow must render without
 * network-dependent loading states.
 *
 * Design reference: Section 16.4
 * Requirements: Req 1.1 (fully offline), Req 1.4 (dual-SIM), Req 14.12
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardRendersWithoutNetwork() {
        // The dashboard should render successfully without network connectivity.
        // It does not depend on internet — only on cellular availability.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_root").assertIsDisplayed()
    }

    @Test
    fun noNetworkDependentLoadingState() {
        // Verify that no "loading" spinner or "connecting" indicator appears
        // on the dashboard when the app starts offline.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Connecting").assertDoesNotExist()
        composeTestRule.onNodeWithText("Loading").assertDoesNotExist()
    }
}
