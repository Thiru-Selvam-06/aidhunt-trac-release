package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.ui.viewmodel.BottomTab
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

enum class DatePreset(val label: String) {
    ALL_TIME("Up to Date"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom Range")
}

fun isDateInPreset(
    timestamp: Long,
    preset: DatePreset,
    customStartMillis: Long = 0L,
    customEndMillis: Long = Long.MAX_VALUE
): Boolean {
    if (preset == DatePreset.ALL_TIME) return true
    val cal = java.util.Calendar.getInstance()
    return when (preset) {
        DatePreset.TODAY -> {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startToday = cal.timeInMillis
            val endToday = startToday + 86400000L
            timestamp in startToday until endToday
        }
        DatePreset.YESTERDAY -> {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startToday = cal.timeInMillis
            val startYesterday = startToday - 86400000L
            timestamp in startYesterday until startToday
        }
        DatePreset.THIS_WEEK -> {
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            timestamp >= cal.timeInMillis
        }
        DatePreset.THIS_MONTH -> {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            timestamp >= cal.timeInMillis
        }
        DatePreset.CUSTOM -> {
            val endInclusive = if (customEndMillis < Long.MAX_VALUE) customEndMillis + 86399999L else Long.MAX_VALUE
            timestamp in customStartMillis..endInclusive
        }
        DatePreset.ALL_TIME -> true
    }
}

@Composable
fun PartnerAvatarImage(
    photoUri: String?,
    name: String,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    avatarColorHex: String = "#1E4D2B",
    fallbackBgColor: Color? = null,
    fallbackTextColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bg = fallbackBgColor ?: try {
        Color(android.graphics.Color.parseColor(avatarColorHex))
    } catch (e: Exception) {
        DeepSageGreen
    }
    val textCol = fallbackTextColor ?: Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = name.trim().take(1).uppercase(Locale.getDefault())
            Text(
                text = if (initial.isNotBlank()) initial else "A",
                color = textCol,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FlatShareIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Share via WhatsApp",
    tint: Color = SuccessPaidGreen,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun FlatPdfIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Share PDF Slip / Report",
    tint: Color = DeepSageGreen,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    reportTitle: String = "Report PDF",
    isTamil: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = AlertDueRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "அறிக்கை PDF ஏற்றுமதி" else "Export Statement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = reportTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.accent
                )
                Text(
                    text = if (isTamil) "இந்த PDF அறிக்கையை வாட்ஸ்அப் வழியாகப் பகிரவும் அல்லது உங்கள் சாதனத்தின் பதிவிறக்கங்கள் கோப்புறையில் சேமிக்கவும்." else "Choose whether to share this PDF report directly via WhatsApp / apps or save it to your device's Downloads folder.",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDownload()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isTamil) "PDF பதிவிறக்கு" else "Download PDF")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onShare()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = DeepSageGreen)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isTamil) "PDF பகிர்க" else "Share PDF", color = AppTheme.colors.textPrimary)
            }
        }
    )
}

fun formatInr(amount: Double, currency: String = "₹"): String {
    val rounded = Math.round(amount)
    return currency + String.format(Locale.US, "%,d", rounded)
}

fun formatInrWithDecimals(amount: Double, currency: String = "₹"): String {
    return formatInr(amount, currency)
}

fun formatDate(timeMillis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}

fun formatDateTime(timeMillis: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}

fun formatWhatsAppPhone(phoneNumber: String?, defaultCountryCode: String = "91"): String {
    if (phoneNumber.isNullOrBlank()) return ""
    // Strip all non-digit characters (including +, spaces, dashes, parentheses, dots, etc.)
    var digits = phoneNumber.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) return ""

    // Strip leading international exit code '00' if present (e.g. 00919876543210 -> 919876543210)
    if (digits.startsWith("00")) {
        digits = digits.substring(2)
    }

    // Strip leading '0's (e.g. 09876543210 -> 9876543210)
    digits = digits.trimStart('0')
    if (digits.isBlank()) return ""

    // If exactly 10 digits (standard Indian mobile number), prefix with country code (91)
    if (digits.length == 10) {
        return "$defaultCountryCode$digits"
    }

    // If already 12 digits starting with the country code (e.g. 919876543210), return as-is
    if (digits.length == 12 && digits.startsWith(defaultCountryCode)) {
        return digits
    }

    // If 11 digits starting with another country code or >= 10 digits without prefix
    return if (digits.length in 10..15) {
        if (!digits.startsWith(defaultCountryCode) && digits.length <= 10) {
            "$defaultCountryCode$digits"
        } else {
            digits
        }
    } else {
        digits
    }
}

