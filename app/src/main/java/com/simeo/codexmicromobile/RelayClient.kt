package com.simeo.codexmicromobile

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import okio.ByteString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class AgentState(
  val slot: Int,
  val threadKey: String?,
  val nativeTitle: String?,
  val projectName: String?,
  val status: String,
  val selected: Boolean,
  val contextPercent: Double?,
  val activityAt: Long?,
  val ownedByHost: Boolean?
)

data class UsageWindowState(
  val id: String,
  val kind: String,
  val usedPercent: Double,
  val remainingPercent: Double,
  val windowDurationMins: Double?,
  val resetsAt: Long?
)

data class UsageState(
  val windows: List<UsageWindowState>,
  val observedAt: Long?,
  val resetCreditsAvailable: Int?,
  val resetCreditsApplicable: Int?
)

data class KeycapCapability(
  val id: String,
  val actionType: String,
  val status: String,
  val danger: Boolean
)

data class DeckSnapshot(
  val hostId: String,
  val hostName: String,
  val platform: HostPlatform,
  val agents: List<AgentState>,
  val actionLabels: Map<String, String>,
  val actionKeycaps: Map<String, String>,
  val agentSource: String,
  val lightingAutoOff: String,
  val theme: String,
  val availableKeycaps: Set<String>,
  val keycapCapabilities: Map<String, KeycapCapability>,
  val activeThreadKey: String?,
  val activeThreadTitle: String?,
  val approvalPending: Boolean,
  val usage: UsageState?,
  val codexVersion: String?,
  val observedAt: Long,
  val receivedAt: Long = System.currentTimeMillis()
) {
  val fiveHourUsed get() = usage?.windows?.firstOrNull { it.kind == "five-hour" }?.usedPercent
  val weeklyUsed get() = usage?.windows?.firstOrNull { it.kind == "weekly" }?.usedPercent
}

interface RelayEvents {
  fun onConnection(state: String, detail: String? = null)
  fun onSnapshot(snapshot: DeckSnapshot)
  fun onCommandResult(label: String, ok: Boolean, error: String?, data: JSONObject? = null)
  fun onPressedStateReset() {}
}

object CommandFactory {
  fun agentTap(slot: Int, threadKey: String) = JSONObject()
    .put("kind", "agent-tap").put("slot", slot).put("threadKey", threadKey)
  fun agent(slot: Int, threadKey: String, act: Int) = JSONObject()
    .put("kind", "agent").put("slot", slot).put("threadKey", threadKey).put("act", act)
  fun action(slot: String, act: Int) = JSONObject().put("kind", "action").put("slot", slot).put("act", act)
  fun joystick(direction: String, distance: Int) = JSONObject()
    .put("kind", "joystick").put("direction", direction).put("distance", distance)
  fun reasoning(direction: String) = JSONObject().put("kind", "reasoning").put("direction", direction)
  fun encoder(act: Int) = JSONObject().put("kind", "encoder").put("act", act)
  fun keycap(keycapId: String) = JSONObject().put("kind", "keycap").put("keycapId", keycapId)
  fun keycapPress(keycapId: String, act: Int) = JSONObject().put("kind", "keycap").put("keycapId", keycapId).put("act", act)
  fun rateLimitReset() = JSONObject().put("kind", "rate-limit-reset")
  fun environmentAction(slot: Int) = JSONObject().put("kind", "environment-action").put("slot", slot)
  fun newTask() = JSONObject().put("kind", "new-task")
  fun hostTarget(hostId: String) = JSONObject().put("kind", "host-target").put("hostId", hostId)

  fun isAllowed(command: JSONObject): Boolean = when (command.optString("kind")) {
    "agent-tap" -> command.optInt("slot") in 0..5 && command.optString("threadKey").isNotBlank()
    "agent" -> command.optInt("slot") in 0..5 && command.optString("threadKey").isNotBlank() && command.optInt("act") in 0..1
    "action" -> command.optString("slot") in RelayClient.ACTION_SLOTS && command.optInt("act") in 0..1
    "joystick" -> command.optString("direction") in setOf("left", "up", "right", "down") && command.optInt("distance") in 0..1
    "encoder" -> command.optInt("act") in 0..1
    "reasoning" -> command.optString("direction") in setOf("increase", "decrease")
    "keycap" -> when (val id = command.optString("keycapId")) {
      "MIC" -> command.has("act") && command.optInt("act") in 0..1
      in OfficialKeycaps.ids -> !command.has("act") && !command.has("confirmationNonce") && !command.has("confirmedHoldMs")
      else -> false
    }
    "rate-limit-reset" -> true
    "environment-action" -> command.optInt("slot") in 1..3
    "new-task" -> true
    "host-target" -> command.optString("hostId").matches(Regex("[A-Za-z0-9._:-]{1,128}"))
    else -> false
  }
}

