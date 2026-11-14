package com.example.ui.screens.report

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AuthorizationManager
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.SettlementObligationEntity
import com.example.data.entity.SettlementRepaymentEntity
import com.example.ui.components.formatInr
import com.example.ui.util.WorkspaceBorrowingOverview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun PartnerSettlementsScreen(
    settings: AppSettingsEntity,
    isOwner: Boolean,
    currentUid: String,
    currentRole: String,
    borrowingOverview: WorkspaceBorrowingOverview,
    borrowingRequests: List<BorrowingRequestEntity>,
    obligations: List<SettlementObligationEntity>,
    repayments: List<SettlementRepaymentEntity>,
    onApproveRequest: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRejectRequest: (Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onExecuteRequest: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onCancelRequest: (Long, String, () -> Unit, (String) -> Unit) -> Unit,
    onRecordRepayment: (Long, Double, String, String, () -> Unit, (String) -> Unit) -> Unit,
    onConfirmRepayment: (Long, () -> Unit, (String) -> Unit) -> Unit,
    onRejectRepayment: (Long, String, () -> Unit, (String) -> Unit) -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var repaymentTargetOb by remember { mutableStateOf<SettlementObligationEntity?>(null) }
    var actionError by remember { mutableStateOf("") }
    val dateFormat = remember {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
    }

    val activeRequests = borrowingRequests.filter { it.status in setOf("PENDING_APPROVAL", "APPROVED_RESERVED") }
    val pendingConfirmations = repayments.filter { it.status == "PENDING_CONFIRMATION" }
    val openObligations = obligations.filter { it.status != "SETTLED" }
    val settledObligations = obligations.filter { it.status == "SETTLED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F6))
    ) {
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = if (isTamil) "கடன் & தீர்வுகள்" else "Borrowing & Partner Settlements",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (isTamil) "அனுமதி, முன்பதிவு & உறுதிப்படுத்தப்பட்ட திருப்பிச் செலுத்தல்கள்"
                    else "Approvals, cash reservations & recipient-confirmed repayments",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        if (actionError.isNotBlank()) {
            Surface(
                color = Color(0xFFFEF2F2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = actionError,
                    color = Color(0xFFDC2626),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isTamil) "ரொக்கப் பாதுகாப்பு நிலை" else "Cash Protection Summary",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF072D18)
                        )
                        SummaryRow(
                            label = if (isTamil) "கிடைக்கும் ரொக்கம்" else "Confirmed Cash",
                            value = formatInr(borrowingOverview.confirmedCash, settings.currency)
                        )
                        SummaryRow(
                            label = if (isTamil) "ஒப்புதல் முன்பதிவு" else "Reserved Approvals",
                            value = formatInr(borrowingOverview.reservedCash, settings.currency)
                        )
                        SummaryRow(
                            label = if (isTamil) "பாதுகாக்கப்பட்ட பங்கு உரிமைகள்" else "Protected Partner Entitlements",
                            value = formatInr(borrowingOverview.protectedEntitlementsTotal, settings.currency)
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        SummaryRow(
                            label = if (isTamil) "வணிக கடன் வரம்பு" else "Available for Business Borrowing",
                            value = formatInr(borrowingOverview.availableForBusinessBorrowing, settings.currency),
                            bold = true,
                            valueColor = Color(0xFF0B532E)
                        )
                    }
                }
            }

            if (activeRequests.isNotEmpty()) {
                item {
                    Text(
                        text = if (isTamil) "நிலுவையில் உள்ள கடன் கோரிக்கைகள் (${activeRequests.size})" else "Pending Borrowing Requests (${activeRequests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color(0xFF0F172A)
                    )
                }
                items(activeRequests, key = { "req_${it.id}" }) { req ->
                    BorrowingRequestCard(
                        req = req,
                        currency = settings.currency,
                        isTamil = isTamil,
                        isOwner = isOwner,
                        currentUid = currentUid,
                        currentRole = currentRole,
                        onApprove = {
                            actionError = ""
                            onApproveRequest(req.id, {}, { actionError = it })
                        },
                        onReject = {
                            actionError = ""
                            onRejectRequest(req.id, "", {}, { actionError = it })
                        },
                        onExecute = {
                            actionError = ""
                            onExecuteRequest(req.id, {}, { actionError = it })
                        },
                        onCancel = {
                            actionError = ""
                            onCancelRequest(req.id, "", {}, { actionError = it })
                        }
                    )
                }
            }

            if (pendingConfirmations.isNotEmpty()) {
                item {
                    Text(
                        text = if (isTamil) "உறுதிப்படுத்தல் காத்திருக்கும் திருப்பிச் செலுத்தல்கள் (${pendingConfirmations.size})"
                        else "Awaiting Repayment Confirmation (${pendingConfirmations.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color(0xFF0F172A)
                    )
                }
                items(pendingConfirmations, key = { "rep_${it.id}" }) { rep ->
                    val canConfirm = AuthorizationManager.canConfirmSettlementRepayment(rep, currentUid, isOwner, currentRole)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_repayment_card_${rep.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${rep.payerName} → ${rep.creditorName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = repaymentModeLabel(rep.mode, isTamil) + " · " + dateFormat.format(Date(rep.paidAt)),
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Text(formatInr(rep.amount, settings.currency), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFB45309))
                            }
                            if (canConfirm) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { actionError = ""; onConfirmRepayment(rep.id, {}, { actionError = it }) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_confirm_repayment_${rep.id}")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isTamil) "உறுதிசெய்" else "Confirm")
                                    }
                                    OutlinedButton(
                                        onClick = { actionError = ""; onRejectRepayment(rep.id, "", {}, { actionError = it }) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_reject_repayment_${rep.id}")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isTamil) "நிராகரி" else "Reject", color = Color(0xFFDC2626))
                                    }
                                }
                            } else {
                                Text(
                                    text = if (rep.mode == "DIRECT_TO_PARTNER") {
                                        if (isTamil) "${rep.creditorName} உறுதிப்படுத்தல் தேவை" else "Awaiting ${rep.creditorName}'s confirmation"
                                    } else {
                                        if (isTamil) "உறுதிப்படுத்தல் நிலுவையில்" else "Pending confirmation"
                                    },
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = if (isTamil) "திறந்த கடன் கடமைகள் (${openObligations.size})" else "Open Settlement Obligations (${openObligations.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = Color(0xFF0F172A)
                )
            }

            if (openObligations.isEmpty()) {
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
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Handshake, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isTamil) "திறந்த கடன் கடமைகள் இல்லை" else "No open settlement obligations",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            } else {
                items(openObligations, key = { "ob_open_${it.id}" }) { ob ->
                    ObligationCard(
                        obligation = ob,
                        currency = settings.currency,
                        isTamil = isTamil,
                        canRecord = AuthorizationManager.canRecordSettlementRepayment(ob, currentUid, isOwner, currentRole),
                        onRecordClick = { repaymentTargetOb = ob }
                    )
                }
            }

            if (settledObligations.isNotEmpty()) {
                item {
                    Text(
                        text = if (isTamil) "தீர்க்கப்பட்ட கடன்கள் (${settledObligations.size})" else "Settled Obligations (${settledObligations.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF475569)
                    )
                }
                items(settledObligations, key = { "ob_done_${it.id}" }) { ob ->
                    ObligationCard(
                        obligation = ob,
                        currency = settings.currency,
                        isTamil = isTamil,
                        canRecord = false,
                        onRecordClick = {}
                    )
                }
            }
        }
    }

    repaymentTargetOb?.let { ob ->
        RecordSettlementRepaymentDialog(
            obligation = ob,
            currency = settings.currency,
            isTamil = isTamil,
            onDismiss = { repaymentTargetOb = null },
            onSubmit = { amt, mode, note, onErr ->
                onRecordRepayment(ob.id, amt, mode, note, { repaymentTargetOb = null }, onErr)
            }
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = Color(0xFF0F172A)
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.5.sp, color = Color(0xFF475569), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun ObligationCard(
    obligation: SettlementObligationEntity,
    currency: String,
    isTamil: Boolean,
    canRecord: Boolean,
    onRecordClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settlement_obligation_${obligation.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${obligation.borrowerName} → ${obligation.creditorName}", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(
                        text = if (obligation.creditorType == "BUSINESS") (if (isTamil) "வணிக கடன்" else "Business Borrow") else (if (isTamil) "பங்குதாரர் கடன்" else "Partner Borrow"),
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Text(
                    text = formatInr(obligation.remainingAmount, currency),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (obligation.status == "SETTLED") Color(0xFF15803D) else Color(0xFFDC2626)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (isTamil) "மொத்தம்" else "Borrowed"}: ${formatInr(obligation.principalAmount, currency)}", fontSize = 11.5.sp, color = Color(0xFF475569))
                Text("${if (isTamil) "செலுத்தியது" else "Repaid"}: ${formatInr(obligation.repaidAmount, currency)}", fontSize = 11.5.sp, color = Color(0xFF15803D))
            }
            if (canRecord && obligation.status != "SETTLED") {
                Button(
                    onClick = onRecordClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_record_settlement_repayment_${obligation.id}")
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
private fun RecordSettlementRepaymentDialog(
    obligation: SettlementObligationEntity,
    currency: String,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Double, String, String, (String) -> Unit) -> Unit
) {
    var amountText by remember { mutableStateOf(com.example.ui.util.storedMoneyInput(obligation.remainingAmount)) }
    var mode by remember(obligation) {
        mutableStateOf(if (obligation.creditorType == "BUSINESS") "INTO_BUSINESS" else "INTO_BUSINESS_FOR_PARTNER")
    }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val modes = if (obligation.creditorType == "BUSINESS") {
        listOf("INTO_BUSINESS" to (if (isTamil) "வணிகத்திற்குள் செலுத்து" else "Pay Into Business"))
    } else {
        listOf(
            "INTO_BUSINESS_FOR_PARTNER" to (if (isTamil) "வணிகம் வழியாக பங்குதாரருக்கு" else "Into Business for Partner"),
            "DIRECT_TO_PARTNER" to (if (isTamil) "நேரடியாக பங்குதாரருக்கு" else "Direct to Partner (Non-Cash)")
        )
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (isTamil) "கடன் திருப்பிச் செலுத்தல்" else "Record Settlement Repayment", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${obligation.borrowerName} → ${obligation.creditorName} (${if (isTamil) "மீதம்" else "Remaining"}: ${formatInr(obligation.remainingAmount, currency)})",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "தொகை (₹)*" else "Amount (₹)*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_settlement_repay_amount")
                )
                modes.forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (mode == key) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (mode == key) Color(0xFF0B532E) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { mode = key }
                            .testTag("settlement_mode_${key.lowercase()}")
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == key, onClick = { mode = key })
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
                if (errorMsg.isNotBlank()) Text(errorMsg, color = Color(0xFFDC2626), fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                modifier = Modifier.testTag("btn_submit_settlement_repayment"),
                onClick = {
                    val amt = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        errorMsg = if (isTamil) "செல்லுபடியான தொகையை உள்ளிடவும்" else "Enter an amount greater than ₹0"
                    } else if (amt > obligation.remainingAmount + 0.001) {
                        errorMsg = if (isTamil) "மீதமுள்ள தொகையை விட அதிகம்" else "Exceeds remaining balance"
                    } else {
                        submitting = true
                        onSubmit(amt, mode, note) { err ->
                            submitting = false
                            errorMsg = err
                        }
                    }
                }
            ) {
                Text(if (isTamil) "பதிவு செய்" else "Submit")
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) { Text(if (isTamil) "ரத்து" else "Cancel") }
        }
    )
}

