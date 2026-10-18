package com.example.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.data.firebase.Workspace

/** Authentication succeeded; workspace access is resolved separately without opening cached business data. */
@Composable
fun WorkspaceRecoveryScreen(message: String, workspaces: List<Workspace>, canCreate: Boolean,
    onRetry: () -> Unit, onSwitch: (String) -> Unit, onCreate: (String, String) -> Unit, onLogout: () -> Unit) {
    var name by remember(canCreate) { mutableStateOf("") }
    var business by remember(canCreate) { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(if (workspaces.isEmpty()) "Workspace access" else "Choose your workspace",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyLarge)
                Text("Your saved records and pending actions stay in their original workspace.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                workspaces.forEach { workspace ->
                    OutlinedCard(
                        onClick = { onSwitch(workspace.workspaceId) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_select_workspace_${workspace.workspaceId}")
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(workspace.name.ifBlank { "Workspace" },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(workspace.accessRole, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(workspace.workspaceId, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Open workspace", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry workspace verification")
                }
                if (canCreate && workspaces.isEmpty()) {
                    OutlinedTextField(name, { name = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(business, { business = it }, label = { Text("Personal workspace name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onCreate(name, business) }, modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && business.isNotBlank()) { Text("Create or recover personal workspace") }
                }
                Text("To join another workspace, ask its Owner to complete the phone invitation. Your personal workspace is retained.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}