object RelayProtocol {
  const val VERSION = 2
  const val MAX_MESSAGE_BYTES = 64 * 1024

  fun parseHealth(message: JSONObject): Pair<String, String> {
    val reason = message.optString("reason")
    val detail = message.optString("error")
    return if (reason == "native-signals-unavailable") {
      "bridge_waiting" to "PC Relayは接続済みですが、Codex操作は未接続です。"
    } else {
      "degraded" to detail.ifBlank { "Codexの状態取得が一時的に利用できません。" }
    }
  }

  fun parseSnapshot(message: JSONObject, fallback: PairingProfile): DeckSnapshot? {
    val host = message.optJSONObject("host") ?: return null
    val snapshot = message.optJSONObject("snapshot") ?: return null
    val slots = snapshot.optJSONArray("slots") ?: return null
    if (slots.length() != 6) return null
    val agents = (0 until slots.length()).map { index ->
      val slot = slots.optJSONObject(index) ?: return null
      AgentState(
        slot = slot.optInt("id", index), threadKey = slot.nullableString("threadKey"),
        nativeTitle = slot.nullableString("title"), projectName = slot.nullableString("projectName"),
        status = slot.optString("status", "off"),
        selected = slot.optBoolean("selected"),
        contextPercent = slot.optDouble("contextUsedPercent").takeIf { !it.isNaN() && it in 0.0..100.0 },
        activityAt = slot.optLong("activityAt").takeIf { slot.has("activityAt") && it > 0 },
        ownedByHost = slot.optBoolean("ownedByHost").takeIf { slot.has("ownedByHost") }
      )
    }
    val layoutSlots = snapshot.optJSONObject("layout")?.optJSONObject("slots")
    val actionKeycaps = RelayClient.ACTION_SLOTS.associateWith { key -> layoutSlots?.optJSONObject(key)?.optString("keycapId").orEmpty() }
    val actionLabels = actionKeycaps.mapValues { keycapLabel(it.value) }
    val windows = snapshot.optJSONObject("usage")?.optJSONArray("windows") ?: JSONArray()
    val usageWindows = mutableListOf<UsageWindowState>()
    for (i in 0 until windows.length()) {
      val window = windows.optJSONObject(i) ?: continue
      val used = window.optDouble("usedPercent").takeIf { !it.isNaN() && it in 0.0..100.0 }
      val remaining = window.optDouble("remainingPercent").takeIf { !it.isNaN() && it in 0.0..100.0 } ?: used?.let { 100.0 - it }
      if (used != null && remaining != null) usageWindows += UsageWindowState(
        id = window.optString("id", "window-$i"), kind = window.optString("kind", "other"),
        usedPercent = used, remainingPercent = remaining,
        windowDurationMins = window.optDouble("windowDurationMins").takeIf { !it.isNaN() && it > 0 },
        resetsAt = window.optLong("resetsAt").takeIf { window.has("resetsAt") && it > 0 }
      )
    }
    val usageObject = snapshot.optJSONObject("usage")
    val usage = usageObject?.let {
      UsageState(
        usageWindows, it.optLong("observedAt").takeIf { value -> it.has("observedAt") && value > 0 },
        it.optInt("resetCreditsAvailable").takeIf { value -> it.has("resetCreditsAvailable") && value >= 0 },
        it.optInt("resetCreditsApplicable").takeIf { value -> it.has("resetCreditsApplicable") && value >= 0 }
      )
    }
    val platform = runCatching { HostPlatform.parse(host.optString("platform")) }.getOrDefault(fallback.platform)
    val capabilities = snapshot.optJSONArray("keycapCapabilities")?.let { values ->
      (0 until values.length()).mapNotNull { index ->
        val value = values.optJSONObject(index) ?: return@mapNotNull null
        val id = value.optString("id").takeIf(OfficialKeycaps.ids::contains) ?: return@mapNotNull null
        val actionType = value.optString("actionType").takeIf { it in setOf("command", "external-url", "composer-text", "push-to-talk") } ?: return@mapNotNull null
        val status = value.optString("status").takeIf { it in setOf("ready", "unsupported") } ?: return@mapNotNull null
        id to KeycapCapability(id, actionType, status, value.optBoolean("danger"))
      }.toMap()
    } ?: emptyMap()
    return DeckSnapshot(
      hostId = host.optString("hostId", fallback.hostId), hostName = host.optString("hostName", fallback.name),
      platform = platform, agents = agents, actionLabels = actionLabels, actionKeycaps = actionKeycaps,
      agentSource = snapshot.optString("agentSource", "unknown"),
      lightingAutoOff = snapshot.optString("lightingAutoOff", "unknown"), theme = snapshot.optString("theme", "dark"),
      availableKeycaps = snapshot.optJSONArray("availableKeycaps")?.let { values ->
        (0 until values.length()).mapNotNull { values.optString(it).takeIf(OfficialKeycaps.ids::contains) }.toSet()
      } ?: emptySet(),
      keycapCapabilities = capabilities,
      activeThreadKey = snapshot.nullableString("activeThreadKey"), activeThreadTitle = snapshot.nullableString("activeThreadTitle"),
      approvalPending = snapshot.optBoolean("approvalPending"),
      usage = usage, codexVersion = host.nullableString("codexVersion"),
      observedAt = message.optLong("observedAt").takeIf { it > 0 } ?: System.currentTimeMillis()
    )
  }

