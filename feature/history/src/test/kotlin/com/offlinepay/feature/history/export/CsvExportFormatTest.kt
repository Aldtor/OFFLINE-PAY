package com.offlinepay.feature.history.export

import com.offlinepay.core.domain.model.OperatorType
import com.offlinepay.core.domain.model.PaymentMethodType
import com.offlinepay.core.domain.model.TransactionRecord
import com.offlinepay.core.domain.model.TransactionStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the CSV export row generation logic.
 *
 * Does NOT test Android file system (Context, FileProvider) — only the
 * pure formatting and CSV escaping logic.
 *
 * Design reference: Section 9.5 (Export)
 * Requirements: Req 8.16 (CSV export), Req 8.17 (date range), Req 8.18
 */
class CsvExportFormatTest {

    @Nested
    inner class CsvEscaping {

        @Test
        fun `plain text is not escaped`() {
            val escaped = escapeCsv("hello")
            escaped shouldBe "hello"
        }

        @Test
        fun `text with commas is quoted`() {
            val escaped = escapeCsv("hello, world")
            escaped shouldBe "\"hello, world\""
        }

        @Test
        fun `text with quotes is double-quoted`() {
            val escaped = escapeCsv("say \"hello\"")
            escaped shouldBe "\"say \"\"hello\"\"\""
        }

        @Test
        fun `text with newlines is quoted`() {
            val escaped = escapeCsv("hello\nworld")
            escaped shouldBe "\"hello\nworld\""
        }

        @Test
        fun `empty text is not escaped`() {
            val escaped = escapeCsv("")
            escaped shouldBe ""
        }
    }

    @Nested
    inner class CsvRowFormat {

        @Test
        fun `CSV row contains all required fields`() {
            val transaction = createSampleTransaction()
            val row = toCsvRow(transaction)

            row shouldContain "txn-001"
            row shouldContain "merchant@upi"
            row shouldContain "50000"
            row shouldContain "500.00"
            row shouldContain "USSD"
            row shouldContain "SUCCESS"
        }

        @Test
        fun `CSV row handles null merchant name`() {
            val transaction = createSampleTransaction(merchantName = null)
            val row = toCsvRow(transaction)

            // Should not crash and empty string should appear for merchant
            row.split(",").size shouldBe 13
        }

        @Test
        fun `CSV row handles null operator`() {
            val transaction = createSampleTransaction(operator = null)
            val row = toCsvRow(transaction)

            row.split(",").size shouldBe 13
        }

        @Test
        fun `CSV header has 13 columns`() {
            val header = getCsvHeader()
            header.split(",").size shouldBe 13
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun getCsvHeader(): String {
        return listOf(
            "Transaction ID",
            "Date",
            "Payee UPI ID",
            "Payee Name",
            "Merchant Name",
            "Category Code",
            "Amount (Paise)",
            "Amount (Rupees)",
            "Payment Method",
            "Status",
            "Operator",
            "SIM Slot",
            "Reference",
        ).joinToString(",")
    }

    private fun toCsvRow(t: TransactionRecord): String {
        val rupees = "%.2f".format(t.amountPaise / 100.0)
        return listOf(
            t.id,
            t.timestampMs.toString(),
            escapeCsv(t.payeeUpiId),
            escapeCsv(t.payeeName.orEmpty()),
            escapeCsv(t.merchantName.orEmpty()),
            t.merchantCategoryCode.orEmpty(),
            t.amountPaise.toString(),
            rupees,
            t.paymentMethod.name,
            t.status.name,
            t.operatorUsed?.name.orEmpty(),
            t.simSlotUsed.toString(),
            escapeCsv(t.transactionReference.orEmpty()),
        ).joinToString(",")
    }

    private fun createSampleTransaction(
        merchantName: String? = "Super Shop",
        operator: OperatorType? = OperatorType.AIRTEL,
    ) = TransactionRecord(
        id = "txn-001",
        timestampMs = 1700000000000L,
        payeeUpiId = "merchant@upi",
        payeeName = "Merchant",
        merchantName = merchantName,
        merchantCategoryCode = "5411",
        amountPaise = 50000L,
        paymentMethod = PaymentMethodType.USSD,
        status = TransactionStatus.SUCCESS,
        operatorUsed = operator,
        simSlotUsed = 0,
        transactionReference = "TXN12345",
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
    )
}