@Composable
fun BorrowingRequestCard(
    req: BorrowingRequestEntity,
    currency: String,
    isTamil: Boolean,
    isOwner: Boolean,
    currentUid: String,
    currentRole: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    val canApprove = AuthorizationManager.canApproveOrRejectBorrowing(req, currentUid, currentRole)
    val canExecute = AuthorizationManager.canExecuteApprovedBorrowing(req, currentUid, isOwner, currentRole)
    val canCancel = AuthorizationManager.canCancelBorrowingRequest(req, currentUid, isOwner, currentRole)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("borrowing_request_card_${req.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${req.borrowerName} ← ${req.lendingPartnerName.ifBlank { if (isTamil) "வணிகம்" else "Business" }}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = if (req.status == "APPROVED_RESERVED") (if (isTamil) "ஒப்புதல் & ரொக்கம் முன்பதிவு" else "Approved & Reserved")
                        else (if (isTamil) "பங்குதாரர் ஒப்புதல் நிலுவை" else "Awaiting Partner Approval"),
                        fontSize = 11.5.sp,
                        color = if (req.status == "APPROVED_RESERVED") Color(0xFF0B532E) else Color(0xFFD97706)
                    )
                }
                Text(formatInr(req.amount, currency), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
            }
            if (req.note.isNotBlank()) Text(req.note, fontSize = 11.5.sp, color = Color(0xFF64748B))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (req.status == "PENDING_APPROVAL" && canApprove) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_approve_borrow_${req.id}")
                    ) {
                        Text(if (isTamil) "ஒப்புதல் & முன்பதிவு" else "Approve & Reserve", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_reject_borrow_${req.id}")
                    ) {
                        Text(if (isTamil) "நிராகரி" else "Reject", color = Color(0xFFDC2626), fontSize = 12.sp)
                    }
                } else if (req.status == "APPROVED_RESERVED" && canExecute) {
                    Button(
                        onClick = onExecute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_execute_borrow_${req.id}")
                    ) {
                        Text(if (isTamil) "பணம் பெறுக" else "Receive Approved", fontSize = 12.sp)
                    }
                }
                if (canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_cancel_borrow_${req.id}")
                    ) {
                        Text(if (isTamil) "ரத்து" else "Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun repaymentModeLabel(mode: String, isTamil: Boolean): String = when (mode) {
    "INTO_BUSINESS" -> if (isTamil) "வணிகத்திற்குள்" else "Into Business"
    "INTO_BUSINESS_FOR_PARTNER" -> if (isTamil) "வணிகம் வழியாக" else "Into Business for Partner"
    "DIRECT_TO_PARTNER" -> if (isTamil) "நேரடியாக பங்குதாரருக்கு" else "Direct to Partner"
    else -> mode
}
