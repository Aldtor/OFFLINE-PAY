package com.offlinepay.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offlinepay.core.data.db.entity.SettingsEntity

/**
 * Room DAO for non-sensitive application settings.
 *
 * Key-value store using INSERT OR REPLACE semantics.
 * Sensitive settings are stored in EncryptedSharedPreferences instead.
 *
 * Design reference: Section 9.3 (SettingsDao query descriptions)
 * Requirements: Req 11 (Settings feature)
 */
@Dao
interface SettingsDao {

    /** Inserts or replaces a settings entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingsEntity)

    /** Returns the settings entry for [key], or null if not found. */
    @Query("SELECT * FROM settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): SettingsEntity?

    /** Returns all settings entries. */
    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingsEntity>

    /** Deletes a settings entry by key. */
    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)
}
