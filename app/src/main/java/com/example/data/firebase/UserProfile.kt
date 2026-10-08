package com.example.data.firebase

import androidx.annotation.Keep

@Keep
data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val photoUrl: String? = null,
    val defaultWorkspaceId: String? = null,
    val workspaces: List<String> = emptyList(),
    val role: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mutableMapOf<String, Any?>(
        "uid" to uid,
        "displayName" to displayName,
        "email" to email,
        "phoneNumber" to phoneNumber,
        "photoUrl" to photoUrl,
        "defaultWorkspaceId" to defaultWorkspaceId,
        "workspaces" to workspaces,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    ).apply {
        if (role != null) put("role", role)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): UserProfile {
            return UserProfile(
                uid = map["uid"] as? String ?: "",
                displayName = (map["displayName"] as? String) ?: (map["name"] as? String),
                email = map["email"] as? String,
                phoneNumber = map["phoneNumber"] as? String,
                photoUrl = map["photoUrl"] as? String,
                defaultWorkspaceId = map["defaultWorkspaceId"] as? String,
                workspaces = (map["workspaces"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                role = map["role"] as? String,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data class Unauthenticated(val message: String? = null) : AuthState()
    data class CodeSent(val verificationId: String, val phoneNumber: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AccountLookupResult {
    data class Found(val uid: String, val displayName: String? = null) : AccountLookupResult()
    object NotFound : AccountLookupResult()
    data class Error(val message: String, val isNetworkError: Boolean = false) : AccountLookupResult()
}

