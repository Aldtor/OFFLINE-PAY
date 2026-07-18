package com.offlinepay.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.offlinepay.core.analytics.work.AnalyticsQueueFlusher
import com.offlinepay.core.data.work.TransactionRetentionWorker
import com.offlinepay.core.security.work.IntegrityRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Responsibilities (Task 21.1 — Req 9.16, Req 20.4):
 * - Plant Timber trees: DebugTree in debug, no-op tree in release (Req 9.16).
 * - Initialize WorkManager with the Hilt WorkerFactory.
 * - Schedule all periodic WorkManager workers.
 *
 * Security startup checks are performed in [MainActivity] via SecurityGuard
 * during the splash screen phase (Design Section 12.3).
 */
@HiltAndroidApp
class OfflinePayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // ── WorkManager Configuration.Provider ───────────────────────────────────

    /**
     * Provide Hilt-aware WorkManager configuration so that @HiltWorker workers
     * receive their injected dependencies correctly.
     *
     * WorkManager must be initialized via [Configuration.Provider] (manual init)
     * when using Hilt — the default WorkManager initializer must be removed in the
     * manifest (android:name="androidx.startup.InitializationProvider" disabled).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        setupLogging()
        schedulePeriodicWorkers()
    }

    // ── Logging setup (Req 9.16) ─────────────────────────────────────────────

    /**
     * Plants a Timber tree appropriate for the build variant.
     *
     * - DEBUG: [Timber.DebugTree] — full Logcat output for development.
     * - RELEASE: No tree planted — [Timber] calls are silently no-ops.
     *   This ensures no PII or sensitive data is emitted in production (Req 9.16, Req 9.17).
     */
    private fun setupLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Release: intentionally no tree planted. Timber.d/e/w/i are all no-ops.
    }

    // ── WorkManager scheduling ────────────────────────────────────────────────

    /**
     * Schedules all periodic background workers on app startup.
     *
     * Workers are registered with [ExistingPeriodicWorkPolicy.KEEP] so that
     * re-scheduling on every app launch does not reset the timing interval of
     * already-queued work. Workers that have not yet run are left untouched.
     *
     * Scheduled workers:
     * 1. [IntegrityRefreshWorker] — every 24h, network required (Req 9.1, Req 9.6)
     * 2. [AnalyticsQueueFlusher]  — every 6h, network required (Design Section 14.2)
     * 3. [TransactionRetentionWorker] — daily, charging + idle (Req 8.12, Design Section 15.3)
     */
    private fun schedulePeriodicWorkers() {
        val workManager = WorkManager.getInstance(this)

        // ── 1. Play Integrity verdict refresh (24h, network) ─────────────────
        IntegrityRefreshWorker.schedule(workManager)

        // ── 2. Analytics offline queue flush (6h, network) ───────────────────
        val analyticsConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val analyticsFlushRequest = PeriodicWorkRequestBuilder<AnalyticsQueueFlusher>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(analyticsConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "analytics_queue_flush_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            analyticsFlushRequest,
        )

        // ── 3. Transaction retention cleanup (daily, charging + idle) ─────────
        val retentionConstraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()
        val retentionRequest = PeriodicWorkRequestBuilder<TransactionRetentionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setConstraints(retentionConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "transaction_retention_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            retentionRequest,
        )
    }
}
