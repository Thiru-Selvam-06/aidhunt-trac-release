package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val name: String,
    val phone: String,
    val role: String = "Partner", // "Owner", "Partner"
    val avatarColorHex: String = "#1E4D2B",
    val photoUri: String = "",
    val isCurrentActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tractors")
data class TractorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val label: String, // e.g. "Mahindra 575 DI"
    val chassisNo: String, // e.g. "MH-575-TN45-9871"
    val modelYear: String = "",
    val operatorName: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val createdByRole: String = ""
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val name: String,
    val phone: String,
    val location: String = "",
    val totalBilled: Double = 0.0,
    val totalPaid: Double = 0.0,
    val balanceDue: Double = 0.0,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "job_entries")
data class JobEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val customerId: Long = 0,
    val customerName: String,
    val customerPhone: String = "",
    val customerLocation: String = "",
    val operatorName: String,
    val tractorId: Long = 0,
    val tractorLabel: String,
    val workType: String, // "Ploughing", "Rotavator", "Cultivator", "Harvester", "Leveler", "Trailer", "Other"
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMinutes: Long,
    val hourlyRate: Double = 1100.0,
    val totalAmount: Double,
    val amountReceived: Double,
    val pendingAmount: Double,
    val addedByPartner: String,
    val notes: String = "",
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val createdByRole: String = "",
    val editedByUid: String = "",
    val editedByName: String = "",
    val editedByRole: String = "",
    val updatedAt: Long = 0L
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val jobEntryId: Long = 0,
    val customerId: Long = 0,
    val customerName: String = "",
    val tractorId: Long = 0,
    val tractorLabel: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "Cash",
    val notes: String = "",
    val collectedByUid: String = "",
    val collectedByName: String = "",
    val collectedByRole: String = "",
    val collectedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val expenseType: String, // "Diesel", "Petrol", "Repair", "Puncture", "Oil Change", "Driver Bata", "Spare Parts", "Other"
    val amount: Double,
    val tractorId: Long = 0,
    val tractorLabel: String,
    val operatorName: String = "",
    val description: String = "",
    val addedByPartner: String,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val relatedJobId: Long? = null,
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val paidBy: String = "I Paid", // "I Paid" or "Business Paid"
    val paidByPartner: String = "",
    val paidByUid: String = "",
    val createdByUid: String = "",
    val createdByRole: String = ""
)

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val partnerId: Long = 0,
    val partnerName: String,
    val amount: Double,
    val category: String = "Personal Use", // "Personal Use", "Fuel Advance", "Salary", "Profit Share", "Emergency", "Other"
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // Authorization identity fields (added in DB v11)
    val targetPartnerUid: String = "",  // Firebase UID of the partner receiving the withdrawal
    val createdByUid: String = "",      // Firebase UID of the actor who created this record
    val createdByRole: String = ""      // Normalized role of the actor at creation time
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val workspaceId: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val businessPhone: String = "",
    val businessAddress: String = "",
    val gstNumber: String = "",
    val defaultHourlyRate: Double = 1100.0,
    val currency: String = "₹",
    val language: String = "EN", // "EN", "TA" (Tamil)
    val sharedAccountId: String = "",
    val isLoggedIn: Boolean = false,
    val activePartnerName: String = "",
    val activePartnerPhone: String = "",
    val profilePhotoUri: String = "",
    val lockedTractorLabel: String = "",
    val lastSyncTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val text: String,
    val isChecked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "work_type_extensions")
data class WorkTypeExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workspaceId: String = "",
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val createdByRole: String = ""
)

