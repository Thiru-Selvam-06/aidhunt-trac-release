package com.example

import com.example.data.entity.*
import com.example.data.sync.*
import com.example.ui.util.CustomerIdentity
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.viewmodel.NewEntryDraft
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomerIdentityEditingTest {
    private fun customer(id: Long = 1, name: String = "saga jothi", ws: String = "a") = CustomerEntity(
        id = id, workspaceId = ws, name = name, phone = "9876543210", location = "Village", createdAt = 100, createdByUid = "owner")
    private fun job(id: Long = 10, customerId: Long = 1, amount: Double = 1000.0, paid: Double = 0.0) = JobEntryEntity(
        id = id, workspaceId = "a", customerId = customerId, customerName = "historical snapshot", customerPhone = "9123456780",
        operatorName = "Original Partner", tractorId = 3, tractorLabel = "Tractor", workType = "Rotavator", startTimeMillis = 100123,
        endTimeMillis = 160123, durationMinutes = 1, hourlyRate = 1100.25, totalAmount = amount, amountReceived = paid,
        pendingAmount = amount - paid, addedByPartner = "Recorder", createdByUid = "partner", createdAt = 100,
        entryForUid = "beneficiary", financialAttributionUid = "beneficiary")
    private fun receipt(source: JobEntryEntity, paid: Double = 200.0) = PaymentEntity(id = 20, workspaceId = "a", jobEntryId = source.id,
        customerId = source.customerId, customerName = "original name", amount = paid, collectedByUid = "partner",
        collectedAt = 100, createdAt = 100, allocationsJson = "[{\"jobId\":${source.id},\"amount\":$paid}]")

    @Test fun capitalizationRenamePreservesIdentityMoneyAndHistory() {
        val before = customer().copy(totalBilled = 1000.0, totalPaid = 200.0, balanceDue = 800.0)
        val after = CustomerProfileEdit.apply(before, CustomerProfileEdit.capture(before, before.copy(name = "Saga Jothi")))
        assertEquals(before.copy(name = "Saga Jothi"), after)
    }
    @Test fun phoneOnlyEditPreservesNameIncludingOriginalSpacing() {
        val before = customer(name = " Saga Jothi ")
        assertEquals(before.copy(phone = ""), CustomerProfileEdit.apply(before, CustomerProfileEdit.capture(before, before.copy(phone = ""))))
    }
    @Test fun independentOfflineEditsMergeWithoutStaleOverwrite() {
        val before = customer(); val phone = CustomerProfileEdit.capture(before, before.copy(phone = "9123456780"))
        val renamed = before.copy(name = "Saga Jothi")
        assertEquals(renamed.copy(phone = "9123456780"), CustomerProfileEdit.apply(renamed, phone))
    }
    @Test fun conflictingSameFieldRetainsExplicitFailure() {
        val before = customer(); val patch = CustomerProfileEdit.capture(before, before.copy(name = "Saga Jothi"))
        try { CustomerProfileEdit.apply(before.copy(name = "Different name"), patch); fail("Must not overwrite concurrent name") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("conflict")) }
        assertEquals("saga jothi", before.name)
    }
    @Test fun repeatedPatchIsIdempotent() {
        val before = customer(); val patch = CustomerProfileEdit.capture(before, before.copy(name = "Saga Jothi"))
        val after = CustomerProfileEdit.apply(before, patch)
        assertEquals(after, CustomerProfileEdit.apply(after, patch))
    }
    @Test fun invalidNamesAndCrossWorkspaceIdentityAreRejected() {
        for (after in listOf(customer().copy(name = " "), customer(2), customer(ws = "b"))) {
            try { CustomerProfileEdit.capture(customer(), after); fail("Invalid profile edit") } catch (_: IllegalArgumentException) {}
        }
        try { CustomerProfileEdit.changes("{\"totalPaid\":{\"before\":0,\"after\":99}}", emptyMap()); fail("Money is not editable profile data") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun displayProjectionUsesScopedIdAndNeverRewritesSnapshots() {
        val original = job(); val profiles = listOf(customer(name = "Saga Jothi"), customer(name = "Foreign", ws = "b"))
        val displayed = CustomerIdentity.jobs(listOf(original), profiles).single()
        assertEquals(original.copy(customerName = "Saga Jothi", customerPhone = profiles.first().phone,
            customerLocation = "Village"), displayed)
        assertEquals("historical snapshot", original.customerName)
        assertEquals(original, CustomerIdentity.jobs(listOf(original), listOf(customer(ws = "b"))).single())
    }
    @Test fun sameNameCustomersRemainSeparateAndCaseSearchFindsBoth() {
        val profiles = listOf(customer(1,"Raman"), customer(2,"RAMAN"))
        assertEquals(2, profiles.filter { CustomerIdentity.matches(it.name,"raman") }.map { it.id }.distinct().size)
        assertEquals(1L, CustomerIdentity.jobs(listOf(job()), profiles).single().customerId)
    }
    @Test fun unicodeNormalizationOnlyChangesSearchKeys() {
        val original = "கொடி"; val decomposed = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD)
        assertTrue(CustomerIdentity.matches(original,decomposed))
        assertTrue(CustomerIdentity.matches("Saga Jothi","SAGA")); assertEquals("கொடி", original)
    }
    @Test fun reassignmentMovesOnlyChosenDueAndConservesWorkspaceTotal() {
        val a = customer(1); val b = customer(2,"Other")
        val source = listOf(job(10,1,800.0),job(11,1,1000.0),job(12,2,500.0))
        val moved = source.map { if (it.id == 11L) it.copy(customerId = b.id) else it }
        assertEquals(800.0, FinancialCalculationEngine.calculateCustomerFinancials(a,moved).balanceDue,0.0)
        assertEquals(1500.0, FinancialCalculationEngine.calculateCustomerFinancials(b,moved).balanceDue,0.0)
        assertEquals(2300.0,moved.sumOf { it.pendingAmount },0.0)
        assertEquals(source.filter { it.id != 11L },moved.filter { it.id != 11L })
    }
    @Test fun partiallyAndFullyPaidMovesPreserveReceiptIdentityAndAmounts() {
        for (paid in listOf(200.0,1000.0)) {
            val source = job(paid=paid); val receipt = receipt(source,paid)
            JobCustomerReassignment.requireSafe(source,listOf(receipt),emptyList())
            val moved = source.copy(customerId = 2)
            val projected = CustomerIdentity.payments(listOf(receipt), listOf(customer(2,"Target")), listOf(moved)).single()
            assertEquals(receipt.copy(customerId = 2, customerName = "Target"), projected)
            assertEquals(1L,receipt.customerId); assertEquals(paid,receipt.amount,0.0)
            assertTrue(JobCustomerReassignment.singleSource(receipt,moved))
        }
    }
    @Test fun multiJobOrMalformedCollectionsBlockReassignment() {
        val source=job(paid=200.0)
        for (invalid in listOf(receipt(source).copy(allocationsJson="[{\"jobId\":10,\"amount\":100},{\"jobId\":11,\"amount\":100}]"),
            receipt(source).copy(jobEntryId=99),receipt(source).copy(workspaceId="b"),receipt(source).copy(allocationsJson="bad"))) {
            assertFalse(JobCustomerReassignment.singleSource(invalid,source))
        }
        try {JobCustomerReassignment.requireSafe(source,listOf(receipt(source).copy(jobEntryId=99)),emptyList());fail("Inconsistent source")}
        catch (_:IllegalArgumentException) {}
    }
    @Test fun pendingOrRejectedCollectionCannotBeSilentlyMovedOrRetried() {
        val source=job()
        for (state in listOf("PENDING","REJECTED")) {
            val op=PendingOperationEntity("original-operation","a","partner","PAYMENT",20,"UPSERT",1,100,
                "{\"jobEntryId\":10,\"customerId\":1}",state=state)
            try { JobCustomerReassignment.requireSafe(source,emptyList(),listOf(op)); fail("Unresolved receipt") }
            catch (e:IllegalArgumentException) { assertTrue(e.message!!.contains("retained")) }
            assertEquals(state,op.state);assertEquals("original-operation",op.operationId)
        }
    }
    @Test fun missingReceiptBlocksPaidJobButUnpaidJobRemainsEligible() {
        JobCustomerReassignment.requireSafe(job(),emptyList(),emptyList())
        try {JobCustomerReassignment.requireSafe(job(paid=200.0),emptyList(),emptyList());fail("Embedded paid history needs review")}
        catch (_:IllegalArgumentException) {}
    }
    @Test fun untouchedJobWorkFieldsSurviveReassignmentFormRounding() {
        val original=job(amount=10358.75,paid=200.25); val draft=NewEntryDraft.fromJobEntry(original)
        val submitted=original.copy(customerId=2,startTimeMillis=100000,endTimeMillis=160000,hourlyRate=1100.0,
            totalAmount=10359.0,amountReceived=200.0,pendingAmount=10159.0)
        assertEquals(original.copy(customerId=2),draft.retainUneditedWork(submitted))
        assertEquals(500.0,draft.copy(customWorkAmountInput="500").retainUneditedWork(submitted.copy(totalAmount=500.0)).totalAmount,0.0)
    }
    @Test fun customerProjectionScalesToRequestedSyntheticSizes() {
        for(size in listOf(100,1000,10000)) {
            val profiles=(1..size).map{customer(it.toLong(),"Person $it")}
            val rows=profiles.map{job(it.id,it.id)};val start=System.nanoTime()
            val displayed=CustomerIdentity.jobs(rows,profiles)
            assertEquals(size,displayed.size);assertEquals("Person $size",displayed.last().customerName)
            println("CUSTOMER_IDENTITY_PERFORMANCE size=$size elapsedMs=${(System.nanoTime()-start)/1000000}")
        }
    }
    @Test fun reassignmentFormPreservesLinkedExpenseHistoryAndOnlyAppliesExpenseIntent() {
        val original=ExpenseEntity(id=80,workspaceId="a",expenseType="Diesel",amount=900.0,tractorLabel="Tractor",
            addedByPartner="Original Recorder",dateTimestamp=123456,createdAt=123450,createdByUid="original-recorder",
            paidBy="Business Paid",relatedJobId=10,description="Original evidence")
        val draft=NewEntryDraft.fromJobEntry(job(),listOf(original))
        val generated=original.copy(description="Expense for new customer's job",dateTimestamp=999999,createdAt=999999,addedByPartner="Other")
        assertEquals(listOf(original),draft.retainExpenseHistory(listOf(generated)))
        assertEquals(listOf(original.copy(amount=950.0)),draft.retainExpenseHistory(listOf(generated.copy(amount=950.0))))
    }
}
