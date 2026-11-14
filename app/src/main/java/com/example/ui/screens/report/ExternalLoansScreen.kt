package com.example.ui.screens.report

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.ui.components.LoanAnalyticsCard
import com.example.ui.components.formatInr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ExternalLoansScreen(
    settings: AppSettingsEntity,
    loans: List<ExternalLoanEntity>,
    repayments: List<ExternalLoanRepaymentEntity>,
    availableCash: Double,
    canManageLoans: Boolean,
    onRecordLoan: (
        lenderName: String,
        principal: Double,
        interest: Double,
        destination: String,
        note: String,
        loanDateMillis: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onRecordRepayment: (
        loanId: Long,
        principalPaid: Double,
        interestPaid: Double,
        source: String,
        note: String,
        repaidAt: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var statusFilter by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var repaymentTargetLoan by remember { mutableStateOf<ExternalLoanEntity?>(null) }
    val dateFormat = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val activeLoans = loans.filter { it.status != "SETTLED" }
    val totalOutstandingPrincipal = activeLoans.sumOf { it.outstandingPrincipal }
    val totalOutstandingInterest = activeLoans.sumOf { it.outstandingInterest }
    val filteredLoans = when (statusFilter) {
        "ACTIVE" -> loans.filter { it.status != "SETTLED" }
        "SETTLED" -> loans.filter { it.status == "SETTLED" }
        else -> loans
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F6))
    ) {
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTamil) "வெளிப்புற கடன்கள்" else "External Business Loans",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (isTamil) "அசல் & வட்டி தனித்தனியாக கண்காணிப்பு" else "Separate principal & interest tracking",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
                if (canManageLoans) {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_add_external_loan")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isTamil) "கடன் சேர்" else "Add Loan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LoanStatBox(
                        modifier = Modifier.weight(1f),
                        label = if (isTamil) "மீதமுள்ள அசல்" else "Principal Due",
                        value = formatInr(totalOutstandingPrincipal, settings.currency),
                        bg = Color(0xFFFFFBEB),
                        accent = Color(0xFFB45309)
                    )
                    LoanStatBox(
                        modifier = Modifier.weight(1f),
                        label = if (isTamil) "மீதமுள்ள வட்டி" else "Interest Due",
                        value = formatInr(totalOutstandingInterest, settings.currency),
                        bg = Color(0xFFFEF2F2),
                        accent = Color(0xFFDC2626)
                    )
                }
            }

            item {
                LoanAnalyticsCard(
                    loans = loans,
                    repayments = repayments,
                    isTamil = isTamil,
                    currency = settings.currency
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "ALL" to (if (isTamil) "அனைத்தும் (${loans.size})" else "All (${loans.size})"),
                        "ACTIVE" to (if (isTamil) "செயலில் (${activeLoans.size})" else "Active (${activeLoans.size})"),
                        "SETTLED" to (if (isTamil) "முடிந்தது (${loans.count { it.status == "SETTLED" }})" else "Settled (${loans.count { it.status == "SETTLED" }})")
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (filteredLoans.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isTamil) "வெளிப்புற கடன் பதிவுகள் இல்லை" else "No external loans recorded",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = if (isTamil) "புதிய கடனைப் பதிவு செய்ய மேலே உள்ள பொத்தானைத் தட்டவும்" else "Tap Add Loan to record a business loan",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            } else {
                items(filteredLoans, key = { it.id }) { loan ->
                    val loanRepayments = repayments.filter { it.loanId == loan.id }
                    ExternalLoanCard(
                        loan = loan,
                        repayments = loanRepayments,
                        currency = settings.currency,
                        isTamil = isTamil,
                        canManageLoans = canManageLoans,
                        dateLabel = dateFormat.format(Date(loan.loanDateMillis)),
                        dateFormat = dateFormat,
                        onRepayClick = { repaymentTargetLoan = loan }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AddExternalLoanDialog(
            isTamil = isTamil,
            onDismiss = { showCreateDialog = false },
            onSave = { lender, principal, interest, dest, note, dateMillis, onErr ->
                onRecordLoan(lender, principal, interest, dest, note, dateMillis, { showCreateDialog = false }, onErr)
            }
        )
    }

    repaymentTargetLoan?.let { target ->
        RepayExternalLoanDialog(
            loan = target,
            currency = settings.currency,
            availableCash = availableCash,
            isTamil = isTamil,
            onDismiss = { repaymentTargetLoan = null },
            onSubmit = { principalPaid, interestPaid, source, note, paidAt, onErr ->
                onRecordRepayment(target.id, principalPaid, interestPaid, source, note, paidAt, { repaymentTargetLoan = null }, onErr)
            }
        )
    }
}

@Composable
private fun LoanStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    bg: Color,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.5.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun ExternalLoanCard(
    loan: ExternalLoanEntity,
    repayments: List<ExternalLoanRepaymentEntity>,
    currency: String,
    isTamil: Boolean,
    canManageLoans: Boolean,
    dateLabel: String,
    dateFormat: SimpleDateFormat,
    onRepayClick: () -> Unit
) {
    val destLabel = when (loan.destination) {
        "BUSINESS_CASH" -> if (isTamil) "வணிக ரொக்கம்" else "Business Cash"
        "BUSINESS_BANK" -> if (isTamil) "வணிக வங்கி" else "Business Bank"
        "DIRECT_TO_SUPPLIER" -> if (isTamil) "விற்பனையாளருக்கு நேரடி" else "Direct to Supplier"
        else -> loan.destination
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("external_loan_card_${loan.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(loan.lenderName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text("$dateLabel · $destLabel", fontSize = 11.5.sp, color = Color(0xFF64748B))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (loan.status == "SETTLED") Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (loan.status == "SETTLED") (if (isTamil) "தீர்க்கப்பட்டது" else "Settled") else (if (isTamil) "நிலுவை" else "Active"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (loan.status == "SETTLED") Color(0xFF15803D) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isTamil) "அசல்" else "Principal", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(formatInr(loan.principalAmount, currency), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                    Text(
                        text = (if (isTamil) "மீதம்: " else "Left: ") + formatInr(loan.outstandingPrincipal, currency),
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isTamil) "வட்டி" else "Interest", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(formatInr(loan.interestAmount, currency), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                    Text(
                        text = (if (isTamil) "மீதம்: " else "Left: ") + formatInr(loan.outstandingInterest, currency),
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626)
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (isTamil) "மொத்த நிலுவை" else "Outstanding", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(formatInr(loan.totalOutstanding, currency), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF072D18))
                }
            }

            if (loan.destination == "DIRECT_TO_SUPPLIER") {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (loan.linkedExpenseId > 0L) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (loan.linkedExpenseId > 0L) {
                            if (isTamil) "இணைக்கப்பட்ட செலவு #${loan.linkedExpenseId}" else "Linked Expense #${loan.linkedExpenseId}"
                        } else {
                            if (isTamil) "இணைக்கப்படவில்லை — செலவுகள் பக்கத்தில் 'கடன் நிதி' மூலம் இணைக்கலாம்"
                            else "Unlinked — select 'Loan Funded' in Expenses to link"
                        },
                        fontSize = 11.sp,
                        color = if (loan.linkedExpenseId > 0L) Color(0xFF15803D) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (loan.note.isNotBlank()) {
                Text(loan.note, fontSize = 11.5.sp, color = Color(0xFF64748B), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (repayments.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Text(
                    text = if (isTamil) "சமீபத்திய திருப்பிச் செலுத்தல்கள் (${repayments.size})" else "Repayments (${repayments.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                repayments.take(3).forEach { rep ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${dateFormat.format(Date(rep.repaidAt))} · ${if (rep.source == "BUSINESS_BANK") (if (isTamil) "வங்கி" else "Bank") else (if (isTamil) "ரொக்கம்" else "Cash")}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "P: ${formatInr(rep.principalPaid, currency)} + I: ${formatInr(rep.interestPaid, currency)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            if (canManageLoans && loan.status != "SETTLED" && loan.totalOutstanding > 0.0) {
                Button(
                    onClick = onRepayClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_repay_loan_${loan.id}")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isTamil) "திருப்பிச் செலுத்துக" else "Record Repayment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AddExternalLoanDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, String, String, Long, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var lenderName by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var interestText by remember { mutableStateOf("0") }
    var destination by remember { mutableStateOf("BUSINESS_CASH") }
    var note by remember { mutableStateOf("") }
    var loanDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var errorMsg by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Text(if (isTamil) "வெளிப்புற கடன் பதிவு" else "Record External Loan", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = lenderName,
                    onValueChange = { lenderName = it; errorMsg = "" },
                    label = { Text(if (isTamil) "கடன் வழங்கியவர்*" else "Lender Name*") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_loan_lender")
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "அசல் தொகை (₹)*" else "Principal Amount (₹)*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_loan_principal")
                )
                OutlinedTextField(
                    value = interestText,
                    onValueChange = { interestText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "வட்டி தொகை (₹)" else "Interest Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_loan_interest")
                )
                Text(if (isTamil) "நிதி சேருமிடம்" else "Loan Destination", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                listOf(
                    "BUSINESS_CASH" to (if (isTamil) "வணிக ரொக்கம்" else "Business Cash"),
                    "BUSINESS_BANK" to (if (isTamil) "வணிக வங்கி" else "Business Bank"),
                    "DIRECT_TO_SUPPLIER" to (if (isTamil) "விற்பனையாளருக்கு நேரடி" else "Direct to Supplier")
                ).forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (destination == key) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (destination == key) Color(0xFF0B532E) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { destination = key }
                            .testTag("loan_dest_${key.lowercase()}")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = destination == key, onClick = { destination = key })
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = loanDateMillis }
                        DatePickerDialog(context, { _, y, m, d ->
                            loanDateMillis = Calendar.getInstance().apply { set(y, m, d) }.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(loanDateMillis)))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (isTamil) "குறிப்பு (விருப்பம்)" else "Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                modifier = Modifier.testTag("btn_save_external_loan"),
                onClick = {
                    val principal = principalText.replace(",", "").toDoubleOrNull() ?: 0.0
                    val interest = interestText.replace(",", "").toDoubleOrNull() ?: -1.0
                    if (lenderName.isBlank()) {
                        errorMsg = if (isTamil) "கடன் வழங்கியவர் பெயரை உள்ளிடவும்" else "Enter lender name"
                    } else if (principal <= 0.0) {
                        errorMsg = if (isTamil) "அசல் தொகை ₹0 விட அதிகமாக இருக்க வேண்டும்" else "Principal must be greater than ₹0"
                    } else if (interest < 0.0) {
                        errorMsg = if (isTamil) "வட்டி ₹0 அல்லது அதற்கு மேல் இருக்க வேண்டும்" else "Interest must be ₹0 or more"
                    } else {
                        submitting = true
                        onSave(lenderName, principal, interest, destination, note, loanDateMillis) { err ->
                            submitting = false
                            errorMsg = err
                        }
                    }
                }
            ) {
                Text(if (isTamil) "சேமி" else "Save Loan")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}

@Composable
private fun RepayExternalLoanDialog(
    loan: ExternalLoanEntity,
    currency: String,
    availableCash: Double,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double, Double, String, String, Long, (String) -> Unit) -> Unit
) {
    var principalText by remember { mutableStateOf("") }
    var interestText by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("BUSINESS_CASH") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Text(if (isTamil) "கடன் திருப்பிச் செலுத்தல்" else "Repay ${loan.lenderName}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isTamil) "மீத அசல்: ${formatInr(loan.outstandingPrincipal, currency)} · மீத வட்டி: ${formatInr(loan.outstandingInterest, currency)}"
                    else "Left Principal: ${formatInr(loan.outstandingPrincipal, currency)} · Left Interest: ${formatInr(loan.outstandingInterest, currency)}",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "அசல் செலுத்துதல் (₹)" else "Principal Paid (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_repay_principal")
                )
                OutlinedTextField(
                    value = interestText,
                    onValueChange = { interestText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "வட்டி செலுத்துதல் (₹)" else "Interest Paid (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_repay_interest")
                )
                Text(if (isTamil) "கட்டண மூலம்" else "Repayment Source", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                listOf(
                    "BUSINESS_CASH" to (if (isTamil) "வணிக ரொக்கம் (இருப்பு: ${formatInr(availableCash, currency)})" else "Business Cash (Avail: ${formatInr(availableCash, currency)})"),
                    "BUSINESS_BANK" to (if (isTamil) "வணிக வங்கி" else "Business Bank")
                ).forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (source == key) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (source == key) Color(0xFF0B532E) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { source = key }
                            .testTag("repay_source_${key.lowercase()}")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = source == key, onClick = { source = key })
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (isTamil) "குறிப்பு (விருப்பம்)" else "Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                modifier = Modifier.testTag("btn_confirm_loan_repayment"),
                onClick = {
                    val pPaid = principalText.ifBlank { "0" }.replace(",", "").toDoubleOrNull() ?: -1.0
                    val iPaid = interestText.ifBlank { "0" }.replace(",", "").toDoubleOrNull() ?: -1.0
                    if (pPaid < 0.0 || iPaid < 0.0 || (pPaid + iPaid) <= 0.0) {
                        errorMsg = if (isTamil) "செல்லுபடியான அசல் அல்லது வட்டி தொகையை உள்ளிடவும்" else "Enter principal or interest > ₹0"
                    } else if (pPaid > loan.outstandingPrincipal + 0.001) {
                        errorMsg = if (isTamil) "அசல் தொகை மீதமுள்ள அசலை விட அதிகம்" else "Exceeds remaining principal"
                    } else if (iPaid > loan.outstandingInterest + 0.001) {
                        errorMsg = if (isTamil) "வட்டி தொகை மீதமுள்ள வட்டியை விட அதிகம்" else "Exceeds remaining interest"
                    } else if (source == "BUSINESS_CASH" && (pPaid + iPaid) > availableCash + 0.001) {
                        errorMsg = if (isTamil) "போதுமான வணிக ரொக்கம் இல்லை" else "Insufficient available business cash"
                    } else {
                        submitting = true
                        onSubmit(pPaid, iPaid, source, note, System.currentTimeMillis()) { err ->
                            submitting = false
                            errorMsg = err
                        }
                    }
                }
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isTamil) "பதிவு செய்" else "Record")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}
