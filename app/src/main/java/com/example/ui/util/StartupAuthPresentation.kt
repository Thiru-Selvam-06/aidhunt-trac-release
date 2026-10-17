package com.example.ui.util

import com.example.data.firebase.AuthState

/** Interactive authentication keeps its form and callback state until verification completes. */
fun shouldShowStartupSplash(startupAuthResolved: Boolean, authState: AuthState, workspaceReady: Boolean, workspaceFailed: Boolean = false): Boolean =
    !startupAuthResolved || (authState is AuthState.Authenticated && !workspaceReady && !workspaceFailed)
