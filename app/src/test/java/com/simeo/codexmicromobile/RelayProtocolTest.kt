package com.simeo.codexmicromobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProtocolTest {
  @Test fun nativeBridgeHealthDistinguishesPcConnectionFromOffline() {
    val (state, detail) = RelayProtocol.parseHealth(JSONObject()
      .put("type", "health").put("reason", "native-signals-unavailable"))
    assertEquals("bridge_waiting", state)
    assertEquals("PC Relayは接続済みですが、Codex操作は未接続です。", detail)
  }

  private val fallback = PairingProfile(
    name = "PC", endpoint = "wss://192.168.1.2:9000/", token = "t".repeat(43), mode = "nearby",
    hostId = "host", platform = HostPlatform.WINDOWS, fingerprint = "aa".repeat(32)
  )

  @Test fun parsesSixAgentDarwinSnapshotAndUsageBounds() {
    val slots = JSONArray()
    repeat(6) { index ->
      slots.put(JSONObject().put("id", index).put("threadKey", "thread-$index").put("title", "Agent $index")
        .put("status", "working").put("selected", index == 0).put("contextUsedPercent", 40 + index))
    }
    val windows = JSONArray().put(JSONObject().put("kind", "five-hour").put("usedPercent", 25.0))
      .put(JSONObject().put("kind", "weekly").put("usedPercent", 101.0))
    val message = JSONObject().put("protocol", 2).put("host", JSONObject().put("hostName", "Mac").put("platform", "darwin"))
      .put("snapshot", JSONObject().put("slots", slots).put("usage", JSONObject().put("windows", windows)))
    val parsed = RelayProtocol.parseSnapshot(message, fallback)!!
    assertEquals(HostPlatform.MACOS, parsed.platform)
    assertEquals(6, parsed.agents.size)
    assertEquals(25.0, parsed.fiveHourUsed!!, 0.0)
    assertNull(parsed.weeklyUsed)
  }

  @Test fun rejectsSnapshotWithoutExactlySixAgents() {
    val message = JSONObject().put("host", JSONObject()).put("snapshot", JSONObject().put("slots", JSONArray()))
    assertNull(RelayProtocol.parseSnapshot(message, fallback))
  }

  @Test fun commandAllowlistRejectsUnknownAndAcceptsTypedCommands() {
    assertTrue(CommandFactory.isAllowed(CommandFactory.action("ACT06", 1)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.agentTap(5, "thread")))
    assertTrue(CommandFactory.isAllowed(CommandFactory.agent(5, "thread", 0)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.joystick("down", 1)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.encoder(1)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycap("APPS")))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycap("APPR")))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycap("REJ")))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycap("DEL")))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycapPress("MIC", 1)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.keycapPress("MIC", 0)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.rateLimitReset()))
    assertTrue(CommandFactory.isAllowed(CommandFactory.environmentAction(3)))
    assertTrue(CommandFactory.isAllowed(CommandFactory.newTask()))
    assertTrue(CommandFactory.isAllowed(CommandFactory.hostTarget("host-1")))
    assertFalse(CommandFactory.isAllowed(JSONObject().put("kind", "shell").put("command", "whoami")))
    assertFalse(CommandFactory.isAllowed(CommandFactory.action("ACT99", 1)))
    assertFalse(CommandFactory.isAllowed(CommandFactory.environmentAction(4)))
    assertFalse(CommandFactory.isAllowed(CommandFactory.agentTap(6, "thread")))
    assertFalse(CommandFactory.isAllowed(CommandFactory.keycap("MIC")))
    assertFalse(CommandFactory.isAllowed(CommandFactory.keycapPress("FAST", 1)))
    assertFalse(CommandFactory.isAllowed(JSONObject().put("kind", "danger-arm").put("keycapId", "DEL").put("threadKey", "thread")))
    assertFalse(CommandFactory.isAllowed(CommandFactory.keycap("DEL").put("confirmationNonce", "12345678-1234-1234-1234-123456789012")))
    assertEquals(2, RelayProtocol.VERSION)
  }

  @Test fun officialPaletteContainsExactlyThirtyUniqueKeys() {
    assertEquals(30, OfficialKeycaps.all.size)
    assertEquals(30, OfficialKeycaps.ids.size)
    assertEquals("FAST", OfficialKeycaps.all.first().id)
    assertEquals("APPS", OfficialKeycaps.all.last().id)
    assertTrue(setOf("APPR", "REJ", "DEL").all(OfficialKeycaps.ids::contains))
  }

  @Test fun parsesThirtyCapabilitiesWithoutUsingSixActionSlotsAsAvailability() {
    val slots = JSONArray()
    repeat(6) { index -> slots.put(JSONObject().put("id", index).put("threadKey", "thread-$index").put("status", "idle")) }
    val capabilities = JSONArray()
    OfficialKeycaps.all.forEach { keycap -> capabilities.put(JSONObject()
      .put("id", keycap.id)
      .put("actionType", if (keycap.id == "MIC") "push-to-talk" else "command")
      .put("status", "ready").put("danger", false)) }
    val message = JSONObject().put("host", JSONObject().put("platform", "win32"))
      .put("snapshot", JSONObject().put("slots", slots)
        .put("layout", JSONObject().put("slots", JSONObject().put("ACT01", JSONObject().put("keycapId", "FAST"))))
        .put("availableKeycaps", JSONArray(OfficialKeycaps.ids.toList()))
        .put("keycapCapabilities", capabilities).put("activeThreadKey", "thread-0").put("approvalPending", true))
    val parsed = RelayProtocol.parseSnapshot(message, fallback)!!
    assertEquals(30, parsed.availableKeycaps.size)
    assertEquals(30, parsed.keycapCapabilities.size)
    assertEquals(0, parsed.keycapCapabilities.values.count { it.danger })
    assertEquals("thread-0", parsed.activeThreadKey)
    assertTrue(parsed.approvalPending)
  }

  @Test fun parsesCompleteUsageAndHostMetadata() {
    val slots = JSONArray()
    repeat(6) { index ->
      slots.put(JSONObject().put("id", index).put("threadKey", "thread-$index")
        .put("title", "Task $index").put("projectName", "Project $index")
        .put("status", if (index == 0) "approval" else "idle").put("selected", index == 0)
        .put("activityAt", 1_700_000_000_000L).put("ownedByHost", true))
    }
    val usage = JSONObject().put("observedAt", 1_700_000_000_000L)
      .put("resetCreditsAvailable", 2).put("resetCreditsApplicable", 1)
      .put("windows", JSONArray().put(JSONObject().put("id", "other-1").put("kind", "other")
        .put("usedPercent", 40).put("remainingPercent", 60).put("windowDurationMins", 120)
        .put("resetsAt", 1_800_000_000_000L)))
    val layout = JSONObject().put("slots", JSONObject().put("ACT06", JSONObject().put("keycapId", "FAST")))
    val message = JSONObject().put("observedAt", 1_700_000_000_000L)
      .put("host", JSONObject().put("hostId", "host").put("hostName", "PC").put("platform", "win32").put("codexVersion", "1.2.3"))
      .put("snapshot", JSONObject().put("slots", slots).put("layout", layout).put("usage", usage)
        .put("agentSource", "pinned").put("lightingAutoOff", "3-minutes").put("theme", "dark"))
    val parsed = RelayProtocol.parseSnapshot(message, fallback)!!
    assertEquals("Project 0", parsed.agents.first().projectName)
    assertEquals("Task 0", parsed.agents.first().nativeTitle)
    assertEquals("FAST", parsed.actionKeycaps["ACT06"])
    assertEquals("pinned", parsed.agentSource)
    assertEquals(2, parsed.usage?.resetCreditsAvailable)
    assertEquals("other", parsed.usage?.windows?.single()?.kind)
  }
}
