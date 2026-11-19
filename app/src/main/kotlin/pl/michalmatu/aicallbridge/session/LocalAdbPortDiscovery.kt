package pl.michalmatu.aicallbridge.session

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android's built-in DNS-SD lookup of ADB Wi-Fi ports.
 *
 * mDNS is untrusted discovery data: we never use an advertised host, address,
 * device ID or shell command. Only ports with a live 127.0.0.1 socket can be
 * candidates; caller subsequently authenticates the ADB transport and exact
 * local Samsung model and shell UID.
 *
 * Invoked on an Activity-owned worker after the user's explicit action.
 * No timers, persistent discovery service, daemon or permission escalation.
 */
internal class LocalAdbPortDiscovery(private val context: Context) {
    companion object {
        const val PAIRING = "_adb-tls-pairing._tcp."
        const val CONNECT = "_adb-tls-connect._tcp."
    }

    fun discover(type: String): Int? {
        if (type != PAIRING && type != CONNECT) return null
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val candidatePorts = ConcurrentHashMap.newKeySet<Int>()
        val discovered = CountDownLatch(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discovered.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                // NSD may reject overlapping or stale resolver requests.
                runCatching {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val port = serviceInfo.port
                            if (port in 1..65535 && socketOnLoopback(port)) {
                                candidatePorts.add(port)
                                discovered.countDown()
                            }
                        }
                    })
                }
            }
        }
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            // Allow time for the Android system to announce the pairing dialog.
            discovered.await(8, TimeUnit.SECONDS)
            if (candidatePorts.isNotEmpty()) {
                // Observe briefly for ambiguous local service announcements.
                Thread.sleep(350L)
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return LocalAdbGsmPolicy.uniqueLocalPort(candidatePorts.toList())
    }

    private fun socketOnLoopback(port: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(LocalAdbGsmPolicy.LOOPBACK, port), 200)
                true
            }
        }.getOrDefault(false)
}
