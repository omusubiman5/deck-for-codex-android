import assert from "node:assert/strict";
import { X509Certificate } from "node:crypto";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { generate } from "selfsigned";
import {
  isRetryableSnapshotInitializationError, normalizedKeycapError, selectCodexMainTarget, selectCodexMainTargets
} from "../src/codex-micro-renderer-bridge.js";
import { codexDeckStateRoot } from "../src/codex-deck-paths.js";
import { getOrCreateHostIdentity } from "../src/host-identity.js";
import { isPrivateLanHost } from "../src/relay-network.js";
import { CodexRelayServer, relayDiscoveryTxt, validateRelayServerConfig } from "../src/codex-relay-server.js";
import { parseRelayCommand, RELAY_PROTOCOL_VERSION } from "../src/relay-protocol.js";

test("state root is isolated from Codex Deck on Windows and macOS", () => {
  assert.equal(codexDeckStateRoot("win32", "C:\\Users\\Test", "D:\\Local"), "D:\\Local\\CodexMicroRelay");
  assert.equal(codexDeckStateRoot("darwin", "/Users/test"), "/Users/test/Library/Application Support/CodexMicroRelay");
});

test("host identity survives restart", async () => {
  const root = await mkdtemp(join(tmpdir(), "codex-micro-relay-"));
  const path = join(root, "host.json");
  const first = await getOrCreateHostIdentity(path);
  const second = await getOrCreateHostIdentity(path);
  assert.equal(first.hostId, second.hostId);
});

test("keycap failures always identify the requested key", () => {
  assert.equal(normalizedKeycapError("DIFF", new Error("This Codex command is not active in the current view.")).message,
    "DIFF: The current Codex view cannot execute this keycap.");
  assert.equal(normalizedKeycapError("OAI", new Error("Error: Codex VS Code event module is unavailable for this keycap.")).message,
    "OAI: Codex VS Code event module is unavailable for this keycap.");
});

test("only private LAN addresses pass Nearby validation", () => {
  assert.equal(isPrivateLanHost("10.0.0.2"), true);
  assert.equal(isPrivateLanHost("172.31.1.2"), true);
  assert.equal(isPrivateLanHost("192.168.1.2"), true);
  assert.equal(isPrivateLanHost("0.0.0.0"), false);
  assert.equal(isPrivateLanHost("8.8.8.8"), false);
});

test("Nearby config requires pinned TLS and discovery never leaks the token", async () => {
  const certificate = await generate([{ name: "commonName", value: "Codex Micro Relay test" }], {
    keyType: "ec", curve: "P-256", algorithm: "sha256"
  });
  const fingerprint = new X509Certificate(certificate.cert).fingerprint256.replaceAll(":", "").toLowerCase();
  const config = {
    enabled: true, listenHost: "auto", port: 47_653, token: "secret".repeat(8), transport: "local" as const,
    tls: { certificate: certificate.cert, privateKey: certificate.private, fingerprintSha256: fingerprint },
    discovery: { enabled: true }
  };
  validateRelayServerConfig(config);
  const txt = relayDiscoveryTxt(config, {
    hostId: "56fd97ad-7073-42cc-85ce-befa17546d7c", hostName: "Test", platform: "win32"
  }, "192.168.1.25");
  assert.equal(txt.fingerprint, fingerprint);
  assert.equal(JSON.stringify(txt).includes(config.token), false);
  assert.throws(() => validateRelayServerConfig({ ...config, listenHost: "0.0.0.0" }), /private-LAN/);
  assert.throws(() => validateRelayServerConfig({ ...config, listenHost: "8.8.8.8" }), /private-LAN/);
  assert.throws(() => validateRelayServerConfig({ ...config, tls: undefined }), /pinned TLS/);
});

