package com.example.data.firebase

/** Offline cache is recovery for unavailable verification, never a substitute for denied access. */
internal fun recoverVerifiedProfile(error: Exception, cached: () -> UserProfile?): UserProfile {
    if (error is SecurityException || error is kotlinx.coroutines.CancellationException ||
        error is com.google.firebase.auth.FirebaseAuthInvalidUserException) throw error
    if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
        error.code in setOf(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED)) {
        // The auth listener also treats SecurityException as a terminal denial, so it
        // cannot re-enter its network fallback and restore the same rejected cache.
        throw SecurityException("Server denied authenticated profile access; reconnect and verify account access", error)
    }
    return cached() ?: throw error
}