fun isValidPhoneNumber(phoneNumber: String?): Boolean {
    if (phoneNumber.isNullOrBlank()) return false
    val digits = phoneNumber.replace(Regex("[^0-9]"), "").trimStart('0')
    return digits.length in 10..15
}

fun sanitizePhoneNumberForStorage(phoneNumber: String?): String {
    if (phoneNumber.isNullOrBlank()) return ""
    var digits = phoneNumber.replace(Regex("[^0-9]"), "")
    if (digits.startsWith("00")) {
        digits = digits.substring(2)
    }
    // If it starts with country code 91 and has 12 digits (e.g. +91 98765 43210), strip 91
    if (digits.startsWith("91") && digits.length == 12) {
        digits = digits.substring(2)
    }
    // If it starts with a single leading 0 and has 11 digits (09876543210), strip the leading 0
    if (digits.startsWith("0") && digits.length == 11) {
        digits = digits.substring(1)
    }
    return digits
}

fun formatAmountText(amount: Double): String {
    val rounded = Math.round(amount)
    return rounded.toString()
}

fun buildJobWhatsAppMessage(job: JobEntryEntity, businessName: String, isTamil: Boolean = false): String {
    val dateStr = formatDate(job.startTimeMillis)
    val durationStr = com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)
    val totalStr = formatAmountText(job.totalAmount)
    val paidStr = formatAmountText(job.amountReceived)
    val dueStr = formatAmountText(job.pendingAmount)
    val statusStr = if (isTamil) {
        when {
            job.pendingAmount <= 0.0 -> "செலுத்தப்பட்டது"
            job.amountReceived > 0.0 -> "பகுதி செலுத்தப்பட்டது"
            else -> "நிலுவை"
        }
    } else {
        when {
            job.pendingAmount <= 0.0 -> "Paid"
            job.amountReceived > 0.0 -> "Partially Paid"
            else -> "Pending"
        }
    }

    return if (isTamil) {
        """
வணக்கம்! 🙏 இதோ ரசீது விவரங்கள்:

வாடிக்கையாளர்: ${job.customerName}
தேதி: $dateStr
டிராக்டர்: ${job.tractorLabel}
கால அளவு: $durationStr
மொத்தத் தொகை: ₹$totalStr
செலுத்திய தொகை: ₹$paidStr
நிலுவைத் தொகை: ₹$dueStr
நிலை: $statusStr

இயக்குநர்: ${job.operatorName}
வணிகம்: $businessName
""".trimIndent()
    } else {
        """
Vanakam! 🙏 Here is the bill details:

Customer: ${job.customerName}
Date: $dateStr
Tractor: ${job.tractorLabel}
Duration: $durationStr
Total Amount: ₹$totalStr
Paid: ₹$paidStr
Due: ₹$dueStr
Status: $statusStr

Operator: ${job.operatorName}
Business: $businessName
""".trimIndent()
    }
}

fun buildCustomerDueWhatsAppMessage(customer: CustomerEntity, businessName: String, isTamil: Boolean = false): String {
    val dateStr = formatDate(
        if (customer.updatedAt > 0) customer.updatedAt
        else if (customer.createdAt > 0) customer.createdAt
        else System.currentTimeMillis()
    )
    val dueStr = formatAmountText(customer.balanceDue)
    val statusStr = if (isTamil) {
        if (customer.balanceDue <= 0.0) "செலுத்தப்பட்டது" else "நிலுவை"
    } else {
        if (customer.balanceDue <= 0.0) "Paid" else "Pending"
    }

    return if (isTamil) {
        """
வணக்கம்! 🙏 இதோ ரசீது விவரங்கள்:

வாடிக்கையாளர்: ${customer.name}
தேதி: $dateStr
மொத்த நிலுவைத் தொகை: ₹$dueStr
நிலை: $statusStr

வணிகம்: $businessName
""".trimIndent()
    } else {
        """
Vanakam! 🙏 Here is the bill details:

Customer: ${customer.name}
Date: $dateStr
Total Balance Due: ₹$dueStr
Status: $statusStr

Business: $businessName
""".trimIndent()
    }
}

