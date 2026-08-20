import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import WebSocket from "ws";
import { selectPrivateLanAddress } from "../dist/src/relay-network.js";

// Runs against the installed Nearby endpoint without printing pairing secrets.

const stateRoot = process.platform === "win32"
  ? join(process.env.LOCALAPPDATA || join(homedir(), "AppData", "Local"), "CodexMicroRelay")
  : join(homedir(), "Library", "Application Support", "CodexMicroRelay");
const configPath = process.env.CODEX_MICRO_RELAY_CONFIG || join(stateRoot, "mobile-local-relay-server.json");
const config = JSON.parse(await readFile(configPath, "utf8"));
if (!config.enabled || !config.token || !config.tls?.fingerprintSha256) {
  throw new Error("Relay pairing configuration is incomplete.");
}

const host = config.listenHost === "auto"
  ? await selectPrivateLanAddress()
  : config.listenHost;
if (!host) throw new Error("No private LAN IPv4 is available for the live test.");
const socket = new WebSocket(`wss://${host}:${config.port}`, { rejectUnauthorized: false });
const observed = new Set();

await new Promise((resolve, reject) => {
  const timeout = setTimeout(() => {
    socket.terminate();
    reject(new Error(`Timed out waiting for ready, snapshot, and invalid-command rejection: ${[...observed].join(", ")}`));
  }, 12_000);
  const finish = (callback, value) => {
    clearTimeout(timeout);
    callback(value);
  };
  socket.once("error", (error) => finish(reject, error));
  socket.once("open", () => {
    try {
      const cert = socket._socket.getPeerCertificate();
      const actual = createHash("sha256").update(cert.raw).digest("hex");
      const expected = String(config.tls.fingerprintSha256).replaceAll(":", "").toLowerCase();
      if (actual !== expected) throw new Error("TLS certificate fingerprint mismatch.");
      observed.add("tls-pinned");
      socket.send(JSON.stringify({ type: "auth", protocol: 2, token: config.token }));
    } catch (error) {
      reject(error);
    }
  });
  socket.on("message", (raw) => {
    try {
      const message = JSON.parse(raw.toString());
      if (message.type === "ready") {
        if (message.protocol !== 1 || message.bridge !== "native-codex-micro") {
          throw new Error("Unexpected Relay ready contract.");
        }
        observed.add("ready");
        socket.send(JSON.stringify({
          type: "command",
          protocol: 2,
          requestId: "live-invalid-command",
          command: { kind: "shell", command: "whoami" }
        }));
      } else if (message.type === "snapshot") {
        if (!Array.isArray(message.snapshot?.slots)) throw new Error("Snapshot contract is incomplete.");
        observed.add("snapshot");
      } else if (message.type === "health") {
        observed.add(`health:${message.state}:${message.reason}`);
      } else if (message.type === "result" && message.requestId === "live-invalid-command") {
        if (message.ok !== false || message.error !== "Invalid relay command.") {
          throw new Error("Relay did not reject an arbitrary command.");
        }
        observed.add("invalid-command-rejected");
      }
      if (["tls-pinned", "ready", "snapshot", "invalid-command-rejected"].every((item) => observed.has(item))) {
        finish(resolve);
      }
    } catch (error) {
      finish(reject, error);
    }
  });
});

socket.close();
console.log(JSON.stringify({ ok: true, checks: [...observed] }));
