package com.simeo.codexmicromobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
  private class MemoryPersistence : ProfilePersistence {
    var values = emptyList<PairingProfile>()
    var selected: String? = null
    override fun loadAll() = values
    override fun saveAll(profiles: List<PairingProfile>) { values = profiles }
    override fun selectedId() = selected
    override fun setSelectedId(id: String?) { selected = id }
  }

  @Test fun sameHostAndFingerprintUpdatesExistingProfile() {
    val memory = MemoryPersistence()
    val repository = ProfileRepository(memory)
    val original = profile(host = "one")
    repository.upsert(original)
    val update = repository.upsert(original.copy(id = "new-id", endpoint = "wss://192.168.1.3:9000/"))
    assertTrue(update is ProfileUpdate.Saved && !update.created)
    assertEquals(1, repository.profiles().size)
    assertEquals(original.id, repository.profiles().single().id)
    assertEquals("wss://192.168.1.3:9000/", repository.profiles().single().endpoint)
  }

  @Test fun sameHostWithNewCertificateIsRejected() {
    val memory = MemoryPersistence()
    val repository = ProfileRepository(memory)
    repository.upsert(profile(host = "one", fingerprint = "aa".repeat(32)))
    val result = repository.upsert(profile(host = "one", fingerprint = "bb".repeat(32)))
    assertTrue(result is ProfileUpdate.FingerprintChanged)
    assertEquals("aa".repeat(32), repository.profiles().single().fingerprint)
  }

  @Test fun enforcesEightProfileLimitAndSupportsDelete() {
    val repository = ProfileRepository(MemoryPersistence())
    repeat(8) { assertTrue(repository.upsert(profile(host = "host-$it")) is ProfileUpdate.Saved) }
    assertTrue(repository.upsert(profile(host = "host-8")) is ProfileUpdate.LimitReached)
    repository.delete(repository.profiles().first().id)
    assertEquals(7, repository.profiles().size)
  }

  private fun profile(host: String, fingerprint: String = "aa".repeat(32)) = PairingProfile(
    id = "id-$host", name = host, endpoint = "wss://192.168.1.2:9000/", token = "t".repeat(43),
    mode = "nearby", hostId = host, platform = HostPlatform.WINDOWS, fingerprint = fingerprint
  )
}
