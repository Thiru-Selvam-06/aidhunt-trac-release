package com.example.data.firebase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CloudConnectionState { IDLE, CONNECTING, CONNECTED, ERROR }

/** Server snapshots, not cached snapshots or an empty upload queue, prove listener health. */
class CloudListenerHealth {
    private val listeners = mutableMapOf<String, MutableMap<String, CloudConnectionState>>()
    private val mutableStates = MutableStateFlow<Map<String, CloudConnectionState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    @Synchronized fun begin(workspaceId: String, names: Set<String>) {
        listeners[workspaceId] = names.associateWith { CloudConnectionState.CONNECTING }.toMutableMap()
        publish()
    }
    @Synchronized fun snapshot(workspaceId: String, name: String, fromCache: Boolean) {
        val state = listeners[workspaceId] ?: return
        if (name !in state || fromCache) return
        state[name] = CloudConnectionState.CONNECTED
        publish()
    }
    @Synchronized fun failed(workspaceId: String, name: String) {
        val state = listeners[workspaceId] ?: return
        if (name !in state) return
        state[name] = CloudConnectionState.ERROR
        publish()
    }
    @Synchronized fun remove(workspaceId: String) { listeners.remove(workspaceId); publish() }
    @Synchronized fun clear() { listeners.clear(); publish() }
    private fun publish() {
        mutableStates.value = listeners.mapValues { (_, state) ->
            when {
                state.values.any { it == CloudConnectionState.ERROR } -> CloudConnectionState.ERROR
                state.isNotEmpty() && state.values.all { it == CloudConnectionState.CONNECTED } -> CloudConnectionState.CONNECTED
                else -> CloudConnectionState.CONNECTING
            }
        }
    }
}

fun cloudSyncLabel(cloud: CloudConnectionState, online: Boolean, uploading: Boolean, pending: Int, isTamil: Boolean = false, rejected: Int = 0, blocked: Int = 0): String = when {
    blocked > 0 && blocked == rejected -> if (isTamil) "$blocked தடுக்கப்பட்டது" else "$blocked Blocked"
    rejected > 0 -> if (isTamil) rejected.toString() + " மறுக்கப்பட்டது" else rejected.toString() + " Need review"
    cloud == CloudConnectionState.ERROR -> if (isTamil) "கிளவுட் பிழை" else "Cloud error"
    !online -> if (isTamil) "ஆஃப்லைன் ($pending)" else "Offline ($pending)"
    uploading -> if (isTamil) "ஒத்திசை..." else "Syncing..."
    pending > 0 -> if (isTamil) "$pending நிலுவை" else "$pending Pending"
    cloud != CloudConnectionState.CONNECTED -> if (isTamil) "இணைக்கிறது..." else "Connecting..."
    else -> if (isTamil) "ஒத்திசைந்தது" else "Synced"
}
