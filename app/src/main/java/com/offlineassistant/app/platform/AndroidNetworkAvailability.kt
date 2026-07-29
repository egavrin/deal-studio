package com.offlineassistant.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicReference

fun Context.hasValidatedInternet(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

class AndroidValidatedInternetMonitor(
    context: Context,
    private val onAvailabilityChanged: (Boolean) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(ConnectivityManager::class.java)
    private val lastPublished = AtomicReference<Boolean?>(null)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publishCurrentState()

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            publishCurrentState()
        }

        override fun onLost(network: Network) = publishCurrentState()

        override fun onUnavailable() = publishCurrentState()
    }
    private val registered: Boolean

    init {
        publishCurrentState()
        registered = runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }.isSuccess
    }

    private fun publishCurrentState() {
        val available = appContext.hasValidatedInternet()
        val previous = lastPublished.getAndSet(available)
        if (previous != available) onAvailabilityChanged(available)
    }

    override fun close() {
        if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
    }
}
