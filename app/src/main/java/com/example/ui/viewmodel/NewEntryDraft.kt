package com.example.ui.viewmodel

import java.util.Calendar

data class FixedWorkType(val en: String, val ta: String)

val FIXED_WORK_TYPES = listOf(
    FixedWorkType("Rotavator", "ரோட்டாவேட்டர்"),
    FixedWorkType("5 Claws", "5 கலப்பை"),
    FixedWorkType("9 Claws", "9 கலப்பை"),
    FixedWorkType("Loader", "லோடர்")
)

data class DraftExpenseItem(
    val id: Long = 0L,
    val expenseType: String = "Diesel",
    val amount: Double = 0.0,
    val paidBy: String = "I Paid", // "I Paid" or "Business Paid"
    val paidByPartner: String = "",
    val paidByUid: String = ""
)

/** Personal funding in New Entry belongs to the selected member; edits retain the original payer. */
fun linkedExpensePayerUid(paidBy: String, retainedPayerUid: String, selectedMemberUid: String): String =
    if (paidBy.equals("I Paid", ignoreCase = true)) retainedPayerUid.ifBlank { selectedMemberUid } else ""

/**
 * Authoritative single source of truth for the New Work Entry draft.
 * Preserves user inputs across in-app navigation (Home, Reports, Account, etc.)
 * until explicitly cleared by the user or after a successful save.
 */