fun buildPaymentRecordWhatsAppMessage(
    customerName: String,
    dateMillis: Long,
    amount: Double,
    status: String,
    businessName: String,
    isTamil: Boolean = false
): String {
    val dateStr = formatDate(dateMillis)
    val amountStr = formatAmountText(amount)
    val statusStr = if (isTamil) {
        if (status.equals("Paid", ignoreCase = true)) "செலுத்தப்பட்டது"
        else if (status.equals("Pending", ignoreCase = true)) "நிலுவை"
        else status
    } else status

    return if (isTamil) {
        """
வணக்கம்! 🙏 இதோ ரசீது விவரங்கள்:

வாடிக்கையாளர்: $customerName
தேதி: $dateStr
தொகை: ₹$amountStr
நிலை: $statusStr

வணிகம்: $businessName
""".trimIndent()
    } else {
        """
Vanakam! 🙏 Here is the bill details:

Customer: $customerName
Date: $dateStr
Amount: ₹$amountStr
Status: $status

Business: $businessName
""".trimIndent()
    }
}

fun buildExpenseWhatsAppMessage(expense: ExpenseEntity, businessName: String, isTamil: Boolean = false): String {
    val dateStr = formatDate(expense.dateTimestamp)
    val amountStr = formatAmountText(expense.amount)
    val isDiesel = expense.expenseType.contains("Diesel", ignoreCase = true) ||
            expense.description.contains("Diesel", ignoreCase = true)

    val descText = if (expense.description.isNotBlank()) {
        "${expense.expenseType} - ${expense.description}"
    } else {
        expense.expenseType
    }

    val sb = StringBuilder()
    if (isTamil) {
        sb.append("வணக்கம்! 🙏 இதோ ரசீது விவரங்கள்:\n\n")
        sb.append("விவரம்: $descText\n")
        sb.append("தொகை: ₹$amountStr\n")
        if (isDiesel) {
            sb.append("டீசல்: ஆம் (${expense.expenseType})\n")
        }
        sb.append("தேதி: $dateStr\n")
        if (expense.tractorLabel.isNotBlank()) {
            sb.append("டிராக்டர்: ${expense.tractorLabel}\n")
        }
        if (expense.operatorName.isNotBlank()) {
            sb.append("இயக்குநர்: ${expense.operatorName}\n")
        }
        sb.append("\nவணிகம்: $businessName")
    } else {
        sb.append("Vanakam! 🙏 Here is the bill details:\n\n")
        sb.append("Description: $descText\n")
        sb.append("Amount: ₹$amountStr\n")
        if (isDiesel) {
            sb.append("Diesel: Yes (${expense.expenseType})\n")
        }
        sb.append("Date: $dateStr\n")
        if (expense.tractorLabel.isNotBlank()) {
            sb.append("Tractor: ${expense.tractorLabel}\n")
        }
        if (expense.operatorName.isNotBlank()) {
            sb.append("Operator: ${expense.operatorName}\n")
        }
        sb.append("\nBusiness: $businessName")
    }

    return sb.toString().trim()
}

fun buildWithdrawalWhatsAppMessage(withdrawal: WithdrawalEntity, businessName: String, isTamil: Boolean = false): String {
    val dateStr = formatDate(withdrawal.timestamp)
    val amountStr = formatAmountText(withdrawal.amount)

    val sb = StringBuilder()
    if (isTamil) {
        sb.append("வணக்கம்! 🙏 இதோ ரசீது விவரங்கள்:\n\n")
        sb.append("பங்குதாரர்: ${withdrawal.partnerName}\n")
        sb.append("வகை: ${withdrawal.category}\n")
        sb.append("தொகை: ₹$amountStr\n")
        sb.append("தேதி: $dateStr\n")
        if (withdrawal.note.isNotBlank()) {
            sb.append("குறிப்பு: ${withdrawal.note}\n")
        }
        sb.append("\nவணிகம்: $businessName")
    } else {
        sb.append("Vanakam! 🙏 Here is the bill details:\n\n")
        sb.append("Partner: ${withdrawal.partnerName}\n")
        sb.append("Category: ${withdrawal.category}\n")
        sb.append("Amount: ₹$amountStr\n")
        sb.append("Date: $dateStr\n")
        if (withdrawal.note.isNotBlank()) {
            sb.append("Note: ${withdrawal.note}\n")
        }
        sb.append("\nBusiness: $businessName")
    }

    return sb.toString().trim()
}

