package com.offlinepay.feature.ussd.autodrive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Helpers for checking and prompting the enablement of [UssdAccessibilityService].
 *
 * An accessibility service can only be enabled by the user in system Settings — it can
 * never be granted programmatically — so the app checks the state and deep-links the user
 * to the Accessibility settings screen.
 */
object UssdAccessibilitySettings {

    /**
     * Returns true if [UssdAccessibilityService] is currently enabled for this app.
     *
     * Reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (a colon-separated list of
     * `package/ServiceClass` component names) and looks for our component.
     */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context.packageName, SERVICE_CLASS_NAME).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val accessibilityOn = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityOn) return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** Opens the system Accessibility settings so the user can enable the service. */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Fully-qualified name of the service class (namespace is com.offlinepay.feature.ussd,
    // so the service lives in the .autodrive sub-package).
    private const val SERVICE_CLASS_NAME =
        "com.offlinepay.feature.ussd.autodrive.UssdAccessibilityService"
}
