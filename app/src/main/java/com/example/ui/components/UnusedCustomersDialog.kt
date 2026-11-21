package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity

object UnusedCustomerPolicy {
    fun candidates(workspace: String, customers: List<CustomerEntity>, jobs: List<JobEntryEntity>, payments: List<PaymentEntity>, retained: List<com.example.data.entity.PendingOperationEntity> = emptyList()): List<CustomerEntity> {
        val referenced = jobs.filter { it.workspaceId == workspace }.map { it.customerId }.toSet() +
            payments.filter { it.workspaceId == workspace }.map { it.customerId }.toSet()
        val retainedCustomers = runCatching { retained.filter { it.workspaceId == workspace && it.state !in setOf("ACKNOWLEDGED", "INVALIDATED") }
            .flatMap { com.example.data.sync.OperationDependencies.references(it) }.filter { it.first == "CUSTOMER" }.map { it.second }.toSet()
        }.getOrElse { return emptyList() }
        return customers.filter { it.workspaceId == workspace && it.id !in referenced && it.id !in retainedCustomers }.distinctBy { it.id }.sortedBy { it.name }
    }
}

@Composable
fun UnusedCustomersDialog(customers: List<CustomerEntity>, isTamil: Boolean, onDismiss: () -> Unit, onDelete: (CustomerEntity) -> Unit) {
    var selected by remember { mutableStateOf<CustomerEntity?>(null) }
    AlertDialog(onDismissRequest=onDismiss, title={Text(if(isTamil) "பதிவுகள் இல்லாத வாடிக்கையாளர்கள்" else "Customers without records")},
        text={LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(customers.isEmpty()) item { Text(if(isTamil) "வாடிக்கையாளர்கள் இல்லை" else "No unused customer profiles") }
            items(customers,key={it.id}) { c -> Row(Modifier.fillMaxWidth()) {
                Text(c.name,Modifier.weight(1f))
                TextButton(onClick={selected=c},Modifier.testTag("delete_unused_customer_${c.id}")) { Text(if(isTamil) "நீக்கு" else "Delete") }
            } }
        }},confirmButton={TextButton(onClick=onDismiss){Text(if(isTamil) "மூடு" else "Close")}})
    selected?.let { c -> AlertDialog(onDismissRequest={selected=null}, title={Text(if(isTamil) "வாடிக்கையாளரை நீக்கவா?" else "Delete unused customer?")},
        text={Text(c.name+"\n"+(if(isTamil) "வேலை அல்லது வசூல் வரலாறு இருந்தால் நீக்க முடியாது. பணப் பதிவுகள் நீக்கப்படாது."
            else "Deletion is refused if any job or collection history exists. Financial records are preserved."))},
        confirmButton={TextButton(onClick={selected=null;onDelete(c)},Modifier.testTag("confirm_unused_customer_delete")){Text(if(isTamil) "நீக்கு" else "Delete")}},
        dismissButton={TextButton(onClick={selected=null}){Text(if(isTamil) "ரத்து" else "Cancel")}}) }
}
