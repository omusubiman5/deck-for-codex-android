package com.simeo.codexmicromobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class PairingProfileTest {
  @Test fun parsesNearbyWindowsProfile() {
    val profile = PairingProfile.parse(pairingUrl())
    assertEquals("host-1", profile.hostId)
    assertEquals(HostPlatform.WINDOWS, profile.platform)
    assertEquals("aa".repeat(32), profile.fingerprint)
    assertEquals("wss://192.168.1.20:9443/", profile.endpoint)
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsPublicAddressForNearby() {
    PairingProfile.parse(pairingUrl(endpoint = "wss://8.8.8.8:9443/"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsChangedProtocol() {
    PairingProfile.parse(pairingUrl().replace("version=1", "version=2"))
  }

  @Test fun recognizesOnlyRfc1918Addresses() {
    assertTrue(PairingProfile.isPrivateIpv4("10.1.2.3"))
    assertTrue(PairingProfile.isPrivateIpv4("172.31.255.1"))
    assertTrue(PairingProfile.isPrivateIpv4("192.168.0.1"))
    assertFalse(PairingProfile.isPrivateIpv4("172.32.0.1"))
    assertFalse(PairingProfile.isPrivateIpv4("127.0.0.1"))
  }

  @Test fun normalizesFingerprint() {
    assertEquals("aabbcc", "AA:BB CC".normalizeFingerprint())
  }

  private fun pairingUrl(endpoint: String = "wss://192.168.1.20:9443/"): String {
    fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    return "codexdeck://pair?version=1&mode=nearby&endpoint=${encode(endpoint)}&token=${"t".repeat(43)}" +
      "&hostId=host-1&platform=windows&name=Office&fingerprint=${"aa".repeat(32)}"
  }
}
