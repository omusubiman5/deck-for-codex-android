import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import WebSocket from "ws";
import { selectPrivateLanAddress } from "../dist/src/relay-network.js";

const PROTOCOL = 2;
const stateRoot = process.platform === "win32"
  ? join(process.env.LOCALAPPDATA || join(homedir(), "AppData", "Local"), "CodexMicroRelay")
  : join(homedir(), "Library", "Application Support", "CodexMicroRelay");
const configPath = process.env.CODEX_MICRO_RELAY_CONFIG || join(stateRoot, "mobile-local-relay-server.json");
const config = JSON.parse(await readFile(configPath, "utf8"));
if (!config.enabled || !config.token || !config.tls?.fingerprintSha256) throw new Error("Relay pairing configuration is incomplete.");
const host = config.listenHost === "auto" ? await selectPrivateLanAddress() : config.listenHost;

function threadDigest(value) {
  return value ? createHash("sha256").update(value).digest("hex").slice(0, 12) : null;
}

async function connect() {
  const socket = new WebSocket(`wss://${host}:${config.port}`, { rejectUnauthorized: false });
  const pending = new Map();
  let latestSnapshot = null;
  let readyResolve;
  let readyReject;
  const ready = new Promise((resolve, reject) => { readyResolve = resolve; readyReject = reject; });
  const timeout = setTimeout(() => readyReject(new Error("Relay authentication or initial snapshot timed out.")), 12_000);
  socket.once("error", readyReject);
  socket.once("open", () => {
    const cert = socket._socket.getPeerCertificate();
    const actual = createHash("sha256").update(cert.raw).digest("hex");
    const expected = String(config.tls.fingerprintSha256).replaceAll(":", "").toLowerCase();
    if (actual !== expected) return readyReject(new Error("TLS certificate fingerprint mismatch."));
    socket.send(JSON.stringify({ type: "auth", protocol: PROTOCOL, token: config.token }));
  });
  socket.on("message", (raw) => {
    const message = JSON.parse(raw.toString());
    if (message.type === "snapshot") latestSnapshot = message.snapshot;
    if (message.type === "result" && pending.has(message.requestId)) {
      pending.get(message.requestId)(message);
      pending.delete(message.requestId);
    }
    if (message.type === "snapshot" && latestSnapshot) {
      clearTimeout(timeout);
      readyResolve();
    }
  });
  await ready;
  return {
    socket,
    snapshot: () => latestSnapshot,
    async command(command, label = command.kind) {
      const requestId = `${label}-${randomUUID()}`;
      const response = new Promise((resolve, reject) => {
        const timer = setTimeout(() => { pending.delete(requestId); reject(new Error(`${label} result timed out.`)); }, 15_000);
        pending.set(requestId, (message) => { clearTimeout(timer); resolve(message); });
      });
      socket.send(JSON.stringify({ type: "command", protocol: PROTOCOL, requestId, command }));
      return await response;
    },
    async waitForThreadNot(previous, timeoutMs = 8_000) {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        if (latestSnapshot?.activeThreadKey !== previous) return latestSnapshot?.activeThreadKey ?? "";
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
      return null;
    },
    close() { socket.close(); }
  };
}

const mode = process.argv[2] ?? "inventory";
const client = await connect();
try {
  if (mode === "inventory") {
    const snapshot = client.snapshot();
    const capabilities = snapshot.keycapCapabilities ?? [];
    console.log(JSON.stringify({
      ok: capabilities.length === 30 && capabilities.every((item) => item.danger === false),
      host,
      activeThread: threadDigest(snapshot.activeThreadKey),
      slots: snapshot.slots?.map((slot, index) => ({ index, thread: threadDigest(slot.threadKey), status: slot.status })),
      approvalPending: snapshot.approvalPending,
      capabilities: capabilities.map(({ id, actionType, status, danger }) => ({ id, actionType, status, danger }))
    }, null, 2));
  } else if (mode === "contract") {
    const removedArm = await client.command({ kind: "danger-arm", keycapId: "DEL", threadKey: "local:removed" }, "removed-danger-arm");
    const removedNonce = await client.command({
      kind: "keycap", keycapId: "DEL", confirmationNonce: randomUUID(), confirmedHoldMs: 1200
    }, "removed-nonce-fields");
    const capabilities = client.snapshot().keycapCapabilities ?? [];
    const results = [
      { name: "danger-arm-rejected", pass: removedArm.ok === false, error: removedArm.error ?? null },
      { name: "nonce-fields-rejected", pass: removedNonce.ok === false, error: removedNonce.error ?? null },
      { name: "capability-count-30", pass: capabilities.length === 30 },
      { name: "danger-classification-zero", pass: capabilities.every((item) => item.danger === false) }
    ];
    console.log(JSON.stringify({ ok: results.every((item) => item.pass), results }, null, 2));
  } else if (mode === "archive-current") {
    const before = client.snapshot().activeThreadKey;
    if (!before) throw new Error("No active disposable task is available for archive-current.");
    const response = await client.command({ kind: "keycap", keycapId: "DEL" }, "archive-current");
    if (!response.ok) throw new Error(`DEL execution failed: ${response.error}`);
    const after = await client.waitForThreadNot(before);
    if (after == null) throw new Error("Archived task remained active.");
    console.log(JSON.stringify({ ok: true, archivedThread: threadDigest(before), activeThread: threadDigest(after) }));
  } else if (mode === "keycap") {
    const keycapId = process.argv[3];
    if (!keycapId) throw new Error("keycap mode requires an ID.");
    const before = client.snapshot().activeThreadKey;
    const response = keycapId === "MIC"
      ? await client.command({ kind: "keycap", keycapId, act: 1 }, "single-MIC-down")
      : await client.command({ kind: "keycap", keycapId }, `single-${keycapId}`);
    if (keycapId === "MIC" && response.ok) await client.command({ kind: "keycap", keycapId, act: 0 }, "single-MIC-up");
    await new Promise((resolve) => setTimeout(resolve, 500));
    console.log(JSON.stringify({
      ok: response.ok, keycapId, error: response.error ?? null,
      threadChanged: before !== client.snapshot().activeThreadKey
    }));
  } else {
    throw new Error(`Unknown mode: ${mode}`);
  }
} finally {
  client.close();
}
