package com.example.ui.screens.report

import com.example.data.auth.RoleUtils
import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.FinancialCalculationEngine

fun duesFilterPartners(workspaceId: String, members: List<WorkspaceMember>): List<WorkspaceMember> =
    members.filter { it.workspaceId == workspaceId && it.uid.isNotBlank() && it.status.equals("ACTIVE", true) &&
        RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER) }.distinctBy { it.uid }

/** Scope source jobs before aggregation; creator, collector and display names are never filter keys. */
fun customerDuesForPartner(workspaceId: String, customers: List<CustomerEntity>, jobs: List<JobEntryEntity>, partnerUid: String?): List<CustomerEntity> =
    FinancialCalculationEngine.customersWithLedger(customers.filter { it.workspaceId == workspaceId },
        jobs.filter { it.workspaceId == workspaceId && (partnerUid == null || it.entryForUid == partnerUid) })
