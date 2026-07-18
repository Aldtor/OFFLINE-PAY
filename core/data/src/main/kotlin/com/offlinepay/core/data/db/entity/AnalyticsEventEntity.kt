package com.offlinepay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a queued offline analytics event.
 * Matches design specification in Section 9.1.
 *
 * Design reference: Section 9.1, Section 14.2
 */
@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_name")
    val eventName: String,

    @ColumnInfo(name = "event_params_json")
    val eventParamsJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "is_flushed")
    val isFlushed: Boolean = false,
)