test("protocol accepts typed commands and rejects arbitrary input", () => {
  assert.equal(RELAY_PROTOCOL_VERSION, 2);
  assert.deepEqual(parseRelayCommand({ kind: "action", slot: "ACT06", act: 1 }), { kind: "action", slot: "ACT06", act: 1 });
  assert.deepEqual(parseRelayCommand({ kind: "joystick", direction: "down", distance: 1 }), { kind: "joystick", direction: "down", distance: 1 });
  assert.deepEqual(parseRelayCommand({ kind: "encoder", act: 1 }), { kind: "encoder", act: 1 });
  assert.deepEqual(parseRelayCommand({ kind: "keycap", keycapId: "APPS" }), { kind: "keycap", keycapId: "APPS" });
  assert.deepEqual(parseRelayCommand({ kind: "keycap", keycapId: "APPR" }), { kind: "keycap", keycapId: "APPR" });
  assert.deepEqual(parseRelayCommand({ kind: "keycap", keycapId: "REJ" }), { kind: "keycap", keycapId: "REJ" });
  assert.deepEqual(parseRelayCommand({ kind: "keycap", keycapId: "DEL" }), { kind: "keycap", keycapId: "DEL" });
  assert.deepEqual(parseRelayCommand({ kind: "keycap", keycapId: "MIC", act: 1 }), { kind: "keycap", keycapId: "MIC", act: 1 });
  assert.equal(parseRelayCommand({ kind: "keycap", keycapId: "MIC" }), null);
  assert.equal(parseRelayCommand({ kind: "keycap", keycapId: "FAST", act: 1 }), null);
  assert.deepEqual(parseRelayCommand({ kind: "new-task" }), { kind: "new-task" });
  assert.deepEqual(parseRelayCommand({ kind: "environment-action", slot: 3 }), { kind: "environment-action", slot: 3 });
  assert.deepEqual(parseRelayCommand({ kind: "host-target", hostId: "host-1" }), { kind: "host-target", hostId: "host-1" });
  assert.equal(parseRelayCommand({ kind: "shell", command: "whoami" }), null);
  assert.equal(parseRelayCommand({ kind: "environment-action", slot: 4 }), null);
  assert.equal(parseRelayCommand({ kind: "agent", slot: 6, act: 1, threadKey: "x" }), null);
  const threadKey = "local:12345678-1234-1234-1234-123456789abc";
  assert.equal(parseRelayCommand({ kind: "danger-arm", keycapId: "DEL", threadKey }), null);
  assert.equal(parseRelayCommand({ kind: "danger-arm", keycapId: "FAST", threadKey }), null);
  assert.equal(parseRelayCommand({
    kind: "keycap", keycapId: "DEL", confirmationNonce: "12345678-1234-1234-1234-123456789abc", confirmedHoldMs: 1200
  }), null);
  assert.deepEqual(parseRelayCommand({ kind: "agent-tap", slot: 5, threadKey }), { kind: "agent-tap", slot: 5, threadKey });
  assert.equal(parseRelayCommand({ kind: "agent-tap", slot: 6, threadKey }), null);
});