fun buildWhatsAppUrl(phoneNumber: String?, message: String = ""): String {
    val formattedPhone = formatWhatsAppPhone(phoneNumber)
    val encodedMessage = if (message.isNotBlank()) {
        try {
            java.net.URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            Uri.encode(message)
        }
    } else ""

    return if (formattedPhone.isNotBlank()) {
        if (encodedMessage.isNotBlank()) {
            "https://wa.me/$formattedPhone?text=$encodedMessage"
        } else {
            "https://wa.me/$formattedPhone"
        }
    } else {
        if (encodedMessage.isNotBlank()) {
            "https://wa.me/?text=$encodedMessage"
        } else {
            "https://wa.me/"
        }
    }
}

fun sendWhatsAppMessage(context: Context, phoneNumber: String?, message: String) {
    val formattedPhone = formatWhatsAppPhone(phoneNumber)
    val encodedMessage = if (message.isNotBlank()) {
        try {
            java.net.URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            Uri.encode(message)
        }
    } else ""

    val url = if (formattedPhone.isNotBlank()) {
        if (encodedMessage.isNotBlank()) "https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedMessage"
        else "https://api.whatsapp.com/send?phone=$formattedPhone"
    } else {
        if (encodedMessage.isNotBlank()) "https://api.whatsapp.com/send?text=$encodedMessage"
        else "https://api.whatsapp.com/send"
    }

    try {
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(waIntent)
    } catch (e1: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            shareGenericText(context, message, "Share Message")
        }
    }
}

fun sendSmsMessage(context: Context, phoneNumber: String?, message: String) {
    try {
        val cleanPhone = sanitizePhoneNumberForStorage(phoneNumber)
        val uri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:$cleanPhone") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        shareGenericText(context, message, "Share Message")
    }
}

