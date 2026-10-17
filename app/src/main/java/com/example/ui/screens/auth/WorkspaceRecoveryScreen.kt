package com.example.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.firebase.Workspace

/** Authentication succeeded; workspace access is resolved separately without opening cached business data. */
@Composable
fun WorkspaceRecoveryScreen(message: String, workspaces: List<Workspace>, canCreate: Boolean,
    onRetry: () -> Unit, onSwitch: (String) -> Unit, onCreate: (String, String) -> Unit, onLogout: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workspace access", style = MaterialTheme.typography.headlineSmall)
        Text(message)
        Text("Your saved records and pending actions are retained in their original workspace.")
        workspaces.forEach { workspace ->
            OutlinedButton(onClick = { onSwitch(workspace.workspaceId) }) { Text("${workspace.name.ifBlank { workspace.workspaceId }} · ${workspace.accessRole}") }
        }
        Button(onClick = onRetry) { Text("Retry workspace verification") }
        Text("To join another workspace, ask its Owner to complete the phone invitation. Your personal workspace is retained.")
        if (canCreate && workspaces.isEmpty()) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") })
            OutlinedTextField(business, { business = it }, label = { Text("Personal workspace name") })
            Button(onClick = { onCreate(name, business) }, enabled = name.isNotBlank() && business.isNotBlank()) { Text("Create or recover personal workspace") }
        }
        TextButton(onClick = onLogout) { Text("Sign out") }
    }
}
