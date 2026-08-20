package com.simeo.codexmicromobile

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

enum class HostPlatform(val wireValue: String) {
  WINDOWS("windows"), MACOS("darwin");

  companion object {
    fun parse(value: String?): HostPlatform = when (value?.trim()?.lowercase()) {
      "windows", "win32" -> WINDOWS
      "darwin", "macos", "mac" -> MACOS
      else -> throw IllegalArgumentException("PCのplatformが不正です。")
    }
  }
}

data class PairingProfile(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val endpoint: String,
  val token: String,
  val mode: String,
  val hostId: String,
  val platform: HostPlatform,
  val fingerprint: String?,
  val lastConnectedAt: Long? = null
) {
  companion object {
    const val MAX_QR_BYTES = 4096

    fun parse(raw: String): PairingProfile {
      val value = raw.trim()
      require(value.toByteArray(Charsets.UTF_8).size in 1..MAX_QR_BYTES) { "ペアリングQRが大きすぎます。" }
      val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("ペアリングQRが不正です。") }
      require(uri.scheme == "codexdeck" && uri.host == "pair") { "Codex DeckのペアリングQRではありません。" }
      val query = queryParameters(uri.rawQuery)
      require(query["version"] == "1") { "未対応のペアリング形式です。" }
      val mode = query["mode"] ?: "remote"
      require(mode == "nearby" || mode == "remote") { "接続モードが不正です。" }
      val endpoint = query["endpoint"] ?: error("接続先がありません。")
      val endpointUri = runCatching { URI(endpoint) }.getOrElse { throw IllegalArgumentException("接続先が不正です。") }
      require(endpointUri.scheme == "wss" && endpointUri.host != null) { "接続先はwss://である必要があります。" }
      require(endpointUri.userInfo == null && endpointUri.fragment == null) { "接続先に不要な情報が含まれています。" }
      val token = query["token"] ?: error("認証トークンがありません。")
      require(token.toByteArray(Charsets.UTF_8).size in 32..512) { "認証トークンの長さが不正です。" }
      val hostId = query["hostId"]?.trim().orEmpty()
      require(hostId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "ホストIDが不正です。" }
      val name = query["name"]?.trim().orEmpty().ifEmpty { "Codex" }
      require(name.length <= 80) { "PC名が長すぎます。" }
      val fingerprint = query["fingerprint"]?.normalizeFingerprint()
      if (mode == "nearby") {
        require(fingerprint?.matches(Regex("[0-9a-f]{64}")) == true) { "証明書フィンガープリントが不正です。" }
        require(isPrivateIpv4(endpointUri.host)) { "Nearby接続はRFC 1918のプライベートIPv4に限定されます。" }
      }
      return PairingProfile(
        name = name, endpoint = endpoint, token = token, mode = mode, hostId = hostId,
        platform = HostPlatform.parse(query["platform"]), fingerprint = fingerprint
      )
    }

    fun isPrivateIpv4(host: String): Boolean {
      val octets = host.split('.').mapNotNull(String::toIntOrNull)
      if (octets.size != 4 || octets.any { it !in 0..255 }) return false
      return octets[0] == 10 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168)
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> = rawQuery.orEmpty().split('&')
      .filter { it.isNotEmpty() }
      .associate { part ->
        val pieces = part.split('=', limit = 2)
        URLDecoder.decode(pieces[0], Charsets.UTF_8.name()) to
          URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
      }
  }
}

fun String.normalizeFingerprint(): String = replace(Regex("[\\s:]"), "").lowercase()
