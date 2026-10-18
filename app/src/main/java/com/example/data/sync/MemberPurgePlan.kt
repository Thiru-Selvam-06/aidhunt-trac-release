package com.example.data.sync

import com.example.data.entity.*

data class MemberPurgePlan(
    val jobs: List<JobEntryEntity>,
    val expenses: List<ExpenseEntity>,
    val withdrawals: List<WithdrawalEntity>,
    val tractors: List<TractorEntity>,
    val extensions: List<WorkTypeExtensionEntity>,
    val collections: List<PaymentEntity>,
    val customers: List<CustomerEntity>
) {
    companion object {
        fun build(wsId: String, uid: String, jobs: List<JobEntryEntity>, expenses: List<ExpenseEntity>,
            withdrawals: List<WithdrawalEntity>, tractors: List<TractorEntity>, extensions: List<WorkTypeExtensionEntity>,
            collections: List<PaymentEntity>, customers: List<CustomerEntity>): MemberPurgePlan {
            require(wsId.isNotBlank() && uid.isNotBlank())
            val removedJobs = jobs.filter { it.workspaceId == wsId && uid in setOf(it.createdByUid, it.entryForUid, it.financialAttributionUid) }
            val ids = removedJobs.map { it.id }.toSet()
            val removedCollections = collections.filter { it.workspaceId == wsId && (it.collectedByUid == uid || it.jobEntryId in ids) }
            val collectionIds = removedCollections.map { it.id }.toSet()
            val retainedCustomerIds = jobs.filter { it.workspaceId == wsId && it.id !in ids }.map { it.customerId }.toSet() +
                collections.filter { it.workspaceId == wsId && it.id !in collectionIds }.map { it.customerId }.toSet()
            return MemberPurgePlan(removedJobs,
                expenses.filter { it.workspaceId == wsId && (uid in setOf(it.createdByUid, it.expenseForUid, it.financialAttributionUid) || it.relatedJobId in ids) },
                withdrawals.filter { it.workspaceId == wsId && uid in setOf(it.createdByUid, it.targetPartnerUid) },
                tractors.filter { it.workspaceId == wsId && it.createdByUid == uid },
                extensions.filter { it.workspaceId == wsId && it.createdByUid == uid }, removedCollections,
                customers.filter { it.workspaceId == wsId && it.createdByUid == uid && it.id !in retainedCustomerIds })
        }
    }
}
