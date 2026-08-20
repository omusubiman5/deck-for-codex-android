import { access, readFile, readdir } from "node:fs/promises";
import { join, relative, resolve } from "node:path";

const root = resolve(process.argv[2] ?? "");
const platform = process.argv[3];
if (!root || !["windows", "macos"].includes(platform)) throw new Error("Usage: audit-package.mjs <staging-root> <windows|macos>");

const required = [
  "dist/src/relay-runtime.js", "dist/src/codex-relay-server.js", "node_modules/ws/package.json",
  "LICENSE", "README.md", "SOURCE_PROVENANCE.md", "THIRD_PARTY_NOTICES.md",
  ...(platform === "windows"
    ? ["launcher/Start-CodexMicroRelay.ps1", "launcher/Watch-CodexMicroRelay.ps1", "installer/windows/Install Codex Micro Relay.cmd"]
    : ["launcher/start-codex-micro-relay.sh", "installer/macos/Install Codex Micro Relay.command", "installer/macos/com.simeo.codex-micro-relay.plist"])
];
for (const path of required) await access(join(root, path));

const files = [];
async function walk(path) {
  for (const entry of await readdir(path, { withFileTypes: true })) {
    const child = join(path, entry.name);
    if (entry.isDirectory()) await walk(child); else files.push(relative(root, child).replaceAll("\\", "/"));
  }
}
await walk(root);
const forbidden = files.filter((path) => /(^|\/)(m18|stream.?deck|vsd.?craft)(\/|$)/i.test(path));
if (forbidden.length) throw new Error(`Forbidden device/plugin artifacts: ${forbidden.join(", ")}`);
const packageJson = JSON.parse(await readFile(join(root, "package.json"), "utf8"));
const dependencyNames = Object.keys(packageJson.dependencies ?? {});
if (dependencyNames.some((name) => /elgato|streamdeck|sharp/i.test(name))) throw new Error("Plugin/image dependency leaked into package.");
console.log(JSON.stringify({ ok: true, platform, files: files.length, runtimeDependencies: dependencyNames.sort() }));
