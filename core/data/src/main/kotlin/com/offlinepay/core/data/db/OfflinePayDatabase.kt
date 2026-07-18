package com.offlinepay.core.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.offlinepay.core.data.crypto.DatabaseKeyProvider
import com.offlinepay.core.data.db.dao.MerchantDao
import com.offlinepay.core.data.db.dao.SettingsDao
import com.offlinepay.core.data.db.dao.TransactionDao
import com.offlinepay.core.data.db.entity.MerchantEntity
import com.offlinepay.core.data.db.entity.SettingsEntity
import com.offlinepay.core.data.db.entity.TransactionEntity
import net.sqlcipher.database.SupportFactory

/**
 * Room database for OfflinePay encrypted with SQLCipher.
 *
 * ### Encryption
 * The database file is encrypted with AES-256 via SQLCipher. The encryption key
 * is derived from [DatabaseKeyProvider] which stores the key material encrypted
 * in the Android Keystore. The key is never derived from a user PIN.
 *
 * ### Schema export
 * Schema JSON files are exported to `app/schemas/` and committed to version control.
 * [fallbackToDestructiveMigration] is disabled — all migrations must be explicit.
 *
 * Design reference: Section 9.1 (database schema), Section 8.4 (SQLCipher key derivation)
 * Requirements: Req 8.3 (SQLCipher AES-256 encryption), Req 9.20 (backup exclusion)
 */
import com.offlinepay.core.data.db.entity.AnalyticsEventEntity
import com.offlinepay.core.data.db.dao.AnalyticsEventDao

@Database(
    entities = [
        TransactionEntity::class,
        MerchantEntity::class,
        SettingsEntity::class,
        AnalyticsEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OfflinePayDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun merchantDao(): MerchantDao
    abstract fun settingsDao(): SettingsDao
    abstract fun analyticsEventDao(): AnalyticsEventDao

    companion object {
        const val DATABASE_NAME = "offlinepay.db"

        /**
         * Creates the [OfflinePayDatabase] instance with SQLCipher encryption.
         *
         * The [keyProvider] supplies the 32-byte AES-256 key for SQLCipher.
         * This method is called by the Hilt [DataModule] singleton.
         *
         * @param context Application context.
         * @param keyProvider Provides the database encryption key from Android Keystore.
         * @return The encrypted [OfflinePayDatabase] instance.
         */
        fun create(context: Context, keyProvider: DatabaseKeyProvider): OfflinePayDatabase {
            val passphrase = keyProvider.getOrCreateKey()
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                OfflinePayDatabase::class.java,
                DATABASE_NAME,
            )
                .openHelperFactory(factory)
                // fallbackToDestructiveMigration is intentionally NOT called here —
                // all migrations must be explicit (Req 8.3, fallbackToDestructiveMigration = false)
                // Future migrations added here: .addMigrations(DataMigrations.MIGRATION_1_2)
                .build()
        }
    }
}
