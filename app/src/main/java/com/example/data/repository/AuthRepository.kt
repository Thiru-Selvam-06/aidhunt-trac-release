package com.example.data.repository

import android.app.Activity
import android.content.Context
import com.example.data.firebase.AuthService
import com.example.data.firebase.AuthState
import com.example.data.firebase.FirebaseAuthService
import com.example.data.firebase.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val authService: AuthService
) {
    val authState: StateFlow<AuthState> = authService.authState
    val currentUserProfile: StateFlow<UserProfile?> = authService.currentUserProfile
    val currentUid: String?
        get() = authService.currentUid

    val isLoggedInFlow: Flow<Boolean> = authState.map { state ->
        state is AuthState.Authenticated
    }

    fun startListening() {
        authService.startAuthStateListener()
    }

    suspend fun signInWithGoogle(context: Context, webClientId: String? = null): Result<UserProfile> {
        return authService.signInWithGoogle(context, webClientId)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        return authService.signInWithEmail(email, password)
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<UserProfile> {
        return authService.signUpWithEmail(email, password)
    }

    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onError: (message: String) -> Unit,
        onAutoVerified: (userProfile: UserProfile) -> Unit
    ) {
        authService.sendPhoneOtp(activity, phoneNumber, onCodeSent, onError, onAutoVerified)
    }

    suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): Result<UserProfile> {
        return authService.verifyPhoneOtp(verificationId, otpCode)
    }

    suspend fun signOut() {
        authService.signOut()
    }

    fun setAuthenticatedProfile(profile: UserProfile) {
        authService.setAuthenticatedProfile(profile)
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                val service = FirebaseAuthService(context.applicationContext)
                val instance = AuthRepository(service)
                INSTANCE = instance
                instance
            }
        }
    }
}
