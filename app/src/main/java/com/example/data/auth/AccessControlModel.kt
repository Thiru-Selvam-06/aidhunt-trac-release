package com.example.data.auth

import androidx.annotation.Keep
import org.json.JSONObject

@Keep
enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    EXPIRED
}

@Keep
data class FeaturePermissions(
    val canCreateNewEntry: Boolean = true,
    val canCreateOldEntry: Boolean = true,
    val canEditEntry: Boolean = true,
    val canDeleteEntry: Boolean = true,
    val canViewCustomerCredit: Boolean = true,
    val canCollectPayment: Boolean = true,
    val canViewBusinessOverview: Boolean = true,
    val canViewBalanceSheet: Boolean = true,
    val canViewCollectionHistory: Boolean = true,
    val canViewExpenses: Boolean = true,
    val canViewWithdrawals: Boolean = true,
    val canExportPdf: Boolean = true,
    val canManageTractors: Boolean = true,
    val canManagePartners: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "canCreateNewEntry" to canCreateNewEntry,
        "canCreateOldEntry" to canCreateOldEntry,
        "canEditEntry" to canEditEntry,
        "canDeleteEntry" to canDeleteEntry,
        "canViewCustomerCredit" to canViewCustomerCredit,
        "canCollectPayment" to canCollectPayment,
        "canViewBusinessOverview" to canViewBusinessOverview,
        "canViewBalanceSheet" to canViewBalanceSheet,
        "canViewCollectionHistory" to canViewCollectionHistory,
        "canViewExpenses" to canViewExpenses,
        "canViewWithdrawals" to canViewWithdrawals,
        "canExportPdf" to canExportPdf,
        "canManageTractors" to canManageTractors,
        "canManagePartners" to canManagePartners
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("canCreateNewEntry", canCreateNewEntry)
        put("canCreateOldEntry", canCreateOldEntry)
        put("canEditEntry", canEditEntry)
        put("canDeleteEntry", canDeleteEntry)
        put("canViewCustomerCredit", canViewCustomerCredit)
        put("canCollectPayment", canCollectPayment)
        put("canViewBusinessOverview", canViewBusinessOverview)
        put("canViewBalanceSheet", canViewBalanceSheet)
        put("canViewCollectionHistory", canViewCollectionHistory)
        put("canViewExpenses", canViewExpenses)
        put("canViewWithdrawals", canViewWithdrawals)
        put("canExportPdf", canExportPdf)
        put("canManageTractors", canManageTractors)
        put("canManagePartners", canManagePartners)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>?): FeaturePermissions {
            if (map == null) return FeaturePermissions()
            return FeaturePermissions(
                canCreateNewEntry = map["canCreateNewEntry"] as? Boolean ?: true,
                canCreateOldEntry = map["canCreateOldEntry"] as? Boolean ?: true,
                canEditEntry = map["canEditEntry"] as? Boolean ?: true,
                canDeleteEntry = map["canDeleteEntry"] as? Boolean ?: true,
                canViewCustomerCredit = map["canViewCustomerCredit"] as? Boolean ?: true,
                canCollectPayment = map["canCollectPayment"] as? Boolean ?: true,
                canViewBusinessOverview = map["canViewBusinessOverview"] as? Boolean ?: true,
                canViewBalanceSheet = map["canViewBalanceSheet"] as? Boolean ?: true,
                canViewCollectionHistory = map["canViewCollectionHistory"] as? Boolean ?: true,
                canViewExpenses = map["canViewExpenses"] as? Boolean ?: true,
                canViewWithdrawals = map["canViewWithdrawals"] as? Boolean ?: true,
                canExportPdf = map["canExportPdf"] as? Boolean ?: true,
                canManageTractors = map["canManageTractors"] as? Boolean ?: true,
                canManagePartners = map["canManagePartners"] as? Boolean ?: true
            )
        }

        fun fromJson(json: JSONObject?): FeaturePermissions {
            if (json == null) return FeaturePermissions()
            return FeaturePermissions(
                canCreateNewEntry = json.optBoolean("canCreateNewEntry", true),
                canCreateOldEntry = json.optBoolean("canCreateOldEntry", true),
                canEditEntry = json.optBoolean("canEditEntry", true),
                canDeleteEntry = json.optBoolean("canDeleteEntry", true),
                canViewCustomerCredit = json.optBoolean("canViewCustomerCredit", true),
                canCollectPayment = json.optBoolean("canCollectPayment", true),
                canViewBusinessOverview = json.optBoolean("canViewBusinessOverview", true),
                canViewBalanceSheet = json.optBoolean("canViewBalanceSheet", true),
                canViewCollectionHistory = json.optBoolean("canViewCollectionHistory", true),
                canViewExpenses = json.optBoolean("canViewExpenses", true),
                canViewWithdrawals = json.optBoolean("canViewWithdrawals", true),
                canExportPdf = json.optBoolean("canExportPdf", true),
                canManageTractors = json.optBoolean("canManageTractors", true),
                canManagePartners = json.optBoolean("canManagePartners", true)
            )
        }
    }
}

