
package com.example.ui.screens.report
import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import com.example.ui.utils.trackFocusedField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.buildExpenseWhatsAppMessage
import com.example.ui.components.formatInr
import com.example.ui.components.sendWhatsAppMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
fun getLocalizedExpenseType(type: String, isTamil: Boolean): String {
    if (!isTamil) return type
    return when (type.trim().lowercase()) {
        "diesel" -> "டீசல்"
        "petrol" -> "பெட்ரோல்"
        "repair" -> "பழுதுபார்ப்பு"
        "puncture" -> "பஞ்சர்"
        "oil change" -> "ஆயில் மாற்றம்"
        "driver bata" -> "டிரைவர் படி"
        "spare parts" -> "உதிரி பாகங்கள்"
        "toll / parking" -> "சுங்கச்சாவடி / பார்க்கிங்"
        "other" -> "இதர"
        else -> type
    }
}
fun getLocalizedPaymentMode(mode: String, isTamil: Boolean): String {
    if (!isTamil) return mode
    return when (mode.trim().lowercase()) {
        "cash" -> "ரொக்கம்"
        "upi / gpay / phonepe" -> "UPI / ஜிபே / போன்பே"
        "bank transfer" -> "வங்கி பரிமாற்றம்"
        "credit / due" -> "கடன் பாக்கி"
        "cheque" -> "காசோலை"
        else -> mode
    }
}
fun getLocalizedDatePreset(preset: DatePreset, isTamil: Boolean): String {
    if (!isTamil) return preset.label
    return when (preset) {
        DatePreset.TODAY -> "இன்று"
        DatePreset.THIS_WEEK -> "இந்த வாரம்"
        DatePreset.THIS_MONTH -> "இந்த மாதம்"
        DatePreset.ALL_TIME -> "முழுவதும்"
    }
}
val ExpenseTypes = listOf(
    "Diesel",
    "Petrol",
    "Repair",
    "Puncture",
    "Oil Change",
    "Driver Bata",
    "Spare Parts",
    "Toll / Parking",
    "Other"
)
val PaymentModes = listOf(
    "Cash",
    "UPI / GPay / PhonePe",
    "Bank Transfer",
    "Credit / Due",
    "Cheque"
)
data class ExpenseVisualConfig(
    val icon: ImageVector,
    val bgColor: Color,
    val tintColor: Color
)
fun getExpenseVisualConfig(type: String): ExpenseVisualConfig {
    return when (type.lowercase().trim()) {
        "diesel" -> ExpenseVisualConfig(
            icon = Icons.Default.LocalGasStation,
            bgColor = Color(0xFFDCFCE7), // Light Emerald Green
            tintColor = Color(0xFF16A34A)
        )
        "petrol" -> ExpenseVisualConfig(
            icon = Icons.Default.Opacity,
            bgColor = Color(0xFFFEF3C7), // Light Amber
            tintColor = Color(0xFFD97706)
        )
        "repair", "spare parts" -> ExpenseVisualConfig(
            icon = Icons.Default.Build,
            bgColor = Color(0xFFF3E8FF), // Light Purple
            tintColor = Color(0xFF9333EA)
        )
        "puncture" -> ExpenseVisualConfig(
            icon = Icons.Default.DirectionsCar,
            bgColor = Color(0xFFFFEDD5), // Light Orange
            tintColor = Color(0xFFEA580C)
        )
        "oil change" -> ExpenseVisualConfig(
            icon = Icons.Default.Opacity,
            bgColor = Color(0xFFE0F2FE), // Light Sky Blue
            tintColor = Color(0xFF0284C7)
        )
        "driver bata" -> ExpenseVisualConfig(
            icon = Icons.Default.Person,
            bgColor = Color(0xFFEDE9FE), // Light Violet
            tintColor = Color(0xFF6366F1)
        )
        else -> ExpenseVisualConfig(
            icon = Icons.Default.MonetizationOn,
            bgColor = Color(0xFFF1F5F9), // Light Slate
            tintColor = Color(0xFF475569)
        )
    }
}
enum class ExpenseScreenView {
    REPORT_LIST,
    ADD_EXPENSE,
    EDIT_EXPENSE,
    EXPENSE_DETAILS
}
enum class DatePreset(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}
@Composable
fun ExpensesTab(
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    jobs: List<JobEntryEntity> = emptyList(),
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    onAddExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    canDeleteExpense: (ExpenseEntity) -> Boolean = { true }
) {
    var currentView by remember { mutableStateOf(ExpenseScreenView.REPORT_LIST) }
    var activeExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    // Intercept back button when in subview
    BackHandler(enabled = currentView != ExpenseScreenView.REPORT_LIST) {
        if (currentView == ExpenseScreenView.EDIT_EXPENSE && activeExpense != null) {
            currentView = ExpenseScreenView.EXPENSE_DETAILS
        } else {
            currentView = ExpenseScreenView.REPORT_LIST
            activeExpense = null
        }
    }
    Crossfade(targetState = currentView, label = "expense_view_transition") { viewState ->
        when (viewState) {
            ExpenseScreenView.REPORT_LIST -> {
                ExpenseReportListScreen(
                    settings = settings,
                    expenses = expenses,
                    jobs = jobs,
                    tractors = tractors,
                    partners = partners,
                    canDeleteExpense = canDeleteExpense,
                    onOpenAddExpense = {
                        activeExpense = null
                        currentView = ExpenseScreenView.ADD_EXPENSE
                    },
                    onOpenExpenseDetails = { expense ->
                        activeExpense = expense
                        currentView = ExpenseScreenView.EXPENSE_DETAILS
                    },
                    onQuickEdit = { expense ->
                        activeExpense = expense
                        currentView = ExpenseScreenView.EDIT_EXPENSE
                    },
                    onQuickDelete = { expense ->
                        onDeleteExpense(expense)
                    }
                )
            }
            ExpenseScreenView.ADD_EXPENSE -> {
                AddOrEditExpenseScreen(
                    isEditMode = false,
                    initialExpense = null,
                    tractors = tractors,
                    partners = partners,
                    settings = settings,
                    onBack = {
                        currentView = ExpenseScreenView.REPORT_LIST
                        activeExpense = null
                    },
                    onSaveExpense = { newExpense ->
                        onAddExpense(newExpense)
                        currentView = ExpenseScreenView.REPORT_LIST
                        activeExpense = null
                    }
                )
            }
            ExpenseScreenView.EDIT_EXPENSE -> {
                AddOrEditExpenseScreen(
                    isEditMode = true,
                    initialExpense = activeExpense,
                    tractors = tractors,
                    partners = partners,
                    settings = settings,
                    onBack = {
                        currentView = if (activeExpense != null) ExpenseScreenView.EXPENSE_DETAILS else ExpenseScreenView.REPORT_LIST
                    },
                    onSaveExpense = { updatedExpense ->
                        onUpdateExpense(updatedExpense)
                        activeExpense = updatedExpense
                        currentView = ExpenseScreenView.EXPENSE_DETAILS
                    }
                )
            }
            ExpenseScreenView.EXPENSE_DETAILS -> {
                activeExpense?.let { expense ->
                    ExpenseDetailsScreen(
                        expense = expense,
                        jobs = jobs,
                        settings = settings,
                        canDelete = canDeleteExpense(expense),
                        onBack = {
                            currentView = ExpenseScreenView.REPORT_LIST
                            activeExpense = null
                        },
                        onEdit = {
                            currentView = ExpenseScreenView.EDIT_EXPENSE
                        },
                        onDelete = {
                            onDeleteExpense(expense)
                            currentView = ExpenseScreenView.REPORT_LIST
                            activeExpense = null
                        }
                    )
                } ?: run {
                    currentView = ExpenseScreenView.REPORT_LIST
                }
            }
        }
    }
}
/* =========================================================================================
   1. EXPENSE REPORT LIST SCREEN (Main Screen)
   ========================================================================================= */
