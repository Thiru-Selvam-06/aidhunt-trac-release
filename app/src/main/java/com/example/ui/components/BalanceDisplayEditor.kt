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
import com.example.data.firebase.BalanceDisplayState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Composable
fun BalanceDisplayEditor(state: BalanceDisplayState, calculatedBalance: Double, isTamil: Boolean,
    online: Boolean, canEdit: Boolean, onDismiss: () -> Unit,
    onSave: (Long?, Long, Long, String, String, (String?) -> Unit) -> Unit) {
    val latestCalculatedPaise = BalanceDisplayPolicy.parsePaise(java.math.BigDecimal.valueOf(calculatedBalance).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
    val calculatedPaise = remember { latestCalculatedPaise }
    val originalRevision = remember { state.revision }
    val changedWhileOpen = calculatedPaise != latestCalculatedPaise || state.revision != originalRevision
    val operationId = remember { UUID.randomUUID().toString() }
    var amount by remember { mutableStateOf(BalanceDisplayPolicy.amount(state.overridePaise ?: calculatedPaise)) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val valid = runCatching { BalanceDisplayPolicy.parsePaise(amount); BalanceDisplayPolicy.reason(reason) }.isSuccess
    fun save(value: Long?) {
        busy = true; error = ""
        onSave(value, calculatedPaise, originalRevision, reason, operationId) { failure ->
            busy = false
            if (failure == null) onDismiss() else error = failure
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isTamil) "இருப்புக் காட்சி மற்றும் திருத்த வரலாறு" else "Balance display and edit history") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isTamil) "இது காட்சியை மட்டும் மாற்றும். கணக்கு இருப்பு, பணம் எடுக்கும் வரம்பு மற்றும் அறிக்கைகள் மாறாது."
                    else "Display only. Accounting balance, withdrawal limits and financial reports do not change.")
                Text((if (isTamil) "கணக்கிடப்பட்ட இருப்பு: " else "Calculated balance: ") + formatInr(calculatedBalance))
                if (canEdit) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, enabled = !busy,
                        label = { Text(if (isTamil) "காட்ட வேண்டிய தொகை" else "Displayed amount") },
                        modifier = Modifier.fillMaxWidth().testTag("balance_display_amount"), singleLine = true)
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, enabled = !busy,
                        label = { Text(if (isTamil) "காரணம் (கட்டாயம்)" else "Reason (required)") },
                        modifier = Modifier.fillMaxWidth().testTag("balance_display_reason"), maxLines = 4)
                    if (changedWhileOpen) Text(if (isTamil) "இருப்பு மாறியுள்ளது. மூடிவிட்டு மீண்டும் திறந்து சமீபத்திய தொகையைச் சரிபார்க்கவும்."
                        else "The balance changed. Close and reopen to review the latest amount.")
                    if (!online || !state.confirmed) Text(if (isTamil) "சேமிக்க இணைய இணைப்பும் உறுதிப்படுத்தப்பட்ட சமீபத்திய காட்சியும் தேவை."
                        else "Saving requires internet and the latest server-confirmed display setting.")
                    if (state.overridePaise != null) TextButton(onClick = { save(null) },
                        enabled = !busy && !changedWhileOpen && online && state.confirmed && runCatching { BalanceDisplayPolicy.reason(reason) }.isSuccess) {
                        Text(if (isTamil) "கணக்கிடப்பட்ட காட்சியை மீட்டமை" else "Restore calculated display")
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                Text(if (isTamil) "சமீபத்திய 100 திருத்தங்கள்; முழு வரலாறும் பாதுகாக்கப்படுகிறது." else "Latest 100 edits; the complete history is retained.")
                if (state.history.isEmpty()) Text(if (isTamil) "திருத்தங்கள் இல்லை" else "No display edits")
                state.history.forEach { edit ->
                    val timestamp = remember(edit.editedAt) { SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'IST'", Locale.ENGLISH).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                    }.format(java.util.Date(edit.editedAt)) }
                    Text("${formatInr(edit.before / 100.0)} → ${formatInr(edit.after / 100.0)}\n" +
                        (if (isTamil) "வேறுபாடு: " else "Difference: ") + formatInr((edit.after - edit.before) / 100.0) +
                        "\n${edit.editorName.ifBlank { edit.editorUid }} · $timestamp\n${edit.reason}" +
                        if (edit.restored) (if (isTamil) "\nகணக்கிடப்பட்ட காட்சி மீட்டமைக்கப்பட்டது" else "\nCalculated display restored") else "")
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { if (canEdit) TextButton(onClick = { save(BalanceDisplayPolicy.parsePaise(amount)) },
            modifier = Modifier.testTag("balance_display_save"), enabled = valid && !busy && !changedWhileOpen && online && state.confirmed) {
            Text(if (busy) (if (isTamil) "சேமிக்கிறது…" else "Saving…") else (if (isTamil) "சேமி" else "Save"))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(if (isTamil) "மூடு" else "Close") } })
}
