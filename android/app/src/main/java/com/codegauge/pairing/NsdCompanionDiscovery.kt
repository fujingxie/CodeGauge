package com.codegauge.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

interface CompanionDiscovery {
    fun start(onEndpointsChanged: (List<CompanionEndpoint>) -> Unit)

    fun stop()
}

object NoopCompanionDiscovery : CompanionDiscovery {
    override fun start(onEndpointsChanged: (List<CompanionEndpoint>) -> Unit) {
        onEndpointsChanged(emptyList())
    }

    override fun stop() = Unit
}

class NsdCompanionDiscovery(context: Context) : CompanionDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val endpoints = linkedMapOf<String, CompanionEndpoint>()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onEndpointsChanged: ((List<CompanionEndpoint>) -> Unit)? = null

    override fun start(onEndpointsChanged: (List<CompanionEndpoint>) -> Unit) {
        stop()
        this.onEndpointsChanged = onEndpointsChanged
        emit(emptyList())
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith(ServiceType)) {
                    return
                }
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                removeEndpoint(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(Tag, "NSD discovery failed to start: $errorCode")
                stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(Tag, "NSD discovery failed to stop: $errorCode")
                stopServiceDiscovery(this)
            }
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices(ServiceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (exception: RuntimeException) {
            Log.w(Tag, "NSD discovery could not start", exception)
            stop()
        }
    }

    override fun stop() {
        discoveryListener?.let { stopServiceDiscovery(it) }
        discoveryListener = null
        onEndpointsChanged = null
        synchronized(lock) {
            endpoints.clear()
        }
        releaseMulticastLock()
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(Tag, "NSD resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                if (port !in 1..65535) {
                    return
                }

                val key = serviceInfo.serviceName.ifBlank { "$host:$port" }
                val endpoint = CompanionEndpoint(
                    name = key,
                    host = host,
                    port = port,
                )

                val snapshot = synchronized(lock) {
                    endpoints[key] = endpoint
                    endpoints.values.sortedBy { it.name.lowercase() }
                }
                emit(snapshot)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, listener)
        } catch (exception: RuntimeException) {
            Log.w(Tag, "NSD service could not resolve", exception)
        }
    }

    private fun removeEndpoint(serviceName: String) {
        val snapshot = synchronized(lock) {
            endpoints.remove(serviceName)
            endpoints.values.sortedBy { it.name.lowercase() }
        }
        emit(snapshot)
    }

    private fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (exception: RuntimeException) {
            Log.w(Tag, "NSD discovery was already stopped", exception)
        }
    }

    private fun acquireMulticastLock() {
        val lock = wifiManager?.createMulticastLock("codegauge-mdns")
        lock?.setReferenceCounted(false)
        lock?.acquire()
        multicastLock = lock
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        multicastLock = null
    }

    private fun emit(snapshot: List<CompanionEndpoint>) {
        val callback = onEndpointsChanged ?: return
        mainHandler.post {
            if (onEndpointsChanged === callback) {
                callback(snapshot)
            }
        }
    }

    companion object {
        private const val Tag = "CodeGaugeDiscovery"
        private const val ServiceType = "_codegauge._tcp."
    }
}