fun shareGenericText(context: Context, text: String, title: String = "Share Message") {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun openDialer(context: Context, phoneNumber: String?) {
    if (phoneNumber.isNullOrBlank()) {
        Toast.makeText(context, "No phone number available to call", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val cleanPhone = phoneNumber.replace(" ", "").replace("-", "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Could not open dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun openWhatsApp(
    context: Context,
    phoneNumber: String?,
    message: String = "",
    onNoPhone: (() -> Unit)? = null
) {
    val formattedPhone = formatWhatsAppPhone(phoneNumber)
    if (formattedPhone.isBlank() && onNoPhone != null) {
        onNoPhone()
        return
    }
    sendWhatsAppMessage(context, phoneNumber, message)
}

@Composable
fun AppTopHeader(
    title: String = "AIDHUNT Trac Services",
    showBack: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    settings: AppSettingsEntity,
    partners: List<PartnerEntity> = emptyList(),
    isSyncing: Boolean,
    isOnline: Boolean = true,
    totalUnsyncedCount: Int = 0,
    onSyncClick: () -> Unit = {},
    onPartnerSelected: (PartnerEntity) -> Unit = {},
    rightActionIcon: ImageVector? = null,
    onRightActionClick: (() -> Unit)? = null,
    isDarkGreenStyle: Boolean = true
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    val infiniteTransition = rememberInfiniteTransition(label = "sync_animations")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val flashGlowProgress by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flashGlowProgress"
    )

    val headerBgColor = if (isDarkGreenStyle) Color(0xFF072D18) else AppTheme.colors.surface
    val headerTextColor = if (isDarkGreenStyle) Color.White else AppTheme.colors.textPrimary
    val headerIconColor = if (isDarkGreenStyle) Color.White else AppTheme.colors.textPrimary

    Surface(
        color = headerBgColor,
        shadowElevation = if (isDarkGreenStyle) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (responsive.isSmallPhone) 54.dp else 58.dp)
                    .padding(horizontal = if (responsive.isSmallPhone) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button (if enabled) and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 4.dp else 8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (showBack && onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("top_app_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = headerIconColor
                            )
                        }
                    }

                    Text(
                        text = title,
                        fontSize = if (responsive.isSmallPhone) 17.sp else if (responsive.isLargePhone) 20.sp else 18.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Sync status / Refresh & Profile avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 4.dp else 6.dp)
                ) {
                    // Sync Status Pill / Badge with animated left-to-right flash glow effect
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isDarkGreenStyle -> Color(0x33FFFFFF)
                            isSyncing -> Color(0xFFE8F5E9)
                            !isOnline -> AlertDueRedBg
                            totalUnsyncedCount > 0 -> SoftSageGreen.copy(alpha = 0.5f)
                            else -> if (AppTheme.colors.isDark) AppTheme.colors.cardBg else Color(0xFFF1F7F3)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSyncClick() }
                            .testTag("sync_status_badge")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(
                                    horizontal = if (responsive.isSmallPhone) 7.dp else 9.dp,
                                    vertical = if (responsive.isSmallPhone) 4.dp else 5.dp
                                )
                            ) {
                                // Pulsing / Status Dot
                                Box(
                                    modifier = Modifier
                                        .size(if (responsive.isSmallPhone) 6.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isDarkGreenStyle -> if (isSyncing) Color(0xFF81C784).copy(alpha = pulseAlpha) else Color(0xFFA5D6A7)
                                                isSyncing -> DeepSageGreen.copy(alpha = pulseAlpha)
                                                !isOnline -> AlertDueRed
                                                totalUnsyncedCount > 0 -> DeepSageGreen.copy(alpha = pulseAlpha)
                                                else -> SuccessPaidGreen
                                            }
                                        )
                                )

                                // Status Icon with spin animation when syncing
                                Icon(
                                    imageVector = when {
                                        isSyncing -> Icons.Default.Sync
                                        !isOnline -> Icons.Default.CloudOff
                                        totalUnsyncedCount > 0 -> Icons.Default.CloudUpload
                                        else -> Icons.Default.CloudDone
                                    },
                                    contentDescription = if (isTamil) "ஒத்திசைவு" else "Sync Status",
                                    tint = when {
                                        isDarkGreenStyle -> Color.White
                                        isSyncing -> DeepSageGreen
                                        !isOnline -> AlertDueRed
                                        totalUnsyncedCount > 0 -> DeepSageGreen
                                        else -> SuccessPaidGreen
                                    },
                                    modifier = Modifier
                                        .size(if (responsive.isSmallPhone) 13.dp else 15.dp)
                                        .let { m -> if (isSyncing) m.rotate(rotation) else m }
                                )

                                Text(
                                    text = when {
                                        isSyncing -> if (isTamil) "ஒத்திசை..." else "Syncing..."
                                        !isOnline -> if (isTamil) "ஆஃப்லைன் ($totalUnsyncedCount)" else "Offline ($totalUnsyncedCount)"
                                        totalUnsyncedCount > 0 -> if (isTamil) "$totalUnsyncedCount நிலுவை" else "$totalUnsyncedCount Pending"
                                        else -> if (responsive.isSmallPhone) (if (isTamil) "சரி" else "Synced") else (if (isTamil) "ஒத்திசைந்தது" else "Synced")
                                    },
                                    fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkGreenStyle) Color.White else when {
                                        isSyncing -> DeepSageGreen
                                        !isOnline -> AlertDueRed
                                        totalUnsyncedCount > 0 -> DeepSageGreen
                                        else -> AppTheme.colors.textPrimary
                                    }
                                )
                            }

                            if (isSyncing) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val w = size.width
                                    val startX = flashGlowProgress * w
                                    val endX = startX + w * 0.75f
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.2f),
                                                Color(0xFF4ADE80).copy(alpha = 0.6f),
                                                Color.White.copy(alpha = 0.85f),
                                                Color(0xFF4ADE80).copy(alpha = 0.6f),
                                                Color.White.copy(alpha = 0.2f),
                                                Color.Transparent
                                            ),
                                            startX = startX,
                                            endX = endX
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (rightActionIcon != null && onRightActionClick != null) {
                        IconButton(
                            onClick = onRightActionClick,
                            modifier = Modifier
                                .size(if (responsive.isSmallPhone) 36.dp else 40.dp)
                                .testTag("top_right_action_btn")
                        ) {
                            Icon(
                                imageVector = rightActionIcon,
                                contentDescription = "Action",
                                tint = headerIconColor,
                                modifier = Modifier.size(if (responsive.isSmallPhone) 22.dp else 24.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (responsive.isSmallPhone) 36.dp else 40.dp)
                                .testTag("top_profile_icon"),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDarkGreenStyle) Color(0x33FFFFFF) else SoftSageGreen,
                                modifier = Modifier.size(if (responsive.isSmallPhone) 32.dp else 36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = settings.activePartnerName.take(1).uppercase(),
                                        fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkGreenStyle) Color.White else DeepSageGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSyncing) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = if (isDarkGreenStyle) Color(0xFF81C784) else DeepSageGreen,
                    trackColor = if (isDarkGreenStyle) Color(0x33FFFFFF) else SoftSageGreen.copy(alpha = 0.4f)
                )
            } else if (!isDarkGreenStyle) {
                HorizontalDivider(color = AppTheme.colors.cardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)
            }
        }
    }
}

