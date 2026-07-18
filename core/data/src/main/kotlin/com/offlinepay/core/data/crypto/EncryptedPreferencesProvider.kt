package com.offlinepay.core.data.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for [EncryptedSharedPreferences] backed by the Android Keystore.
 *
 * Creates a `MasterKey` using `KEY_PREFS_ENCRYPTION` (alias [KEY_ALIAS]) from the Android
 * Keystore with AES256-GCM encryption per design Section 8.4. All sensitive settings
 * (routing config, manual override, SIM preference, Play Integrity verdict, DB key blob)
 * are stored via this provider.
 *
 * The underlying `MasterKey` is created lazily and is thread-safe via Kotlin `by lazy`.
 *
 * Design reference: Section 8.4 (Cryptography Architecture — Key Hierarchy)
 * Requirements: Req 9.21 (sensitive configuration in EncryptedSharedPreferences)
 */
@Singleton
class EncryptedPreferencesProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Android Keystore alias matching the KEY_PREFS_ENCRYPTION slot in design Section 8.4. */
        const val KEY_ALIAS = "KEY_PREFS_ENCRYPTION"
        private const val PREFS_FILE_NAME = "offlinepay_encrypted_prefs"
    }

    /**
     * Lazily creates and returns the [EncryptedSharedPreferences] instance.
     * Thread-safe via Kotlin's `lazy` delegate (default `LazyThreadSafetyMode.SYNCHRONIZED`).
     */
    val prefs: EncryptedSharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }

    // ── Typed accessors ───────────────────────────────────────────────────────

    /** Stores a [String] value under [key]. */
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    /** Retrieves a [String] value for [key], or [default] if absent. */
    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)

    /** Stores a [Long] value under [key]. */
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    /** Retrieves a [Long] value for [key], or [default] if absent. */
    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)

    /** Stores a [Boolean] value under [key]. */
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    /** Retrieves a [Boolean] value for [key], or [default] if absent. */
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    /** Stores an [Int] value under [key]. */
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    /** Retrieves an [Int] value for [key], or [default] if absent. */
    fun getInt(key: String, default: Int = -1): Int = prefs.getInt(key, default)

    /** Removes the entry for [key]. */
    fun remove(key: String) = prefs.edit().remove(key).apply()

    /** Returns true if [key] exists in the preferences. */
    fun contains(key: String): Boolean = prefs.contains(key)
}
