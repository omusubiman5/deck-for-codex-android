package com.simeo.codexmicromobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ProfilePersistence {
  fun loadAll(): List<PairingProfile>
  fun saveAll(profiles: List<PairingProfile>)
  fun selectedId(): String?
  fun setSelectedId(id: String?)
}

sealed class ProfileUpdate {
  data class Saved(val profile: PairingProfile, val created: Boolean) : ProfileUpdate()
  data class FingerprintChanged(val existing: PairingProfile, val scanned: PairingProfile) : ProfileUpdate()
  data object LimitReached : ProfileUpdate()
}

class ProfileRepository(private val persistence: ProfilePersistence) {
  companion object { const val MAX_PROFILES = 8 }

  fun profiles(): List<PairingProfile> = persistence.loadAll().sortedByDescending { it.lastConnectedAt ?: 0L }
  fun selected(): PairingProfile? {
    val profiles = profiles()
    return profiles.firstOrNull { it.id == persistence.selectedId() } ?: profiles.firstOrNull()
  }

  fun upsert(scanned: PairingProfile): ProfileUpdate {
    val current = persistence.loadAll().toMutableList()
    val sameHost = current.firstOrNull { it.hostId == scanned.hostId }
    if (sameHost != null && sameHost.fingerprint != scanned.fingerprint) {
      return ProfileUpdate.FingerprintChanged(sameHost, scanned)
    }
    if (sameHost == null && current.size >= MAX_PROFILES) return ProfileUpdate.LimitReached
    val saved = if (sameHost == null) scanned else scanned.copy(id = sameHost.id, lastConnectedAt = sameHost.lastConnectedAt)
    if (sameHost == null) current += saved else current[current.indexOf(sameHost)] = saved
    persistence.saveAll(current)
    persistence.setSelectedId(saved.id)
    return ProfileUpdate.Saved(saved, sameHost == null)
  }

  fun select(id: String): PairingProfile? = profiles().firstOrNull { it.id == id }?.also { persistence.setSelectedId(id) }

  fun markConnected(id: String, time: Long = System.currentTimeMillis()) {
    persistence.saveAll(persistence.loadAll().map { if (it.id == id) it.copy(lastConnectedAt = time) else it })
  }

  fun delete(id: String) {
    val remaining = persistence.loadAll().filterNot { it.id == id }
    persistence.saveAll(remaining)
    if (persistence.selectedId() == id) persistence.setSelectedId(remaining.firstOrNull()?.id)
  }
}

class SecureProfileStore(context: Context) : ProfilePersistence {
  private val prefs = context.getSharedPreferences("codex_deck_secure", Context.MODE_PRIVATE)
  private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  init { migrateLegacyProfile() }

  override fun loadAll(): List<PairingProfile> = runCatching {
    val ids = JSONArray(prefs.getString(KEY_INDEX, "[]"))
    (0 until ids.length()).mapNotNull { index -> decrypt(ids.optString(index)) }
  }.getOrDefault(emptyList())

  override fun saveAll(profiles: List<PairingProfile>) {
    require(profiles.size <= ProfileRepository.MAX_PROFILES)
    val oldIds = loadIds().toSet()
    val newIds = profiles.map { it.id }.toSet()
    val editor = prefs.edit()
    profiles.forEach { profile -> editor.putString(profileKey(profile.id), encrypt(profile)) }
    (oldIds - newIds).forEach { id ->
      editor.remove(profileKey(id))
      runCatching { keyStore.deleteEntry(alias(id)) }
    }
    editor.putString(KEY_INDEX, JSONArray(profiles.map { it.id }).toString()).apply()
  }

  override fun selectedId(): String? = prefs.getString(KEY_SELECTED, null)
  override fun setSelectedId(id: String?) { prefs.edit().putString(KEY_SELECTED, id).apply() }

  private fun encrypt(profile: PairingProfile): String {
    val json = profile.toJson().toString().toByteArray(Charsets.UTF_8)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key(alias(profile.id)))
    return JSONObject().put("version", 2)
      .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
      .put("payload", Base64.encodeToString(cipher.doFinal(json), Base64.NO_WRAP)).toString()
  }

  private fun decrypt(id: String): PairingProfile? = runCatching {
    val blob = JSONObject(prefs.getString(profileKey(id), null) ?: return null)
    require(blob.getInt("version") == 2)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key(alias(id)), GCMParameterSpec(128, Base64.decode(blob.getString("iv"), Base64.NO_WRAP)))
    JSONObject(String(cipher.doFinal(Base64.decode(blob.getString("payload"), Base64.NO_WRAP)), Charsets.UTF_8)).toProfile()
  }.getOrNull()

  private fun migrateLegacyProfile() {
    if (prefs.contains(KEY_INDEX) || !prefs.contains("payload")) return
    val legacy = runCatching {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, keyStore.getKey(LEGACY_ALIAS, null) as SecretKey,
        GCMParameterSpec(128, Base64.decode(prefs.getString("iv", null), Base64.NO_WRAP)))
      val json = JSONObject(String(cipher.doFinal(Base64.decode(prefs.getString("payload", null), Base64.NO_WRAP)), Charsets.UTF_8))
      PairingProfile(
        name = json.getString("name"), endpoint = json.getString("endpoint"), token = json.getString("token"),
        mode = json.getString("mode"), hostId = json.getString("hostId"),
        platform = runCatching { HostPlatform.parse(json.optString("platform")) }.getOrDefault(HostPlatform.WINDOWS),
        fingerprint = json.optString("fingerprint").takeIf { it.isNotBlank() && it != "null" }
      )
    }.getOrNull()
    if (legacy != null) {
      saveAll(listOf(legacy))
      setSelectedId(legacy.id)
    }
    prefs.edit().remove("payload").remove("iv").apply()
  }

  private fun loadIds(): List<String> = runCatching {
    val value = JSONArray(prefs.getString(KEY_INDEX, "[]"))
    (0 until value.length()).map { value.getString(it) }
  }.getOrDefault(emptyList())

  private fun key(alias: String): SecretKey {
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
      init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
      generateKey()
    }
  }

  private fun PairingProfile.toJson() = JSONObject()
    .put("id", id).put("name", name).put("endpoint", endpoint).put("token", token)
    .put("mode", mode).put("hostId", hostId).put("platform", platform.wireValue)
    .put("fingerprint", fingerprint).put("lastConnectedAt", lastConnectedAt)

  private fun JSONObject.toProfile() = PairingProfile(
    id = getString("id"), name = getString("name"), endpoint = getString("endpoint"), token = getString("token"),
    mode = getString("mode"), hostId = getString("hostId"), platform = HostPlatform.parse(getString("platform")),
    fingerprint = optString("fingerprint").takeIf { it.isNotBlank() && it != "null" },
    lastConnectedAt = optLong("lastConnectedAt").takeIf { has("lastConnectedAt") && !isNull("lastConnectedAt") }
  )

  private fun alias(id: String) = "codex_deck_profile_key_v2_$id"
  private fun profileKey(id: String) = "profile_v2_$id"

  companion object {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val LEGACY_ALIAS = "codex_deck_profile_key"
    private const val KEY_INDEX = "profile_index_v2"
    private const val KEY_SELECTED = "selected_profile_v2"
  }
}