data class NewEntryDraft(
    val entryId: Long = 0L,
    val linkedExpenseId: Long = 0L,
    val isReviewScreenVisible: Boolean = false,
    val selectedTractor: String = "",
    val isTractorLocked: Boolean = false,
    val selectedPersonUid: String = "",
    val selectedPersonName: String = "",
    val isPersonLocked: Boolean = false,
    val isEditingExistingEntry: Boolean = false,
    val selectedWorkType: String = "Rotavator",
    val customerNameInput: String = "",
    val customerPhoneInput: String = "",
    val customerLocationInput: String = "",
    val matchedCustomerId: Long = 0L,
    val createNewCustomer: Boolean = false,
    val isDirectDurationMode: Boolean = true,
    val manualHoursInput: String = "",
    val manualMinutesInput: String = "",
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val hourlyRateInput: String = "",
    val customWorkAmountInput: String = "",
    val isWorkAmountManuallyEdited: Boolean = false,
    val extraChargesInput: String = "",
    val amountReceivedInput: String = "0",
    val includeLinkedExpense: Boolean = false,
    val linkedExpenseType: String = "Diesel",
    val linkedExpenseAmountInput: String = "",
    val linkedExpenseDesc: String = "",
    val expensesList: List<DraftExpenseItem> = emptyList(),
    val notes: String = "",
    val hasAttemptedReview: Boolean = false,
    val isOldEntry: Boolean = false,
    val historicalDateMillis: Long? = null,
    val originalCreatedAt: Long? = null,
    val originalAddedByPartner: String = "",
    val originalCreatedByUid: String = "",
    val originalCreatedByRole: String = "",
    val originalCustomerPhone: String? = null,
    val isCustomerPhoneManuallyEdited: Boolean = false,
    val originalCustomerName: String? = null,
    val isCustomerNameManuallyEdited: Boolean = false,
    val originalCustomerId: Long = 0L,
    val originalJob: com.example.data.entity.JobEntryEntity? = null
) {
    /** Reopening a minute/whole-rupee form must not round untouched historical values. */
    fun retainUneditedWork(submitted: com.example.data.entity.JobEntryEntity): com.example.data.entity.JobEntryEntity {
        val original = originalJob ?: return submitted
        val initial = fromJobEntry(original)
        val sameTime = isDirectDurationMode == initial.isDirectDurationMode && manualHoursInput == initial.manualHoursInput &&
            manualMinutesInput == initial.manualMinutesInput && startHour == initial.startHour && startMinute == initial.startMinute &&
            endHour == initial.endHour && endMinute == initial.endMinute && historicalDateMillis == initial.historicalDateMillis
        val sameRate = hourlyRateInput == initial.hourlyRateInput
        val sameBill = sameTime && sameRate && customWorkAmountInput == initial.customWorkAmountInput &&
            isWorkAmountManuallyEdited == initial.isWorkAmountManuallyEdited && extraChargesInput == initial.extraChargesInput
        val sameReceived = amountReceivedInput == initial.amountReceivedInput
        val total = if (sameBill) original.totalAmount else submitted.totalAmount
        val paid = if (sameReceived) original.amountReceived else submitted.amountReceived
        return submitted.copy(startTimeMillis = if (sameTime) original.startTimeMillis else submitted.startTimeMillis,
            endTimeMillis = if (sameTime) original.endTimeMillis else submitted.endTimeMillis,
            durationMinutes = if (sameTime) original.durationMinutes else submitted.durationMinutes,
            hourlyRate = if (sameRate) original.hourlyRate else submitted.hourlyRate,
            totalAmount = total, amountReceived = paid, pendingAmount = if (sameBill && sameReceived) original.pendingAmount else total - paid)
    }

    val formattedHistoricalDate: String
        get() = historicalDateMillis?.let {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: ""

    /**
     * Determines whether the user has entered any non-default data into the form.
     * Used to decide whether to prompt the user with a confirmation dialog before clearing.
     */
    fun isModified(): Boolean {
        return customerNameInput.isNotBlank() ||
                customerPhoneInput.isNotBlank() ||
                customerLocationInput.isNotBlank() ||
                manualHoursInput.isNotBlank() ||
                manualMinutesInput.isNotBlank() ||
                endHour != null ||
                endMinute != null ||
                customWorkAmountInput.isNotBlank() ||
                isWorkAmountManuallyEdited ||
                extraChargesInput.isNotBlank() ||
                (amountReceivedInput.isNotBlank() && amountReceivedInput != "0") ||
                includeLinkedExpense ||
                linkedExpenseAmountInput.isNotBlank() ||
                linkedExpenseDesc.isNotBlank() ||
                expensesList.isNotEmpty() ||
                notes.isNotBlank() ||
                isReviewScreenVisible
    }

    companion object {
        fun createDefault(
            defaultTractor: String = "",
            lockedTractor: String = "",
            defaultHourlyRate: Double = 0.0,
            defaultPersonUid: String = "",
            defaultPersonName: String = "",
            lockedPersonUid: String = "",
            lockedPersonName: String = ""
        ): NewEntryDraft {
            val now = Calendar.getInstance()
            val tractor = if (lockedTractor.isNotBlank()) lockedTractor else defaultTractor
            val rateStr = if (defaultHourlyRate > 0.0) defaultHourlyRate.toInt().toString() else ""
            val personUid = if (lockedPersonUid.isNotBlank()) lockedPersonUid else defaultPersonUid
            val personName = if (lockedPersonName.isNotBlank()) lockedPersonName else defaultPersonName
            return NewEntryDraft(
                entryId = com.example.data.util.IdGenerator.generateId(),
                linkedExpenseId = com.example.data.util.IdGenerator.generateId(),
                selectedTractor = tractor,
                isTractorLocked = lockedTractor.isNotBlank(),
                selectedPersonUid = personUid,
                selectedPersonName = personName,
                isPersonLocked = lockedPersonUid.isNotBlank(),
                isEditingExistingEntry = false,
                isDirectDurationMode = true,
                startHour = now.get(Calendar.HOUR_OF_DAY),
                startMinute = now.get(Calendar.MINUTE),
                hourlyRateInput = rateStr,
                customWorkAmountInput = "",
                isWorkAmountManuallyEdited = false,
                amountReceivedInput = "0"
            )
        }

        fun fromJobEntry(
            job: com.example.data.entity.JobEntryEntity,
            linkedExpenses: List<com.example.data.entity.ExpenseEntity> = emptyList()
        ): NewEntryDraft {
            val startCal = if (job.startTimeMillis > 0L) Calendar.getInstance().apply { timeInMillis = job.startTimeMillis } else null
            val endCal = if (job.endTimeMillis > 0L) Calendar.getInstance().apply { timeInMillis = job.endTimeMillis } else null

            val calculatedAmt = if (job.hourlyRate > 0.0 && job.durationMinutes > 0L) {
                (job.durationMinutes / 60.0) * job.hourlyRate
            } else 0.0

            val isCustomAmt = (job.totalAmount > 0.0 && Math.abs(job.totalAmount - calculatedAmt) > 1.0) || (job.totalAmount > 0.0 && job.hourlyRate <= 0.0)
            val amountStr = if (job.totalAmount > 0.0) {
                Math.round(job.totalAmount).toString()
            } else ""

            val entryForName = job.operatorName.ifBlank { job.addedByPartner }
            val amountReceivedStr = if (job.amountReceived > 0.0) {
                Math.round(job.amountReceived).toString()
            } else if (job.pendingAmount <= 0.0 && job.totalAmount > 0.0) {
                amountStr
            } else "0"

            val jobDateMillis = if (job.startTimeMillis > 0L) job.startTimeMillis else job.createdAt

            val isOld = job.isHistorical

            val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(job.customerPhone)

            val draftExpenses = linkedExpenses.map { exp ->
                DraftExpenseItem(
                    id = exp.id,
                    expenseType = exp.expenseType,
                    amount = exp.amount,
                    paidBy = exp.paidBy,
                    paidByPartner = exp.paidByPartner,
                    paidByUid = exp.paidByUid
                )
            }

            return NewEntryDraft(
                entryId = job.id,
                selectedTractor = job.tractorLabel,
                selectedWorkType = job.workType,
                selectedPersonUid = job.entryForUid,
                selectedPersonName = entryForName,
                isPersonLocked = false,
                isEditingExistingEntry = true,
                isOldEntry = isOld,
                customerNameInput = job.customerName,
                customerPhoneInput = if (cleanPhone.isNotBlank()) cleanPhone else job.customerPhone,
                originalCustomerPhone = job.customerPhone,
                isCustomerPhoneManuallyEdited = false,
                originalCustomerName = job.customerName,
                isCustomerNameManuallyEdited = false,
                originalCustomerId = job.customerId,
                originalJob = job,
                customerLocationInput = job.customerLocation,
                matchedCustomerId = job.customerId,
                originalCreatedAt = job.createdAt,
                originalAddedByPartner = job.addedByPartner.ifBlank { job.operatorName },
                originalCreatedByUid = job.createdByUid,
                originalCreatedByRole = job.createdByRole,
                isDirectDurationMode = (startCal == null || endCal == null),
                startHour = startCal?.get(Calendar.HOUR_OF_DAY),
                startMinute = startCal?.get(Calendar.MINUTE),
                endHour = endCal?.get(Calendar.HOUR_OF_DAY),
                endMinute = endCal?.get(Calendar.MINUTE),
                manualHoursInput = (job.durationMinutes / 60).toString(),
                manualMinutesInput = (job.durationMinutes % 60).toString(),
                hourlyRateInput = if (job.hourlyRate > 0.0) job.hourlyRate.toInt().toString() else "",
                customWorkAmountInput = if (isCustomAmt) amountStr else (if (job.totalAmount > 0.0 && calculatedAmt <= 0.0) amountStr else ""),
                isWorkAmountManuallyEdited = isCustomAmt,
                extraChargesInput = "",
                amountReceivedInput = amountReceivedStr,
                includeLinkedExpense = false,
                expensesList = draftExpenses,
                notes = job.notes,
                historicalDateMillis = jobDateMillis
            )
        }

        fun createHistorical(
            dateMillis: Long,
            defaultTractor: String = "",
            lockedTractor: String = "",
            defaultHourlyRate: Double = 0.0,
            defaultPersonUid: String = "",
            defaultPersonName: String = "",
            lockedPersonUid: String = "",
            lockedPersonName: String = ""
        ): NewEntryDraft {
            val baseDraft = createDefault(
                defaultTractor = defaultTractor,
                lockedTractor = lockedTractor,
                defaultHourlyRate = defaultHourlyRate,
                defaultPersonUid = defaultPersonUid,
                defaultPersonName = defaultPersonName,
                lockedPersonUid = lockedPersonUid,
                lockedPersonName = lockedPersonName
            )
            return baseDraft.copy(
                isOldEntry = true,
                historicalDateMillis = dateMillis,
                originalCreatedAt = null
            )
        }
    }
}
