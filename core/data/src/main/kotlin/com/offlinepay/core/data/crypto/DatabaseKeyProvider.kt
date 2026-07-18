package com.offlinepay.core.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher database encryption key derived from the Android Keystore.
 *
 * ### Two-layer key derivation (Design Section 8.4):
 * 1. A 256-bit random key material blob is generated on first run using [SecureRandom].
 * 2. The blob is encrypted with an AES-256-GCM key stored in the Android Keystore
 *    (alias [KEY_ALIAS_DB_ENCRYPTION]).
 * 3. The encrypted blob (with IV prepended) is persisted in [EncryptedSharedPreferences]
 *    under [PREFS_KEY_DB_KEY_BLOB].
 * 4. On subsequent launches, the blob is decrypted using the Keystore key.
 *
 * The SQLCipher key is NEVER derived from a user PIN — it is exclusively hardware-backed.
 * Keys are StrongBox-backed where the device hardware supports it, otherwise TEE-backed.
 *
 * Design reference: Section 8.4 (Cryptography Architecture — SQLCipher Key Derivation)
 * Requirements: Req 9.18 (SQLCipher key in Android Keystore)
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    private val encryptedPrefsProvider: EncryptedPreferencesProvider,
) {
    companion object {
        /** Keystore alias for the AES-256-GCM key that wraps the SQLCipher key material blob. */
        const val KEY_ALIAS_DB_ENCRYPTION = "KEY_DB_ENCRYPTION"

        /** EncryptedSharedPreferences key storing `iv (12 bytes) + encryptedKeyBlob` as Base64. */
        const val PREFS_KEY_DB_KEY_BLOB = "db_key_blob"

        private const val KEY_SIZE_BITS = 256
        private const val AES_GCM_IV_SIZE = 12
        private const val AES_GCM_TAG_SIZE = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    /**
     * Returns the 256-bit raw database encryption key as a [ByteArray].
     *
     * On first call: generates random key material, encrypts with Keystore key, persists blob.
     * On subsequent calls: retrieves blob from EncryptedSharedPreferences and decrypts.
     *
     * @return 32-byte key material for use as the SQLCipher database password.
     */
    fun getOrCreateKey(): ByteArray = getOrCreateKeyForAlias(KEY_ALIAS_DB_ENCRYPTION, PREFS_KEY_DB_KEY_BLOB)

    /**
     * Returns or creates the database key for a specific Keystore key alias.
     *
     * Used by [KeyRotationManager] to generate key material under a new alias
     * while re-encrypting the SQLCipher database.
     *
     * @param keystoreAlias The Android Keystore alias for the wrapping key.
     * @param prefsBlobKey  The EncryptedSharedPreferences key storing the encrypted blob.
     * @return 32-byte key material.
     */
    fun getOrCreateKeyForAlias(keystoreAlias: String, prefsBlobKey: String): ByteArray {
        val existingBlob = encryptedPrefsProvider.getString(prefsBlobKey)
        return if (existingBlob != null) {
            // Decrypt existing key material: stored as Base64(iv[12] + encryptedKey)
            val raw = android.util.Base64.decode(existingBlob, android.util.Base64.DEFAULT)
            val iv = raw.copyOfRange(0, AES_GCM_IV_SIZE)
            val encrypted = raw.copyOfRange(AES_GCM_IV_SIZE, raw.size)
            decryptKeyMaterial(keystoreAlias, encrypted, iv)
        } else {
            // First run: generate new 256-bit key material
            val rawKey = ByteArray(KEY_SIZE_BITS / 8).also {
                java.security.SecureRandom().nextBytes(it)
            }
            // Encrypt and persist as Base64(iv + encryptedKey)
            val (encryptedBlob, iv) = encryptKeyMaterial(keystoreAlias, rawKey)
            val combined = iv + encryptedBlob
            encryptedPrefsProvider.putString(
                prefsBlobKey,
                android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT),
            )
            rawKey
        }
    }

    /**
     * Gets an existing Keystore key or creates a new one for [keystoreAlias].
     *
     * Keys are StrongBox-backed on devices with `isStrongBoxSupported()`, otherwise TEE-backed.
     * `setUserAuthenticationRequired(false)` ensures payment works when device is locked.
     */
    fun getOrCreateKeystoreKey(keystoreAlias: String = KEY_ALIAS_DB_ENCRYPTION): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return if (keyStore.containsAlias(keystoreAlias)) {
            (keyStore.getEntry(keystoreAlias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val specBuilder = KeyGenParameterSpec.Builder(
                keystoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)

            // Prefer StrongBox-backed hardware security where available (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Attempt StrongBox; fall back gracefully if unsupported
                try {
                    specBuilder.setIsStrongBoxBacked(true)
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                        .apply { init(specBuilder.build()) }
                        .generateKey()
                } catch (_: Exception) {
                    // StrongBox not available on this device; fall through to TEE key
                    specBuilder.setIsStrongBoxBacked(false)
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                        .apply { init(specBuilder.build()) }
                        .generateKey()
                }
            } else {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .apply { init(specBuilder.build()) }
                    .generateKey()
            }
        }
    }

    private fun encryptKeyMaterial(keystoreAlias: String, rawKey: ByteArray): Pair<ByteArray, ByteArray> {
        val keystoreKey = getOrCreateKeystoreKey(keystoreAlias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey) }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(rawKey)
        return Pair(encrypted, iv)
    }

    private fun decryptKeyMaterial(keystoreAlias: String, encryptedBlob: ByteArray, iv: ByteArray): ByteArray {
        val keystoreKey = getOrCreateKeystoreKey(keystoreAlias)
        val spec = GCMParameterSpec(AES_GCM_TAG_SIZE, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, keystoreKey, spec) }
        return cipher.doFinal(encryptedBlob)
    }
}
