package com.offlinepay.core.security

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects accessibility services that may be abusing their elevated screen-reading
 * permissions to spy on or automate payment interactions.
 *
 * Enumerates all enabled [AccessibilityServiceInfo] via [AccessibilityManager]
 * and filters against an internal whitelist of known legitimate services.
 * Services not on the whitelist are returned as suspicious.
 *
 * Design reference: Section 8.3 (Accessibility Service Abuse Detection)
 * Requirements: Req 9.15 (accessibility abuse detection)
 *
 * Note: This is non-blocking. The result is presented to the user as a security
 * notice, not a hard block. Users with legitimate assistive technology needs
 * should not be denied access.
 */
@Singleton
open class AccessibilityAbuseDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        /**
         * Package names of known legitimate accessibility services.
         * Services from these packages are whitelisted and not flagged.
         */
        private val WHITELISTED_PACKAGES = setOf(
            // TalkBack (Google)
            "com.google.android.marvin.talkback",
            "com.android.talkback",
            // Switch Access (Google)
            "com.google.android.apps.accessibility.switch",
            "com.android.switchaccess",
            // Google Voice Access
            "com.google.android.apps.accessibility.voiceaccess",
            // BrailleBack (Google)
            "com.googlecode.eyesfree.brailleback",
            // Select to Speak (Google)
            "com.google.android.apps.accessibility.selecttospeak",
            // Sound Amplifier (Google)
            "com.google.android.accessibility.soundamplifier",
            // Android Accessibility Suite (covers TalkBack, Switch, etc.)
            "com.google.android.accessibility.suite",
            // Lookout (Google)
            "com.google.android.apps.accessibility.lookout",
            // OEM TalkBack variants
            "com.samsung.android.accessibility.talkback",
            "com.samsung.accessibility",
        )
    }

    /**
     * Returns the list of enabled accessibility service names that are NOT on the whitelist.
     *
     * An empty list means no suspicious services are running.
     * A non-empty list contains the package names of potentially suspicious services.
     *
     * @return List of suspicious accessibility service package names.
     */
    fun getSuspiciousServices(): List<String> {
        return try {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    ?: return emptyList()

            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            )

            // The app's own package hosts UssdAccessibilityService (auto-fills the *99#
            // payment menus). It is first-party and must not flag itself.
            val ownPackage = context.packageName

            enabledServices
                .mapNotNull { info ->
                    info.resolveInfo?.serviceInfo?.packageName
                }
                .filter { packageName ->
                    packageName != ownPackage &&
                        !WHITELISTED_PACKAGES.any { whitelisted ->
                            packageName.startsWith(whitelisted) || whitelisted.startsWith(packageName)
                        }
                }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns true if any suspicious accessibility services are currently enabled.
     */
    fun hasSuspiciousServices(): Boolean = getSuspiciousServices().isNotEmpty()
}
