package com.qoder.atvremote.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.qoder.atvremote.model.TvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Discovers Android TV / Google TV boxes advertising the v2 remote service over mDNS.
 * The library holds a multicast lock because several Android builds filter mDNS replies
 * unless Wi-Fi multicast reception is explicitly enabled.
 */
@Singleton
class DeviceDiscovery @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun discover(scanDurationMillis: Long = SCAN_DURATION_MS): Flow<List<TvDevice>> = channelFlow {
        val found = LinkedHashMap<String, TvDevice>()
        val requested = ConcurrentHashMap.newKeySet<String>()
        val resolutionQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) {
                    return
                }
                if (requested.add(serviceInfo.serviceName)) {
                    resolutionQueue.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                resolutionQueue.close(IOException("mDNS discovery failed with code $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        val resolver = launch(Dispatchers.IO) {
            for (candidate in resolutionQueue) {
                val resolved = resolve(candidate) ?: continue
                val address = resolved.host?.hostAddress ?: continue
                val name = resolved.serviceName.ifBlank { address }
                val pairingPort = resolved.port.takeIf { it > 0 } ?: TvDevice.DEFAULT_PAIRING_PORT
                val device = TvDevice(name = name, host = address, pairingPort = pairingPort)

                val previous = found[name]
                if (previous == null || (previous.host.contains(':') && !address.contains(':'))) {
                    found[name] = device
                    trySend(found.values.toList())
                }
            }
        }

        try {
            multicastLock.acquire()
        } catch (t: Throwable) {
            resolutionQueue.close(t)
            throw t
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            trySend(emptyList())
            delay(scanDurationMillis)
            delay(RESOLUTION_GRACE_MS)
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            resolutionQueue.close()
            resolver.cancel()
            if (multicastLock.isHeld) multicastLock.release()
        }
    }

    private suspend fun resolve(serviceInfo: NsdServiceInfo): NsdServiceInfo? = try {
        suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(resolved)
                    }

                    override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("mDNS resolve failed: $errorCode"))
                        }
                    }
                },
            )
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
        const val MULTICAST_LOCK_TAG = "atv-remote-mdns"
        const val SCAN_DURATION_MS = 6_000L
        const val RESOLUTION_GRACE_MS = 500L
    }
}
