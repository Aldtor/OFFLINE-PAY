package com.offlinepay.core.data.db.mapper

import com.offlinepay.core.data.db.entity.MerchantEntity
import com.offlinepay.core.data.db.entity.SettingsEntity
import com.offlinepay.core.data.db.entity.TransactionEntity
import com.offlinepay.core.domain.model.AppSettings
import com.offlinepay.core.domain.model.AppTheme
import com.offlinepay.core.domain.model.MerchantProfile
import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.RoutingPriorityConfig
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bidirectional mapper between Room entities and domain models.
 *
 * Room entities MUST NEVER leak past the data layer boundary. This object provides
 * the single, authoritative mapping point. All repository implementations use these
 * extension functions exclusively.
 *
 * ### Coverage:
 * - [TransactionEntity] ↔ [TransactionRecord]
 * - [MerchantEntity] ↔ [MerchantProfile]
 * - [List<SettingsEntity>] → [AppSettings]  (settings is a key-value table, not 1-to-1)
 *
 * Design reference: Section 9.5 (EntityMapper)
 * Architecture: Clean Architecture — Data layer maps entities → domain models before returning
 */
object EntityMapper {

    private val json = Json { ignoreUnknownKeys = true }

    // ── TransactionEntity ↔ TransactionRecord ─────────────────────────────────

    /**
     * Maps a [TransactionEntity] to its domain [TransactionRecord].
     * Called by repository implementations before returning data to use cases.
     */
    fun TransactionEntity.toDomain(): TransactionRecord = TransactionRecord(
        id = id,
        timestampMs = timestamp,
        payeeUpiId = payeeUpiId,
        payeeName = payeeName,
        merchantName = merchantName,
        merchantCategoryCode = merchantCategoryCode,
        amountPaise = amount,
        paymentMethod = PaymentMethodType.valueOf(paymentMethod),
        status = TransactionStatus.valueOf(status),
        operatorUsed = operatorUsed?.let { runCatching { OperatorType.valueOf(it) }.getOrNull() },
        simSlotUsed = simSlotIndex,
        ussdResponse = ussdResponse,
        transactionReference = transactionReference,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /**
     * Maps a domain [TransactionRecord] to a [TransactionEntity] for persistence.
     * Called before any INSERT or UPDATE operation.
     */
    fun TransactionRecord.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        timestamp = timestampMs,
        payeeUpiId = payeeUpiId,
        payeeName = payeeName,
        merchantName = merchantName,
        merchantCategoryCode = merchantCategoryCode,
        amount = amountPaise,
        paymentMethod = paymentMethod.name,
        status = status.name,
        operatorUsed = operatorUsed?.name,
        simSlotIndex = simSlotUsed,
        ussdResponse = ussdResponse,
        transactionReference = transactionReference,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /** Maps a list of entities to domain models. */
    fun List<TransactionEntity>.toDomainList(): List<TransactionRecord> = map { it.toDomain() }

    // ── MerchantEntity ↔ MerchantProfile ──────────────────────────────────────

    /**
     * Maps a [MerchantEntity] to its domain [MerchantProfile].
     */
    fun MerchantEntity.toDomain(): MerchantProfile = MerchantProfile(
        id = id,
        upiId = upiId,
        name = name,
        categoryCode = categoryCode,
        categoryName = categoryName,
        avatarColor = avatarColor,
        isFavourite = isFavourite == 1,
        lastSeenAt = lastSeenAt,
        transactionCount = transactionCount,
        createdAt = createdAt,
    )

    /**
     * Maps a domain [MerchantProfile] to a [MerchantEntity] for persistence.
     */
    fun MerchantProfile.toEntity(): MerchantEntity = MerchantEntity(
        id = id,
        upiId = upiId,
        name = name,
        categoryCode = categoryCode,
        categoryName = categoryName,
        avatarColor = avatarColor,
        isFavourite = if (isFavourite) 1 else 0,
        lastSeenAt = lastSeenAt,
        transactionCount = transactionCount,
        createdAt = createdAt,
    )

    /** Maps a list of merchant entities to domain models. */
    fun List<MerchantEntity>.toDomainMerchantList(): List<MerchantProfile> = map { it.toDomain() }

    // ── List<SettingsEntity> → AppSettings ────────────────────────────────────
    //
    // The settings table is a key-value store; there is no single SettingsEntity that
    // maps 1:1 to AppSettings. The mapping direction is therefore:
    //   List<SettingsEntity> → AppSettings   (for reading all settings at once)
    //   AppSettings → List<SettingsEntity>   (for bulk-writing all settings at once)
    //
    // Per-key reads/writes are handled directly by the SettingsDao + SettingsRepositoryImpl
    // without going through this mapper, keeping repository code explicit.

    // Settings keys — must match SettingsRepositoryImpl constants
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_ONBOARDING = "onboarding_complete"

    /**
     * Converts a flat list of [SettingsEntity] rows (from `SELECT * FROM settings`)
     * into a fully-populated [AppSettings] domain model.
     *
     * Sensitive settings (routing config, SIM preference, manual override) are stored
     * in EncryptedSharedPreferences, not in this table; they default to their zero-values
     * when reconstructed solely from the settings table.
     *
     * Used for bulk-restore scenarios and migrations only.
     */
    fun List<SettingsEntity>.toAppSettings(): AppSettings {
        val map = associate { it.key to it.value }
        val theme = map[KEY_THEME]
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
            ?: AppTheme.SYSTEM
        val language = map[KEY_LANGUAGE] ?: "en"
        val onboarding = map[KEY_ONBOARDING]?.toBooleanStrictOrNull() ?: false
        return AppSettings(
            theme = theme,
            language = language,
            isOnboardingComplete = onboarding,
            // Sensitive fields default to null/default — caller must merge from EncryptedPrefs
            preferredSimSlot = null,
            manualPaymentMethodOverride = null,
            routingPriorityConfig = RoutingPriorityConfig.default(),
        )
    }

    /**
     * Converts the non-sensitive fields of [AppSettings] to a list of [SettingsEntity] rows
     * suitable for bulk persistence to the `settings` table.
     *
     * Sensitive fields (preferredSimSlot, manualPaymentMethodOverride, routingPriorityConfig)
     * are NOT included — they must be written to EncryptedSharedPreferences separately.
     */
    fun AppSettings.toSettingsEntityList(nowMs: Long = System.currentTimeMillis()): List<SettingsEntity> =
        listOf(
            SettingsEntity(KEY_THEME, theme.name, nowMs),
            SettingsEntity(KEY_LANGUAGE, language, nowMs),
            SettingsEntity(KEY_ONBOARDING, isOnboardingComplete.toString(), nowMs),
        )
}
