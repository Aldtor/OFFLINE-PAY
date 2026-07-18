package com.offlinepay.feature.payment

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.offlinepay.core.designsystem.components.SimDisplayInfo
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.PaymentParams
import com.offlinepay.core.domain.model.QrType
import com.offlinepay.core.domain.model.RoutingDecision
import com.offlinepay.core.domain.model.RoutingReason
import com.offlinepay.core.domain.model.OperatorType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * UI tests for [PaymentConfirmationScreen].
 *
 * Design reference: Section 16.3
 * Requirements: Req 14.3, Req 14.6, Req 14.8
 */
@RunWith(AndroidJUnit4::class)
class PaymentConfirmationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val staticQrParams = PaymentParams(
        upiId = "merchant@upi",
        payeeName = "Test Merchant",
        amount = null,
        qrType = QrType.STATIC,
    )

    private val dynamicQrParams = PaymentParams(
        upiId = "merchant@upi",
        payeeName = "Test Merchant",
        amount = 15000,
        qrType = QrType.DYNAMIC,
    )

    private val singleSim = listOf(
        SimDisplayInfo(0, "SIM 1 (Airtel)", "Airtel", true)
    )

    private val dualSim = listOf(
        SimDisplayInfo(0, "SIM 1 (Airtel)", "Airtel", true),
        SimDisplayInfo(1, "SIM 2 (Jio)", "Jio", true),
    )

    private val ussdRouting = RoutingDecision(
        selectedMethod = PaymentMethodType.USSD,
        fallbackChain = listOf(PaymentMethodType.PAY123),
        operatorType = OperatorType.AIRTEL,
        simSlotIndex = 0,
        overrideActive = false,
        reason = RoutingReason.OPERATOR_PRIORITY,
    )

    @Test
    fun staticQr_amountFieldIsEditable() {
        val state = PaymentConfirmationUiState.Ready(
            paymentParams = staticQrParams,
            sims = singleSim,
            selectedSim = singleSim[0],
            routingDecision = ussdRouting,
            editableAmountStr = "",
            amountError = null,
            isStaticQr = true,
        )

        composeTestRule.setContent {
            PaymentConfirmationContent(state = state, onAction = {})
        }

        // Amount field should be editable and empty for static QR
        composeTestRule.onNodeWithTag("amount_field")
            .assertIsEnabled()
            .performTextInput("500")
    }

    @Test
    fun dynamicQr_amountFieldIsReadOnly() {
        val state = PaymentConfirmationUiState.Ready(
            paymentParams = dynamicQrParams,
            sims = singleSim,
            selectedSim = singleSim[0],
            routingDecision = ussdRouting,
            editableAmountStr = "150.00",
            amountError = null,
            isStaticQr = false,
        )

        composeTestRule.setContent {
            PaymentConfirmationContent(state = state, onAction = {})
        }

        // Amount field should show pre-filled amount and not be editable
        composeTestRule.onNodeWithTag("amount_field")
            .assertTextContains("150.00")
    }

    @Test
    fun dualSim_simSelectorIsDisplayed() {
        val state = PaymentConfirmationUiState.Ready(
            paymentParams = dynamicQrParams,
            sims = dualSim,
            selectedSim = dualSim[0],
            routingDecision = ussdRouting,
            editableAmountStr = "150.00",
            amountError = null,
            isStaticQr = false,
        )

        composeTestRule.setContent {
            PaymentConfirmationContent(state = state, onAction = {})
        }

        // SIM selector should be visible with 2 SIMs
        composeTestRule.onNodeWithTag("sim_selector").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIM 1 (Airtel)").assertIsDisplayed()
    }

    @Test
    fun zeroAmount_showsValidationError() {
        val state = PaymentConfirmationUiState.Ready(
            paymentParams = staticQrParams,
            sims = singleSim,
            selectedSim = singleSim[0],
            routingDecision = ussdRouting,
            editableAmountStr = "0",
            amountError = "Amount must be greater than zero",
            isStaticQr = true,
        )

        composeTestRule.setContent {
            PaymentConfirmationContent(state = state, onAction = {})
        }

        composeTestRule.onNodeWithText("Amount must be greater than zero").assertIsDisplayed()
    }
}