@Composable
fun ExpenseReportListScreen(
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    jobs: List<JobEntryEntity> = emptyList(),
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    canDeleteExpense: (ExpenseEntity) -> Boolean = { true },
    onOpenAddExpense: () -> Unit,
    onOpenExpenseDetails: (ExpenseEntity) -> Unit,
    onQuickEdit: (ExpenseEntity) -> Unit,
    onQuickDelete: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val coroutineScope = rememberCoroutineScope()
    val searchRequester = remember { BringIntoViewRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDatePreset by remember { mutableStateOf(DatePreset.ALL_TIME) }
    var customStartDate by remember { mutableLongStateOf(getStartOfDayMillis()) }
    var customEndDate by remember { mutableLongStateOf(getEndOfDayMillis()) }
    var selectedOperatorFilter by remember { mutableStateOf("All") }
    var selectedTractorFilter by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var isPresetMenuOpen by remember { mutableStateOf(false) }
    var isOperatorMenuOpen by remember { mutableStateOf(false) }
    var isTractorMenuOpen by remember { mutableStateOf(false) }
    var isTypeMenuOpen by remember { mutableStateOf(false) }

    // Distinct operator list for filtering: includes current user, owner, partners, and any historical operators
    val availableOperators = remember(partners, settings.activePartnerName, settings.ownerName, expenses) {
        val set = linkedSetOf<String>()
        val currentActor = settings.activePartnerName.trim()
        if (currentActor.isNotBlank()) {
            set.add(currentActor)
        }
        val ownerName = settings.ownerName.trim()
        if (ownerName.isNotBlank()) {
            set.add(ownerName)
        }
        partners.forEach { p ->
            val pName = p.name.trim()
            if (pName.isNotBlank()) set.add(pName)
        }
        expenses.forEach { exp ->
            val op = exp.operatorName.trim()
            if (op.isNotBlank()) set.add(op)
            val added = exp.addedByPartner.trim()
            if (added.isNotBlank()) set.add(added)
        }
        set.toList()
    }

    // Filter Logic
    val filteredExpenses = expenses.filter { exp ->
        // Search
        val matchesSearch = searchQuery.isBlank() ||
                exp.description.contains(searchQuery, ignoreCase = true) ||
                exp.expenseType.contains(searchQuery, ignoreCase = true) ||
                exp.operatorName.contains(searchQuery, ignoreCase = true) ||
                exp.tractorLabel.contains(searchQuery, ignoreCase = true)
        // Dropdowns
        val matchesOperator = selectedOperatorFilter == "All" ||
                exp.operatorName.contains(selectedOperatorFilter, ignoreCase = true) ||
                exp.addedByPartner.contains(selectedOperatorFilter, ignoreCase = true)
        val matchesTractor = selectedTractorFilter == "All" ||
                exp.tractorLabel.contains(selectedTractorFilter, ignoreCase = true)
        val matchesType = selectedTypeFilter == "All" ||
                exp.expenseType.equals(selectedTypeFilter, ignoreCase = true)
        // Date filter
        val matchesDate = when (selectedDatePreset) {
            DatePreset.TODAY -> {
                val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
                ts in getStartOfDayMillis()..getEndOfDayMillis()
            }
            DatePreset.THIS_WEEK -> {
                val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
                ts in getStartOfWeekMillis()..getEndOfDayMillis()
            }
            DatePreset.THIS_MONTH -> {
                val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
                ts in getStartOfMonthMillis()..getEndOfDayMillis()
            }
            DatePreset.ALL_TIME -> true
        }
        matchesSearch && matchesOperator && matchesTractor && matchesType && matchesDate
    }
    val totalAmount = filteredExpenses.sumOf { it.amount }
    val totalCount = filteredExpenses.size
    val avgAmount = if (totalCount > 0) totalAmount / totalCount else 0.0
    val lowestAmount = filteredExpenses.minOfOrNull { it.amount } ?: 0.0
    val highestAmount = filteredExpenses.maxOfOrNull { it.amount } ?: 0.0
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy\nhh:mm a", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Static Top Action Header outside LazyColumn
        Surface(
            color = Color(0xFFF9FAFB),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTamil) "செலவு அறிக்கை" else "Expense Report",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                // Add Expense Button
                Button(
                    onClick = onOpenAddExpense,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_add_expense_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTamil) "செலவு சேர்க்க" else "Add Expense",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB)),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date Range Selector Row (Preset + From Date + - + To Date + Calendar Icon)
            item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Preset Dropdown Chip
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF072D18),
                        modifier = Modifier
                            .clickable { isPresetMenuOpen = true }
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = getLocalizedDatePreset(selectedDatePreset, isTamil),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isPresetMenuOpen,
                        onDismissRequest = { isPresetMenuOpen = false }
                    ) {
                        DatePreset.values().forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(getLocalizedDatePreset(preset, isTamil)) },
                                onClick = {
                                    selectedDatePreset = preset
                                    isPresetMenuOpen = false
                                }
                            )
                        }
                    }
                }
                // From Date Box (Clickable to pick start date)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable {
                            showDatePicker(context, customStartDate) { picked ->
                                customStartDate = picked
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(customStartDate)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
                Text(
                    text = "-",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280)
                )
                // To Date Box (Clickable to pick end date)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable {
                            showDatePicker(context, customEndDate) { picked ->
                                customEndDate = picked + 86399000L
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormat.format(Date(customEndDate)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
                // Calendar Picker Icon
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            showDatePicker(context, customStartDate) { picked ->
                                customStartDate = picked
                                customEndDate = picked + 86399000L
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Pick Date",
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        // 3 Filter Dropdowns Row: Operator, Tractor, Expense Type
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Operator Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF16A34A),
                    label = if (isTamil) "இயக்குநர்" else "Operator",
                    selectedValue = if (selectedOperatorFilter == "All") (if (isTamil) "அனைத்தும்" else "All") else selectedOperatorFilter,
                    isOpen = isOperatorMenuOpen,
                    onToggle = { isOperatorMenuOpen = !isOperatorMenuOpen },
                    onDismiss = { isOperatorMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isTamil) "அனைத்து இயக்குநர்கள்" else "All Operators", fontWeight = if (selectedOperatorFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedOperatorFilter = "All"
                            isOperatorMenuOpen = false
                        }
                    )
                    availableOperators.forEach { opName ->
                        DropdownMenuItem(
                            text = { Text(opName) },
                            onClick = {
                                selectedOperatorFilter = opName
                                isOperatorMenuOpen = false
                            }
                        )
                    }
                }
                // 2. Tractor Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DirectionsCar,
                    iconTint = Color(0xFF16A34A),
                    label = if (isTamil) "டிராக்டர்" else "Tractor",
                    selectedValue = if (selectedTractorFilter == "All") (if (isTamil) "அனைத்தும்" else "All") else selectedTractorFilter,
                    isOpen = isTractorMenuOpen,
                    onToggle = { isTractorMenuOpen = !isTractorMenuOpen },
                    onDismiss = { isTractorMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isTamil) "அனைத்து டிராக்டர்கள்" else "All Fleet", fontWeight = if (selectedTractorFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedTractorFilter = "All"
                            isTractorMenuOpen = false
                        }
                    )
                    tractors.forEach { tractor ->
                        DropdownMenuItem(
                            text = { Text(tractor.label) },
                            onClick = {
                                selectedTractorFilter = tractor.label
                                isTractorMenuOpen = false
                            }
                        )
                    }
                }
                // 3. Expense Type Filter
                FilterDropdownButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Tag,
                    iconTint = Color(0xFF16A34A),
                    label = if (isTamil) "வகை" else "Type",
                    selectedValue = if (selectedTypeFilter == "All") (if (isTamil) "அனைத்தும்" else "All") else getLocalizedExpenseType(selectedTypeFilter, isTamil),
                    isOpen = isTypeMenuOpen,
                    onToggle = { isTypeMenuOpen = !isTypeMenuOpen },
                    onDismiss = { isTypeMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isTamil) "அனைத்து வகைகள்" else "All Types", fontWeight = if (selectedTypeFilter == "All") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedTypeFilter = "All"
                            isTypeMenuOpen = false
                        }
                    )
                    ExpenseTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(getLocalizedExpenseType(type, isTamil)) },
                            onClick = {
                                selectedTypeFilter = type
                                isTypeMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
        // Search Description Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isTamil) "விவரம், வகை, இயக்குநர் மூலம் தேடுக..." else "Search description, type, operator...", fontSize = 13.sp, color = Color(0xFF9CA3AF)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isTamil) "அழி" else "Clear",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF072D18),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .trackFocusedField(searchRequester, coroutineScope)
                    .testTag("expense_search_bar")
            )
        }
        // 3 Stat Cards: Total Expenses, Total Entries, Avg. per Entry
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Total Expenses
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowDownward,
                    iconBg = Color(0xFFDCFCE7),
                    iconColor = Color(0xFF16A34A),
                    title = if (isTamil) "மொத்த செலவுகள்" else "Total Expenses",
                    value = formatInr(totalAmount, settings.currency)
                )
                // Card 2: Total Entries
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    iconBg = Color(0xFFE0E7FF),
                    iconColor = Color(0xFF4F46E5),
                    title = if (isTamil) "மொத்த பதிவுகள்" else "Total Entries",
                    value = "$totalCount"
                )
                // Card 3: Avg. per Entry
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MonetizationOn,
                    iconBg = Color(0xFFFEF3C7),
                    iconColor = Color(0xFFD97706),
                    title = if (isTamil) "சராசரி செலவு" else "Avg. per Entry",
                    value = formatInr(avgAmount, settings.currency)
                )
            }
        }
        // Expense List Items or Empty State
        if (filteredExpenses.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTamil) "செலவுப் பதிவுகள் ஏதும் இல்லை" else "No expenses found",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF374151)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isTamil) "புதிய செலவை பதிவு செய்ய மேலே உள்ள '+ செலவு சேர்க்க' பொத்தானைத் தட்டவும்" else "Tap '+ Add Expense' above to record a new expense",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                ExpenseListItemRow(
                    expense = expense,
                    jobs = jobs,
                    currency = settings.currency,
                    isTamil = isTamil,
                    canDelete = canDeleteExpense(expense),
                    onClick = { onOpenExpenseDetails(expense) },
                    onEdit = { onQuickEdit(expense) },
                    onDelete = { onQuickDelete(expense) },
                    onShare = {
                        val shareText = buildExpenseWhatsAppMessage(expense, settings.businessName, isTamil)
                        sendWhatsAppMessage(context, null, shareText)
                    }
                )
            }
        }
        // Footer Summary: Lowest Expense & Highest Expense
        if (filteredExpenses.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isTamil) "குறைந்தபட்ச செலவு" else "Lowest Expense",
                                fontSize = 11.5.sp,
                                color = Color(0xFF4B5563),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatInr(lowestAmount, settings.currency),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                        Divider(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp),
                            color = Color(0xFFE5E7EB)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isTamil) "அதிகபட்ச செலவு" else "Highest Expense",
                                fontSize = 11.5.sp,
                                color = Color(0xFF4B5563),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatInr(highestAmount, settings.currency),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
