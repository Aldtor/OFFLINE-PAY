package com.offlinepay.core.data.db

/**
 * Room database migration objects for OfflinePay.
 *
 * Schema export is enabled (`exportSchema = true` in [OfflinePayDatabase]).
 * Schema JSON files are committed to `app/schemas/` and tested with [MigrationTestHelper].
 *
 * [OfflinePayDatabase.fallbackToDestructiveMigrationFrom] is disabled in production —
 * every schema change requires an explicit migration object here.
 *
 * Design reference: Section 9.4 (Migration Strategy)
 * Requirements: Req 8.3 (no destructive migration in production)
 *
 * ### How to add a migration:
 * ```kotlin
 * val MIGRATION_1_TO_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE merchants ADD COLUMN category_name TEXT")
 *     }
 * }
 * ```
 * Then add it to [OfflinePayDatabase.create]:
 * `.addMigrations(DataMigrations.MIGRATION_1_TO_2)`
 */
object DataMigrations {
    // No migrations yet — database is at version 1.
    // Add future migrations here as the schema evolves.
}
