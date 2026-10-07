package com.example.pdf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.WithdrawalEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGeneratorHelper {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun formatInr(amount: Double, currency: String = "₹"): String {
        return currency + String.format(Locale.US, "%,.2f", amount)
    }

    private fun checkPdfExportAllowed(context: Context, settings: AppSettingsEntity): Boolean {
        if (!com.example.data.auth.AuthorizationManager.canExportPdf()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val isTamil = settings.language == "TA"
                android.widget.Toast.makeText(
                    context,
                    if (isTamil) "PDF ஏற்றுமதி நிர்வாகத்தால் முடக்கப்பட்டுள்ளது" else "PDF export is restricted by administration",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        return true
    }

    /**
     * Generates a formal Job Work Receipt / Tax Slip PDF for a single job entry
     */
    fun generateJobReceiptPdf(
        context: Context,
        settings: AppSettingsEntity,
        job: JobEntryEntity
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // 1. Header Banner (Deep Sage Green)
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(0f, 0f, 595f, 95f, paint)

        // Business Title
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 38f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Phone: ${settings.businessPhone} | ${settings.businessAddress}", 30f, 56f, paint)
        canvas.drawText("Tractor Work Bill & Job Receipt • Entry #${job.id}", 30f, 72f, paint)

        // Receipt Badge
        paint.color = Color.rgb(226, 239, 224)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("JOB WORK RECEIPT", 400f, 40f, paint)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Entry No: #${job.id}", 400f, 56f, paint)
        canvas.drawText("Date: ${dateTimeFormat.format(Date(job.startTimeMillis))}", 400f, 72f, paint)

        // 2. Customer & Job Metadata Card
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(30f, 110f, 290f, 205f, 8f, 8f, paint)
        canvas.drawRoundRect(305f, 110f, 565f, 205f, 8f, 8f, paint)

        // Border for cards
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(202, 216, 201)
        paint.strokeWidth = 1f
        canvas.drawRoundRect(30f, 110f, 290f, 205f, 8f, 8f, paint)
        canvas.drawRoundRect(305f, 110f, 565f, 205f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Customer Info Content (Left)
        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER DETAILS", 42f, 128f, paint)

        paint.color = Color.BLACK
        paint.textSize = 12f
        canvas.drawText(job.customerName, 42f, 146f, paint)

        paint.color = Color.rgb(80, 80, 80)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Phone: ${job.customerPhone.ifBlank { "N/A" }}", 42f, 164f, paint)
        canvas.drawText("Village / Location: ${job.customerLocation.ifBlank { "N/A" }}", 42f, 182f, paint)

        // Tractor & Partner info (Right)
        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MACHINERY & OPERATOR", 317f, 128f, paint)

        paint.color = Color.rgb(70, 70, 70)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Tractor: ${job.tractorLabel}", 317f, 146f, paint)
        canvas.drawText("Operator: ${job.operatorName}", 317f, 164f, paint)
        canvas.drawText("Recorded by Partner: ${job.addedByPartner}", 317f, 182f, paint)

        // 3. Work Details Table Header
        var yPos = 225f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ITEM / WORK DESCRIPTION", 40f, yPos + 16f, paint)
        canvas.drawText("DURATION (HRS)", 260f, yPos + 16f, paint)
        canvas.drawText("RATE/HR", 380f, yPos + 16f, paint)
        canvas.drawText("AMOUNT", 490f, yPos + 16f, paint)

        yPos += 24f

        // Table Content Row
        paint.color = Color.WHITE
        canvas.drawRect(30f, yPos, 565f, yPos + 40f, paint)

        // Inner border
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(202, 216, 201)
        paint.strokeWidth = 1f
        canvas.drawRect(30f, yPos, 565f, yPos + 40f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(job.workType, 40f, yPos + 18f, paint)

        paint.color = Color.rgb(100, 100, 100)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (job.notes.isNotBlank()) {
            val noteText = if (job.notes.length > 35) job.notes.take(33) + "..." else job.notes
            canvas.drawText("Note: $noteText", 40f, yPos + 32f, paint)
        } else {
            canvas.drawText("Tractor Work Execution Slip", 40f, yPos + 32f, paint)
        }

        val durationFormatted = com.example.ui.util.WorkBillingCalculator.formatDurationDetailed(job.durationMinutes)
        paint.color = Color.rgb(40, 40, 40)
        paint.textSize = 10f
        canvas.drawText(durationFormatted, 260f, yPos + 22f, paint)

        canvas.drawText(formatInr(job.hourlyRate, settings.currency), 380f, yPos + 22f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(formatInr(job.totalAmount, settings.currency), 490f, yPos + 22f, paint)

        // 4. Financial Summary Card
        yPos += 55f
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(280f, yPos, 565f, yPos + 85f, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(202, 216, 201)
        paint.strokeWidth = 1f
        canvas.drawRoundRect(280f, yPos, 565f, yPos + 85f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Total Work Amount:", 300f, yPos + 22f, paint)
        canvas.drawText(formatInr(job.totalAmount, settings.currency), 470f, yPos + 22f, paint)

        paint.color = Color.rgb(46, 125, 50)
        canvas.drawText("Amount Paid (Received):", 300f, yPos + 44f, paint)
        canvas.drawText(formatInr(job.amountReceived, settings.currency), 470f, yPos + 44f, paint)

        paint.color = if (job.pendingAmount > 0) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Balance Due:", 300f, yPos + 68f, paint)
        canvas.drawText(formatInr(job.pendingAmount, settings.currency), 470f, yPos + 68f, paint)

        // 5. Footer & Terms
        val footerY = 740f
        paint.color = Color.rgb(120, 120, 120)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Note: This is a computer generated work slip for tractor agricultural service.", 30f, footerY, paint)
        canvas.drawText("Thank you for your business! Generated via AIDHUNT Trac.", 30f, footerY + 14f, paint)

        // Signature Lines
        paint.color = Color.rgb(60, 60, 60)
        canvas.drawLine(50f, footerY + 45f, 200f, footerY + 45f, paint)
        canvas.drawLine(390f, footerY + 45f, 540f, footerY + 45f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Customer Signature", 70f, footerY + 58f, paint)
        canvas.drawText("Authorized Partner", 410f, footerY + 58f, paint)

        document.finishPage(page)
        return savePdfToFile(context, document, "Job_Receipt_${job.id}_${job.customerName.replace(" ", "_")}.pdf")
    }

    /**
     * Generates a formal Letterhead Customer Credit Statement PDF
     */
    fun generateCustomerStatementPdf(
        context: Context,
        settings: AppSettingsEntity,
        customer: CustomerEntity,
        jobs: List<JobEntryEntity>
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points (72 dpi)
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // 1. Header Background (Deep Sage Green Banner)
        paint.color = Color.rgb(30, 77, 43) // Deep Sage
        canvas.drawRect(0f, 0f, 595f, 95f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 38f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Phone: ${settings.businessPhone} | ${settings.businessAddress}", 30f, 56f, paint)
        canvas.drawText("Tractor Work Statements • Customer Credit Due Record", 30f, 72f, paint)

        // Statement Badge
        paint.color = Color.rgb(226, 239, 224)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("STATEMENT OF ACCOUNT", 400f, 40f, paint)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Date: ${dateFormat.format(Date())}", 400f, 56f, paint)

        // 2. Customer Details Card (Left) & Account Summary (Right)
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(30f, 110f, 290f, 195f, 8f, 8f, paint)
        canvas.drawRoundRect(305f, 110f, 565f, 195f, 8f, 8f, paint)

        // Border for cards
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(202, 216, 201)
        paint.strokeWidth = 1f
        canvas.drawRoundRect(30f, 110f, 290f, 195f, 8f, 8f, paint)
        canvas.drawRoundRect(305f, 110f, 565f, 195f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Customer Info Content
        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER DETAILS", 42f, 128f, paint)

        paint.color = Color.BLACK
        paint.textSize = 12f
        canvas.drawText(customer.name, 42f, 146f, paint)

        paint.color = Color.rgb(80, 80, 80)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Phone: ${customer.phone}", 42f, 162f, paint)
        canvas.drawText("Village / Location: ${customer.location.ifBlank { "N/A" }}", 42f, 178f, paint)

        // Account Summary Content
        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BILLING SUMMARY", 317f, 128f, paint)

        paint.color = Color.rgb(70, 70, 70)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Total Work Billed: ${formatInr(customer.totalBilled)}", 317f, 146f, paint)
        canvas.drawText("Total Amount Paid: ${formatInr(customer.totalPaid)}", 317f, 162f, paint)

        // Balance Due Highlight
        paint.color = Color.rgb(198, 40, 40)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("OUTSTANDING DUE: ${formatInr(customer.balanceDue)}", 317f, 182f, paint)

        // 3. Transactions Table Header
        var yPos = 220f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DATE", 35f, yPos + 15f, paint)
        canvas.drawText("WORK TYPE & TRACTOR", 100f, yPos + 15f, paint)
        canvas.drawText("HOURS", 290f, yPos + 15f, paint)
        canvas.drawText("RATE/HR", 340f, yPos + 15f, paint)
        canvas.drawText("TOTAL", 400f, yPos + 15f, paint)
        canvas.drawText("PAID", 460f, yPos + 15f, paint)
        canvas.drawText("DUE", 515f, yPos + 15f, paint)

        yPos += 22f

        // Table Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (i in jobs.indices) {
            val job = jobs[i]
            if (yPos > 720f) break // page overflow protection

            // Alternating row color
            paint.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 249, 246)
            canvas.drawRect(30f, yPos, 565f, yPos + 24f, paint)

            paint.color = Color.rgb(40, 40, 40)
            paint.textSize = 8.5f

            val dateStr = dateFormat.format(Date(job.startTimeMillis))
            val hoursStr = com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)

            canvas.drawText(dateStr, 35f, yPos + 16f, paint)

            // Work type short label
            val workDesc = if (job.workType.length > 25) job.workType.take(23) + ".." else job.workType
            canvas.drawText(workDesc, 100f, yPos + 11f, paint)
            paint.textSize = 7.5f
            paint.color = Color.rgb(100, 100, 100)
            val tractorDesc = if (job.tractorLabel.length > 28) job.tractorLabel.take(26) + ".." else job.tractorLabel
            canvas.drawText("(${tractorDesc} • ${job.operatorName})", 100f, yPos + 21f, paint)

            paint.textSize = 8.5f
            paint.color = Color.rgb(40, 40, 40)
            canvas.drawText(hoursStr, 290f, yPos + 16f, paint)
            canvas.drawText("₹${job.hourlyRate.toInt()}", 340f, yPos + 16f, paint)
            canvas.drawText(formatInr(job.totalAmount), 400f, yPos + 16f, paint)

            paint.color = Color.rgb(46, 125, 50)
            canvas.drawText(formatInr(job.amountReceived), 460f, yPos + 16f, paint)

            paint.color = if (job.pendingAmount > 0) Color.rgb(198, 40, 40) else Color.rgb(70, 70, 70)
            paint.typeface = if (job.pendingAmount > 0) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(formatInr(job.pendingAmount), 515f, yPos + 16f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            yPos += 24f
        }

        // Horizontal line
        paint.color = Color.rgb(202, 216, 201)
        paint.strokeWidth = 1.5f
        canvas.drawLine(30f, yPos, 565f, yPos, paint)

        // 4. Totals Row
        yPos += 15f
        paint.color = Color.rgb(235, 245, 235)
        canvas.drawRoundRect(300f, yPos, 565f, yPos + 60f, 6f, 6f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        canvas.drawText("Total Work Amount:", 310f, yPos + 18f, paint)
        canvas.drawText(formatInr(customer.totalBilled), 470f, yPos + 18f, paint)

        paint.color = Color.rgb(46, 125, 50)
        canvas.drawText("Total Received (Paid):", 310f, yPos + 34f, paint)
        canvas.drawText(formatInr(customer.totalPaid), 470f, yPos + 34f, paint)

        paint.color = Color.rgb(198, 40, 40)
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Total Balance Pending:", 310f, yPos + 52f, paint)
        canvas.drawText(formatInr(customer.balanceDue), 470f, yPos + 52f, paint)

        // 5. Footer & Signatures
        val footerY = 760f
        paint.color = Color.rgb(120, 120, 120)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Payment Note: Please remit pending balance at earliest via Cash / UPI.", 30f, footerY, paint)
        canvas.drawText("Generated via AIDHUNT Trac • Joint Tractor Business Management", 30f, footerY + 14f, paint)

        // Signature lines
        paint.color = Color.rgb(50, 50, 50)
        canvas.drawLine(400f, footerY + 30f, 550f, footerY + 30f, paint)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Authorized Signatory / Partner", 400f, footerY + 42f, paint)

        document.finishPage(page)

        return savePdfToFile(context, document, "Statement_${customer.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Generates a Bulk Customer Credit Due Summary PDF
     */
    fun generateBulkCustomerDuesPdf(
        context: Context,
        settings: AppSettingsEntity,
        customers: List<CustomerEntity>
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(0f, 0f, 595f, 85f, paint)

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 35f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("CUSTOMER CREDIT DUE SUMMARY REPORT", 30f, 55f, paint)
        canvas.drawText("Report Generated: ${dateTimeFormat.format(Date())}", 30f, 70f, paint)

        // Summary Metric
        val totalDue = customers.sumOf { it.balanceDue }
        val totalCustomersWithDue = customers.count { it.balanceDue > 0 }

        paint.color = Color.rgb(255, 235, 238)
        canvas.drawRoundRect(30f, 100f, 565f, 150f, 8f, 8f, paint)

        paint.color = Color.rgb(198, 40, 40)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL OUTSTANDING CUSTOMER DUES: ${formatInr(totalDue)}", 45f, 125f, paint)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Number of pending accounts: $totalCustomersWithDue customers", 45f, 140f, paint)

        // Table Header
        var yPos = 170f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER NAME", 35f, yPos + 15f, paint)
        canvas.drawText("PHONE / VILLAGE", 180f, yPos + 15f, paint)
        canvas.drawText("TOTAL BILLED", 340f, yPos + 15f, paint)
        canvas.drawText("PAID", 430f, yPos + 15f, paint)
        canvas.drawText("BALANCE DUE", 495f, yPos + 15f, paint)

        yPos += 22f
        val dueList = customers.filter { it.balanceDue > 0 }.sortedByDescending { it.balanceDue }

        for (i in dueList.indices) {
            val c = dueList[i]
            if (yPos > 760f) break

            paint.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 249, 246)
            canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

            paint.color = Color.BLACK
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(c.name, 35f, yPos + 15f, paint)

            paint.color = Color.rgb(80, 80, 80)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val loc = if (c.location.isNotBlank()) " • ${c.location}" else ""
            canvas.drawText("${c.phone}$loc", 180f, yPos + 15f, paint)

            canvas.drawText(formatInr(c.totalBilled), 340f, yPos + 15f, paint)

            paint.color = Color.rgb(46, 125, 50)
            canvas.drawText(formatInr(c.totalPaid), 430f, yPos + 15f, paint)

            paint.color = Color.rgb(198, 40, 40)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(formatInr(c.balanceDue), 495f, yPos + 15f, paint)

            yPos += 22f
        }

        document.finishPage(page)
        return savePdfToFile(context, document, "Customer_Credit_Dues_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Generates an Expense Report PDF
     */
    fun generateExpensesReportPdf(
        context: Context,
        settings: AppSettingsEntity,
        expenses: List<ExpenseEntity>,
        filterDescription: String
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(0f, 0f, 595f, 85f, paint)

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 35f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("BUSINESS EXPENSE AUDIT REPORT", 30f, 55f, paint)
        canvas.drawText("Filter: $filterDescription • Date: ${dateFormat.format(Date())}", 30f, 70f, paint)

        val totalExpense = expenses.sumOf { it.amount }
        val avgExpense = if (expenses.isNotEmpty()) totalExpense / expenses.size else 0.0

        // Metric Card
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(30f, 100f, 565f, 150f, 8f, 8f, paint)

        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL EXPENSES: ${formatInr(totalExpense)}", 45f, 125f, paint)

        paint.color = Color.rgb(70, 70, 70)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Total Entries: ${expenses.size} | Average per Entry: ${formatInr(avgExpense)}", 45f, 140f, paint)

        // Table
        var yPos = 170f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DATE & TIME", 35f, yPos + 15f, paint)
        canvas.drawText("TYPE", 140f, yPos + 15f, paint)
        canvas.drawText("TRACTOR / OPERATOR", 210f, yPos + 15f, paint)
        canvas.drawText("ADDED BY", 370f, yPos + 15f, paint)
        canvas.drawText("AMOUNT", 490f, yPos + 15f, paint)

        yPos += 22f

        for (i in expenses.indices) {
            val exp = expenses[i]
            if (yPos > 760f) break

            paint.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 249, 246)
            canvas.drawRect(30f, yPos, 565f, yPos + 24f, paint)

            paint.color = Color.BLACK
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(dateTimeFormat.format(Date(exp.dateTimestamp)), 35f, yPos + 16f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(exp.expenseType, 140f, yPos + 16f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8f
            canvas.drawText("${exp.tractorLabel.take(20)} (${exp.operatorName})", 210f, yPos + 16f, paint)

            canvas.drawText(exp.addedByPartner, 370f, yPos + 16f, paint)

            paint.color = Color.rgb(198, 40, 40)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(formatInr(exp.amount), 490f, yPos + 16f, paint)

            yPos += 24f
        }

        document.finishPage(page)
        return savePdfToFile(context, document, "Expense_Report_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Generates a Balance Sheet PDF
     */
    fun generateBalanceSheetPdf(
        context: Context,
        settings: AppSettingsEntity,
        totalSales: Double,
        totalExpenses: Double,
        netBalance: Double,
        periodSummary: List<BalanceSheetRow>
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(0f, 0f, 595f, 85f, paint)

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 35f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("BALANCE SHEET & PROFIT/LOSS STATEMENT", 30f, 55f, paint)
        canvas.drawText("Statement As Of: ${dateTimeFormat.format(Date())}", 30f, 70f, paint)

        // Summary Card
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(30f, 100f, 565f, 160f, 8f, 8f, paint)

        paint.color = Color.rgb(46, 125, 50)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL RECEIVED SALES: ${formatInr(totalSales)}", 45f, 125f, paint)

        paint.color = Color.rgb(198, 40, 40)
        canvas.drawText("TOTAL EXPENSES: ${formatInr(totalExpenses)}", 45f, 145f, paint)

        val netColor = if (netBalance >= 0) Color.rgb(30, 77, 43) else Color.rgb(198, 40, 40)
        paint.color = netColor
        paint.textSize = 12f
        canvas.drawText("NET PROFIT / BALANCE: ${formatInr(netBalance)}", 330f, 135f, paint)

        // Table
        var yPos = 180f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PERIOD / DATE", 35f, yPos + 15f, paint)
        canvas.drawText("RECEIVED SALES", 200f, yPos + 15f, paint)
        canvas.drawText("EXPENSES", 340f, yPos + 15f, paint)
        canvas.drawText("NET BALANCE", 460f, yPos + 15f, paint)

        yPos += 22f

        for (i in periodSummary.indices) {
            val row = periodSummary[i]
            if (yPos > 760f) break

            paint.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 249, 246)
            canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

            paint.color = Color.BLACK
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(row.periodLabel, 35f, yPos + 15f, paint)

            paint.color = Color.rgb(46, 125, 50)
            canvas.drawText(formatInr(row.sales), 200f, yPos + 15f, paint)

            paint.color = Color.rgb(198, 40, 40)
            canvas.drawText(formatInr(row.expenses), 340f, yPos + 15f, paint)

            paint.color = if (row.balance >= 0) Color.rgb(30, 77, 43) else Color.rgb(198, 40, 40)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(formatInr(row.balance), 460f, yPos + 15f, paint)

            yPos += 22f
        }

        document.finishPage(page)
        return savePdfToFile(context, document, "Balance_Sheet_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Generates a Withdrawal Report PDF
     */
    fun generateWithdrawalReportPdf(
        context: Context,
        settings: AppSettingsEntity,
        availableAmount: Double,
        totalWithdrawn: Double,
        withdrawals: List<WithdrawalEntity>
    ): File? {
        if (!checkPdfExportAllowed(context, settings)) return null
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(0f, 0f, 595f, 85f, paint)

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(settings.businessName.uppercase(Locale.ROOT), 30f, 35f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("PARTNER WITHDRAWALS & PAYOUT REPORT", 30f, 55f, paint)
        canvas.drawText("Generated on: ${dateTimeFormat.format(Date())}", 30f, 70f, paint)

        // Summary
        paint.color = Color.rgb(243, 247, 242)
        canvas.drawRoundRect(30f, 100f, 565f, 150f, 8f, 8f, paint)

        paint.color = Color.rgb(30, 77, 43)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("AVAILABLE BALANCE IN BUSINESS: ${formatInr(availableAmount)}", 45f, 125f, paint)

        paint.color = Color.rgb(184, 134, 11)
        paint.textSize = 10f
        canvas.drawText("TOTAL PARTNER WITHDRAWALS: ${formatInr(totalWithdrawn)}", 45f, 140f, paint)

        // Table
        var yPos = 170f
        paint.color = Color.rgb(30, 77, 43)
        canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DATE", 35f, yPos + 15f, paint)
        canvas.drawText("PARTNER", 140f, yPos + 15f, paint)
        canvas.drawText("CATEGORY", 240f, yPos + 15f, paint)
        canvas.drawText("NOTE", 340f, yPos + 15f, paint)
        canvas.drawText("AMOUNT", 490f, yPos + 15f, paint)

        yPos += 22f

        for (i in withdrawals.indices) {
            val w = withdrawals[i]
            if (yPos > 760f) break

            paint.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 249, 246)
            canvas.drawRect(30f, yPos, 565f, yPos + 22f, paint)

            paint.color = Color.BLACK
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(dateTimeFormat.format(Date(w.timestamp)), 35f, yPos + 15f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(w.partnerName, 140f, yPos + 15f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(w.category, 240f, yPos + 15f, paint)

            canvas.drawText(w.note.take(20), 340f, yPos + 15f, paint)

            paint.color = Color.rgb(184, 134, 11)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(formatInr(w.amount), 490f, yPos + 15f, paint)

            yPos += 22f
        }

        document.finishPage(page)
        return savePdfToFile(context, document, "Withdrawals_${System.currentTimeMillis()}.pdf")
    }

    private fun savePdfToFile(context: Context, document: PdfDocument, filename: String): File? {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, filename)
        return try {
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun sharePdf(context: Context, file: File, subject: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "$subject\nShared from AIDHUNT Trac")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share PDF Statement")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdfToWhatsAppOrGeneral(
        context: Context,
        file: File,
        phoneNumber: String?,
        subject: String,
        message: String = ""
    ) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val formattedPhone = com.example.ui.components.formatWhatsAppPhone(phoneNumber)
            val shareText = if (message.isNotBlank()) message else "$subject\nShared from ${file.nameWithoutExtension}"

            if (formattedPhone.isNotBlank()) {
                try {
                    // Try WhatsApp primary
                    val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra("jid", "$formattedPhone@s.whatsapp.net")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(whatsappIntent)
                    return
                } catch (e1: Exception) {
                    try {
                        // Try WhatsApp Business
                        val wbIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            putExtra("jid", "$formattedPhone@s.whatsapp.net")
                            setPackage("com.whatsapp.w4b")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(wbIntent)
                        return
                    } catch (e2: Exception) {
                        // WhatsApp not installed, fallback to general share
                        Toast.makeText(context, "WhatsApp not found. Opening share options...", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "No valid customer phone number found. Opening share options...",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Fallback to standard share sheet
            sharePdf(context, file, subject)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private const val PDF_DOWNLOAD_CHANNEL_ID = "trac_pdf_downloads_channel"
    private const val PDF_DOWNLOAD_CHANNEL_NAME = "PDF Downloads"

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null) {
                val existing = manager.getNotificationChannel(PDF_DOWNLOAD_CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        PDF_DOWNLOAD_CHANNEL_ID,
                        PDF_DOWNLOAD_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications for downloaded PDF statements and reports"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    private fun showPdfDownloadedNotification(
        context: Context,
        pdfUri: Uri,
        fileName: String,
        isTamil: Boolean
    ) {
        try {
            // Check POST_NOTIFICATIONS permission on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("PdfGeneratorHelper", "POST_NOTIFICATIONS not granted, skipping notification")
                    return
                }
            }

            val notificationManagerCompat = NotificationManagerCompat.from(context)
            if (!notificationManagerCompat.areNotificationsEnabled()) {
                Log.d("PdfGeneratorHelper", "Notifications disabled in system, skipping notification")
                return
            }

            ensureNotificationChannel(context)

            val title = if (isTamil) "PDF பதிவிறக்கம் செய்யப்பட்டது" else "PDF Downloaded"
            val subtitle = if (isTamil) "பதிவிறக்கங்களில் சேமிக்கப்பட்டது" else "Saved to Downloads"

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserTitle = if (isTamil) "PDF கோப்பைத் திறக்கவும்" else "Open PDF"
            val chooserIntent = Intent.createChooser(viewIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                chooserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1000

            val builder = NotificationCompat.Builder(context, PDF_DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(fileName)
                .setSubText(subtitle)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText("$fileName\n$subtitle")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManagerCompat.notify(notificationId, builder.build())
            Log.d("PdfGeneratorHelper", "Notification posted for $fileName with uri $pdfUri")
        } catch (e: Exception) {
            // Fail-safe: notification error must never crash app or break download
            Log.w("PdfGeneratorHelper", "Could not show PDF downloaded notification: ${e.message}")
        }
    }

    fun downloadPdfToDownloads(context: Context, file: File, displayName: String, isTamil: Boolean = false): Boolean {
        try {
            val baseName = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName.dropLast(4) else displayName
            val cleanBaseName = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Statement" }
            val fileName = "$cleanBaseName.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Trac")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                    }
                    val successMsg = if (isTamil) "PDF பதிவிறக்கங்களில் சேமிக்கப்பட்டது" else "PDF saved to Downloads"
                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                    showPdfDownloadedNotification(context, uri, fileName, isTamil)
                    return true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "Trac")
                if (!targetDir.exists()) targetDir.mkdirs()
                var targetFile = File(targetDir, fileName)
                var count = 1
                while (targetFile.exists()) {
                    targetFile = File(targetDir, "${cleanBaseName}_$count.pdf")
                    count++
                }
                FileInputStream(file).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val targetUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
                val successMsg = if (isTamil) "PDF பதிவிறக்கங்களில் சேமிக்கப்பட்டது" else "PDF saved to Downloads"
                Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                showPdfDownloadedNotification(context, targetUri, targetFile.name, isTamil)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = if (isTamil) "PDF சேமிப்பதில் பிழை ஏற்பட்டது" else "Error saving PDF: ${e.localizedMessage}"
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            return false
        }
        return false
    }
}

data class BalanceSheetRow(
    val periodLabel: String,
    val sales: Double,
    val expenses: Double,
    val balance: Double
)