test("APPR, REJ, DEL and their dynamic Action slots use the standard execution path", async () => {
  const certificate = await generate([{ name: "commonName", value: "Codex Micro standard keycap integration" }], {
    keyType: "ec", curve: "P-256", algorithm: "sha256"
  });
  const fingerprint = new X509Certificate(certificate.cert).fingerprint256.replaceAll(":", "").toLowerCase();
  const threadKey = "local:12345678-1234-1234-1234-123456789abc";
  const keycapCalls: string[] = [];
  const actionCalls: string[] = [];
  const snapshot = {
    activeThreadKey: threadKey, approvalPending: true, slots: [], agentSource: "recent", lightingAutoOff: "never", theme: "dark",
    layout: { version: 1, slots: {}, analogStick: {} }
  };
  const control = {
    refresh: async () => snapshot,
    tapAgent: async () => {}, sendAgent: async () => {}, sendAction: async (slot: string, act: number) => { actionCalls.push(`${slot}:${act}`); }, sendJoystick: async () => {},
    sendEncoder: async () => {}, adjustReasoning: async () => {}, runEnvironmentAction: async () => {}, consumeRateLimitReset: async () => {},
    runKeycap: async (id: string) => { keycapCalls.push(id); }
  };
  const server = new CodexRelayServer({
    enabled: true, listenHost: "auto", port: 47_654, token: "secret".repeat(8), transport: "local",
    tls: { certificate: certificate.cert, privateKey: certificate.private, fingerprintSha256: fingerprint }, discovery: { enabled: true }
  }, { hostId: "56fd97ad-7073-42cc-85ce-befa17546d7c", hostName: "Test", platform: "win32" }, control as never, () => {});
  const sent: Array<Record<string, unknown>> = [];
  const socket = { readyState: 1, send: (value: string) => sent.push(JSON.parse(value)) };
  const handle = async (requestId: string, command: Record<string, unknown>) => {
    await (server as unknown as { handleMessage(socket: unknown, raw: string): Promise<void> }).handleMessage(socket,
      JSON.stringify({ type: "command", protocol: 2, requestId, command }));
    return sent.findLast((item) => item.requestId === requestId)!;
  };
  for (const id of ["APPR", "REJ", "DEL"] as const) {
    const executed = await handle(`execute-${id}`, { kind: "keycap", keycapId: id });
    assert.equal(executed.ok, true);
  }
  for (const slot of ["ACT07", "ACT08", "ACT12"] as const) {
    assert.equal((await handle(`action-${slot}-down`, { kind: "action", slot, act: 1 })).ok, true);
    assert.equal((await handle(`action-${slot}-up`, { kind: "action", slot, act: 0 })).ok, true);
  }
  assert.deepEqual(keycapCalls, ["APPR", "REJ", "DEL"]);
  assert.deepEqual(actionCalls, ["ACT07:1", "ACT07:0", "ACT08:1", "ACT08:0", "ACT12:1", "ACT12:0"]);
});

test("renderer target selection ignores devtools pages", () => {
  const target = selectCodexMainTarget([
    { id: "dev", type: "page", title: "DevTools", url: "devtools://devtools", webSocketDebuggerUrl: "ws://dev" },
    { id: "main", type: "page", title: "Codex", url: "app://codex/index.html", webSocketDebuggerUrl: "ws://main" }
  ]);
  assert.equal(target?.id, "main");
});

test("renderer target selection retains every main window for readiness fallback", () => {
  const targets = selectCodexMainTargets([
    { id: "overlay", type: "page", url: "app://-/index.html?initialRoute=%2Favatar-overlay", webSocketDebuggerUrl: "ws://overlay" },
    { id: "first", type: "page", url: "app://-/index.html", webSocketDebuggerUrl: "ws://first" },
    { id: "second", type: "page", url: "app://-/index.html", webSocketDebuggerUrl: "ws://second" }
  ]);
  assert.deepEqual(targets.map((target) => target.id), ["first", "second", "overlay"]);
});

test("only renderer initialization failures trigger snapshot target retry", () => {
  assert.equal(isRetryableSnapshotInitializationError(new Error("Error: Codex Micro slot store was not found.")), true);
  assert.equal(isRetryableSnapshotInitializationError(new Error("Codex React root was not found.")), true);
  assert.equal(isRetryableSnapshotInitializationError(new Error("TLS certificate fingerprint mismatch.")), false);
  assert.equal(isRetryableSnapshotInitializationError(new Error("Invalid relay command.")), false);
});

test("macOS launcher and LaunchAgent retain the loopback and product boundaries", async () => {
  const launcher = await readFile(new URL("../launcher/start-codex-micro-relay.sh", import.meta.url), "utf8");
  const plist = await readFile(new URL("../installer/macos/com.simeo.codex-micro-relay.plist", import.meta.url), "utf8");
  assert.match(launcher, /--remote-debugging-address=127\.0\.0\.1/);
  assert.match(launcher, /Application Support\/CodexMicroRelay/);
  assert.match(plist, /com\.simeo\.codex-micro-relay/);
  assert.doesNotMatch(`${launcher}\n${plist}`, /Stream Deck|VSD Craft|M18/i);
});

