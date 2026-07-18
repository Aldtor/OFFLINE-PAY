package com.offlinepay.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Validates a raw string input as a rupee amount.
 *
 * Rules:
 * - Empty string is allowed (represents "not yet entered").
 * - Must contain only digits and at most one decimal point.
 * - At most 2 digits after the decimal point.
 * - No other characters are permitted.
 *
 * @param value The raw string to validate.
 * @return `true` if the value is a valid rupee amount string.
 */
fun isValidAmountInput(value: String): Boolean {
    if (value.isEmpty()) return true
    // Match: optional digits, optional single dot, optional 1-2 digits
    val regex = Regex("""^\d+(\.\d{0,2})?$""")
    return regex.matches(value)
}

/**
 * Validated numeric input field for a rupee payment amount.
 *
 * The field:
 * - Shows a ₹ prefix label to the left of the input text.
 * - Uses `KeyboardType.Decimal` to surface a numeric decimal keyboard.
 * - Enforces format via [isValidAmountInput]: digits + optional single decimal point
 *   with at most 2 decimal places.  Invalid characters are silently rejected at the
 *   `onValueChange` level (i.e. the `onValueChange` callback is only invoked when
 *   the new value passes validation).
 * - Has a minimum touch target height of 48dp (Req 14.8 / WCAG 2.1 AAA).
 * - When `isEditable = false` the field is read-only (no cursor, keyboard disabled).
 * - When `errorMessage != null` it is displayed in red below the field.
 * - The accessibility `contentDescription` is "Amount in rupees, edit field"
 *   (Req 14.5, design Section 10.5).
 *
 * Design reference: Section 3.2, 10.3, 10.4
 * Requirements: Req 14.5, Req 14.8, Req 15.6
 *
 * ### Usage
 * ```kotlin
 * var amount by remember { mutableStateOf("") }
 * var error by remember { mutableStateOf<String?>(null) }
 *
 * AmountInput(
 *     value = amount,
 *     onValueChange = { amount = it },
 *     isEditable = true,
 *     errorMessage = error,
 *     modifier = Modifier.fillMaxWidth(),
 * )
 * ```
 *
 * @param value         The current string value of the amount field.
 * @param onValueChange Callback invoked with the new value whenever a valid character
 *                      is typed. Invalid input is rejected without calling this callback.
 * @param isEditable    When `false` the field is read-only — no keyboard is raised and
 *                      no cursor is shown.
 * @param errorMessage  When non-null, an error message rendered in red directly below
 *                      the field.
 * @param modifier      Modifier applied to the outer [Column] container.
 */
@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    isEditable: Boolean = true,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Only propagate changes that pass the decimal format validation.
                if (isValidAmountInput(newValue)) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                // Minimum 48dp height for WCAG 2.1 AAA touch target compliance (Req 14.8).
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    contentDescription = "Amount in rupees, edit field"
                },
            label = {
                Text(text = "₹ Amount")
            },
            prefix = {
                Text(text = "₹")
            },
            placeholder = {
                Text(text = "0.00")
            },
            isError = errorMessage != null,
            readOnly = !isEditable,
            enabled = isEditable,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        // Display the error message below the field when present.
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color(0xFFDC2626), // ErrorRed token
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
