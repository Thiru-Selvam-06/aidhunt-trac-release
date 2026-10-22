package com.example.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.data.auth.RoleUtils
import com.example.data.firebase.WorkspaceMember

/** Presentation only. Never use this role or preference for authorization or money. */
fun businessDisplayRole(role: String?, hasHadActivePartner: Boolean): String =
    RoleUtils.normalizeRole(role).let {
        if (it == RoleUtils.ROLE_OWNER && hasHadActivePartner) RoleUtils.ROLE_PARTNER else it
    }

fun hasActiveBusinessPartner(workspaceId: String, members: List<WorkspaceMember>): Boolean =
    workspaceId.isNotBlank() && members.any { it.workspaceId == workspaceId && it.uid.isNotBlank() &&
        it.status.equals("ACTIVE", true) && RoleUtils.normalizeRole(it.role) == RoleUtils.ROLE_PARTNER }

/** Retains the approved visible label on this installation after the last Partner leaves. */
@Composable
fun rememberBusinessPartnership(workspaceId: String, members: List<WorkspaceMember>): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("business_role_presentation", Context.MODE_PRIVATE) }
    val key = "has_had_active_partner_$workspaceId"
    val observed = hasActiveBusinessPartner(workspaceId, members)
    val prior = remember(workspaceId) { prefs.getBoolean(key, false) }
    val partnership = workspaceId.isNotBlank() && (observed || prior || prefs.getBoolean(key, false))
    SideEffect { if (observed && !prefs.getBoolean(key, false)) prefs.edit().putBoolean(key, true).apply() }
    return partnership
}

@Composable
fun businessRoleDisplayName(role: String?, workspaceId: String, members: List<WorkspaceMember>, isTamil: Boolean): String =
    RoleUtils.getRoleDisplayName(businessDisplayRole(role, rememberBusinessPartnership(workspaceId, members)), isTamil)
