package com.offlinepay.core.domain.model

import kotlinx.serialization.Serializable

/**
 * A merchant profile derived from UPI QR scan data and enriched over time.
 *
 * Merchants are automatically upserted on every payment completion.
 * The avatar color is deterministically generated from the UPI ID hash
 * so it is consistent across app restarts without network access.
 *
 * Design reference: Section 9.1 (merchants table), Req 18 (Merchant Card)
 * Requirements: Req 18.1–18.7
 *
 * @param id UUID v4 primary key.
 * @param upiId Merchant's UPI ID (normalised to lowercase, unique).
 * @param name Display name from the most recent QR scan.
 * @param categoryCode Optional ISO 18245 merchant category code.
 * @param categoryName Human-readable category (e.g., "Grocery", "Restaurant").
 * @param avatarColor ARGB color integer generated from [upiId] hash for consistent avatar.
 * @param isFavourite True if the user has marked this merchant as a favourite.
 * @param lastSeenAt Epoch milliseconds of the most recent transaction with this merchant.
 * @param transactionCount Total number of transactions with this merchant.
 * @param createdAt Epoch milliseconds when this profile was first created.
 */
@Serializable
data class MerchantProfile(
    val id: String,
    val upiId: String,
    val name: String,
    val categoryCode: String? = null,
    val categoryName: String? = null,
    val avatarColor: Int,
    val isFavourite: Boolean = false,
    val lastSeenAt: Long,
    val transactionCount: Int = 1,
    val createdAt: Long,
)