/* =========================================================================================
   Filter Dropdown Button Component
   ========================================================================================= */
@Composable
fun FilterDropdownButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    selectedValue: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val isFiltered = selectedValue != "All" && selectedValue.isNotBlank()
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFiltered) Color(0xFFF0FDF4) else Color.White,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(if (isFiltered) Color(0xFF072D18) else Color(0xFFE5E7EB))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFiltered) Color(0xFF072D18) else iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedValue,
                            fontSize = 10.5.sp,
                            fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isFiltered) Color(0xFF072D18) else Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = onDismiss
        ) {
            content()
        }
    }
}
/* =========================================================================================
   Stat Card Component
   ========================================================================================= */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    title: String,
    value: String
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = 12.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
/* =========================================================================================
   Expense List Item Row
   ========================================================================================= */
@Composable
fun ExpenseListItemRow(
    expense: ExpenseEntity,
    jobs: List<JobEntryEntity> = emptyList(),
    currency: String,
    isTamil: Boolean = false,
    canDelete: Boolean = true,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val visualConfig = getExpenseVisualConfig(expense.expenseType)
    val itemDateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", if (isTamil) Locale("ta", "IN") else Locale.getDefault()) }

    val linkedJob = jobs.find { it.id == expense.relatedJobId }
    val linkedCustomerName = linkedJob?.customerName?.ifBlank { null }
        ?: if (expense.description.startsWith("Expense for ") && expense.description.contains("'s job")) {
            expense.description.removePrefix("Expense for ").substringBefore("'s job")
        } else null

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("expense_item_${expense.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Category Rounded Square Icon Box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(visualConfig.bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visualConfig.icon,
                    contentDescription = getLocalizedExpenseType(expense.expenseType, isTamil),
                    tint = visualConfig.tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            // Middle Info Column (Expense Type, Customer/Job, Tractor, Operator, Description)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getLocalizedExpenseType(expense.expenseType, isTamil),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                if (linkedCustomerName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isTamil) "வாடிக்கையாளர்: $linkedCustomerName" else "For: $linkedCustomerName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF15803D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (expense.tractorLabel.isNotBlank()) {
                    Text(
                        text = expense.tractorLabel,
                        fontSize = 11.5.sp,
                        color = Color(0xFF4B5563),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val opName = expense.operatorName.ifBlank { expense.addedByPartner }
                if (opName.isNotBlank()) {
                    Text(
                        text = opName,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (expense.description.isNotBlank() && (linkedCustomerName == null || !expense.description.startsWith("Expense for "))) {
                    Text(
                        text = expense.description,
                        fontSize = 10.5.sp,
                        color = Color(0xFF9CA3AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Right Column: Amount + 3-dots Menu + Date
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatInr(expense.amount, currency),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF072D18)) },
                                text = { Text(if (isTamil) "விவரங்களைக் காண்க" else "View Details") },
                                onClick = {
                                    isMenuExpanded = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF2563EB)) },
                                text = { Text(if (isTamil) "செலவைத் திருத்து" else "Edit Expense") },
                                onClick = {
                                    isMenuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF16A34A)) },
                                text = { Text(if (isTamil) "வாட்ஸ்அப்பில் பகிர்க" else "Share WhatsApp") },
                                onClick = {
                                    isMenuExpanded = false
                                    onShare()
                                }
                            )
                            if (canDelete) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                                    text = { Text(if (isTamil) "நீக்கு" else "Delete", color = Color(0xFFDC2626)) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = itemDateFormat.format(Date(expense.dateTimestamp)),
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}
/* =========================================================================================
   2. ADD / EDIT EXPENSE SCREEN
   ========================================================================================= */