data class PieChartSlice(
    val name: String,
    val value: Double,
    val color: Color,
    val subText: String = ""
)

val ChartPalette = listOf(
    Color(0xFF2C5E43), // Deep Sage / Forest
    Color(0xFFD4A373), // Warm Sand / Gold
    Color(0xFF709775), // Soft Sage
    Color(0xFF415A77), // Slate Blue
    Color(0xFFE07A5F), // Terracotta
    Color(0xFF81B29A)  // Light Sage
)

@Composable
fun PartnerWithdrawalPieChart(
    slices: List<PieChartSlice>,
    totalAmount: Double,
    modifier: Modifier = Modifier,
    isTamil: Boolean = false
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.6f))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(if (responsive.isSmallPhone) 12.dp else 16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = if (isTamil) "பங்குதாரர் எடுப்பு பகிர்வு" else "Partner Withdrawal Distribution",
                fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 10.dp else 14.dp))

            if (totalAmount <= 0 || slices.all { it.value <= 0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTamil) "எடுப்பு பதிவுகள் எதுவும் இல்லை" else "No withdrawal records matching current filter",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                if (responsive.isSmallPhone) {
                    // On small screen (<360dp), layout Donut and Legend vertically to prevent horizontal crowding
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Donut Canvas
                        Box(
                            modifier = Modifier.size(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(100.dp)) {
                                val strokeWidth = 20.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                var startAngle = -90f
                                slices.filter { it.value > 0 }.forEach { slice ->
                                    val sweepAngle = (slice.value / totalAmount).toFloat() * 360f
                                    drawArc(
                                        color = slice.color,
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                    startAngle += sweepAngle
                                }
                            }

                            // Center total
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isTamil) "மொத்தம்" else "Total",
                                    fontSize = 9.sp,
                                    color = AppTheme.colors.textMuted
                                )
                                Text(
                                    text = formatInr(totalAmount),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }

                        // Legend list
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            slices.forEach { slice ->
                                val percentage = if (totalAmount > 0) (slice.value / totalAmount * 100) else 0.0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(slice.color)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = slice.name,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AppTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f%%", percentage),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = slice.color
                                            )
                                        }
                                        Text(
                                            text = formatInr(slice.value) + if (slice.subText.isNotBlank()) " • ${slice.subText}" else "",
                                            fontSize = 10.5.sp,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Standard and Large screens: Side by Side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Donut Canvas
                        Box(
                            modifier = Modifier.size(if (responsive.isLargePhone) 140.dp else 125.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(if (responsive.isLargePhone) 128.dp else 114.dp)) {
                                val strokeWidth = 22.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)

                                var startAngle = -90f
                                slices.filter { it.value > 0 }.forEach { slice ->
                                    val sweepAngle = (slice.value / totalAmount).toFloat() * 360f
                                    drawArc(
                                        color = slice.color,
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                    startAngle += sweepAngle
                                }
                            }

                            // Center total
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isTamil) "மொத்தம்" else "Total",
                                    fontSize = 10.sp,
                                    color = AppTheme.colors.textMuted
                                )
                                Text(
                                    text = formatInr(totalAmount),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }

                        // Legend list
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            slices.forEach { slice ->
                                val percentage = if (totalAmount > 0) (slice.value / totalAmount * 100) else 0.0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(slice.color)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = slice.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AppTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f%%", percentage),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = slice.color
                                            )
                                        }
                                        Text(
                                            text = formatInr(slice.value) + if (slice.subText.isNotBlank()) " • ${slice.subText}" else "",
                                            fontSize = 11.sp,
                                            color = AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleFilterCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    activeFiltersCount: Int,
    onClearFilters: () -> Unit,
    onApplyFilters: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isTamil: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isExpanded) AppTheme.colors.cardBg else AppTheme.colors.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.8f))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = DeepSageGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isTamil) "வடிகட்டுதல்" else "Filter Records",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary
                    )
                    if (activeFiltersCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SoftSageGreen
                        ) {
                            Text(
                                text = if (isTamil) "$activeFiltersCount செயலில்" else "$activeFiltersCount active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeFiltersCount > 0 && !isExpanded) {
                        TextButton(
                            onClick = onClearFilters,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (isTamil) "மீட்டமை" else "Reset", fontSize = 11.sp, color = AlertDueRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = DeepSageGreen
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()

                    // Action buttons: Reset & Apply Filters
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppTheme.colors.cardBorder.copy(alpha = 0.5f))
                            .padding(vertical = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onClearFilters,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed)
                        ) {
                            Text(if (isTamil) "மீட்டமை / அனைத்தையும் நீக்கு" else "Reset / Clear All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (onApplyFilters != null) {
                            Button(
                                onClick = onApplyFilters,
                                modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isTamil) "வடிகட்டியைப் பயன்படுத்து" else "Apply Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    amount: Double,
    subtitle: String? = null,
    secondaryLabel: String? = null,
    secondaryAmount: Double? = null,
    tertiaryLabel: String? = null,
    tertiaryAmount: Double? = null,
    icon: ImageVector,
    containerColor: Color = SageCardBg,
    contentColor: Color = DeepSageGreen,
    titleColor: Color? = null,
    modifier: Modifier = Modifier,
    isNegative: Boolean = false
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (containerColor == SageCardBg) AppTheme.colors.cardBg else containerColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.6f))),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(if (responsive.isSmallPhone) 10.dp else 14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = if (responsive.isSmallPhone) 11.5.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor ?: Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 24.dp else 28.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(if (responsive.isSmallPhone) 14.dp else 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 6.dp else 8.dp))

            Text(
                text = formatInr(amount),
                fontSize = if (responsive.isSmallPhone) 16.sp else if (responsive.isLargePhone) 22.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNegative && amount > 0) AlertDueRed else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (secondaryLabel != null && secondaryAmount != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = secondaryLabel,
                            fontSize = if (responsive.isSmallPhone) 9.sp else 10.sp,
                            color = (titleColor ?: contentColor).copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = formatInr(secondaryAmount),
                            fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNegative && secondaryAmount > 0) AlertDueRed else contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (tertiaryLabel != null && tertiaryAmount != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tertiaryLabel,
                                fontSize = if (responsive.isSmallPhone) 9.sp else 10.sp,
                                color = (titleColor ?: contentColor).copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = formatInr(tertiaryAmount),
                                fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNegative && tertiaryAmount > 0) AlertDueRed else contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    isPaid: Boolean,
    pendingAmount: Double = 0.0,
    isTamil: Boolean = false,
    isOldEntry: Boolean = false
) {
    val isOldAndDue = isOldEntry && !isPaid
    val bg = when {
        isPaid -> SuccessPaidGreenBg
        isOldAndDue -> Color(0xFF7F1D1D)
        else -> AlertDueRedBg
    }
    val textCol = when {
        isPaid -> SuccessPaidGreen
        isOldAndDue -> Color(0xFFFEF08A)
        else -> AlertDueRed
    }
    val border = if (isOldAndDue) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)) else null
    val label = if (isPaid) {
        if (isTamil) "செலுத்தப்பட்டது" else "PAID"
    } else {
        if (isTamil) "நிலுவை: ${formatInr(pendingAmount)}" else "DUE: ${formatInr(pendingAmount)}"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = border,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            if (isOldAndDue) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFFCD34D), CircleShape)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    labelColor: Color = AppTheme.colors.textSecondary,
    valueColor: Color = AppTheme.colors.textPrimary,
    isBoldValue: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = labelColor,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (isBoldValue) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppBottomNav(
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    isTamil: Boolean = false
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isNewEntrySelected = currentTab == BottomTab.NEW_ENTRY

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color(0xFF072D18),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .height(68.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            BottomNavItem(
                icon = Icons.Default.Home,
                label = if (isTamil) "முகப்பு" else "Home",
                isSelected = currentTab == BottomTab.HOME,
                testTag = "nav_home",
                onClick = { onTabSelected(BottomTab.HOME) },
                modifier = Modifier.weight(1f)
            )

            // 2. Reports
            BottomNavItem(
                icon = Icons.Default.BarChart,
                label = if (isTamil) "அறிக்கைகள்" else "Reports",
                isSelected = currentTab == BottomTab.REPORT,
                testTag = "nav_report",
                onClick = { onTabSelected(BottomTab.REPORT) },
                modifier = Modifier.weight(1f)
            )

            // 3. New Entry
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(BottomTab.NEW_ENTRY) }
                    .padding(vertical = 2.dp)
                    .testTag("nav_new_job"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 36.dp else 38.dp)
                        .clip(CircleShape)
                        .background(
                            brush = if (isNewEntrySelected) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF4ADE80),
                                        Color(0xFF16A34A)
                                    )
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x33FFFFFF),
                                        Color(0x1AFFFFFF)
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = if (isTamil) "புதிய பதிவு" else "New Entry",
                        tint = if (isNewEntrySelected) Color(0xFF072D18) else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(if (responsive.isSmallPhone) 22.dp else 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = if (isTamil) "புதிய பதிவு" else "New Entry",
                    fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                    fontWeight = if (isNewEntrySelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isNewEntrySelected) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 4. Account
            BottomNavItem(
                icon = Icons.Default.Person,
                label = if (isTamil) "கணக்கு" else "Account",
                isSelected = currentTab == BottomTab.ACCOUNT,
                testTag = "nav_account",
                onClick = { onTabSelected(BottomTab.ACCOUNT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val activeColor = Color(0xFF4ADE80)
    val inactiveColor = Color.White.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(if (responsive.isSmallPhone) 22.dp else 24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StartupSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF072D18)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF166534),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = "AIDHUNT Trac",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Text(
                text = "AIDHUNT Trac Services",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color(0xFF4ADE80),
                strokeWidth = 2.5.dp
            )
        }
    }
}

