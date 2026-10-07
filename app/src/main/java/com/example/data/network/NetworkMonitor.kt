package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors live device network connectivity (WiFi / Cellular / Ethernet)
 * and notifies listeners when network connectivity transitions between Offline and Online.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var onNetworkAvailableListener: (() -> Unit)? = null

    init {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                        CoroutineScope(Dispatchers.IO).launch {
                            onNetworkAvailableListener?.invoke()
                        }
                    }

                    override fun onLost(network: Network) {
                        // Check if any other valid network remains active
                        val stillConnected = checkInitialConnectivity()
                        _isOnline.value = stillConnected
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        _isOnline.value = hasInternet
                        if (hasInternet) {
                            CoroutineScope(Dispatchers.IO).launch {
                                onNetworkAvailableListener?.invoke()
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            // Fallback for restricted test environments
            _isOnline.value = true
        }
    }

    fun setOnNetworkAvailableListener(listener: () -> Unit) {
        this.onNetworkAvailableListener = listener
    }

    private fun checkInitialConnectivity(): Boolean {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // default to true if check fails
        }
    }
}
