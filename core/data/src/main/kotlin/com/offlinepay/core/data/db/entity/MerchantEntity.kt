package com.offlinepay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a merchant profile derived from UPI QR scan data.
 *
 * Must NEVER be exposed outside `:core:data` — use [MerchantProfile] instead.
 *
 * Design reference: Section 9.1 (merchants table schema)
 * Requirements: Req 18 (Merchant Card)
 */
@Entity(
    tableName = "merchants",
    indices = [
        Index(value = ["upi_id"], unique = true),
        Index(value = ["is_favourite"]),
        Index(value = ["last_seen_at"]),
    ],
)
data class MerchantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "upi_id")
    val upiId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category_code")
    val categoryCode: String?,

    @ColumnInfo(name = "category_name")
    val categoryName: String?,

    /** ARGB color integer generated from upiId hash for consistent avatar color. */
    @ColumnInfo(name = "avatar_color")
    val avatarColor: Int,

    /** 0 = not favourite, 1 = favourite */
    @ColumnInfo(name = "is_favourite")
    val isFavourite: Int = 0,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long,

    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int = 1,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
