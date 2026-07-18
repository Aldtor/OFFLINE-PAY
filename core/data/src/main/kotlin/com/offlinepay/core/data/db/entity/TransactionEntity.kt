package com.offlinepay.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single offline payment attempt.
 *
 * All monetary amounts are stored as paise (Long) to avoid floating-point errors.
 * This entity must NEVER be exposed outside `:core:data` — use [TransactionRecord] instead.
 *
 * Design reference: Section 9.1 (transactions table schema)
 * Requirements: Req 8.1, Req 8.2 (all required fields persisted)
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["payee_upi_id"]),
        Index(value = ["status"]),
        Index(value = ["payment_method"]),
        Index(value = ["amount"]),
        Index(value = ["status", "timestamp"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "payee_upi_id")
    val payeeUpiId: String,

    @ColumnInfo(name = "payee_name")
    val payeeName: String?,

    @ColumnInfo(name = "merchant_name")
    val merchantName: String?,

    @ColumnInfo(name = "merchant_category_code")
    val merchantCategoryCode: String?,

    /** Amount in paise (Long). ₹100.50 = 10050L */
    @ColumnInfo(name = "amount")
    val amount: Long,

    /** "USSD" or "PAY123" */
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,

    /** "SUCCESS", "FAILURE", "PENDING", "UNKNOWN" */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "operator_used")
    val operatorUsed: String?,

    @ColumnInfo(name = "sim_slot_index")
    val simSlotIndex: Int,

    /** Sanitised USSD response — UPI PINs and PII stripped before storage */
    @ColumnInfo(name = "ussd_response")
    val ussdResponse: String?,

    @ColumnInfo(name = "transaction_reference")
    val transactionReference: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
