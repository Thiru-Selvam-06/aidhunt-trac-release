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

private fun WorkspaceMember.isPartnerIn(workspaceId: String): Boolean = workspaceId.isNotBlank() &&
    this.workspaceId == workspaceId && uid.isNotBlank() && RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER

fun hasActiveBusinessPartner(workspaceId: String, members: List<WorkspaceMember>): Boolean =
    members.any { it.isPartnerIn(workspaceId) && it.status.equals("ACTIVE", true) }

/** The workspace retains the original member on removal; Rules allow ACTIVE -> REMOVED only. */
fun hasBusinessPartnershipHistory(workspaceId: String, members: List<WorkspaceMember>): Boolean =
    members.any { it.isPartnerIn(workspaceId) && (it.status.equals("ACTIVE", true) || it.status.equals("REMOVED", true)) }

/** A fresh device uses the retained membership; the local observation also survives offline. */
@Composable
fun rememberBusinessPartnership(workspaceId: String, members: List<WorkspaceMember>): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("business_role_presentation", Context.MODE_PRIVATE) }
    val key = "has_had_active_partner_$workspaceId"
    val observed = hasBusinessPartnershipHistory(workspaceId, members)
    val prior = remember(workspaceId) { prefs.getBoolean(key, false) }
    val partnership = workspaceId.isNotBlank() && (observed || prior || prefs.getBoolean(key, false))
    SideEffect { if (observed && !prefs.getBoolean(key, false)) prefs.edit().putBoolean(key, true).apply() }
    return partnership
}

@Composable
fun businessRoleDisplayName(role: String?, workspaceId: String, members: List<WorkspaceMember>, isTamil: Boolean): String =
    RoleUtils.getRoleDisplayName(businessDisplayRole(role, rememberBusinessPartnership(workspaceId, members)), isTamil)
