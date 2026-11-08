package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.expense.ExpenseCategories

/** Saves through the existing durable queue; it never writes customer/financial records. */
@Composable
fun AddExpenseCategoryDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    onSelected: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isTamil) "செலவு வகையைச் சேர்" else "Add expense category") },
        text = {
            OutlinedTextField(value = name, onValueChange = { if (!saving) { name = it; error = "" } },
                label = { Text(if (isTamil) "வகையின் பெயர்" else "Category name") },
                singleLine = true, enabled = !saving, isError = error.isNotBlank(),
                supportingText = { if (error.isNotBlank()) Text(error) },
                modifier = Modifier.testTag("expense-category-name"))
        },
        confirmButton = {
            TextButton(enabled = !saving, modifier = Modifier.testTag("expense-category-save"), onClick = {
                if (runCatching { ExpenseCategories.validate(name) }.isFailure) {
                    error = if (isTamil) "கட்டுப்பாட்டு எழுத்துகள் இல்லாமல் 1–60 எழுத்துகளை உள்ளிடவும்" else "Enter 1–60 characters without control characters"
                } else {
                    saving = true
                    onSave(name, { selected -> saving = false; onSelected(selected); onDismiss() }, {
                        saving = false
                        error = if (isTamil) "வகையைச் சேமிக்க முடியவில்லை. உங்கள் வணிக அணுகலைச் சரிபார்த்து மீண்டும் முயலவும்."
                        else "Could not save category. Check your business access and try again."
                    })
                }
            }) { Text(if (isTamil) "சேமி" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(if (isTamil) "ரத்து" else "Cancel") } }
    )
}
