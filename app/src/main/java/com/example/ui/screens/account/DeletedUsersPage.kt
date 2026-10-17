package com.example.ui.screens.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.DeepSageGreen
import com.example.data.entity.DeletedMemberEntity

@Composable
fun DeletedUsersPage(members: List<DeletedMemberEntity>, onBack: () -> Unit,
    onPurge: (String, String) -> Unit, error: String = "") {
    var selected by remember { mutableStateOf<DeletedMemberEntity?>(null) }
    var confirmation by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Deleted Users", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.textPrimary)
        Text("Removed members have no workspace access. Their records remain until you permanently delete them.")
        if (error.isNotBlank()) Text(error, color = AlertDueRed)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (members.isEmpty()) item { Text("No removed members") }
            items(members, key = { it.uid }) { member ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(member.displayName.ifBlank { member.uid }, color = AppTheme.colors.textPrimary)
                        Text("${member.role} · ${member.purgeState}", color = AppTheme.colors.textMuted)
                        if (member.purgeState != "PURGED") TextButton(onClick = { selected = member; confirmation = "" }) {
                            Text(if (member.purgeState == "PURGE_PENDING") "Retry permanent deletion" else "Permanently Delete Records")
                        }
                    }
                }
            }
        }
    }
    selected?.let { member ->
        AlertDialog(containerColor = AppTheme.colors.cardBg, onDismissRequest = { selected = null }, title = { Text("Permanently delete ${member.displayName.ifBlank { "this member" }}'s records?") },
            text = { Column {
                Text("This erases records they created or received in this workspace, including financial records and collections. Shared customers and other workspaces are preserved. This cannot be undone in the app.")
                Text("Type PERMANENTLY DELETE to confirm.")
                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = confirmation == "PERMANENTLY DELETE", onClick = {
                onPurge(member.uid, confirmation); selected = null
            }) { Text("Permanently Delete Records") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
    }
}
