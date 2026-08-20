package com.simeo.codexmicromobile

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

data class DiscoveredRelay(
  val hostId: String,
  val fingerprint: String,
  val endpoint: String,
  val platform: HostPlatform
)

class NearbyDiscovery(context: Context, private val onRelay: (DiscoveredRelay) -> Unit) {
  private val manager = context.getSystemService(NsdManager::class.java)
  private var running = false
  private val listener = object : NsdManager.DiscoveryListener {
    override fun onDiscoveryStarted(serviceType: String) { running = true }
    override fun onDiscoveryStopped(serviceType: String) { running = false }
    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { running = false }
    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { running = false }
    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
      if (!serviceInfo.serviceType.startsWith("_codexdeck._tcp")) return
      @Suppress("DEPRECATION")
      manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceResolved(info: NsdServiceInfo) {
          val attributes = info.attributes.mapValues { String(it.value, Charsets.UTF_8) }
          val hostId = attributes["hostId"]?.trim().orEmpty()
          val fingerprint = attributes["fingerprint"]?.normalizeFingerprint().orEmpty()
          val address = info.host?.hostAddress?.substringBefore('%').orEmpty()
          val port = info.port
          if (hostId.isBlank() || !fingerprint.matches(Regex("[0-9a-f]{64}"))) return
          if (!PairingProfile.isPrivateIpv4(address) || port !in 1..65535) return
          val platform = runCatching { HostPlatform.parse(attributes["platform"]) }.getOrNull() ?: return
          val path = attributes["path"]?.takeIf { it.startsWith('/') } ?: "/"
          onRelay(DiscoveredRelay(hostId, fingerprint, "wss://$address:$port$path", platform))
        }
      })
    }
  }

  fun start() {
    if (running) return
    runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
  }

  fun stop() {
    if (!running) return
    runCatching { manager.stopServiceDiscovery(listener) }
    running = false
  }

  companion object { private const val SERVICE_TYPE = "_codexdeck._tcp." }
}
