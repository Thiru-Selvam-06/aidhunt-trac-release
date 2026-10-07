package com.example.data.firebase

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthService {
    val authState: StateFlow<AuthState>
    val currentUserProfile: StateFlow<UserProfile?>
    val currentUid: String?

    fun startAuthStateListener()
    suspend fun signInWithGoogle(context: Context, webClientId: String? = null): Result<UserProfile>
    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile>
    suspend fun signUpWithEmail(email: String, password: String): Result<UserProfile>
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onError: (message: String) -> Unit,
        onAutoVerified: (userProfile: UserProfile) -> Unit
    )
    suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): Result<UserProfile>
    suspend fun signOut()
    fun setAuthenticatedProfile(profile: UserProfile)
}