test("Windows watcher never terminates Codex and enforces one watcher", async () => {
  const start = await readFile(new URL("../launcher/Start-CodexMicroRelay.ps1", import.meta.url), "utf8");
  const watcher = await readFile(new URL("../launcher/Watch-CodexMicroRelay.ps1", import.meta.url), "utf8");
  const installer = await readFile(new URL("../installer/windows/Install-CodexMicroRelay.ps1", import.meta.url), "utf8");
  const capture = start.indexOf("$port = [int]$Matches[1]");
  const probe = start.indexOf("Invoke-WebRequest");
  const relayStart = start.indexOf("$relayProcess = Start-Process");
  const codexProcessScan = start.indexOf("$processes = @(Get-CimInstance");
  assert.ok(capture >= 0 && capture < probe, "debug port must be retained before the readiness probe");
  assert.ok(relayStart >= 0 && relayStart < codexProcessScan, "Android relay must start before Codex bridge discovery");
  assert.doesNotMatch(start, /Stop-Process/);
  assert.match(start, /Never terminate a running Codex instance/);
  assert.match(start, /Android can connect in limited mode/);
  assert.doesNotMatch(start, /throw 'Codex is running without the Micro activation port/);
  assert.match(start, /codex-micro-activation-request\.json/);
  assert.match(start, /Close Codex normally; the watcher will reopen it once/);
  assert.match(start, /-not \$AllowCodexLaunch -and -not \$activationRequested/);
  assert.match(start, /Remove-Item -LiteralPath \$activationRequestPath/);
  assert.match(start, /\[switch\]\$AllowCodexLaunch/);
  assert.match(start, /if \(-not \$AllowCodexLaunch -and -not \$activationRequested\)/);
  assert.doesNotMatch(watcher, /AllowCodexLaunch/);
  assert.match(watcher, /Local\\CodexMicroRelayWatcher/);
  assert.match(watcher, /WaitOne\(0, \$false\)/);
  assert.match(watcher, /ReleaseMutex/);
  assert.match(installer, /param\(\[switch\]\$NoUi\)/);
  assert.match(installer, /if \(-not \$NoUi\)/);
  const ui = await readFile(new URL("../launcher/Show-CodexMicroRelay.ps1", import.meta.url), "utf8");
  assert.match(ui, /Start-CodexMicroRelay\.ps1'\) -AllowCodexLaunch/);
  assert.match(ui, /Micro起動を予約しました/);
});

test("snapshot collection does not execute every lazy renderer module", async () => {
  const source = await readFile(new URL("../src/codex-micro-renderer-bridge.ts", import.meta.url), "utf8");
  const snapshotStart = source.indexOf("const SNAPSHOT_EXPRESSION");
  const bridgeClass = source.indexOf("export class CodexMicroRendererBridge");
  const snapshot = source.slice(snapshotStart, bridgeClass);
  assert.match(snapshot, /assets\/app-initial-/);
  assert.match(snapshot, /codex-micro-slot-signals-/);
  assert.doesNotMatch(snapshot, /for \(const url of urls\)/);
  assert.doesNotMatch(snapshot, /candidate\('FAST'\)/);
  assert.match(snapshot, /codex-micro-layout-/);
  assert.match(snapshot, /officialKeycapIds\.every/);
  assert.match(snapshot, /keycapCapabilities\.push/);
  assert.match(snapshot, /danger: false/);
  assert.doesNotMatch(snapshot, /dangerKeycaps/);
  assert.doesNotMatch(snapshot, /Object\.values\(layout\?\.slots/);
  assert.doesNotMatch(snapshot, /const availableKeycaps = \["FAST"/);
});
