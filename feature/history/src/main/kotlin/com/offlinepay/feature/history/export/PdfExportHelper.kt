package com.offlinepay.feature.history.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.offlinepay.core.domain.model.TransactionRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates PDF receipt documents using Android's [PdfDocument] API.
 *
 * Output files are written to [FileProvider] URIs in `cache/exports/` to avoid
 * external storage permissions.
 *
 * Design reference: Section 9.5 (Export)
 * Requirements: Req 8.15 (PDF receipt), Req 8.16 (date range report), Req 8.18
 */
object PdfExportHelper {

    private const val PAGE_WIDTH = 595   // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842  // A4 height in points
    private const val MARGIN = 40f
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    /**
     * Generates a single-transaction PDF receipt.
     * Returns a [FileProvider] URI for sharing.
     */
    fun generateSingleReceipt(
        context: Context,
        transaction: TransactionRecord,
    ): Uri {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawSingleReceipt(canvas, transaction)

        document.finishPage(page)

        val file = getExportFile(context, "receipt_${transaction.id.take(8)}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Generates a multi-transaction date-range PDF report.
     * Returns a [FileProvider] URI for sharing.
     */
    fun generateDateRangeReport(
        context: Context,
        transactions: List<TransactionRecord>,
        startDate: Long,
        endDate: Long,
    ): Uri {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawReportHeader(canvas, startDate, endDate, transactions.size)

        var y = 160f
        val paint = Paint().apply {
            textSize = 10f
            isAntiAlias = true
        }

        for (transaction in transactions) {
            if (y > PAGE_HEIGHT - MARGIN) break // Simplified — production would add pages

            val name = transaction.merchantName ?: transaction.payeeName ?: transaction.payeeUpiId
            val amount = formatAmount(transaction.amountPaise)
            val date = dateFormat.format(Date(transaction.timestampMs))
            val status = transaction.status.name

            canvas.drawText("$date  |  $name  |  $amount  |  $status", MARGIN, y, paint)
            y += 18f
        }

        document.finishPage(page)

        val file = getExportFile(context, "report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun drawSingleReceipt(canvas: Canvas, transaction: TransactionRecord) {
        val titlePaint = Paint().apply {
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            textSize = 12f
            color = 0xFF666666.toInt()
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            textSize = 14f
            isAntiAlias = true
        }

        var y = MARGIN + 40f
        canvas.drawText("OfflinePay Receipt", MARGIN, y, titlePaint)
        y += 40f

        val fields = listOf(
            "Transaction ID" to transaction.id,
            "Date" to dateFormat.format(Date(transaction.timestampMs)),
            "Payee" to (transaction.merchantName ?: transaction.payeeName ?: transaction.payeeUpiId),
            "UPI ID" to transaction.payeeUpiId,
            "Amount" to formatAmount(transaction.amountPaise),
            "Method" to transaction.paymentMethod.name,
            "Status" to transaction.status.name,
            "Reference" to (transaction.transactionReference ?: "N/A"),
        )

        for ((label, value) in fields) {
            canvas.drawText(label, MARGIN, y, labelPaint)
            y += 18f
            canvas.drawText(value, MARGIN, y, valuePaint)
            y += 28f
        }
    }

    private fun drawReportHeader(canvas: Canvas, startDate: Long, endDate: Long, count: Int) {
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val subtitlePaint = Paint().apply { textSize = 12f; isAntiAlias = true }

        canvas.drawText("OfflinePay Transaction Report", MARGIN, MARGIN + 30f, titlePaint)
        canvas.drawText(
            "${dateFormat.format(Date(startDate))} — ${dateFormat.format(Date(endDate))} ($count transactions)",
            MARGIN,
            MARGIN + 55f,
            subtitlePaint,
        )
    }

    private fun getExportFile(context: Context, filename: String): File {
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        return File(exportDir, filename)
    }

    private fun formatAmount(paise: Long): String {
        val rupees = paise / 100.0
        return "₹%.2f".format(rupees)
    }
}
