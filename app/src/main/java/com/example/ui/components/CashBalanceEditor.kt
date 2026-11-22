package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    onSave: (Long, Long, Long, String, String, (String?) -> Unit) -> Unit) {
    val latestBase = BalanceDisplayPolicy.parsePaise(java.math.BigDecimal.valueOf(calculatedBalance)
        .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
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
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isTamil) "பண இருப்புத் திருத்தம் மற்றும் வரலாறு" else "Adjust available cash / history") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isTamil) "இந்தத் திருத்தம் அனைவரின் கிடைக்கும் ரொக்க இருப்பிலும் பணம் எடுக்கும் வரம்பிலும் சேரும். வாடிக்கையாளர் பாக்கி, வரவு மற்றும் ஈட்டிய லாபம் மாறாது."
                else "This changes shared available cash and withdrawal limits. Customer dues, recorded receipts and earned profit stay unchanged.")
            Text((if (isTamil) "தற்போதைய இருப்பு: " else "Current cash: ") + formatInr((base + state.totalAdjustmentPaise) / 100.0))
            if (!state.enabled) Text(if (isTamil) "அனைத்து பங்குதாரர் செயலிகளும் புதுப்பிக்கப்பட்ட பின் பாதுகாப்பான செயலாக்கம் தேவை. இதுவரை எந்த இருப்பும் மாற்றப்படவில்லை."
                else "Activation is required after all partner apps are updated. No cash balance has been changed.")
            if (canEdit) {
                OutlinedTextField(amount, { amount = it }, enabled = !busy && state.enabled,
                    label = { Text(if (isTamil) "சரியான ரொக்க இருப்பு" else "Correct available cash") },
                    modifier = Modifier.fillMaxWidth().testTag("cash_balance_amount"), singleLine = true)
                OutlinedTextField(reason, { reason = it }, enabled = !busy && state.enabled,
                    label = { Text(if (isTamil) "காரணம் (கட்டாயம்)" else "Reason (required)") },
                    modifier = Modifier.fillMaxWidth().testTag("cash_balance_reason"), maxLines = 4)
                if (target != null) Text((if (isTamil) "திருத்த வேறுபாடு: " else "Adjustment: ") + formatInr((target - base - state.totalAdjustmentPaise) / 100.0))
                if (changed) Text(if (isTamil) "இருப்பு மாறியுள்ளது. மூடிவிட்டு மீண்டும் திறந்து சரிபார்க்கவும்."
                    else "The balance changed. Close and reopen to review the latest amount.")
                if (state.enabled && (!online || !state.confirmed)) Text(if (isTamil) "சேமிக்க இணைய இணைப்பும் சேவையக உறுதிப்படுத்தலும் தேவை."
                    else "Saving requires internet and a server-confirmed balance and history.")
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            Text(if (isTamil) "சமீபத்திய 100 திருத்தங்கள்; முழு வரலாறும் பாதுகாக்கப்படுகிறது." else "Latest 100 adjustments; the complete history is retained.")
            state.edits.sortedByDescending { it.revision }.take(100).forEach { edit ->
                val time = remember(edit.confirmedAt) { java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'IST'", java.util.Locale.ENGLISH).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                }.format(java.util.Date(edit.confirmedAt)) }
                Text("${formatInr(edit.beforePaise / 100.0)} → ${formatInr(edit.afterPaise / 100.0)}\n" +
                    (if (isTamil) "வேறுபாடு: " else "Difference: ") + formatInr(edit.deltaPaise / 100.0) +
                    "\n${edit.actorName.ifBlank { edit.actorUid }} · $time\n${edit.reason}")
                HorizontalDivider()
            }
        } },
        confirmButton = { if (canEdit) TextButton(modifier = Modifier.testTag("cash_balance_save"),
            enabled = valid && !busy && !changed && online && state.enabled && state.loaded && state.confirmed,
            onClick = {
                busy = true; error = ""
                onSave(requireNotNull(target), base, revision, reason, operationId) { failure ->
                    busy = false
                    if (failure == null) onDismiss() else error = failure
                }
            }) { Text(if (busy) (if (isTamil) "சேமிக்கிறது…" else "Saving…") else (if (isTamil) "சேமி" else "Save")) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(if (isTamil) "மூடு" else "Close") } })
}
