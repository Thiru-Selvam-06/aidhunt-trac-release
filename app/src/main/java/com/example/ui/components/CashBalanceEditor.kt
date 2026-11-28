package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.BalanceDisplayPolicy
import com.example.data.firebase.CashBalanceState
import java.util.UUID

@Composable
fun CashBalanceEditor(
    state: CashBalanceState,
    calculatedBalance: Double,
    isTamil: Boolean,
    online: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long, String, String, (String?) -> Unit) -> Unit
) {
    val latestBase = BalanceDisplayPolicy.parsePaise(
        java.math.BigDecimal.valueOf(calculatedBalance)
            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    )
    val base = remember { latestBase }
    val revision = remember { state.revision }
    val changed = base != latestBase || revision != state.revision
    val operationId = remember { UUID.randomUUID().toString() }
    var amount by remember { mutableStateOf(BalanceDisplayPolicy.amount(base + state.totalAdjustmentPaise)) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val target = runCatching { BalanceDisplayPolicy.parsePaise(amount) }.getOrNull()
    val valid = target != null && target != base + state.totalAdjustmentPaise &&
        runCatching { BalanceDisplayPolicy.reason(reason) }.isSuccess

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (isTamil) "பண இருப்புத் திருத்தம் /\nவரலாறு" else "Adjust available cash /\nhistory",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    lineHeight = 26.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (!busy) onDismiss() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (isTamil) "மூடு" else "Close",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Explanatory financial note
                Text(
                    text = if (isTamil) "இந்தத் திருத்தம் அனைவரின் கிடைக்கும் ரொக்க இருப்பிலும் பணம் எடுக்கும் வரம்பிலும் சேரும். வாடிக்கையாளர் பாக்கி, வரவு மற்றும் ஈட்டிய லாபம் மாறாது."
                    else "This changes shared available cash and withdrawal limits. Customer dues, recorded receipts and earned profit stay unchanged.",
                    fontSize = 13.sp,
                    color = Color(0xFF4B5563),
                    lineHeight = 18.sp
                )

                // Current cash summary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isTamil) "தற்போதைய இருப்பு: " else "Current cash: ",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = formatInr((base + state.totalAdjustmentPaise) / 100.0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF072D18)
                    )
                }

                if (!state.enabled) {
                    Text(
                        text = if (isTamil) "அனைத்து பங்குதாரர் செயலிகளும் புதுப்பிக்கப்பட்ட பின் பாதுகாப்பான செயலாக்கம் தேவை. இதுவரை எந்த இருப்பும் மாற்றப்படவில்லை."
                        else "Activation is required after all partner apps are updated. No cash balance has been changed.",
                        fontSize = 12.5.sp,
                        color = Color(0xFFD97706)
                    )
                }

                if (canEdit) {
                    // Correct available cash input
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        enabled = !busy && state.enabled,
                        label = {
                            Text(
                                text = if (isTamil) "சரியான ரொக்க இருப்பு" else "Correct available cash",
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF16A34A),
                            unfocusedBorderColor = Color(0xFF86EFAC),
                            focusedContainerColor = Color(0xFFF0FDF4),
                            unfocusedContainerColor = Color(0xFFF9FDF9),
                            focusedTextColor = Color(0xFF111827),
                            unfocusedTextColor = Color(0xFF111827),
                            focusedLabelColor = Color(0xFF16A34A),
                            unfocusedLabelColor = Color(0xFF374151)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_balance_amount"),
                        singleLine = true
                    )

                    // Reason input
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        enabled = !busy && state.enabled,
                        label = {
                            Text(
                                text = if (isTamil) "காரணம் (கட்டாயம்)" else "Reason (required)",
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF072D18),
                            unfocusedBorderColor = Color(0xFFD1D5DB),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color(0xFF111827),
                            unfocusedTextColor = Color(0xFF111827),
                            focusedLabelColor = Color(0xFF072D18),
                            unfocusedLabelColor = Color(0xFF374151)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cash_balance_reason"),
                        maxLines = 4
                    )

                    // Adjustment amount
                    if (target != null) {
                        val deltaPaise = target - base - state.totalAdjustmentPaise
                        val deltaColor = when {
                            deltaPaise > 0 -> Color(0xFF16A34A)
                            deltaPaise < 0 -> Color(0xFFDC2626)
                            else -> Color(0xFF111827)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isTamil) "திருத்த வேறுபாடு: " else "Adjustment: ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = formatInr(deltaPaise / 100.0),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = deltaColor
                            )
                        }
                    }

                    if (changed) {
                        Text(
                            text = if (isTamil) "இருப்பு மாறியுள்ளது. மூடிவிட்டு மீண்டும் திறந்து சரிபார்க்கவும்."
                            else "The balance changed. Close and reopen to review the latest amount.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (state.enabled && (!online || !state.confirmed)) {
                        Text(
                            text = if (isTamil) "சேமிக்க இணைய இணைப்பும் சேவையக உறுதிப்படுத்தலும் தேவை."
                            else "Saving requires internet and a server-confirmed balance and history.",
                            fontSize = 12.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                if (error.isNotBlank()) {
                    Text(text = error, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                }
                if (state.error.isNotBlank()) {
                    Text(text = state.error, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
                }

                // History Section Header
                Text(
                    text = if (isTamil) "சமீபத்திய 100 திருத்தங்கள்; முழு வரலாறும் பாதுகாக்கப்படுகிறது."
                    else "Latest 100 adjustments; the complete history is retained.",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    modifier = Modifier.padding(top = 4.dp)
                )

                // History Cards List
                state.edits.sortedByDescending { it.revision }.take(100).forEach { edit ->
                    val time = remember(edit.confirmedAt) {
                        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'IST'", java.util.Locale.ENGLISH).apply {
                            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                        }.format(java.util.Date(edit.confirmedAt))
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Transaction badge icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Card details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Before -> After
                                Text(
                                    text = "${formatInr(edit.beforePaise / 100.0)} → ${formatInr(edit.afterPaise / 100.0)}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )

                                // Difference badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isTamil) "வேறுபாடு: " else "Difference: ",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF4B5563)
                                    )
                                    val (diffBg, diffTextColor) = when {
                                        edit.deltaPaise < 0 -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                                        edit.deltaPaise > 0 -> Color(0xFFDCFCE7) to Color(0xFF16A34A)
                                        else -> Color(0xFFF3F4F6) to Color(0xFF374151)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(diffBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatInr(edit.deltaPaise / 100.0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = diffTextColor
                                        )
                                    }
                                }

                                // Actor and Date
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF6B7280),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${edit.actorName.ifBlank { edit.actorUid }} · $time",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }

                                // Reason (if present)
                                if (edit.reason.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF6B7280),
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(13.dp)
                                        )
                                        Text(
                                            text = edit.reason,
                                            fontSize = 12.sp,
                                            color = Color(0xFF374151),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text(
                    text = if (isTamil) "மூடு" else "Close",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF072D18)
                )
            }
        },
        confirmButton = {
            if (canEdit) {
                val canSave = valid && !busy && !changed && online && state.enabled && state.loaded && state.confirmed
                TextButton(
                    modifier = Modifier.testTag("cash_balance_save"),
                    enabled = canSave,
                    onClick = {
                        busy = true
                        error = ""
                        onSave(requireNotNull(target), base, revision, reason, operationId) { failure ->
                            busy = false
                            if (failure == null) onDismiss() else error = failure
                        }
                    }
                ) {
                    Text(
                        text = if (busy) (if (isTamil) "சேமிக்கிறது…" else "Saving…") else (if (isTamil) "சேமி" else "Save"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) Color(0xFF072D18) else Color(0xFF9CA3AF)
                    )
                }
            }
        }
    )
}
