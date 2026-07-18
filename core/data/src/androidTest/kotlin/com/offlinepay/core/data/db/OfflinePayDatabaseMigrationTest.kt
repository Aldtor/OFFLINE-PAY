package com.offlinepay.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration test infrastructure for [OfflinePayDatabase].
 *
 * Uses [MigrationTestHelper] to validate that every schema migration in
 * [DataMigrations] correctly upgrades the database without data loss.
 *
 * Schema JSON files must be exported to `core/data/schemas/` (configured via
 * `ksp { arg("room.schemaLocation", ...) }` in `build.gradle.kts`) and
 * exposed as an androidTest asset source set so [MigrationTestHelper] can
 * load them from the classpath.
 *
 * ### How to add migration tests
 * When a new migration `MIGRATION_X_TO_Y` is added to [DataMigrations]:
 * 1. Bump the database version in [OfflinePayDatabase].
 * 2. Add the migration to [OfflinePayDatabase.create] via `.addMigrations(...)`.
 * 3. Run a debug build so KSP exports the new schema JSON (version Y.json).
 * 4. Add a test method below following the pattern in [testMigrationFrom1To2].
 *
 * ### Why [FrameworkSQLiteOpenHelperFactory] and not SQLCipher?
 * [MigrationTestHelper] runs on an instrumented (device/emulator) environment and
 * validates schema structure, not encryption. A plain [FrameworkSQLiteOpenHelperFactory]
 * is intentional here — migration correctness is schema-level, not key-level.
 * SQLCipher encryption is validated by the production [OfflinePayDatabase.create] path.
 *
 * Design reference: Section 9.4 (Migration Strategy)
 * Requirements: Req 8.3 (no destructive migration in production)
 */
@RunWith(AndroidJUnit4::class)
class OfflinePayDatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    /**
     * [MigrationTestHelper] automatically:
     * - Creates the database at any historical schema version from the JSON files.
     * - Applies the specified [DataMigrations] migration(s).
     * - Validates the resulting schema against the target version JSON.
     *
     * Schema JSON files are read from the `androidTest` assets source set,
     * which is mapped to `core/data/schemas/` in `build.gradle.kts`.
     */
    @get:Rule
    val migrationTestHelper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = OfflinePayDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * Smoke-test: verifies that version 1 schema can be created and opened
     * without errors. This confirms the exported schema JSON is valid.
     */
    @Test
    @Throws(IOException::class)
    fun createVersion1AndReopen() {
        // Create DB at version 1
        val db = migrationTestHelper.createDatabase(TEST_DB, 1)
        db.close()

        // Reopen to confirm no migration errors on identical version
        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 1, true)
    }

    // ── Future migration tests ────────────────────────────────────────────────
    //
    // When DataMigrations.MIGRATION_1_TO_2 is added, add:
    //
    // @Test
    // @Throws(IOException::class)
    // fun testMigrationFrom1To2() {
    //     migrationTestHelper.createDatabase(TEST_DB, 1).close()
    //     migrationTestHelper.runMigrationsAndValidate(
    //         TEST_DB, 2, true, DataMigrations.MIGRATION_1_TO_2
    //     )
    // }
}
