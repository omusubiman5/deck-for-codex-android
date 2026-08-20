import { appendFile, mkdir, readFile, rename, rm, stat } from "node:fs/promises";
import { join } from "node:path";
import { CodexMicroRendererBridge } from "./codex-micro-renderer-bridge.js";
import { codexDeckStateRoot } from "./codex-deck-paths.js";
import { CodexRelayServer, readRelayServerConfig } from "./codex-relay-server.js";
import { getOrCreateHostIdentity } from "./host-identity.js";
import { configureLocalMobilePairing, LOCAL_MOBILE_CONFIG, LOCAL_PAIRING_QR, LOCAL_PAIRING_QR_PNG } from "./mobile-local-pairing.js";

const stateRoot = codexDeckStateRoot();
const configPath = join(stateRoot, LOCAL_MOBILE_CONFIG);
const logPath = join(stateRoot, "relay.log");
const MAX_LOG_BYTES = 1024 * 1024;

async function log(message: string): Promise<void> {
  await mkdir(stateRoot, { recursive: true, mode: 0o700 });
  try {
    if ((await stat(logPath)).size >= MAX_LOG_BYTES) {
      await rm(`${logPath}.1`, { force: true });
      await rename(logPath, `${logPath}.1`);
    }
  } catch { /* first log entry */ }
  await appendFile(logPath, `${new Date().toISOString()} ${message}\n`, { encoding: "utf8", mode: 0o600 });
}

async function run(): Promise<void> {
  const config = await readRelayServerConfig(configPath);
  if (!config) throw new Error(`Pairing is not configured. Run: node dist/src/relay-runtime.js configure`);
  const host = await getOrCreateHostIdentity(join(stateRoot, "host.json"));
  const bridge = new CodexMicroRendererBridge((message) => { void log(message); });
  const server = new CodexRelayServer(config, host, bridge, (message) => { void log(message); });
  await server.start();
  await log(`Relay started for ${host.hostName} (${host.platform}) on ${config.listenHost}:${config.port}.`);
  let stopping = false;
  const stop = async (signal: string) => {
    if (stopping) return;
    stopping = true;
    await log(`Relay stopping after ${signal}.`);
    await server.close();
    process.exitCode = 0;
  };
  process.once("SIGINT", () => { void stop("SIGINT"); });
  process.once("SIGTERM", () => { void stop("SIGTERM"); });
}

async function configure(rotate: boolean): Promise<void> {
  const result = await configureLocalMobilePairing({ stateRoot, rotate });
  console.log(JSON.stringify({ ...result, warning: "The QR contains the authentication token. Do not share it." }, null, 2));
}

async function disable(): Promise<void> {
  await rm(configPath, { force: true });
  await rm(join(stateRoot, LOCAL_PAIRING_QR), { force: true });
  await rm(join(stateRoot, LOCAL_PAIRING_QR_PNG), { force: true });
  console.log(`Nearby pairing disabled: ${stateRoot}`);
}

async function selfTest(): Promise<void> {
  const packageJson = JSON.parse(await readFile(new URL("../../package.json", import.meta.url), "utf8")) as { name?: string };
  if (packageJson.name !== "codex-micro-relay") throw new Error("Unexpected package identity.");
  if (!stateRoot.includes("CodexMicroRelay")) throw new Error(`Unsafe state root: ${stateRoot}`);
  console.log(JSON.stringify({ ok: true, platform: process.platform, stateRoot }));
}

const command = process.argv[2] ?? "run";
if (command === "run") await run();
else if (command === "configure") await configure(false);
else if (command === "rotate") await configure(true);
else if (command === "disable") await disable();
else if (command === "self-test") await selfTest();
else throw new Error(`Unknown command: ${command}`);
