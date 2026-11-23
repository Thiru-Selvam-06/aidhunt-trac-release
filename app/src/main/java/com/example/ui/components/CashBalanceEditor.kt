package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.firebase.BalanceDisplayPolicy
import com.example.data.firebase.CashBalanceState
import java.util.UUID

@Composable
fun CashBalanceEditor(state: CashBalanceState, calculatedBalance: Double, isTamil: Boolean,
    online: Boolean, canEdit: Boolean, onDismiss: () -> Unit,
    onSave: (Long, Long, Long, String, String, (String?) -> Unit) -> Unit,
    onBusyChange: (Boolean) -> Unit = {}) {
    val latestBase = BalanceDisplayPolicy.parsePaise(java.math.BigDecimal.valueOf(calculatedBalance)
        .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    val base = rememberSaveable { latestBase }
    val revision = rememberSaveable { state.revision }
    val changed = base != latestBase || revision != state.revision
    val operationId = rememberSaveable { UUID.randomUUID().toString() }
    var amount by rememberSaveable { mutableStateOf(BalanceDisplayPolicy.amount(base + state.totalAdjustmentPaise)) }
    var reason by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val target = runCatching { BalanceDisplayPolicy.parsePaise(amount) }.getOrNull()
    val valid = target != null && target != base + state.totalAdjustmentPaise &&
        runCatching { BalanceDisplayPolicy.reason(reason) }.isSuccess
    // This is normal Scaffold content, not a Dialog window. The parent retains the originating tab.
    SideEffect { onBusyChange(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    Box(Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).testTag("cash_balance_page"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("cash_balance_content"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    ReportMetrics(listOf(ReportMetric(
                        if (isTamil) "தற்போதைய இருப்பு" else "Current cash",
                        formatInr((base + state.totalAdjustmentPaise) / 100.0), tag = "cash_current")))
                }
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (isTamil) "இந்தத் திருத்தம் அனைவரின் கிடைக்கும் ரொக்க இருப்பிலும் பணம் எடுக்கும் வரம்பிலும் சேரும். வாடிக்கையாளர் பாக்கி, வரவு மற்றும் ஈட்டிய லாபம் மாறாது."
                                else "This changes shared available cash and withdrawal limits. Customer dues, recorded receipts and earned profit stay unchanged.")
                            if (!state.enabled) Text(if (isTamil) "அனைத்து பங்குதாரர் செயலிகளும் புதுப்பிக்கப்பட்ட பின் பாதுகாப்பான செயலாக்கம் தேவை. இதுவரை எந்த இருப்பும் மாற்றப்படவில்லை."
                                else "Activation is required after all partner apps are updated. No cash balance has been changed.")
                            if (canEdit) {
                                OutlinedTextField(amount, { amount = it }, enabled = !busy && state.enabled,
                                    label = { Text(if (isTamil) "சரியான ரொக்க இருப்பு" else "Correct available cash") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth().testTag("cash_balance_amount"), singleLine = true)
                                OutlinedTextField(reason, { reason = it }, enabled = !busy && state.enabled,
                                    label = { Text(if (isTamil) "காரணம் (கட்டாயம்)" else "Reason (required)") },
                                    modifier = Modifier.fillMaxWidth().testTag("cash_balance_reason"), minLines = 2, maxLines = 4)
                                if (target != null) Text((if (isTamil) "திருத்த வேறுபாடு: " else "Adjustment: ") + formatInr((target - base - state.totalAdjustmentPaise) / 100.0),
                                    fontWeight = FontWeight.SemiBold)
                                if (changed) Text(if (isTamil) "இருப்பு மாறியுள்ளது. மூடிவிட்டு மீண்டும் திறந்து சரிபார்க்கவும்."
                                    else "The balance changed. Close and reopen to review the latest amount.", color = MaterialTheme.colorScheme.error)
                                if (state.enabled && (!online || !state.confirmed)) Text(if (isTamil) "சேமிக்க இணைய இணைப்பும் சேவையக உறுதிப்படுத்தலும் தேவை."
                                    else "Saving requires internet and a server-confirmed balance and history.")
                            }
                            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                            if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportSectionHeading(if (isTamil) "திருத்த வரலாறு" else "Adjustment history")
                    Text(if (isTamil) "சமீபத்திய 100 திருத்தங்கள்; முழு வரலாறும் பாதுகாக்கப்படுகிறது." else "Latest 100 adjustments; the complete history is retained.")
                } }
                items(state.edits.sortedByDescending { it.revision }.take(100), key = { it.revision }) { edit ->
                    val time = remember(edit.confirmedAt) { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'IST'", java.util.Locale.ENGLISH).apply {
                        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    }.format(java.util.Date(edit.confirmedAt)) }
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${formatInr(edit.beforePaise / 100.0)} → ${formatInr(edit.afterPaise / 100.0)}", fontWeight = FontWeight.Bold)
                            Text((if (isTamil) "வேறுபாடு: " else "Difference: ") + formatInr(edit.deltaPaise / 100.0))
                            Text("${edit.actorName.ifBlank { edit.actorUid }} · $time")
                            Text(edit.reason)
                        }
                    }
                }
            }
            Surface(color = Color.White, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (isTamil) "மூடு" else "Close")
                    }
                    if (canEdit) Button(modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cash_balance_save"),
                        enabled = valid && !busy && !changed && online && state.enabled && state.loaded && state.confirmed,
                        onClick = {
                            busy = true; onBusyChange(true); error = ""
                            onSave(requireNotNull(target), base, revision, reason, operationId) { failure ->
                                busy = false; onBusyChange(false)
                                if (failure == null) onDismiss() else error = failure
                            }
                        }) { Text(if (busy) (if (isTamil) "சேமிக்கிறது…" else "Saving…") else (if (isTamil) "சேமி" else "Save")) }
                }
            }
        }
    }
}
