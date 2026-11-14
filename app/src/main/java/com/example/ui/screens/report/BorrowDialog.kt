package com.example.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.RoleUtils
import com.example.data.firebase.WorkspaceMember
import com.example.ui.components.formatInr
import com.example.ui.util.WorkspaceBorrowingOverview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowDialog(
    workspaceId: String,
    workspaceMembers: List<WorkspaceMember>,
    borrowingOverview: WorkspaceBorrowingOverview,
    currency: String,
    isTamil: Boolean,
    isOwner: Boolean,
    currentUid: String,
    onDismiss: () -> Unit,
    onSubmit: (
        sourceType: String,
        lendingPartnerUid: String,
        amount: Double,
        note: String,
        targetBorrowerUid: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    val activeShareMembers = remember(workspaceMembers, workspaceId) {
        workspaceMembers.filter {
            it.uid.isNotBlank() &&
                (workspaceId.isBlank() || it.workspaceId == workspaceId) &&
                it.status.equals("active", ignoreCase = true) &&
                RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER)
        }.distinctBy { it.uid }
    }
    val eligibleBorrowers = remember(activeShareMembers, isOwner, currentUid) {
        if (isOwner && activeShareMembers.isNotEmpty()) activeShareMembers
        else activeShareMembers.filter { it.uid == currentUid }
    }
    var targetBorrowerUid by remember(currentUid, eligibleBorrowers) {
        mutableStateOf(
            eligibleBorrowers.firstOrNull { it.uid == currentUid }?.uid
                ?: eligibleBorrowers.firstOrNull()?.uid
                ?: currentUid
        )
    }
    val eligibleLenders = remember(activeShareMembers, targetBorrowerUid) {
        activeShareMembers.filter { it.uid != targetBorrowerUid }
    }

    var sourceType by remember { mutableStateOf("BUSINESS_ONLY") }
    var lendingPartnerUid by remember(eligibleLenders) {
        mutableStateOf(eligibleLenders.firstOrNull()?.uid.orEmpty())
    }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var borrowerDropdownExpanded by remember { mutableStateOf(false) }
    var lenderDropdownExpanded by remember { mutableStateOf(false) }

    val selectedBorrower = eligibleBorrowers.firstOrNull { it.uid == targetBorrowerUid }
    val selectedLender = eligibleLenders.firstOrNull { it.uid == lendingPartnerUid }
    val lenderWithdrawable = selectedLender?.let {
        borrowingOverview.partnerSummaries[it.uid]?.withdrawableEntitlement ?: 0.0
    } ?: 0.0

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        modifier = Modifier.testTag("borrow_dialog"),
        title = {
            Text(
                text = if (isTamil) "கடன் எடுத்தல்" else "Borrow Funds",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isOwner && eligibleBorrowers.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = borrowerDropdownExpanded,
                        onExpandedChange = { if (!submitting) borrowerDropdownExpanded = !borrowerDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedBorrower?.displayName?.ifBlank { null } ?: "Member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (isTamil) "கடன் பெறுபவர்" else "Borrower") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = borrowerDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("borrow_borrower_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = borrowerDropdownExpanded,
                            onDismissRequest = { borrowerDropdownExpanded = false }
                        ) {
                            eligibleBorrowers.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.displayName?.ifBlank { null } ?: member.uid) },
                                    onClick = {
                                        targetBorrowerUid = member.uid
                                        if (lendingPartnerUid == member.uid) {
                                            lendingPartnerUid = activeShareMembers.firstOrNull { it.uid != member.uid }?.uid.orEmpty()
                                        }
                                        borrowerDropdownExpanded = false
                                        errorMsg = ""
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (isTamil) "கடன் மூலம்" else "Borrowing Source",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sourceType == "BUSINESS_ONLY") Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (sourceType == "BUSINESS_ONLY") Color(0xFF0B532E) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !submitting) { sourceType = "BUSINESS_ONLY"; errorMsg = "" }
                        .testTag("borrow_source_business")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sourceType == "BUSINESS_ONLY",
                            onClick = { if (!submitting) { sourceType = "BUSINESS_ONLY"; errorMsg = "" } }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isTamil) "வணிகத்திலிருந்து கடன்" else "Borrow from Business",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isTamil) "வரம்பு: ${formatInr(borrowingOverview.availableForBusinessBorrowing, currency)} (பங்குதாரர் ஒப்புதல் தேவையில்லை)"
                                else "Limit: ${formatInr(borrowingOverview.availableForBusinessBorrowing, currency)} · No partner approval needed",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sourceType == "NAMED_PARTNER") Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (sourceType == "NAMED_PARTNER") Color(0xFF0B532E) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !submitting) { sourceType = "NAMED_PARTNER"; errorMsg = "" }
                        .testTag("borrow_source_partner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sourceType == "NAMED_PARTNER",
                            onClick = { if (!submitting) { sourceType = "NAMED_PARTNER"; errorMsg = "" } }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isTamil) "பங்குதாரரிடமிருந்து கடன்" else "Borrow from Partner",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isTamil) "வழங்கும் பங்குதாரர் ஒப்புதல் & முன்பதிவு தேவை"
                                else "Requires lending partner approval & cash reservation",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }

                if (sourceType == "NAMED_PARTNER") {
                    if (eligibleLenders.isEmpty()) {
                        Text(
                            text = if (isTamil) "மற்ற செயலில் உள்ள பங்குதாரர்கள் இல்லை" else "No other active partners available to lend",
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = lenderDropdownExpanded,
                            onExpandedChange = { if (!submitting) lenderDropdownExpanded = !lenderDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLender?.displayName?.ifBlank { null } ?: "Partner",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (isTamil) "கடன் வழங்கும் பங்குதாரர்" else "Lending Partner") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lenderDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("borrow_lender_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = lenderDropdownExpanded,
                                onDismissRequest = { lenderDropdownExpanded = false }
                            ) {
                                eligibleLenders.forEach { lender ->
                                    val entitle = borrowingOverview.partnerSummaries[lender.uid]?.withdrawableEntitlement ?: 0.0
                                    DropdownMenuItem(
                                        text = {
                                            Text("${lender.displayName?.ifBlank { null } ?: lender.uid} (${formatInr(entitle, currency)})")
                                        },
                                        onClick = {
                                            lendingPartnerUid = lender.uid
                                            lenderDropdownExpanded = false
                                            errorMsg = ""
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isTamil) "வழங்கக்கூடிய பங்கு: ${formatInr(lenderWithdrawable, currency)} · முன்பதிவு செய்யாத ரொக்கம்: ${formatInr(borrowingOverview.unreservedCash, currency)}"
                            else "Lender withdrawable: ${formatInr(lenderWithdrawable, currency)} · Unreserved cash: ${formatInr(borrowingOverview.unreservedCash, currency)}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMsg = "" },
                    label = { Text(if (isTamil) "தொகை (₹)*" else "Amount (₹)*") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_borrow_amount")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 120) note = it },
                    label = { Text(if (isTamil) "குறிப்பு (விருப்பம்)" else "Note (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_borrow_note")
                )

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && (sourceType != "NAMED_PARTNER" || eligibleLenders.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_submit_borrow"),
                onClick = {
                    val amt = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                    when {
                        amt <= 0.0 -> {
                            errorMsg = if (isTamil) "செல்லுபடியான தொகையை உள்ளிடவும்" else "Enter an amount greater than ₹0"
                        }
                        sourceType == "BUSINESS_ONLY" && amt > borrowingOverview.availableForBusinessBorrowing + 0.001 -> {
                            errorMsg = if (isTamil) "பாதுகாக்கப்பட்ட பங்குகளுக்குப் பிறகு ${formatInr(borrowingOverview.availableForBusinessBorrowing, currency)} மட்டுமே கிடைக்கும்"
                            else "Exceeds available business borrowing limit (${formatInr(borrowingOverview.availableForBusinessBorrowing, currency)})"
                        }
                        sourceType == "NAMED_PARTNER" && lendingPartnerUid.isBlank() -> {
                            errorMsg = if (isTamil) "பங்குதாரரைத் தேர்ந்தெடுக்கவும்" else "Select a lending partner"
                        }
                        sourceType == "NAMED_PARTNER" && amt > lenderWithdrawable + 0.001 -> {
                            errorMsg = if (isTamil) "பங்குதாரரின் எடுக்கக்கூடிய பங்கை (${formatInr(lenderWithdrawable, currency)}) விட அதிகம்"
                            else "Exceeds lending partner's withdrawable entitlement (${formatInr(lenderWithdrawable, currency)})"
                        }
                        sourceType == "NAMED_PARTNER" && amt > borrowingOverview.unreservedCash + 0.001 -> {
                            errorMsg = if (isTamil) "முன்பதிவு செய்யாத ரொக்கம் போதுமானதாக இல்லை (${formatInr(borrowingOverview.unreservedCash, currency)})"
                            else "Insufficient unreserved business cash (${formatInr(borrowingOverview.unreservedCash, currency)})"
                        }
                        else -> {
                            submitting = true
                            val lenderUid = if (sourceType == "NAMED_PARTNER") lendingPartnerUid else ""
                            onSubmit(sourceType, lenderUid, amt, note, targetBorrowerUid) { err ->
                                submitting = false
                                errorMsg = err
                            }
                        }
                    }
                }
            ) {
                Text(
                    text = if (sourceType == "BUSINESS_ONLY") {
                        if (isTamil) "கடன் பெறுக" else "Borrow Now"
                    } else {
                        if (isTamil) "கோரிக்கை அனுப்பு" else "Send Request"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}
