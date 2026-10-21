package com.example.data.sync

import com.google.firebase.firestore.FirebaseFirestoreException

/** Attempt count is diagnostic evidence, never a reason to reject a valid financial action. */
object SyncFailurePolicy {
    fun permanent(error: Exception): Boolean = error is SecurityException ||
        error is IllegalArgumentException || error is org.json.JSONException ||
        error is com.example.data.firebase.WorkspaceBackendUnavailableException ||
        (error as? FirebaseFirestoreException)?.code in setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION,
            FirebaseFirestoreException.Code.NOT_FOUND)
}
