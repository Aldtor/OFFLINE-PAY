package com.offlinepay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for non-sensitive application settings (key-value store).
 *
 * Sensitive settings (routing config, manual override, SIM preference) are stored
 * in [EncryptedSharedPreferences], not in this table.
 *
 * Design reference: Section 9.1 (settings table schema)
 * Requirements: Req 11 (Settings feature)
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