  private fun keycapLabel(id: String?): String = when (id?.lowercase()) {
    "fast", "fast-mode" -> "FAST"
    "approve" -> "承認"
    "decline", "reject" -> "拒否"
    "fork", "new-thread" -> "分岐"
    "dictation", "voice" -> "音声"
    "send", "codex" -> "送信"
    else -> id?.replace('-', ' ')?.take(14)?.uppercase() ?: "ACTION"
  }
}

class RelayClient(private val profile: PairingProfile, private val events: RelayEvents) {
  private data class Pending(val label: String, val timeout: Runnable)

  private val main = Handler(Looper.getMainLooper())
  private val pending = ConcurrentHashMap<String, Pending>()
  private var socket: WebSocket? = null
  private var stopped = false
  private var retrySeconds = 1L
  private var reconnectScheduled = false
  private var authTimeout: Runnable? = null
  private var staleTimeout: Runnable? = null
  private var connectionAttempt = 0
  private val client = buildClient(profile)

  fun start() { stopped = false; connect() }

  fun stop() {
    stopped = true
    main.removeCallbacksAndMessages(null)
    clearPending(null)
    socket?.close(1000, "app stopped")
    socket = null
    client.dispatcher.executorService.shutdown()
    dispatch { events.onPressedStateReset() }
  }

  fun send(command: JSONObject, label: String) {
    if (!CommandFactory.isAllowed(command)) {
      dispatch { events.onCommandResult(label, false, "許可されていないコマンドです。", null) }
      return
    }
    val requestId = UUID.randomUUID().toString()
    val timeout = Runnable {
      pending.remove(requestId)?.let { events.onCommandResult(it.label, false, "10秒以内に応答がありませんでした。", null) }
    }
    pending[requestId] = Pending(label, timeout)
    val envelope = JSONObject().put("type", "command").put("protocol", RelayProtocol.VERSION)
      .put("requestId", requestId).put("command", command)
    if (socket?.send(envelope.toString()) != true) {
      pending.remove(requestId)
      dispatch { events.onCommandResult(label, false, "PCに接続されていません。", null) }
    } else {
      main.postDelayed(timeout, COMMAND_TIMEOUT_MS)
    }
  }