@Composable
fun PdfOptionsDialog(
    title: String,
    subtitle: String? = null,
    isTamil: Boolean = false,
    onSharePdf: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or denied, download proceeds regardless
        onDismiss()
        onDownloadPdf()
    }

    val handleDownloadClick = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDismiss()
            onDownloadPdf()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = AlertDueRed,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = TextMutedDark
                    )
                }

                // Option 1: Share PDF
                Surface(
                    onClick = {
                        onDismiss()
                        onSharePdf()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = SageCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_pdf_option_share")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ForestGreenHeader.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = ForestGreenHeader,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTamil) "PDF பகிர்க (Share)" else "Share PDF",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isTamil) "வாட்ஸ்அப் அல்லது பிற செயலிகளில் அனுப்ப" else "Send via WhatsApp, Email, etc.",
                                fontSize = 12.sp,
                                color = TextMutedDark
                            )
                        }
                    }
                }

                // Option 2: Download / Save PDF
                Surface(
                    onClick = handleDownloadClick,
                    shape = RoundedCornerShape(12.dp),
                    color = SageCardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_pdf_option_download")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SuccessPaidGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = SuccessPaidGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTamil) "பதிவிறக்கம் செய்க (Save)" else "Download / Save PDF",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isTamil) "மொபைல் பதிவிறக்கங்கள் கோப்புறையில் சேமிக்க" else "Save PDF directly to device Downloads",
                                fontSize = 12.sp,
                                color = TextMutedDark
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_pdf_options_cancel")
            ) {
                Text(
                    text = if (isTamil) "ரத்து செய்" else "Cancel",
                    color = TextMutedDark,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