@Keep
data class WorkspaceQuotas(
    val maxTractors: Int? = null,
    val maxOperators: Int? = null,
    val maxPartners: Int? = null,
    val maxRecords: Int? = null,
    val maxReports: Int? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "maxTractors" to maxTractors,
        "maxOperators" to maxOperators,
        "maxPartners" to maxPartners,
        "maxRecords" to maxRecords,
        "maxReports" to maxReports
    )

    fun toJson(): JSONObject = JSONObject().apply {
        maxTractors?.let { put("maxTractors", it) }
        maxOperators?.let { put("maxOperators", it) }
        maxPartners?.let { put("maxPartners", it) }
        maxRecords?.let { put("maxRecords", it) }
        maxReports?.let { put("maxReports", it) }
    }

    companion object {
        fun fromMap(map: Map<String, Any?>?): WorkspaceQuotas {
            if (map == null) return WorkspaceQuotas()
            return WorkspaceQuotas(
                maxTractors = (map["maxTractors"] as? Number)?.toInt(),
                maxOperators = (map["maxOperators"] as? Number)?.toInt(),
                maxPartners = (map["maxPartners"] as? Number)?.toInt(),
                maxRecords = (map["maxRecords"] as? Number)?.toInt(),
                maxReports = (map["maxReports"] as? Number)?.toInt()
            )
        }

        fun fromJson(json: JSONObject?): WorkspaceQuotas {
            if (json == null) return WorkspaceQuotas()
            return WorkspaceQuotas(
                maxTractors = if (json.has("maxTractors")) json.optInt("maxTractors") else null,
                maxOperators = if (json.has("maxOperators")) json.optInt("maxOperators") else null,
                maxPartners = if (json.has("maxPartners")) json.optInt("maxPartners") else null,
                maxRecords = if (json.has("maxRecords")) json.optInt("maxRecords") else null,
                maxReports = if (json.has("maxReports")) json.optInt("maxReports") else null
            )
        }
    }
}

@Keep
data class AccessControlDocument(
    val accountStatus: String = "ACTIVE",
    val validFrom: Long = 0L,
    val validUntil: Long = 0L,
    val permissions: FeaturePermissions = FeaturePermissions(),
    val quotas: WorkspaceQuotas = WorkspaceQuotas(),
    val updatedAt: Long = 0L
) {
    fun getResolvedStatus(currentTime: Long = System.currentTimeMillis()): AccountStatus {
        val normalized = accountStatus.trim().uppercase()
        if (normalized == "SUSPENDED") return AccountStatus.SUSPENDED
        if (normalized == "EXPIRED") return AccountStatus.EXPIRED
        if (validUntil > 0L && currentTime > validUntil) return AccountStatus.EXPIRED
        return AccountStatus.ACTIVE
    }

    val isAccountBlocked: Boolean
        get() = getResolvedStatus() != AccountStatus.ACTIVE

    fun toMap(): Map<String, Any?> = mapOf(
        "accountStatus" to accountStatus,
        "validFrom" to validFrom,
        "validUntil" to validUntil,
        "permissions" to permissions.toMap(),
        "quotas" to quotas.toMap(),
        "updatedAt" to updatedAt
    )

    fun toJsonString(): String {
        return JSONObject().apply {
            put("accountStatus", accountStatus)
            put("validFrom", validFrom)
            put("validUntil", validUntil)
            put("permissions", permissions.toJson())
            put("quotas", quotas.toJson())
            put("updatedAt", updatedAt)
        }.toString()
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): AccessControlDocument {
            if (map == null) return AccessControlDocument()
            val rawStatus = map["accountStatus"] as? String ?: "ACTIVE"
            val vFrom = (map["validFrom"] as? Number)?.toLong() ?: 0L
            val vUntil = (map["validUntil"] as? Number)?.toLong() ?: 0L
            val permMap = map["permissions"] as? Map<String, Any?>
            val quotaMap = map["quotas"] as? Map<String, Any?>
            val upAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
            return AccessControlDocument(
                accountStatus = rawStatus,
                validFrom = vFrom,
                validUntil = vUntil,
                permissions = FeaturePermissions.fromMap(permMap),
                quotas = WorkspaceQuotas.fromMap(quotaMap),
                updatedAt = upAt
            )
        }

        fun fromJsonString(jsonStr: String?): AccessControlDocument {
            if (jsonStr.isNullOrBlank()) return AccessControlDocument()
            return try {
                val obj = JSONObject(jsonStr)
                AccessControlDocument(
                    accountStatus = obj.optString("accountStatus", "ACTIVE"),
                    validFrom = obj.optLong("validFrom", 0L),
                    validUntil = obj.optLong("validUntil", 0L),
                    permissions = FeaturePermissions.fromJson(obj.optJSONObject("permissions")),
                    quotas = WorkspaceQuotas.fromJson(obj.optJSONObject("quotas")),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            } catch (e: Exception) {
                AccessControlDocument()
            }
        }
    }
}