@Composable
fun AddOrEditExpenseScreen(
    isEditMode: Boolean,
    initialExpense: ExpenseEntity?,
    tractors: List<TractorEntity>,
    partners: List<PartnerEntity>,
    settings: AppSettingsEntity,
    onBack: () -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)

    val operatorOptions = remember(partners, settings.activePartnerName, settings.ownerName, isTamil) {
        val list = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()

        // 1. Current user / active partner
        val currentActor = settings.activePartnerName.trim()
        if (currentActor.isNotBlank()) {
            val role = if (isTamil) "பங்குதாரர்" else "Partner"
            list.add("$currentActor ($role)")
            seenNames.add(currentActor.lowercase())
        }

        // 2. Business owner
        val owner = settings.ownerName.trim()
        if (owner.isNotBlank() && !seenNames.contains(owner.lowercase())) {
            val role = if (isTamil) "உரிமையாளர்" else "Business Owner"
            list.add("$owner ($role)")
            seenNames.add(owner.lowercase())
        }

        // 3. Registered partners
        partners.forEach { p ->
            val pName = p.name.trim()
            if (pName.isNotBlank() && !seenNames.contains(pName.lowercase())) {
                val pRole = p.role.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" }
                list.add("$pName ($pRole)")
                seenNames.add(pName.lowercase())
            }
        }
        list
    }

    // Operator Default: If creating a new expense, default to whoever is logged in (settings.activePartnerName)
    val defaultOperatorName = remember(initialExpense, settings.activePartnerName, settings.ownerName, operatorOptions) {
        if (initialExpense != null) {
            initialExpense.operatorName
        } else {
            val currentActor = settings.activePartnerName.trim().ifBlank { settings.ownerName.trim() }
            val matchedOption = operatorOptions.find { it.startsWith(currentActor, ignoreCase = true) }
            matchedOption ?: operatorOptions.firstOrNull() ?: currentActor
        }
    }
    var selectedTimestamp by remember { mutableLongStateOf(initialExpense?.dateTimestamp ?: System.currentTimeMillis()) }
    var selectedType by remember { mutableStateOf(initialExpense?.expenseType ?: "Diesel") }
    var selectedOperator by remember { mutableStateOf(defaultOperatorName) }
    var selectedTractor by remember {
        mutableStateOf(initialExpense?.tractorLabel ?: tractors.firstOrNull()?.label ?: "")
    }
    var amountText by remember { mutableStateOf(if (initialExpense != null) initialExpense.amount.toInt().toString() else "") }
    var descriptionText by remember { mutableStateOf(initialExpense?.description ?: "") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var selectedWhoPaid by remember { mutableStateOf(initialExpense?.paidBy ?: "I Paid") }
    var isTypeDropdownOpen by remember { mutableStateOf(false) }
    var isOperatorDropdownOpen by remember { mutableStateOf(false) }
    var isTractorDropdownOpen by remember { mutableStateOf(false) }
    var isPaymentModeDropdownOpen by remember { mutableStateOf(false) }
    var hasValidated by remember { mutableStateOf(false) }
    val isAmountValid = (amountText.toDoubleOrNull() ?: 0.0) > 0
    val formDateFormat = remember { SimpleDateFormat("dd/MM/yyyy  hh:mm a", if (isTamil) Locale("ta", "IN") else java.util.Locale.getDefault()) }
    fun doSave() {
        hasValidated = true
        val amt = amountText.toDoubleOrNull() ?: 0.0
        if (amt > 0) {
            val matchedTractor = tractors.find { it.label == selectedTractor }
            val cleanOperator = selectedOperator.substringBefore(" (").trim()
            val currentActor = settings.activePartnerName.ifBlank { settings.ownerName.ifBlank { "Partner" } }
            val expense = ExpenseEntity(
                id = initialExpense?.id ?: 0,
                expenseType = selectedType,
                amount = amt,
                tractorId = matchedTractor?.id ?: 0,
                tractorLabel = selectedTractor,
                operatorName = cleanOperator,
                description = descriptionText,
                addedByPartner = initialExpense?.addedByPartner?.ifBlank { null } ?: currentActor,
                dateTimestamp = selectedTimestamp,
                createdAt = initialExpense?.createdAt ?: System.currentTimeMillis(),
                paidBy = selectedWhoPaid,
                paidByPartner = if (selectedWhoPaid == "I Paid") {
                    initialExpense?.paidByPartner?.takeIf { it.isNotBlank() } ?: currentActor
                } else "",
                paidByUid = if (selectedWhoPaid == "I Paid") {
                    initialExpense?.paidByUid ?: ""
                } else ""
            )
            onSaveExpense(expense)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Top Navigation Bar
        Surface(
            color = Color(0xFF072D18), // Consistent Dark Green Header
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isTamil) "பின்விளக்கம்" else "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEditMode) (if (isTamil) "செலவைத் திருத்து" else "Edit Expense") else (if (isTamil) "செலவு சேர்க்க" else "Add Expense"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Top Right Save Text + Icon Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { doSave() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = if (isTamil) "சேமி" else "Save",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTamil) "சேமி" else "Save",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        // Form Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Date & Time* (Left) and Expense Type* (Right)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date & Time Field (Full visible datetime)
                    FormFieldCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarToday,
                        label = if (isTamil) "தேதி & நேரம்*" else "Date & Time*",
                        value = formDateFormat.format(Date(selectedTimestamp)),
                        isDropdown = false,
                        onClick = {
                            showDateTimePicker(context, selectedTimestamp) { pickedTime ->
                                selectedTimestamp = pickedTime
                            }
                        }
                    )
                    // Expense Type Field
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.Tag,
                            label = if (isTamil) "செலவு வகை*" else "Expense Type*",
                            value = getLocalizedExpenseType(selectedType, isTamil),
                            isDropdown = true,
                            onClick = { isTypeDropdownOpen = true }
                        )
                        DropdownMenu(
                            expanded = isTypeDropdownOpen,
                            onDismissRequest = { isTypeDropdownOpen = false }
                        ) {
                            ExpenseTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(getLocalizedExpenseType(type, isTamil)) },
                                    onClick = {
                                        selectedType = type
                                        isTypeDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            // Row 2: Operator* (Left) and Tractor (Chassis No.)* (Right)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Operator Dropdown (Default to logged in user)
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.Person,
                            label = if (isTamil) "இயக்குநர்*" else "Operator*",
                            value = selectedOperator,
                            isDropdown = true,
                            onClick = { isOperatorDropdownOpen = true }
                        )
                        DropdownMenu(
                            expanded = isOperatorDropdownOpen,
                            onDismissRequest = { isOperatorDropdownOpen = false }
                        ) {
                            operatorOptions.forEach { operatorLabel ->
                                DropdownMenuItem(
                                    text = { Text(operatorLabel) },
                                    onClick = {
                                        selectedOperator = operatorLabel
                                        isOperatorDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                    // Tractor Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        FormFieldCard(
                            icon = Icons.Default.DirectionsCar,
                            label = if (isTamil) "டிராக்டர் (சேஸ் எண்)*" else "Tractor (Chassis No.)*",
                            value = selectedTractor,
                            isDropdown = true,
                            onClick = { isTractorDropdownOpen = true }
                        )
                        DropdownMenu(
                            expanded = isTractorDropdownOpen,
                            onDismissRequest = { isTractorDropdownOpen = false }
                        ) {
                            tractors.forEach { tractor ->
                                DropdownMenuItem(
                                    text = { Text(tractor.label) },
                                    onClick = {
                                        selectedTractor = tractor.label
                                        isTractorDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            // Row 3: Amount (₹)* Input
            item {
                FormInputCard(
                    iconText = "₹",
                    label = if (isTamil) "தொகை (₹)*" else "Amount (₹)*",
                    value = amountText,
                    onValueChange = { amountText = it },
                    keyboardType = KeyboardType.Number,
                    placeholder = if (isTamil) "எ.கா: 2500" else "e.g. 2500",
                    isError = hasValidated && !isAmountValid,
                    errorMessage = if (isTamil) "தயவுசெய்து 0 ஐ விட அதிகமான சரியான தொகையை உள்ளிடவும்" else "Please enter a valid amount greater than 0",
                    testTag = "input_expense_amount"
                )
            }
            // Row 4: Description / Purpose (Optional)
            item {
                FormInputCard(
                    icon = Icons.Default.Description,
                    label = if (isTamil) "விவரம் / நோக்கம் (விருப்பத்தேர்வு)" else "Description / Purpose (Optional)",
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    keyboardType = KeyboardType.Text,
                    placeholder = if (isTamil) "எ.கா: களப் பணிக்கான டீசல் / டயர் பஞ்சர் பழுதுபார்த்தல்" else "e.g. Diesel for field work / Tyre puncture repair",
                    testTag = "input_expense_description"
                )
            }
            // Row 5: Payment Mode
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FormFieldCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Payments,
                        label = if (isTamil) "கட்டண முறை" else "Payment Mode",
                        value = getLocalizedPaymentMode(paymentMode, isTamil),
                        isDropdown = true,
                        onClick = { isPaymentModeDropdownOpen = true }
                    )
                    DropdownMenu(
                        expanded = isPaymentModeDropdownOpen,
                        onDismissRequest = { isPaymentModeDropdownOpen = false }
                    ) {
                        PaymentModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(getLocalizedPaymentMode(mode, isTamil)) },
                                onClick = {
                                    paymentMode = mode
                                    isPaymentModeDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }
            // Row 6: Who Paid ("I Paid" / "Business Paid")
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isTamil) "யார் செலுத்தியது?*" else "Who Paid?*",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isIPaid = selectedWhoPaid == "I Paid"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isIPaid) 1.5.dp else 1.dp,
                                color = if (isIPaid) Color(0xFF15803D) else Color(0xFFCBD5E1)
                            ),
                            color = if (isIPaid) Color(0xFFF0FDF4) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clickable { selectedWhoPaid = "I Paid" }
                                .testTag("btn_expense_who_paid_i_paid")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isIPaid) Color(0xFF15803D) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTamil) "நான் செலுத்தினேன்" else "I Paid",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (isIPaid) Color(0xFF15803D) else Color(0xFF334155)
                                )
                            }
                        }

                        val isBusinessPaid = selectedWhoPaid == "Business Paid"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isBusinessPaid) 1.5.dp else 1.dp,
                                color = if (isBusinessPaid) Color(0xFF15803D) else Color(0xFFCBD5E1)
                            ),
                            color = if (isBusinessPaid) Color(0xFFF0FDF4) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clickable { selectedWhoPaid = "Business Paid" }
                                .testTag("btn_expense_who_paid_business_paid")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = if (isBusinessPaid) Color(0xFF15803D) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTamil) "வணிகம் செலுத்தியது" else "Business Paid",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (isBusinessPaid) Color(0xFF15803D) else Color(0xFF334155)
                                )
                            }
                        }
                    }
                }
            }
            // Required fields note
            item {
                Text(
                    text = if (isTamil) "* கட்டாயப் புலங்கள்" else "* Required fields",
                    fontSize = 11.5.sp,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            }
            // Big Green Save Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { doSave() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072D18)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_save_expense_submit")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditMode) (if (isTamil) "செலவைப் புதுப்பி" else "Update Expense") else (if (isTamil) "செலவைச் சேமி" else "Save Expense"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
/* =========================================================================================
   3. EXPENSE DETAILS SCREEN
   ========================================================================================= */
@Composable
fun ExpenseDetailsScreen(
    expense: ExpenseEntity,
    jobs: List<JobEntryEntity> = emptyList(),
    settings: AppSettingsEntity,
    canDelete: Boolean = true,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val visualConfig = getExpenseVisualConfig(expense.expenseType)
    val detailsDateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", if (isTamil) Locale("ta", "IN") else Locale.getDefault()) }

    val linkedJob = jobs.find { it.id == expense.relatedJobId }
    val linkedCustomerName = linkedJob?.customerName?.ifBlank { null }
        ?: if (expense.description.startsWith("Expense for ") && expense.description.contains("'s job")) {
            expense.description.removePrefix("Expense for ").substringBefore("'s job")
        } else null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // Top Green Header
        Surface(
            color = Color(0xFF072D18), // Consistent Dark Green Header
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isTamil) "பின்விளக்கம்" else "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTamil) "செலவு விவரங்கள்" else "Expense Details",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Top Right Edit & Share Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val shareText = buildExpenseWhatsAppMessage(expense, settings.businessName, isTamil)
                            sendWhatsAppMessage(context, null, shareText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = if (isTamil) "பகிர்" else "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("btn_edit_expense_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (isTamil) "செலவைத் திருத்து" else "Edit Expense",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        // Details Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card with Category, Description, and Amount
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(visualConfig.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = visualConfig.icon,
                                contentDescription = null,
                                tint = visualConfig.tintColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getLocalizedExpenseType(expense.expenseType, isTamil),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (expense.description.isNotBlank()) expense.description else (if (isTamil) "விவரங்கள் எதுவும் வழங்கப்படவில்லை" else "No description provided"),
                                fontSize = 12.5.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatInr(expense.amount, settings.currency),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getLocalizedPaymentMode("Cash", isTamil),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
            // 6 Grid Details Cards
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Row 1: Date & Time & Operator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            label = if (isTamil) "தேதி & நேரம்" else "Date & Time",
                            value = detailsDateFormat.format(Date(expense.dateTimestamp))
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            label = if (isTamil) "இயக்குநர்" else "Operator",
                            value = expense.operatorName.ifBlank { settings.activePartnerName }
                        )
                    }
                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Row 2: Tractor & Expense Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.DirectionsCar,
                            label = if (isTamil) "டிராக்டர் (சேஸ் எண்)" else "Tractor (Chassis No.)",
                            value = expense.tractorLabel
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Tag,
                            label = if (isTamil) "செலவு வகை" else "Expense Type",
                            value = getLocalizedExpenseType(expense.expenseType, isTamil)
                        )
                    }
                    if (linkedCustomerName != null) {
                        Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailInfoItem(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.Person,
                                label = if (isTamil) "தொடர்புடைய வாடிக்கையாளர் / வேலை" else "Related Customer / Job",
                                value = linkedCustomerName
                            )
                        }
                    }
                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Row 3: Added By & Created At
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Person,
                            label = if (isTamil) "சேர்த்தவர்" else "Added By",
                            value = expense.addedByPartner.ifBlank { settings.activePartnerName }
                        )
                        DetailInfoItem(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            label = if (isTamil) "உருவாக்கப்பட்ட நேரம்" else "Created At",
                            value = detailsDateFormat.format(Date(expense.createdAt))
                        )
                    }
                    Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Row 4: Who Paid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val whoPaidLabel = if (expense.paidBy == "Business Paid") {
                            if (isTamil) "வணிகம் செலுத்தியது" else "Business Paid"
                        } else {
                            val payer = expense.paidByPartner.ifBlank { expense.operatorName.ifBlank { expense.addedByPartner } }
                            if (payer.isNotBlank()) "${if (isTamil) "நான் செலுத்தினேன்" else "I Paid"} ($payer)" else (if (isTamil) "நான் செலுத்தினேன்" else "I Paid")
                        }
                        DetailInfoItem(
                            modifier = Modifier.fillMaxWidth(),
                            icon = if (expense.paidBy == "Business Paid") Icons.Default.Storefront else Icons.Default.Person,
                            label = if (isTamil) "யார் செலுத்தியது" else "Who Paid",
                            value = whoPaidLabel
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (canDelete) {
                // Delete Expense Button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color(0xFFDC2626))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_delete_expense_details")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTamil) "செலவை நீக்கு" else "Delete Expense",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    // Confirmation Alert
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (isTamil) "செலவை நீக்கவா?" else "Delete Expense?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (isTamil) "ரூ. ${formatInr(expense.amount, settings.currency)} மதிப்புள்ள இந்த ${getLocalizedExpenseType(expense.expenseType, isTamil)} செலவுப் பதிவை நிச்சயமாக நீக்க விரும்புகிறீர்களா? இந்தச் செயலைத் திரும்பப் பெற முடியாது."
                    else "Are you sure you want to delete this ${expense.expenseType} record of ${formatInr(expense.amount, settings.currency)}? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}
/* =========================================================================================
   Helper Form Components
   ========================================================================================= */
@Composable
fun FormFieldCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isDropdown: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE5E7EB))),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isDropdown) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
@Composable
fun FormInputCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconText: String? = null,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String = "",
    testTag: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isError) Color(0xFFDC2626) else Color(0xFFE5E7EB))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (iconText != null) {
                    Text(
                        text = iconText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFF9CA3AF)) },
                        singleLine = keyboardType == KeyboardType.Number,
                        maxLines = if (keyboardType == KeyboardType.Number) 1 else 3,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackFocusedField(requester, coroutineScope)
                            .testTag(testTag)
                    )
                }
            }
            if (isError && errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFDC2626),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
@Composable
fun DetailInfoItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                maxLines = 2,
                lineHeight = 15.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
/* =========================================================================================
   Date & Time Helper Utilities
   ========================================================================================= */
fun showDatePicker(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val resultCal = Calendar.getInstance().apply {
                timeInMillis = initialMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            onPicked(resultCal.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
fun showDateTimePicker(context: Context, initialMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(resultCal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
fun getStartOfDayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
fun getEndOfDayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
fun getStartOfWeekMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
fun getStartOfMonthMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
