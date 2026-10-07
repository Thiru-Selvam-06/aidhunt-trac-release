package com.example.ui.screens.entry

import com.example.ui.utils.trackFocusedField
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Refresh
import com.example.ui.viewmodel.DraftExpenseItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.ui.components.DetailRow
import com.example.ui.components.PartnerAvatarImage
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.screens.report.ExpenseTypes
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.EarthGold
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.FIXED_WORK_TYPES
import com.example.ui.viewmodel.FixedWorkType
import com.example.ui.viewmodel.NewEntryDraft
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private enum class FocusedField {
    NONE,
    NAME,
    PHONE,
    LOCATION,
    HOURS,
    MINUTES,
    HOURLY_RATE,
    WORK_AMOUNT,
    EXTRA_CHARGES,
    AMOUNT_RECEIVED,
    EXPENSE_AMOUNT,
    EXPENSE_DESC,
    NOTES
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewEntryScreen(
    settings: AppSettingsEntity,
    tractors: List<TractorEntity>,
    customers: List<CustomerEntity>,
    jobs: List<JobEntryEntity> = emptyList(),
    draft: NewEntryDraft,
    isSaving: Boolean = false,
    isCollaborationOwner: Boolean = false,
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    workTypeExtensions: List<com.example.data.entity.WorkTypeExtensionEntity> = emptyList(),
    currentUid: String = "",
    onUpdateDraft: (NewEntryDraft) -> Unit,
    onClearDraft: () -> Unit,
    onSaveJob: (JobEntryEntity, ExpenseEntity?, List<ExpenseEntity>) -> Unit,
    onUpdateLockedTractor: ((String) -> Unit)? = null,
    onUpdateLockedPerson: ((String) -> Unit)? = null,
    onResolvePersonalRate: (String) -> Double = { 1100.0 }
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)

    val allAvailableWorkTypes: List<FixedWorkType> = remember(workTypeExtensions) {
        val builtIn: List<FixedWorkType> = FIXED_WORK_TYPES
        val custom: List<FixedWorkType> = workTypeExtensions.map { ext: WorkTypeExtensionEntity ->
            FixedWorkType(en = ext.name, ta = ext.name)
        }
        builtIn + custom
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        Log.d("TRAC_ENTRY", "NewEntryScreen OPEN wsId=${settings.workspaceId}")
    }

    var localIsSaving by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isSaving) {
        if (!isSaving) {
            localIsSaving = false
        }
    }
    val effectiveIsSaving = isSaving || localIsSaving

    // Handle Back Press when on Review Screen or Dialogs to prevent accidental screen close
    BackHandler(enabled = draft.isReviewScreenVisible) {
        Log.d("TRAC_ENTRY", "BackHandler: exiting review screen back to edit form")
        if (!effectiveIsSaving) {
            onUpdateDraft(draft.copy(isReviewScreenVisible = false))
        }
    }

    // 1. Partner / Person Identification & Owner Selection Logic
    val ownerDisplayName = settings.ownerName.ifBlank { settings.activePartnerName.ifBlank { "Owner" } }
    val currentPartnerName = settings.activePartnerName.ifBlank { settings.ownerName.ifBlank { "Partner" } }

    val activeRegisteredPartners = remember(workspaceMembers, currentUid) {
        workspaceMembers
            .filter { member ->
                member.status.equals("active", ignoreCase = true) &&
                member.uid.isNotBlank() &&
                member.uid != currentUid
            }
            .distinctBy { it.uid }
    }

    val effectivePersonName = if (isCollaborationOwner) {
        if (draft.selectedPersonName.isNotBlank()) draft.selectedPersonName else ownerDisplayName
    } else {
        currentPartnerName
    }

    // 2. Tractor Selection & Lock State
    val ownTractors = remember(tractors, settings.workspaceId) {
        tractors.filter { it.workspaceId == settings.workspaceId }
    }
    val selectedTractor = if (draft.selectedTractor.isNotBlank() && tractors.any { it.label == draft.selectedTractor }) {
        draft.selectedTractor
    } else if (settings.lockedTractorLabel.isNotBlank() && tractors.any { it.label == settings.lockedTractorLabel }) {
        settings.lockedTractorLabel
    } else {
        ownTractors.firstOrNull()?.label ?: ""
    }
    val isTractorLocked = draft.isTractorLocked
    val selectedWorkType = draft.selectedWorkType

    var tractorDropdownExpanded by remember { mutableStateOf(false) }
    var workTypeDropdownExpanded by remember { mutableStateOf(false) }

    // 3. Customer Details
    val customerNameInput = draft.customerNameInput
    val customerPhoneInput = draft.customerPhoneInput
    val customerLocationInput = draft.customerLocationInput
    val matchedCustomerId = draft.matchedCustomerId
    var showSuggestions by remember { mutableStateOf(false) }
    var showAddExpenseModal by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<DraftExpenseItem?>(null) }

    // 4. Time Selection: Manual Duration vs Clock Time Option
    val isDirectDurationMode = draft.isDirectDurationMode
    val manualHoursInput = draft.manualHoursInput
    val manualMinutesInput = draft.manualMinutesInput

    val startHour = draft.startHour
    val startMinute = draft.startMinute
    val endHour = draft.endHour
    val endMinute = draft.endMinute

    val computedDurationMinutes: Long = if (isDirectDurationMode) {
        com.example.ui.util.WorkBillingCalculator.calculateDurationFromManual(manualHoursInput, manualMinutesInput)
    } else {
        com.example.ui.util.WorkBillingCalculator.calculateDurationFromClock(startHour, startMinute, endHour, endMinute)
    }

    // 5. Payment Details
    val hourlyRateInput = draft.hourlyRateInput
    val hourlyRate = hourlyRateInput.toDoubleOrNull() ?: 0.0

    val calculatedWorkAmount = com.example.ui.util.WorkBillingCalculator.calculateAmount(computedDurationMinutes, hourlyRate)
    val isWorkAmountManuallyEdited = draft.isWorkAmountManuallyEdited
    val customWorkAmountInput = draft.customWorkAmountInput
    val displayedWorkAmount = if (isWorkAmountManuallyEdited) {
        customWorkAmountInput
    } else if (customWorkAmountInput.isNotBlank() && calculatedWorkAmount <= 0.0) {
        customWorkAmountInput
    } else {
        if (calculatedWorkAmount > 0.0) String.format(Locale.US, "%.0f", calculatedWorkAmount) else ""
    }
    val baseWorkAmount = if (isWorkAmountManuallyEdited && customWorkAmountInput.isNotBlank()) {
        com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customWorkAmountInput.toDoubleOrNull() ?: 0.0)
    } else if (customWorkAmountInput.isNotBlank() && calculatedWorkAmount <= 0.0) {
        com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customWorkAmountInput.toDoubleOrNull() ?: 0.0)
    } else {
        com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(calculatedWorkAmount)
    }

    val extraChargesInput = draft.extraChargesInput
    var isExtraChargesEnabled by remember(draft.entryId, draft.extraChargesInput.isNotBlank()) {
        mutableStateOf(draft.extraChargesInput.isNotBlank() && (draft.extraChargesInput.toDoubleOrNull() ?: 0.0) > 0.0)
    }
    val extraCharges = if (isExtraChargesEnabled || extraChargesInput.isNotBlank()) {
        com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(extraChargesInput.toDoubleOrNull() ?: 0.0)
    } else 0.0

    val finalTotalAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(baseWorkAmount + extraCharges)

    val amountReceivedInput = draft.amountReceivedInput
    val amountReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(amountReceivedInput.toDoubleOrNull() ?: 0.0)
    val pendingAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, finalTotalAmount - amountReceived))

    // 6. Optional Linked Expense
    val includeLinkedExpense = draft.includeLinkedExpense
    val linkedExpenseType = draft.linkedExpenseType
    val linkedExpenseAmountInput = draft.linkedExpenseAmountInput
    val linkedExpenseDesc = draft.linkedExpenseDesc
    var expenseTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Notes
    val notes = draft.notes

    // Validation & Clear State
    val hasAttemptedReview = draft.hasAttemptedReview
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val tractorRequester = remember { BringIntoViewRequester() }
    val workTypeRequester = remember { BringIntoViewRequester() }
    val expenseTypeRequester = remember { BringIntoViewRequester() }
    val customerNameRequester = remember { BringIntoViewRequester() }
    val customerPhoneRequester = remember { BringIntoViewRequester() }
    val customerLocationRequester = remember { BringIntoViewRequester() }
    val hoursRequester = remember { BringIntoViewRequester() }
    val minutesRequester = remember { BringIntoViewRequester() }
    val hourlyRateRequester = remember { BringIntoViewRequester() }
    val workAmountRequester = remember { BringIntoViewRequester() }
    val extraChargesRequester = remember { BringIntoViewRequester() }
    val amountReceivedRequester = remember { BringIntoViewRequester() }
    val expenseAmountRequester = remember { BringIntoViewRequester() }
    val expenseDescRequester = remember { BringIntoViewRequester() }
    val notesRequester = remember { BringIntoViewRequester() }

    BackHandler(enabled = showClearConfirmationDialog) {
        Log.d("TRAC_ENTRY", "BackHandler: dismissing clear confirmation dialog")
        showClearConfirmationDialog = false
    }

    val isTractorInvalid = selectedTractor.isBlank() || (tractors.isNotEmpty() && tractors.none { it.label == selectedTractor })
    val isCustomerNameInvalid = customerNameInput.isBlank()
    val isPhoneRequired = false
    val isCustomerPhoneInvalid = customerPhoneInput.isNotBlank() && (customerPhoneInput.length != 10 || !customerPhoneInput.all { it.isDigit() })
    val isTimeInvalid = computedDurationMinutes <= 0L
    val isWorkAmountInvalid = if (isWorkAmountManuallyEdited) {
        (customWorkAmountInput.toDoubleOrNull() ?: 0.0) <= 0.0
    } else {
        calculatedWorkAmount <= 0.0
    }
    val isAmountReceivedExcess = amountReceived > (finalTotalAmount + 0.001)
    val isAmountReceivedInvalid = amountReceivedInput.isBlank() || amountReceivedInput.toDoubleOrNull() == null || amountReceived < 0.0 || isAmountReceivedExcess
    val isLinkedExpenseInvalid = includeLinkedExpense && ((linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) <= 0.0 || linkedExpenseType.isBlank())

    val isSystem24Hour = android.text.format.DateFormat.is24HourFormat(context)

    fun formatTimeDisplay(hour: Int?, minute: Int?): String {
        if (hour == null || minute == null) return "--:--"
        return if (isSystem24Hour) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            val amPm = if (hour < 12) "AM" else "PM"
            val h = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.US, "%02d:%02d %s", h, minute, amPm)
        }
    }

    // Scoped strictly to active canonical workspace
    val activeWsCustomers = remember(customers, settings.workspaceId) {
        val currentWs = settings.workspaceId.trim()
        if (currentWs.isNotBlank()) {
            customers.filter { it.workspaceId.isBlank() || it.workspaceId == currentWs }
        } else {
            customers
        }
    }

    val nameSuggestions = remember(customerNameInput, activeWsCustomers, matchedCustomerId) {
        val q = customerNameInput.trim()
        if (q.isNotEmpty() && matchedCustomerId == 0L) {
            activeWsCustomers
                .filter { it.name.trim().contains(q, ignoreCase = true) }
                .distinctBy { it.id.takeIf { id -> id > 0L } ?: (it.name.trim().lowercase() + "_" + it.phone) }
        } else {
            emptyList()
        }
    }

    val phoneSuggestions = remember(customerPhoneInput, activeWsCustomers, matchedCustomerId) {
        val queryDigits = customerPhoneInput.filter { it.isDigit() }
        if (queryDigits.isNotEmpty() && matchedCustomerId == 0L) {
            activeWsCustomers
                .filter { c ->
                    val cDigits = c.phone.filter { it.isDigit() }
                    cDigits.contains(queryDigits)
                }
                .distinctBy { it.id.takeIf { id -> id > 0L } ?: (it.name.trim().lowercase() + "_" + it.phone) }
        } else {
            emptyList()
        }
    }

    val resolveRateForCustomer: (CustomerEntity) -> Double = { c ->
        // 1. Customer-specific rate from customer's latest local job
        val custRate = jobs.filter {
            (it.customerId > 0L && it.customerId == c.id) ||
            (c.name.isNotBlank() && it.customerName.trim().equals(c.name.trim(), ignoreCase = true))
        }.maxByOrNull { it.createdAt }?.hourlyRate?.takeIf { it > 0.0 }

        // 2. Tractor/default rate from tractor's latest local job
        val tractorRate = if (draft.selectedTractor.isNotBlank()) {
            jobs.filter {
                it.tractorLabel.equals(draft.selectedTractor, ignoreCase = true)
            }.maxByOrNull { it.createdAt }?.hourlyRate?.takeIf { it > 0.0 }
        } else null

        // 3. Fallback: personal default rate of the person the entry is for
        val targetPersonUid = draft.selectedPersonUid.ifBlank { currentUid }
        val personalRate = onResolvePersonalRate(targetPersonUid).takeIf { it > 0.0 }
        val businessRate = settings.defaultHourlyRate.takeIf { it > 0.0 }

        custRate ?: tractorRate ?: personalRate ?: businessRate ?: 1100.0
    }

    val onSelectCustomer: (CustomerEntity) -> Unit = { c ->
        val resolvedRate = resolveRateForCustomer(c)
        val rateStr = if (resolvedRate > 0.0) {
            if (resolvedRate % 1.0 == 0.0) resolvedRate.toInt().toString() else resolvedRate.toString()
        } else {
            draft.hourlyRateInput
        }
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(c.phone)
        onUpdateDraft(
            draft.copy(
                customerNameInput = c.name,
                isCustomerNameManuallyEdited = true,
                customerPhoneInput = if (cleanPhone.isNotBlank()) cleanPhone else c.phone,
                isCustomerPhoneManuallyEdited = true,
                customerLocationInput = c.location,
                matchedCustomerId = c.id,
                hourlyRateInput = if (rateStr.isNotBlank()) rateStr else draft.hourlyRateInput
            )
        )
        showSuggestions = false
    }

    val handleClearAction = {
        if (draft.isModified()) {
            showClearConfirmationDialog = true
        } else {
            onClearDraft()
        }
    }

    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    if (draft.isReviewScreenVisible) {
        // Full Dedicated Review Page
        val baseDateMillis = if (draft.historicalDateMillis != null && draft.historicalDateMillis > 0L) {
            draft.historicalDateMillis
        } else if (draft.originalCreatedAt != null && draft.originalCreatedAt > 0L) {
            draft.originalCreatedAt
        } else {
            System.currentTimeMillis()
        }
        val startMillis: Long
        val endMillis: Long
        if (isDirectDurationMode || startHour == null || startMinute == null || endHour == null || endMinute == null) {
            val cal = Calendar.getInstance().apply { timeInMillis = baseDateMillis }
            startMillis = cal.timeInMillis - (computedDurationMinutes * 60 * 1000L)
            endMillis = cal.timeInMillis
        } else {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = baseDateMillis
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
            }
            startMillis = calendar.timeInMillis
            val endCal = Calendar.getInstance().apply {
                timeInMillis = baseDateMillis
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
                set(Calendar.SECOND, 0)
            }
            endMillis = if (endCal.timeInMillis >= startMillis) endCal.timeInMillis else endCal.timeInMillis + (24 * 3600 * 1000L)
        }

        FullReviewAndSaveScreen(
            settings = settings,
            partnerName = effectivePersonName,
            tractorLabel = selectedTractor,
            workType = if (isTamil) (allAvailableWorkTypes.find { it.en == selectedWorkType }?.ta ?: selectedWorkType) else selectedWorkType,
            customerName = customerNameInput,
            customerPhone = customerPhoneInput,
            customerLocation = customerLocationInput,
            durationMinutes = computedDurationMinutes,
            hourlyRate = hourlyRate,
            workAmount = baseWorkAmount,
            extraCharges = extraCharges,
            totalAmount = finalTotalAmount,
            amountReceived = amountReceived,
            pendingAmount = pendingAmount,
            notes = notes,
            linkedExpense = if (includeLinkedExpense && (linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) > 0) {
                ExpenseEntity(
                    id = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else 0L,
                    expenseType = linkedExpenseType,
                    amount = linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0,
                    tractorLabel = selectedTractor,
                    operatorName = effectivePersonName,
                    description = linkedExpenseDesc.ifBlank { "Expense for ${customerNameInput.trim()}'s job" },
                    addedByPartner = effectivePersonName,
                    dateTimestamp = startMillis,
                    relatedJobId = if (draft.entryId > 0L) draft.entryId else null,
                    createdAt = if (draft.isOldEntry && draft.historicalDateMillis != null) startMillis else System.currentTimeMillis()
                )
            } else null,
            linkedExpenses = draft.expensesList.map { exp ->
                ExpenseEntity(
                    id = if (exp.id > 0L) exp.id else 0L,
                    expenseType = exp.expenseType,
                    amount = exp.amount,
                    tractorLabel = selectedTractor,
                    operatorName = effectivePersonName,
                    description = "Expense for ${customerNameInput.trim()}'s job",
                    addedByPartner = if (exp.paidBy == "I Paid") {
                        if (exp.paidByPartner.isNotBlank()) exp.paidByPartner else effectivePersonName
                    } else effectivePersonName,
                    dateTimestamp = startMillis,
                    relatedJobId = if (draft.entryId > 0L) draft.entryId else null,
                    createdAt = if (draft.isOldEntry && draft.historicalDateMillis != null) startMillis else System.currentTimeMillis(),
                    paidBy = exp.paidBy,
                    paidByPartner = if (exp.paidBy == "I Paid") {
                        if (exp.paidByPartner.isNotBlank()) exp.paidByPartner else effectivePersonName
                    } else ""
                )
            },
            isSaving = effectiveIsSaving,
            onBackToEdit = {
                if (!effectiveIsSaving) {
                    onUpdateDraft(draft.copy(isReviewScreenVisible = false))
                }
            },
            onConfirmSave = {
                if (effectiveIsSaving) return@FullReviewAndSaveScreen
                if (isAmountReceivedExcess) {
                    Toast.makeText(context, if (isTamil) "பெற்ற தொகை மொத்த தொகையைக் கதισ முடியாது" else "Amount received cannot exceed total amount", Toast.LENGTH_SHORT).show()
                    return@FullReviewAndSaveScreen
                }
                localIsSaving = true

                val entryId = if (draft.entryId > 0L) draft.entryId else com.example.data.util.IdGenerator.generateId()
                val expenseId = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else com.example.data.util.IdGenerator.generateId()
                if (draft.entryId <= 0L || draft.linkedExpenseId <= 0L) {
                    onUpdateDraft(draft.copy(entryId = entryId, linkedExpenseId = expenseId))
                }

                val originalCreatedAt = draft.originalCreatedAt
                val originalAddedBy = draft.originalAddedByPartner
                val resolvedCreatedAt = if (draft.isOldEntry && draft.historicalDateMillis != null) {
                    startMillis
                } else if (originalCreatedAt != null && originalCreatedAt > 0L) {
                    originalCreatedAt
                } else {
                    System.currentTimeMillis()
                }

                // Determine Creator vs Entry For identity
                val activeCreatorRole = if (isCollaborationOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else com.example.data.auth.RoleUtils.normalizeRole(workspaceMembers.find { it.uid == currentUid }?.role ?: "Partner")
                val activeCreatorName = if (isCollaborationOwner) ownerDisplayName else currentPartnerName

                val (resolvedCreatorUid, resolvedCreatorRole, resolvedCreatorName) = if (draft.isEditingExistingEntry) {
                    Triple(
                        draft.originalCreatedByUid,
                        draft.originalCreatedByRole.ifBlank { activeCreatorRole },
                        if (!originalAddedBy.isNullOrBlank()) originalAddedBy else activeCreatorName
                    )
                } else {
                    Triple(
                        currentUid,
                        activeCreatorRole,
                        activeCreatorName
                    )
                }

                val matchedTractor = tractors.find { it.label == selectedTractor }

                val resolvedCustomerPhone = if (draft.isEditingExistingEntry) {
                    if (draft.isCustomerPhoneManuallyEdited) {
                        if (customerPhoneInput.isBlank()) "" else com.example.ui.components.sanitizePhoneNumberForStorage(customerPhoneInput)
                    } else {
                        draft.originalCustomerPhone ?: customerPhoneInput.trim()
                    }
                } else {
                    if (customerPhoneInput.isBlank()) "" else com.example.ui.components.sanitizePhoneNumberForStorage(customerPhoneInput)
                }

                val resolvedCustomerName = if (draft.isEditingExistingEntry) {
                    if (draft.isCustomerNameManuallyEdited) {
                        customerNameInput.trim()
                    } else {
                        draft.originalCustomerName?.ifBlank { customerNameInput.trim() } ?: customerNameInput.trim()
                    }
                } else {
                    customerNameInput.trim()
                }

                val resolvedCustomerId = if (draft.isEditingExistingEntry) {
                    if (draft.matchedCustomerId > 0L) {
                        draft.matchedCustomerId
                    } else if (draft.originalCustomerId > 0L) {
                        draft.originalCustomerId
                    } else {
                        0L
                    }
                } else {
                    draft.matchedCustomerId
                }

                val finalNotes = if (draft.isOldEntry) {
                    if (notes.isBlank()) {
                        "Historical Entry"
                    } else if (!notes.contains("Old", ignoreCase = true) && !notes.contains("Historical", ignoreCase = true)) {
                        "$notes • Historical Entry"
                    } else {
                        notes
                    }
                } else {
                    notes
                }

                val resolvedOperatorName = if (isCollaborationOwner) effectivePersonName else activeCreatorName
                val job = JobEntryEntity(
                    id = entryId,
                    customerId = resolvedCustomerId,
                    customerName = resolvedCustomerName,
                    customerPhone = resolvedCustomerPhone,
                    customerLocation = customerLocationInput.trim(),
                    tractorId = matchedTractor?.id ?: 0L,
                    tractorLabel = selectedTractor,
                    operatorName = resolvedOperatorName,
                    workType = selectedWorkType,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    hourlyRate = hourlyRate,
                    durationMinutes = computedDurationMinutes,
                    totalAmount = finalTotalAmount,
                    amountReceived = amountReceived,
                    pendingAmount = pendingAmount,
                    notes = finalNotes,
                    addedByPartner = resolvedCreatorName,
                    createdAt = resolvedCreatedAt,
                    createdByUid = resolvedCreatorUid,
                    createdByRole = resolvedCreatorRole,
                    editedByUid = if (draft.isEditingExistingEntry) currentUid else "",
                    editedByName = if (draft.isEditingExistingEntry) activeCreatorName else "",
                    editedByRole = if (draft.isEditingExistingEntry) activeCreatorRole else "",
                    updatedAt = if (draft.isEditingExistingEntry) System.currentTimeMillis() else 0L
                )

                val allLinkedExpenses = mutableListOf<ExpenseEntity>()
                if (includeLinkedExpense && (linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0) > 0) {
                    allLinkedExpenses.add(
                        ExpenseEntity(
                            id = expenseId,
                            expenseType = linkedExpenseType,
                            amount = linkedExpenseAmountInput.toDoubleOrNull() ?: 0.0,
                            tractorId = matchedTractor?.id ?: 0L,
                            tractorLabel = selectedTractor,
                            operatorName = resolvedOperatorName,
                            description = linkedExpenseDesc.ifBlank { "Expense for ${customerNameInput.trim()}'s job" },
                            addedByPartner = if (!originalAddedBy.isNullOrBlank()) originalAddedBy else activeCreatorName,
                            dateTimestamp = startMillis,
                            relatedJobId = entryId,
                            createdAt = resolvedCreatedAt,
                            paidBy = "I Paid",
                            paidByPartner = resolvedOperatorName
                        )
                    )
                }
                draft.expensesList.forEach { draftExp ->
                    allLinkedExpenses.add(
                        ExpenseEntity(
                            id = if (draftExp.id > 0L) draftExp.id else com.example.data.util.IdGenerator.generateId(),
                            expenseType = draftExp.expenseType,
                            amount = draftExp.amount,
                            tractorId = matchedTractor?.id ?: 0L,
                            tractorLabel = selectedTractor,
                            operatorName = resolvedOperatorName,
                            description = "Expense for ${customerNameInput.trim()}'s job",
                            addedByPartner = if (!originalAddedBy.isNullOrBlank()) originalAddedBy else activeCreatorName,
                            dateTimestamp = startMillis,
                            relatedJobId = entryId,
                            createdAt = resolvedCreatedAt,
                            paidBy = draftExp.paidBy,
                            paidByPartner = if (draftExp.paidBy == "I Paid") {
                                if (draftExp.paidByPartner.isNotBlank()) draftExp.paidByPartner else resolvedOperatorName
                            } else "",
                            paidByUid = if (draftExp.paidBy == "I Paid") {
                                draftExp.paidByUid.ifBlank { resolvedCreatorUid }
                            } else "",
                            createdByUid = resolvedCreatorUid,
                            createdByRole = resolvedCreatorRole
                        )
                    )
                }

                onSaveJob(job, allLinkedExpenses.firstOrNull(), allLinkedExpenses)
            }
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentPadding = PaddingValues(
                start = responsive.screenPaddingHorizontal,
                end = responsive.screenPaddingHorizontal,
                top = responsive.screenPaddingVertical,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Historical Old Entry Banner
            if (draft.isOldEntry && draft.historicalDateMillis != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_old_entry")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isTamil) "பழைய வேலைப் பதிவு" else "Historical Job Entry",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepSageGreen
                                    )
                                    Text(
                                        text = if (isTamil) "தேதி: ${draft.formattedHistoricalDate}" else "Date: ${draft.formattedHistoricalDate}",
                                        fontSize = 11.sp,
                                        color = ForestGreenHeader
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = draft.historicalDateMillis ?: System.currentTimeMillis()
                                    }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newCal = Calendar.getInstance().apply {
                                                set(y, m, d)
                                            }
                                            onUpdateDraft(
                                                draft.copy(
                                                    historicalDateMillis = newCal.timeInMillis
                                                )
                                            )
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isTamil) "தேதி மாற்று" else "Change Date",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSageGreen
                                )
                            }
                        }
                    }
                }
            } else if (draft.isEditingExistingEntry) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF8E1),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EarthGold.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_editing_entry")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = EarthGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (isTamil) "வேலைப் பதிவு திருத்தப்படுகிறது" else "Editing Job Entry",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = if (isTamil) "அசல் பதிவு நேரம் மாறாமல் பாதுகாக்கப்படும்" else "Original timestamp will be preserved",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
            }
            // Header Bar: Unsaved Draft Indicator on Left + Compact Clear Button on Right
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (draft.isModified()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EarthGold.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isTamil) "வரைவு" else "Unsaved Draft",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EarthGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    OutlinedButton(
                        onClick = handleClearAction,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("btn_clear_header")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear",
                            tint = AlertDueRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTamil) "அழி" else "Clear Draft",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AlertDueRed
                        )
                    }
                }
            }

            // 1. Entry For Section (No outer card)
            item {
                if (isCollaborationOwner) {
                    var personDropdownExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "வேலை பதிவு செய்பவர்" else "Entry For",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen
                            )

                            // Person Lock Indicator / Button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (draft.isPersonLocked) SoftSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .testTag("btn_person_lock")
                                    .clickable {
                                        val newLocked = !draft.isPersonLocked
                                        val currentSelectedUid = draft.selectedPersonUid.ifBlank { currentUid }
                                        onUpdateDraft(draft.copy(isPersonLocked = newLocked, selectedPersonUid = currentSelectedUid))
                                        val lockedUid = if (newLocked) currentSelectedUid else ""
                                        onUpdateLockedPerson?.invoke(lockedUid)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (draft.isPersonLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (draft.isPersonLocked) "Person Locked" else "Person Unlocked",
                                        tint = if (draft.isPersonLocked) DeepSageGreen else AppTheme.colors.textMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (draft.isPersonLocked) {
                                            if (isTamil) "பூட்டப்பட்டது" else "Locked"
                                        } else {
                                            if (isTamil) "பூட்டு" else "Lock"
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (draft.isPersonLocked) DeepSageGreen else AppTheme.colors.textMuted
                                    )
                                }
                            }
                        }

                        // ExposedDropdown for Owner & Active Registered Partners
                        ExposedDropdownMenuBox(
                            expanded = personDropdownExpanded,
                            onExpandedChange = { personDropdownExpanded = !personDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = effectivePersonName,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = DeepSageGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personDropdownExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = SageOutline,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("entry_for_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = personDropdownExpanded,
                                onDismissRequest = { personDropdownExpanded = false }
                            ) {
                                // 1. Owner Option
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = ownerDisplayName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = if (isTamil) "உரிமையாளர் (நீங்கள்)" else "Owner (You)",
                                                    fontSize = 11.sp,
                                                    color = DeepSageGreen
                                                )
                                            }
                                            if (draft.selectedPersonUid == currentUid || (draft.selectedPersonUid.isBlank() && (draft.selectedPersonName.isBlank() || draft.selectedPersonName == ownerDisplayName))) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = DeepSageGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        personDropdownExpanded = false
                                        val personalRate = onResolvePersonalRate(currentUid)
                                        val newDraft = draft.copy(
                                            selectedPersonUid = currentUid,
                                            selectedPersonName = ownerDisplayName,
                                            hourlyRateInput = if (personalRate > 0.0) personalRate.toInt().toString() else draft.hourlyRateInput
                                        )
                                        onUpdateDraft(newDraft)
                                        if (draft.isPersonLocked) {
                                            onUpdateLockedPerson?.invoke(currentUid)
                                        }
                                    }
                                )

                                // 2. Active Registered Partners
                                activeRegisteredPartners.forEach { partner ->
                                    val pName = partner.displayName?.ifBlank { null } ?: partner.phoneNumber ?: "Partner"
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = pName,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AppTheme.colors.textPrimary
                                                    )
                                                    val memberRoleLabel = com.example.data.auth.RoleUtils.getRoleDisplayName(partner.role, isTamil)
                                                    Text(
                                                        text = if (!partner.phoneNumber.isNullOrBlank()) "$memberRoleLabel • ${partner.phoneNumber}" else memberRoleLabel,
                                                        fontSize = 11.sp,
                                                        color = AppTheme.colors.textMuted
                                                    )
                                                }
                                                if (draft.selectedPersonUid == partner.uid || draft.selectedPersonName == pName) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = DeepSageGreen,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            personDropdownExpanded = false
                                            val personalRate = onResolvePersonalRate(partner.uid)
                                            val newDraft = draft.copy(
                                                selectedPersonUid = partner.uid,
                                                selectedPersonName = pName,
                                                hourlyRateInput = if (personalRate > 0.0) personalRate.toInt().toString() else draft.hourlyRateInput
                                            )
                                            onUpdateDraft(newDraft)
                                            if (draft.isPersonLocked) {
                                                onUpdateLockedPerson?.invoke(partner.uid)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Read-only Member Identity for Partner / Operator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PartnerAvatarImage(
                            photoUri = settings.profilePhotoUri,
                            name = currentPartnerName,
                            size = 36.dp,
                            avatarColorHex = "#1E4D2B"
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            val currentMember = workspaceMembers.find { it.uid == currentUid }
                            val currentRole = currentMember?.role?.ifBlank { null } ?: "Partner"
                            val roleLabel = com.example.data.auth.RoleUtils.getRoleDisplayName(currentRole, isTamil)
                            Text(
                                text = if (isTamil) "வேலை செய்பவர் ($roleLabel)" else "Entry For: $roleLabel",
                                fontSize = 10.5.sp,
                                color = AppTheme.colors.textMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentPartnerName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftSageGreen
                        ) {
                            Text(
                                text = if (isTamil) "உள்நுழைவு" else "Active",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. Tractor & Attachment Section (No outer card)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "1. டிராக்டர் & பணி தேர்வு" else "1. Tractor & Attachment",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )

                        // Tractor Lock Indicator / Button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isTractorLocked) SoftSageGreen else Color.Transparent,
                            modifier = Modifier.clickable {
                                val newLocked = !isTractorLocked
                                onUpdateDraft(draft.copy(isTractorLocked = newLocked))
                                val lockedLabel = if (newLocked) selectedTractor else ""
                                onUpdateLockedTractor?.invoke(lockedLabel)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTractorLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (isTractorLocked) "Locked" else "Unlocked",
                                    tint = if (isTractorLocked) DeepSageGreen else TextMutedDark,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isTractorLocked) (if (isTamil) "பூட்டப்பட்டது" else "Locked") else (if (isTamil) "பூட்டு" else "Lock Default"),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isTractorLocked) DeepSageGreen else TextMutedDark
                                )
                            }
                        }
                    }

                    // 2-Column Row: Tractor Dropdown (Left) & Work Type Dropdown (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tractor Dropdown (Single Line Value)
                        val tractorDisplayText = if (tractors.isEmpty()) {
                            if (isTamil) "டிராக்டர் இல்லை" else "No tractor"
                        } else if (selectedTractor.isBlank()) {
                            if (isTamil) "தேர்வுசெய்க" else "Select tractor"
                        } else {
                            selectedTractor
                        }

                        ExposedDropdownMenuBox(
                            expanded = tractorDropdownExpanded,
                            onExpandedChange = {
                                if (tractors.isNotEmpty()) {
                                    tractorDropdownExpanded = !tractorDropdownExpanded
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = tractorDisplayText,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                isError = hasAttemptedReview && (isTractorInvalid || tractors.isEmpty()),
                                label = { Text(if (isTamil) "டிராக்டர் *" else "Tractor *", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    if (tractors.isNotEmpty()) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = tractorDropdownExpanded)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Agriculture,
                                        contentDescription = null,
                                        tint = if (hasAttemptedReview && (isTractorInvalid || tractors.isEmpty())) AlertDueRed else DeepSageGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .trackFocusedField(tractorRequester, coroutineScope)
                                    .testTag("entry_tractor_dropdown"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            if (tractors.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = tractorDropdownExpanded,
                                    onDismissRequest = { tractorDropdownExpanded = false }
                                ) {
                                    tractors.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text("${t.label} • ${t.modelYear}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                onUpdateDraft(draft.copy(selectedTractor = t.label))
                                                tractorDropdownExpanded = false
                                                if (isTractorLocked) {
                                                    onUpdateLockedTractor?.invoke(t.label)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Work / Extension Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = workTypeDropdownExpanded,
                            onExpandedChange = { workTypeDropdownExpanded = !workTypeDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val displayWorkType = if (isTamil) {
                                allAvailableWorkTypes.find { it.en == selectedWorkType }?.ta ?: selectedWorkType
                            } else {
                                selectedWorkType
                            }

                            OutlinedTextField(
                                value = displayWorkType,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text(if (isTamil) "பணி வகை" else "Work Type", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workTypeDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .trackFocusedField(workTypeRequester, coroutineScope)
                                    .testTag("entry_work_type_dropdown"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = workTypeDropdownExpanded,
                                onDismissRequest = { workTypeDropdownExpanded = false }
                            ) {
                                // Built-in work types
                                FIXED_WORK_TYPES.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isTamil) "${item.ta} (${item.en})" else item.en, maxLines = 1)
                                        },
                                        onClick = {
                                            onUpdateDraft(draft.copy(selectedWorkType = item.en))
                                            workTypeDropdownExpanded = false
                                        }
                                    )
                                }

                                // Workspace Extensions (if any configured)
                                val customExtensions = workTypeExtensions.map { it.name }.distinct()
                                if (customExtensions.isNotEmpty()) {
                                    Divider(color = SageOutline.copy(alpha = 0.4f))
                                    customExtensions.forEach { extName ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(extName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = SoftSageGreen
                                                    ) {
                                                        Text(
                                                            text = if (isTamil) "நீட்டிப்பு" else "Extension",
                                                            fontSize = 9.sp,
                                                            color = DeepSageGreen,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onUpdateDraft(draft.copy(selectedWorkType = extName))
                                                workTypeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Customer Details Section (No outer card)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isTamil) "2. வாடிக்கையாளர் விவரங்கள்" else "2. Customer Details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )

                    // Customer Name Field
                    OutlinedTextField(
                        value = customerNameInput,
                        onValueChange = {
                            onUpdateDraft(draft.copy(customerNameInput = it, isCustomerNameManuallyEdited = true, matchedCustomerId = 0L))
                            showSuggestions = true
                        },
                        label = { Text(if (isTamil) "வாடிக்கையாளர் பெயர் *" else "Customer Name *", maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (hasAttemptedReview && isCustomerNameInvalid) AlertDueRed else DeepSageGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        isError = hasAttemptedReview && isCustomerNameInvalid,
                        supportingText = {
                            if (hasAttemptedReview && isCustomerNameInvalid) {
                                Text(if (isTamil) "வாடிக்கையாளர் பெயர் தேவை" else "This field is required", color = AlertDueRed, fontSize = 10.sp)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackFocusedField(customerNameRequester, coroutineScope)
                            .testTag("entry_customer_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Autocomplete suggestions directly below customer name field
                    AnimatedVisibility(
                        visible = showSuggestions && nameSuggestions.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CustomerSuggestionsCard(
                            suggestions = nameSuggestions,
                            isTamil = isTamil,
                            currency = settings.currency,
                            onSelect = onSelectCustomer
                        )
                    }

                    // Customer Phone & Location (2-Column Row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customerPhoneInput,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }.take(10)
                                onUpdateDraft(draft.copy(customerPhoneInput = digitsOnly, isCustomerPhoneManuallyEdited = true))
                                showSuggestions = true
                            },
                            label = { Text(if (isTamil) "மொபைல் எண் (விருப்பத்தேர்வு)" else "Mobile Number (Optional)", fontSize = 11.5.sp, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = if (hasAttemptedReview && isCustomerPhoneInvalid) AlertDueRed else DeepSageGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            isError = hasAttemptedReview && isCustomerPhoneInvalid,
                            supportingText = {
                                if (hasAttemptedReview && isCustomerPhoneInvalid) {
                                    Text(
                                        text = if (isTamil) "சரியான 10 இலக்க எண் தேவை" else "Valid 10-digit number required",
                                        color = AlertDueRed,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .trackFocusedField(customerPhoneRequester, coroutineScope)
                                .testTag("entry_customer_phone_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = customerLocationInput,
                            onValueChange = { onUpdateDraft(draft.copy(customerLocationInput = it)) },
                            label = { Text(if (isTamil) "ஊர் / இடம்" else "Village / Location", fontSize = 11.5.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .trackFocusedField(customerLocationRequester, coroutineScope),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Autocomplete suggestions below phone field
                    AnimatedVisibility(
                        visible = showSuggestions && phoneSuggestions.isNotEmpty() && nameSuggestions.isEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CustomerSuggestionsCard(
                            suggestions = phoneSuggestions,
                            isTamil = isTamil,
                            currency = settings.currency,
                            onSelect = onSelectCustomer
                        )
                    }
                }
            }

            // 4. Work Time Section (No outer card, Segmented Control BELOW Heading)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isTamil) "3. வேலை நேரம்" else "3. Work Time",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )

                    // Full-width Segmented Control directly BELOW the heading
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoftSageGreen.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDirectDurationMode) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateDraft(draft.copy(isDirectDurationMode = true)) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isTamil) "மணி / நிமிடம்" else "Hour / Min",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDirectDurationMode) Color.White else DeepSageGreen
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isDirectDurationMode) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateDraft(draft.copy(isDirectDurationMode = false)) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isTamil) "நேரம் (Clock Time)" else "Clock Time",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isDirectDurationMode) Color.White else DeepSageGreen
                                    )
                                }
                            }
                        }
                    }

                    if (!isDirectDurationMode) {
                        // Clock Start / End pickers in 2-Column Row
                        val curCal = Calendar.getInstance()
                        val isStartSet = startHour != null && startMinute != null
                        val isEndSet = endHour != null && endMinute != null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val initH = startHour ?: curCal.get(Calendar.HOUR_OF_DAY)
                                    val initM = startMinute ?: curCal.get(Calendar.MINUTE)
                                    val dialog = TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            onUpdateDraft(draft.copy(startHour = h, startMinute = m))
                                        },
                                        initH,
                                        initM,
                                        isSystem24Hour
                                    )
                                    dialog.show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp)
                                    .testTag("entry_start_time_btn")
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(if (isTamil) "தொடக்க நேரம்" else "Start Time", fontSize = 10.sp, color = TextMutedDark, maxLines = 1)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        formatTimeDisplay(startHour, startMinute),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isStartSet) ForestGreenHeader else TextMutedDark,
                                        maxLines = 1
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val initH = endHour ?: curCal.get(Calendar.HOUR_OF_DAY)
                                    val initM = endMinute ?: curCal.get(Calendar.MINUTE)
                                    val dialog = TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            onUpdateDraft(draft.copy(endHour = h, endMinute = m))
                                        },
                                        initH,
                                        initM,
                                        isSystem24Hour
                                    )
                                    dialog.show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp)
                                    .testTag("entry_end_time_btn")
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(if (isTamil) "முடிவு நேரம்" else "End Time", fontSize = 10.sp, color = TextMutedDark, maxLines = 1)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        formatTimeDisplay(endHour, endMinute),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEndSet) ForestGreenHeader else TextMutedDark,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        // Direct Hours & Minutes Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualHoursInput,
                                onValueChange = { onUpdateDraft(draft.copy(manualHoursInput = it)) },
                                label = { Text(if (isTamil) "மணிநேரம் (Hours) *" else "Hours *", maxLines = 1) },
                                placeholder = { Text("0") },
                                singleLine = true,
                                isError = hasAttemptedReview && isTimeInvalid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(hoursRequester, coroutineScope)
                                    .testTag("entry_manual_hours_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = manualMinutesInput,
                                onValueChange = { onUpdateDraft(draft.copy(manualMinutesInput = it)) },
                                label = { Text(if (isTamil) "நிமிடங்கள் (Mins) *" else "Minutes *", maxLines = 1) },
                                placeholder = { Text("0") },
                                singleLine = true,
                                isError = hasAttemptedReview && isTimeInvalid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .trackFocusedField(minutesRequester, coroutineScope)
                                    .testTag("entry_manual_minutes_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Compact Duration Summary Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SoftSageGreen.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isTamil) "மொத்த வேலை நேரம்:" else "Total Work Duration:", fontSize = 12.sp, color = ForestGreenHeader, fontWeight = FontWeight.Medium)
                            Text(
                                if (computedDurationMinutes > 0) "${com.example.ui.util.WorkBillingCalculator.formatDuration(computedDurationMinutes)} ($computedDurationMinutes mins)" else "0 mins",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen
                            )
                        }
                    }
                }
            }

            // 5. Payment & Billing Details Section (No outer card)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isTamil) "4. கட்டணக் கணக்கீடு" else "4. Payment & Billing Details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )

                    // Rate & Work Amount (2-Column Row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hourlyRateInput,
                            onValueChange = { onUpdateDraft(draft.copy(hourlyRateInput = it)) },
                            label = { Text(if (isTamil) "விகிதம் (${settings.currency}/hr)" else "Rate (${settings.currency}/hr)", fontSize = 11.5.sp, maxLines = 1) },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .trackFocusedField(hourlyRateRequester, coroutineScope)
                                .testTag("entry_hourly_rate_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = displayedWorkAmount,
                            onValueChange = { newValue ->
                                onUpdateDraft(
                                    draft.copy(
                                        customWorkAmountInput = newValue,
                                        isWorkAmountManuallyEdited = true
                                    )
                                )
                            },
                            label = { Text(if (isTamil) "வேலைத் தொகை *" else "Work Amount (${settings.currency}) *", fontSize = 11.5.sp, maxLines = 1) },
                            placeholder = { Text("0") },
                            singleLine = true,
                            isError = hasAttemptedReview && isWorkAmountInvalid,
                            trailingIcon = {
                                if (isWorkAmountManuallyEdited) {
                                    IconButton(
                                        onClick = {
                                            onUpdateDraft(
                                                draft.copy(
                                                    customWorkAmountInput = "",
                                                    isWorkAmountManuallyEdited = false
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reset",
                                            tint = ForestGreenHeader,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .trackFocusedField(workAmountRequester, coroutineScope)
                                .testTag("entry_work_amount_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Extra Charges (Optional) Toggle Row
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = !isExtraChargesEnabled
                                isExtraChargesEnabled = newState
                                if (!newState) {
                                    onUpdateDraft(draft.copy(extraChargesInput = ""))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (isTamil) "கூடுதல் கட்டணம் (விருப்பத்தேர்வு)" else "Extra Charges (Optional)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenHeader
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isExtraChargesEnabled) DeepSageGreen else SoftSageGreen
                            ) {
                                Text(
                                    text = if (isExtraChargesEnabled) "ON" else "OFF",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExtraChargesEnabled) Color.White else ForestGreenHeader,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Extra Charges Input (Revealed only when toggle is ON)
                    AnimatedVisibility(visible = isExtraChargesEnabled) {
                        OutlinedTextField(
                            value = extraChargesInput,
                            onValueChange = { onUpdateDraft(draft.copy(extraChargesInput = it)) },
                            label = { Text(if (isTamil) "கூடுதல் கட்டணம் (${settings.currency}) *" else "Extra Charges Amount (${settings.currency}) *", fontSize = 11.5.sp, maxLines = 1) },
                            placeholder = { Text("e.g. 200") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackFocusedField(extraChargesRequester, coroutineScope)
                                .testTag("entry_extra_charges_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Amount Received & Pending Due (2-Column Row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = amountReceivedInput,
                            onValueChange = { onUpdateDraft(draft.copy(amountReceivedInput = it)) },
                            label = { Text(if (isTamil) "பெற்ற தொகை *" else "Amount Received (${settings.currency}) *", fontSize = 11.5.sp, maxLines = 1) },
                            singleLine = true,
                            isError = isAmountReceivedExcess || (hasAttemptedReview && isAmountReceivedInvalid),
                            supportingText = {
                                if (isAmountReceivedExcess) {
                                    Text(
                                        text = if (isTamil) "தொகை ${formatInr(finalTotalAmount, settings.currency)} ஐ விட அதிகமாக இருக்க முடியாது" else "Amount cannot exceed ${formatInr(finalTotalAmount, settings.currency)}",
                                        color = AlertDueRed,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                } else if (hasAttemptedReview && isAmountReceivedInvalid) {
                                    Text(if (isTamil) "தேவை (கடன் எனில் 0)" else "Required (enter 0 if credit)", color = AlertDueRed, fontSize = 10.sp)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .trackFocusedField(amountReceivedRequester, coroutineScope)
                                .testTag("entry_amount_received_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (pendingAmount > 0) AlertDueRed.copy(alpha = 0.08f) else SuccessPaidGreen.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (pendingAmount > 0) AlertDueRed.copy(alpha = 0.3f) else SuccessPaidGreen.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 54.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isTamil) "நிலுவைத் தொகை" else "Pending Due",
                                    fontSize = 10.5.sp,
                                    color = TextMutedDark,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = formatInr(pendingAmount, settings.currency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pendingAmount > 0) AlertDueRed else SuccessPaidGreen,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Total Bill Summary Row (Compact)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SoftSageGreen.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "மொத்த கட்டணம் (வேலை + கூடுதல்):" else "Total Payment (Work + Extra)",
                                fontSize = 12.sp,
                                color = ForestGreenHeader,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatInr(finalTotalAmount, settings.currency),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )
                        }
                    }
                }
            }

            // 5. Multiple Expenses (Optional)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "5. செலவுகள் (விருப்பத்தேர்வு)" else "5. Expenses (Optional)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DeepSageGreen),
                            modifier = Modifier.clickable {
                                expenseToEdit = null
                                showAddExpenseModal = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isTamil) "செலவு சேர்க்க" else "Add Expense",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepSageGreen
                                )
                            }
                        }
                    }

                    // Multiple Expenses List
                    draft.expensesList.forEachIndexed { index, exp ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expenseToEdit = exp
                                    showAddExpenseModal = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isFuel = exp.expenseType.contains("Diesel", ignoreCase = true) || exp.expenseType.contains("Petrol", ignoreCase = true) || exp.expenseType.contains("Oil", ignoreCase = true)
                                    val iconBg = if (isFuel) Color(0xFFFFEFE5) else Color(0xFFE8F1FC)
                                    val iconTint = if (isFuel) Color(0xFFE65100) else Color(0xFF1976D2)
                                    val iconVector = if (isFuel) Icons.Default.LocalGasStation else Icons.Default.Build

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(iconBg, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = exp.expenseType,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = AppTheme.colors.textPrimary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (exp.paidBy == "I Paid") Icons.Default.Person else Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = TextMutedDark,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (exp.paidBy == "I Paid" && exp.paidByPartner.isNotBlank()) exp.paidByPartner else exp.paidBy,
                                                fontSize = 11.5.sp,
                                                color = TextMutedDark
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatInr(exp.amount, settings.currency),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    IconButton(
                                        onClick = {
                                            expenseToEdit = exp
                                            showAddExpenseModal = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = DeepSageGreen,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = draft.expensesList.toMutableList().also { it.removeAt(index) }
                                            onUpdateDraft(draft.copy(expensesList = updated))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = AlertDueRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Work Notes Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isTamil) "6. வேலைக் குறிப்புகள் (விருப்பத்தேர்வு)" else "6. Work Notes (Optional)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            if (it.length <= 200) {
                                onUpdateDraft(draft.copy(notes = it))
                            }
                        },
                        placeholder = { Text(if (isTamil) "குறிப்புகளைச் சேர்க்கவும்..." else "Add work notes...", color = TextMutedDark, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(20.dp))
                        },
                        supportingText = {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Text("${notes.length}/200", fontSize = 11.sp, color = TextMutedDark)
                            }
                        },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackFocusedField(notesRequester, coroutineScope),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            unfocusedBorderColor = SageOutline
                        )
                    )
                }
            }

            // 7. Review & Save Primary CTA
            item {
                Button(
                    onClick = {
                        if (effectiveIsSaving) return@Button
                        onUpdateDraft(draft.copy(hasAttemptedReview = true))
                        if (tractors.isEmpty()) {
                            Toast.makeText(
                                context,
                                if (isTamil) "முதலில் ஒரு டிராக்டரைச் சேர்க்கவும்" else "Please add a tractor before creating an entry.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        val isFormValid = !isTractorInvalid &&
                                !isCustomerNameInvalid &&
                                !isCustomerPhoneInvalid &&
                                !isTimeInvalid &&
                                !isWorkAmountInvalid &&
                                !isAmountReceivedInvalid &&
                                !isAmountReceivedExcess

                        if (isFormValid) {
                            val entryId = if (draft.entryId > 0L) draft.entryId else com.example.data.util.IdGenerator.generateId()
                            val expenseId = if (draft.linkedExpenseId > 0L) draft.linkedExpenseId else com.example.data.util.IdGenerator.generateId()
                            onUpdateDraft(draft.copy(entryId = entryId, linkedExpenseId = expenseId, isReviewScreenVisible = true, hasAttemptedReview = true))
                        } else {
                            val errorMsg = when {
                                isTractorInvalid -> if (isTamil) "டிராக்டரைத் தேர்வுசெய்க" else "Please select a tractor"
                                isCustomerNameInvalid -> if (isTamil) "வாடிக்கையாளர் பெயர் தேவை" else "Customer name is required"
                                isCustomerPhoneInvalid -> if (isTamil) "சரியான 10 இலக்க தொலைபேசி எண்ணை உள்ளிடவும்" else "Enter a valid 10-digit mobile number"
                                isTimeInvalid -> if (isTamil) "வேலை நேரம் பூஜ்ஜியத்திற்கு மேல் இருக்க வேண்டும்" else "Work duration must be greater than 0"
                                isWorkAmountInvalid -> if (isTamil) "வேலைத் தொகை பூஜ்ஜியத்திற்கு மேல் இருக்க வேண்டும்" else "Work amount must be greater than 0"
                                isAmountReceivedExcess -> if (isTamil) "பெற்ற தொகை மொத்த கட்டணத்தை (${formatInr(finalTotalAmount, settings.currency)}) விட அதிகமாக இருக்கக்கூடாது" else "Amount Received cannot exceed Total Payable (${formatInr(finalTotalAmount, settings.currency)})"
                                isAmountReceivedInvalid -> if (isTamil) "பெறப்பட்ட தொகையை உள்ளிடவும்" else "Please enter amount received"
                                else -> if (isTamil) "தேவையான தகவல்களை நிரப்பவும்" else "Please fill all required highlighted fields"
                            }
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !effectiveIsSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_review_and_save")
                ) {
                    Text(
                        text = if (isTamil) "சரிபார்த்து சேமிக்கவும்" else "Review & Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    )
                }
            }
        }
    }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = if (isTamil) "புதிய பதிவை அழிக்கவா?" else "Clear New Entry?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTamil)
                        "சேமிக்கப்படாத அனைத்து விவரங்களும் நீக்கப்படும்."
                    else
                        "All unsaved information will be removed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmationDialog = false
                        onClearDraft()
                        Toast.makeText(
                            context,
                            if (isTamil) "படிவம் அழிக்கப்பட்டது" else "Draft cleared",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.testTag("dialog_confirm_clear")
                ) {
                    Text(
                        text = if (isTamil) "அழி" else "Clear",
                        color = AlertDueRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmationDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_clear")
                ) {
                    Text(text = if (isTamil) "ரத்து செய்" else "Cancel")
                }
            }
        )
    }

    if (showAddExpenseModal) {
        AddExpenseModal(
            entryForPartnerName = effectivePersonName,
            currency = settings.currency,
            initialExpense = expenseToEdit,
            onDismiss = {
                showAddExpenseModal = false
                expenseToEdit = null
            },
            onAddExpense = { item ->
                if (expenseToEdit != null) {
                    val updated = draft.expensesList.map { if (it.id == expenseToEdit!!.id) item else it }
                    onUpdateDraft(draft.copy(expensesList = updated))
                } else {
                    onUpdateDraft(draft.copy(expensesList = draft.expensesList + item))
                }
                showAddExpenseModal = false
                expenseToEdit = null
            }
        )
    }
}

@Composable
fun FullReviewAndSaveScreen(
    settings: AppSettingsEntity,
    partnerName: String,
    tractorLabel: String,
    workType: String,
    customerName: String,
    customerPhone: String,
    customerLocation: String,
    durationMinutes: Long,
    hourlyRate: Double,
    workAmount: Double,
    extraCharges: Double,
    totalAmount: Double,
    amountReceived: Double,
    pendingAmount: Double,
    notes: String,
    linkedExpense: ExpenseEntity?,
    linkedExpenses: List<ExpenseEntity> = emptyList(),
    isSaving: Boolean = false,
    onBackToEdit: () -> Unit,
    onConfirmSave: () -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { if (!isSaving) onBackToEdit() },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("btn_review_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isSaving) DeepSageGreen.copy(alpha = 0.4f) else DeepSageGreen
                    )
                }

                Text(
                    text = if (isTamil) "வேலை விவரங்களை சரிபார்க்கவும்" else "Review Entry",
                    fontSize = if (responsive.isSmallPhone) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 10.dp)
                ) {
                    Text(
                        text = if (isTamil) "முக்கிய விவரங்கள்" else "Entry Summary",
                        fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )

                    DetailRow(
                        label = if (isTamil) "பங்குதாரர்" else "Attributed Partner",
                        value = partnerName
                    )
                    DetailRow(
                        label = if (isTamil) "டிராக்டர்" else "Tractor",
                        value = tractorLabel
                    )
                    DetailRow(
                        label = if (isTamil) "பணி / கருவி" else "Work Type",
                        value = workType
                    )

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "வாடிக்கையாளர் பெயர்" else "Customer Name",
                        value = customerName
                    )
                    if (customerPhone.isNotBlank()) {
                        DetailRow(
                            label = if (isTamil) "தொலைபேசி" else "Phone Number",
                            value = customerPhone
                        )
                    }
                    if (customerLocation.isNotBlank()) {
                        DetailRow(
                            label = if (isTamil) "ஊர்" else "Location",
                            value = customerLocation
                        )
                    }

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "வேலை நேரம்" else "Duration",
                        value = if (durationMinutes > 0) "${com.example.ui.util.WorkBillingCalculator.formatDuration(durationMinutes)} ($durationMinutes mins)" else "0 mins"
                    )
                    DetailRow(
                        label = if (isTamil) "மணிநேர கட்டணம்" else "Hourly Rate",
                        value = "${formatInr(hourlyRate, settings.currency)} / hr"
                    )
                    DetailRow(
                        label = if (isTamil) "வேலைத் தொகை" else "Work Amount",
                        value = formatInr(workAmount, settings.currency)
                    )
                    if (extraCharges > 0) {
                        DetailRow(
                            label = if (isTamil) "கூடுதல் கட்டணம்" else "Extra Charges",
                            value = "+ ${formatInr(extraCharges, settings.currency)}"
                        )
                    }

                    Divider(color = SageOutline.copy(alpha = 0.5f))

                    DetailRow(
                        label = if (isTamil) "மொத்த பில் தொகை" else "Total Amount",
                        value = formatInr(totalAmount, settings.currency),
                        valueColor = ForestGreenHeader,
                        isBoldValue = true
                    )
                    DetailRow(
                        label = if (isTamil) "பெற்ற தொகை" else "Amount Received",
                        value = formatInr(amountReceived, settings.currency),
                        valueColor = SuccessPaidGreen
                    )
                    DetailRow(
                        label = if (isTamil) "நிலுவைத் தொகை" else "Pending Due",
                        value = formatInr(pendingAmount, settings.currency),
                        valueColor = if (pendingAmount > 0) AlertDueRed else SuccessPaidGreen
                    )

                    if (linkedExpenses.isNotEmpty()) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        Text(
                            text = if (isTamil) "இணைக்கப்பட்ட செலவுகள்" else "Linked Expenses",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )
                        linkedExpenses.forEach { exp ->
                            DetailRow(
                                label = "${exp.expenseType} (${exp.paidBy})",
                                value = formatInr(exp.amount, settings.currency)
                            )
                        }
                    } else if (linkedExpense != null) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        DetailRow(
                            label = if (isTamil) "இணைக்கப்பட்ட செலவு" else "Linked Expense",
                            value = "${linkedExpense.expenseType}: ${formatInr(linkedExpense.amount, settings.currency)}"
                        )
                    }

                    if (notes.isNotBlank()) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        DetailRow(
                            label = if (isTamil) "குறிப்புகள்" else "Notes",
                            value = notes,
                            isBoldValue = false
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToEdit,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                        .testTag("btn_review_edit")
                ) {
                    Text(if (isTamil) "திருத்துக" else "Edit", fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp)
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        onConfirmSave()
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepSageGreen,
                        disabledContainerColor = DeepSageGreen.copy(alpha = 0.6f),
                        disabledContentColor = Color.White.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                        .testTag("btn_confirm_final_save")
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTamil) "சேமிக்கப்படுகிறது..." else "Saving...",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                            maxLines = 1
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTamil) "உறுதிசெய்து சேமிக்கவும்" else "Confirm & Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerSuggestionsCard(
    suggestions: List<CustomerEntity>,
    isTamil: Boolean,
    currency: String,
    onSelect: (CustomerEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SageCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = if (isTamil) "பரிந்துரைக்கப்பட்ட வாடிக்கையாளர்கள்:" else "Existing Customers (Tap to auto-fill):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SageAccent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            suggestions.take(4).forEach { c ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelect(c) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                            Text(
                                "${c.phone} ${if (c.location.isNotBlank()) "• ${c.location}" else ""}",
                                fontSize = 11.sp,
                                color = TextMutedDark
                            )
                        }
                        if (c.balanceDue > 0) {
                            Text(
                                "Due: ${formatInr(c.balanceDue, currency)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertDueRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseModal(
    entryForPartnerName: String,
    currency: String,
    initialExpense: DraftExpenseItem? = null,
    onDismiss: () -> Unit,
    onAddExpense: (DraftExpenseItem) -> Unit
) {
    var selectedExpenseType by remember(initialExpense) { mutableStateOf(initialExpense?.expenseType ?: "Diesel") }
    var amountInput by remember(initialExpense) {
        mutableStateOf(
            if (initialExpense != null && initialExpense.amount > 0) {
                if (initialExpense.amount % 1.0 == 0.0) initialExpense.amount.toLong().toString() else initialExpense.amount.toString()
            } else ""
        )
    }
    var selectedWhoPaid by remember(initialExpense) { mutableStateOf(initialExpense?.paidBy ?: "I Paid") }
    var expenseTypeExpanded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val expenseCategories = listOf(
        "Diesel", "Petrol", "Puncture", "Repair", "Maintenance", "Oil", "Parts", "Other"
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFCBD5E1))
                            .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initialExpense != null) "Edit Expense" else "Add Expense",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Expense",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMutedDark
                            )

                            ExposedDropdownMenuBox(
                                expanded = expenseTypeExpanded,
                                onExpandedChange = { expenseTypeExpanded = !expenseTypeExpanded }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SageOutline),
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .menuAnchor()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val isGas = selectedExpenseType.contains("Diesel", true) || selectedExpenseType.contains("Petrol", true)
                                                Icon(
                                                    imageVector = if (isGas) Icons.Default.LocalGasStation else Icons.Default.Build,
                                                    contentDescription = null,
                                                    tint = DeepSageGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Text(
                                                text = selectedExpenseType,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = TextMutedDark
                                        )
                                    }
                                }

                                ExposedDropdownMenu(
                                    expanded = expenseTypeExpanded,
                                    onDismissRequest = { expenseTypeExpanded = false }
                                ) {
                                    expenseCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat, fontSize = 13.5.sp) },
                                            onClick = {
                                                selectedExpenseType = cat
                                                expenseTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Amount (₹)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMutedDark
                            )

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = {
                                    if (it.all { ch -> ch.isDigit() || ch == '.' }) {
                                        amountInput = it
                                        hasError = false
                                    }
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("₹", color = DeepSageGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                },
                                placeholder = { Text("500", color = TextMutedDark, fontSize = 13.5.sp) },
                                singleLine = true,
                                isError = hasError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = SageOutline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Who paid?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMutedDark
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isIPaid = selectedWhoPaid == "I Paid"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isIPaid) 1.5.dp else 1.dp,
                                    color = if (isIPaid) DeepSageGreen else Color(0xFFCBD5E1)
                                ),
                                color = if (isIPaid) Color(0xFFF0FDF4) else Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clickable { selectedWhoPaid = "I Paid" }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isIPaid) DeepSageGreen else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "I Paid",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isIPaid) DeepSageGreen else Color(0xFF334155)
                                    )
                                }
                            }

                            val isBusinessPaid = selectedWhoPaid == "Business Paid"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isBusinessPaid) 1.5.dp else 1.dp,
                                    color = if (isBusinessPaid) DeepSageGreen else Color(0xFFCBD5E1)
                                ),
                                color = if (isBusinessPaid) Color(0xFFF0FDF4) else Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clickable { selectedWhoPaid = "Business Paid" }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = if (isBusinessPaid) DeepSageGreen else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Business Paid",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isBusinessPaid) DeepSageGreen else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
                            if (parsedAmount <= 0.0) {
                                hasError = true
                                return@Button
                            }
                            onAddExpense(
                                DraftExpenseItem(
                                    id = initialExpense?.id?.takeIf { it > 0L } ?: com.example.data.util.IdGenerator.generateId(),
                                    expenseType = selectedExpenseType,
                                    amount = parsedAmount,
                                    paidBy = selectedWhoPaid,
                                    paidByPartner = if (selectedWhoPaid == "I Paid") {
                                        initialExpense?.paidByPartner?.takeIf { it.isNotBlank() } ?: entryForPartnerName
                                    } else "",
                                    paidByUid = if (selectedWhoPaid == "I Paid") {
                                        initialExpense?.paidByUid ?: ""
                                    } else ""
                                )
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (initialExpense != null) "Save Expense" else "Add Expense",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
    }
}
