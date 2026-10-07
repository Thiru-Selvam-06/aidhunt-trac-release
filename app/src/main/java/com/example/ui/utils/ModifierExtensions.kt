package com.example.ui.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.trackFocusedField(
    requester: BringIntoViewRequester,
    coroutineScope: CoroutineScope,
    onFocused: () -> Unit = {},
    onFocusLost: () -> Unit = {}
): Modifier = this
    .bringIntoViewRequester(requester)
    .onFocusChanged { focusState ->
        if (focusState.isFocused) {
            onFocused()
            coroutineScope.launch {
                try {
                    requester.bringIntoView()
                } catch (_: Exception) {}
            }
        } else {
            onFocusLost()
        }
    }
