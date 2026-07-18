package com.offlinepay.feature.ussd.autodrive

/**
 * What to type into a `*99#` USSD dialog for a given menu step.
 *
 * The concrete string is resolved by [UssdAutoDriveSession] from the armed payment
 * details (payee / amount / note); the script itself is payment-agnostic.
 *
 * SECURITY: there is intentionally **no** variant that supplies a UPI PIN. The PIN
 * prompt is detected by [com.offlinepay.feature.ussd.UssdResponseParser] and control is
 * always handed back to the user. [StopForPin] exists only to mark the end of the
 * auto-driven portion of the flow.
 */
sealed interface StepResponse {
    /** Type a literal menu-selection digit (e.g. "1" for "Send Money"). */
    data class Digit(val value: String) : StepResponse

    /** Type the payee's UPI ID / virtual payment address. */
    data object PayeeUpiId : StepResponse

    /** Type the amount, in rupees (e.g. "100" or "100.50"). */
    data object AmountRupees : StepResponse

    /** Type the transaction remark / note. */
    data object Remark : StepResponse

    /** End of the auto-driven portion — the next screen is the UPI PIN (user types it). */
    data object StopForPin : StepResponse
}

/**
 * A single step in a `*99#` navigation script.
 *
 * A step is selected when the (lower-cased) USSD dialog text contains **any** of
 * [matchers]. Matching is keyword-based rather than position-based so the script
 * survives banks/telecom-circles that reorder, merge, or reword menu screens.
 *
 * @param label     Human-readable label shown in the guidance UI ("Choosing Send Money…").
 * @param matchers  Lower-case keywords; the step fires if the dialog text contains any.
 * @param response  What to inject when this step fires.
 */
data class UssdStep(
    val label: String,
    val matchers: List<String>,
    val response: StepResponse,
)

/**
 * An ordered list of [UssdStep]s describing how to navigate a `*99#` send-money flow.
 *
 * [UssdAutoDriveSession] walks this list with a forward-only cursor: for each incoming
 * dialog it picks the first not-yet-consumed step whose matchers hit. Forward-only
 * matching disambiguates screens that share keywords (e.g. the "2. UPI ID" beneficiary
 * menu vs. the "Enter UPI ID" prompt that follows it).
 */
data class UssdMenuScript(
    val name: String,
    val steps: List<UssdStep>,
)

/**
 * The default NPCI `*99#` "Send money to a UPI ID" script.
 *
 * Standard NUUP flow:
 *  1. Welcome menu               → "1" (Send Money)
 *  2. Beneficiary-type menu      → "2" (UPI ID / VPA)
 *  3. Enter UPI ID / VPA prompt  → payee UPI ID
 *  4. Enter amount prompt        → amount in rupees
 *  5. Enter remark prompt        → note (optional; harmless if the screen is skipped)
 *  6. Confirm menu (if present)  → "1" (Confirm)
 *  7. Enter UPI PIN              → STOP (user types the PIN)
 *
 * Menu wording varies by bank + telecom circle; tune [UssdStep.matchers] on-device.
 */
object DefaultNpciScript {

    val script: UssdMenuScript = UssdMenuScript(
        name = "NPCI *99# — Send to UPI ID",
        steps = listOf(
            UssdStep(
                label = "Choosing Send Money",
                matchers = listOf("send money", "send/pay", "1.send", "1. send"),
                response = StepResponse.Digit("1"),
            ),
            UssdStep(
                label = "Selecting UPI ID",
                matchers = listOf("upi id", "virtual", "vpa"),
                response = StepResponse.Digit("2"),
            ),
            UssdStep(
                label = "Entering payee UPI ID",
                matchers = listOf("enter upi", "enter virtual", "enter vpa", "enter payee", "beneficiary upi"),
                response = StepResponse.PayeeUpiId,
            ),
            UssdStep(
                label = "Entering amount",
                matchers = listOf("amount"),
                response = StepResponse.AmountRupees,
            ),
            UssdStep(
                label = "Entering remark",
                matchers = listOf("remark", "remarks", "comment", "narration"),
                response = StepResponse.Remark,
            ),
            UssdStep(
                label = "Confirming payment",
                matchers = listOf("confirm", "1. confirm", "1.confirm"),
                response = StepResponse.Digit("1"),
            ),
            UssdStep(
                label = "Waiting for UPI PIN",
                matchers = listOf("pin"),
                response = StepResponse.StopForPin,
            ),
        ),
    )
}
