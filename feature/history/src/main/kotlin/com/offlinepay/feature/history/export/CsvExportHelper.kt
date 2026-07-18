package com.offlinepay.feature.history.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.offlinepay.core.domain.model.TransactionRecord
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates CSV export files for transaction history.
 *
 * Output files are written to [FileProvider] URIs in `cache/exports/`.
 *
 * Design reference: Section 9.5 (Export)
 * Requirements: Req 8.16 (CSV export), Req 8.17 (date range), Req 8.18
 */
object CsvExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val HEADER = listOf(
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

    /**
     * Exports transactions to a CSV file and returns its [FileProvider] URI.
     */
    fun exportToCsv(
        context: Context,
        transactions: List<TransactionRecord>,
        filename: String = "transactions_${System.currentTimeMillis()}.csv",
    ): Uri {
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val file = File(exportDir, filename)

        FileWriter(file).use { writer ->
            writer.appendLine(HEADER)
            for (transaction in transactions) {
                writer.appendLine(toCsvRow(transaction))
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun toCsvRow(t: TransactionRecord): String {
        val rupees = "%.2f".format(t.amountPaise / 100.0)
        return listOf(
            t.id,
            dateFormat.format(Date(t.timestampMs)),
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

    /** Escapes a CSV field value — wraps in quotes if it contains commas, quotes, or newlines. */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
