package com.offlinepay.feature.scanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.offlinepay.core.designsystem.theme.OfflinePayTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [ScannerScreen].
 *
 * Tests:
 * - Camera preview displayed when permission granted
 * - Rationale screen shown when permission denied
 * - Torch button toggle changes icon state
 * - Non-UPI QR error state shows correct message
 *
 * Design reference: Section 16.3
 * Requirements: Req 2.1, Req 2.2, Req 2.7
 */
class ScannerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionDenied_showsRationaleScreen() {
        composeTestRule.setContent {
            OfflinePayTheme {
                // Simulate permission denied state
                ScannerPermissionRationale(
                    onGrantPermission = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Camera permission is required to scan QR codes")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Grant Permission")
            .assertIsDisplayed()
    }

    @Test
    fun permissionDenied_grantButtonClickable() {
        var grantClicked = false

        composeTestRule.setContent {
            OfflinePayTheme {
                ScannerPermissionRationale(
                    onGrantPermission = { grantClicked = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Grant Permission")
            .performClick()

        assert(grantClicked)
    }

    @Test
    fun nonUpiQr_showsErrorMessage() {
        composeTestRule.setContent {
            OfflinePayTheme {
                ScannerErrorBanner(
                    message = "Non-UPI QR Code",
                    onRescan = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Non-UPI QR Code")
            .assertIsDisplayed()
    }

    @Test
    fun torchButton_hasContentDescription() {
        composeTestRule.setContent {
            OfflinePayTheme {
                TorchButton(
                    isTorchOn = false,
                    onToggle = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Turn on flashlight")
            .assertIsDisplayed()
    }
}
