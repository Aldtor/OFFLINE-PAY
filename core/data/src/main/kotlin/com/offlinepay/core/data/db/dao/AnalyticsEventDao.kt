package com.offlinepay.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinepay.core.data.db.entity.AnalyticsEventEntity

/**
 * Data Access Object for local analytics events queue.
 *
 * Design reference: Section 14.2
 */
@Dao
interface AnalyticsEventDao {

    @Insert
    suspend fun insert(event: AnalyticsEventEntity): Long

    @Query("SELECT * FROM analytics_events WHERE is_flushed = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getUnflushed(limit: Int): List<AnalyticsEventEntity>

    @Query("UPDATE analytics_events SET is_flushed = 1 WHERE id IN (:ids)")
    suspend fun markAsFlushed(ids: List<Long>)

    @Query("DELETE FROM analytics_events WHERE is_flushed = 1 OR created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM analytics_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
