package com.example.data.firebase

/** Offline cache is recovery for unavailable verification, never a substitute for denied access. */
internal fun recoverVerifiedProfile(error: Exception, cached: () -> UserProfile?): UserProfile {
    if (error is SecurityException || error is kotlinx.coroutines.CancellationException ||
        error is com.google.firebase.auth.FirebaseAuthInvalidUserException) throw error
    return cached() ?: throw error
}