  private fun connect() {
    if (stopped) return
    reconnectScheduled = false
    if (connectionAttempt++ == 0) dispatch { events.onConnection("connecting", profile.name) }
    socket = client.newWebSocket(Request.Builder().url(profile.endpoint).build(), object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        retrySeconds = 1
        webSocket.send(JSONObject().put("type", "auth").put("protocol", RelayProtocol.VERSION).put("token", profile.token).toString())
        authTimeout = Runnable {
          if (socket === webSocket) {
            events.onConnection("offline", "認証が3秒以内に完了しませんでした。")
            webSocket.cancel()
          }
        }.also { main.postDelayed(it, AUTH_TIMEOUT_MS) }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (text.toByteArray(Charsets.UTF_8).size > RelayProtocol.MAX_MESSAGE_BYTES) {
          dispatch { events.onConnection("degraded", "64 KiBを超えるメッセージを拒否しました。") }
          webSocket.close(1009, "message too large")
          return
        }
        runCatching { handle(JSONObject(text)) }
          .onFailure { dispatch { events.onConnection("degraded", "受信データを解釈できません。") } }
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        dispatch { events.onConnection("degraded", "バイナリメッセージは未対応です。") }
        webSocket.close(1003, "binary unsupported")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnect(reason)
      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val pinMismatch = generateSequence(t as Throwable?) { it.cause }.any { it.message?.contains("fingerprint mismatch") == true }
        if (pinMismatch) {
          stopped = true
          socket = null
          cancelConnectionTimers()
          clearPending("証明書が一致しないため中止しました。")
          dispatch { events.onPressedStateReset(); events.onConnection("certificate_mismatch", "証明書が登録時と一致しません。再ペアリングしてください。") }
        } else reconnect(t.localizedMessage)
      }
    })
  }

  private fun reconnect(detail: String?) {
    socket = null
    cancelConnectionTimers()
    clearPending("接続が切断されました。")
    dispatch { events.onPressedStateReset() }
    if (stopped || reconnectScheduled) return
    reconnectScheduled = true
    dispatch { events.onConnection("offline", detail) }
    val delay = retrySeconds
    retrySeconds = (retrySeconds * 2).coerceAtMost(30)
    main.postDelayed({ connect() }, delay * 1_000)
  }

  private fun handle(message: JSONObject) {
    if (message.optInt("protocol", -1) != RelayProtocol.VERSION) {
      stopped = true
      cancelConnectionTimers()
      clearPending("Relay protocolに互換性がありません。")
      socket?.close(1002, "protocol mismatch")
      dispatch { events.onPressedStateReset(); events.onConnection("protocol_mismatch", "Relay protocol ${message.optInt("protocol", -1)} は未対応です。") }
      return
    }
    when (message.optString("type")) {
      "ready" -> {
        connectionAttempt = 0
        authTimeout?.let(main::removeCallbacks); authTimeout = null
        dispatch { events.onConnection("ready", message.optJSONObject("host")?.optString("hostName")) }
      }
      "health" -> RelayProtocol.parseHealth(message).let { (state, detail) ->
        dispatch { events.onConnection(state, detail) }
      }
      "snapshot" -> RelayProtocol.parseSnapshot(message, profile)?.let { snapshot ->
        scheduleStale(); dispatch { events.onSnapshot(snapshot) }
      } ?: dispatch { events.onConnection("degraded", "snapshotの形式が不正です。") }
      "result" -> {
        val item = pending.remove(message.optString("requestId")) ?: return
        main.removeCallbacks(item.timeout)
        val ok = message.optBoolean("ok")
        dispatch { events.onCommandResult(item.label, ok, message.optString("error").takeIf { it.isNotBlank() }, message.optJSONObject("data")) }
      }
      "error" -> dispatch { events.onConnection("degraded", message.optString("error", "Relayエラー")) }
    }
  }

  private fun scheduleStale() {
    staleTimeout?.let(main::removeCallbacks)
    staleTimeout = Runnable { events.onConnection("stale", "snapshotが30秒以上更新されていません。") }
      .also { main.postDelayed(it, STALE_TIMEOUT_MS) }
  }

  private fun cancelConnectionTimers() {
    authTimeout?.let(main::removeCallbacks); authTimeout = null
    staleTimeout?.let(main::removeCallbacks); staleTimeout = null
  }

  private fun clearPending(error: String?) {
    pending.values.forEach {
      main.removeCallbacks(it.timeout)
      if (error != null) dispatch { events.onCommandResult(it.label, false, error, null) }
    }
    pending.clear()
  }

  private fun dispatch(block: () -> Unit) = main.post(block)

  companion object {
    val ACTION_SLOTS = listOf("ACT06", "ACT07", "ACT08", "ACT09", "ACT10_ACT11", "ACT12")
    private const val COMMAND_TIMEOUT_MS = 10_000L
    private const val AUTH_TIMEOUT_MS = 3_000L
    private const val STALE_TIMEOUT_MS = 30_000L

    @SuppressLint("CustomX509TrustManager")
    private fun buildClient(profile: PairingProfile): OkHttpClient {
      val builder = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
      val expected = profile.fingerprint ?: return builder.build()
      val trust = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) =
          throw java.security.cert.CertificateException("Client certificates are not accepted")
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
          val certificate = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Missing server certificate")
          if (sha256(certificate) != expected) throw java.security.cert.CertificateException("Codex Deck certificate fingerprint mismatch")
        }
      }
      val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), SecureRandom()) }
      return builder.sslSocketFactory(context.socketFactory, trust)
        .hostnameVerifier { _, session -> (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::sha256) == expected }
        .build()
    }

    private fun sha256(certificate: X509Certificate): String =
      MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
  }
}

private fun JSONObject.nullableString(key: String): String? =
  if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
