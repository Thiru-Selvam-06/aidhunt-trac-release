package com.example.ui.util

import com.example.data.firebase.CloudConnectionState

enum class CloudStatusAction { OPEN_RETAINED_ACTIONS, RETRY_LISTENERS_AND_QUEUE, SYNC_QUEUE }

/** A retained rejection needs its diagnostic, not another unchanged upload attempt. */
fun cloudStatusAction(rejectedCount: Int, connection: CloudConnectionState): CloudStatusAction = when {
    rejectedCount > 0 -> CloudStatusAction.OPEN_RETAINED_ACTIONS
    connection == CloudConnectionState.ERROR -> CloudStatusAction.RETRY_LISTENERS_AND_QUEUE
    else -> CloudStatusAction.SYNC_QUEUE
}
