import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import WebSocket from "ws";

const root = join(process.env.LOCALAPPDATA, "CodexMicroRelay");
const { port } = JSON.parse(await readFile(join(root, "codex-micro-bridge.json"), "utf8"));
const targets = await fetch(`http://127.0.0.1:${port}/json`).then((response) => response.json());
const target = targets.find((item) => item.type === "page" && /^app:\/\/.*\/index\.html(?:$|\?)/.test(item.url));
if (!target?.webSocketDebuggerUrl) throw new Error("Codex main renderer target was not found.");

const socket = new WebSocket(target.webSocketDebuggerUrl);
let nextId = 0;
const pending = new Map();
socket.on("message", (raw) => {
  const message = JSON.parse(raw.toString());
  if (!pending.has(message.id)) return;
  const { resolve, reject } = pending.get(message.id);
  pending.delete(message.id);
  if (message.error || message.result?.exceptionDetails) reject(new Error(message.error?.message || message.result.exceptionDetails.text));
  else resolve(message.result);
});
await new Promise((resolve, reject) => { socket.once("open", resolve); socket.once("error", reject); });
const call = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++nextId;
  pending.set(id, { resolve, reject });
  socket.send(JSON.stringify({ id, method, params }));
});
const evaluate = async (expression) => (await call("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true })).result.value;

try {
  const initial = await evaluate(String.raw`(() => {
    const active = document.querySelector('[data-above-composer-conversation-id]')?.getAttribute('data-above-composer-conversation-id');
    const editor = [...document.querySelectorAll('[contenteditable="true"]')].find((element) => element.getClientRects().length && element.getAttribute('aria-label') === '何でもどうぞ');
    if (active) return { ok: false, reason: 'active-task' };
    if (!editor) return { ok: false, reason: 'composer-missing' };
    const projectless = [...document.querySelectorAll('button')].find((element) => element.getClientRects().length && element.getAttribute('aria-label') === 'プロジェクトなしで作業');
    if (projectless && typeof projectless.click === 'function') projectless.click();
    editor.focus();
    return { ok: true };
  })()`);
  if (!initial.ok) throw new Error(`Disposable task precondition failed: ${initial.reason}`);
  const label = `codex-micro-palette-test-${Date.now()}`;
  await call("Input.insertText", { text: `${label}: Reply only READY. Do not use tools or modify files.` });
  await new Promise((resolve) => setTimeout(resolve, 300));
  await call("Input.dispatchKeyEvent", { type: "keyDown", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
  await call("Input.dispatchKeyEvent", { type: "keyUp", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
  const deadline = Date.now() + 15_000;
  let threadKey = null;
  while (Date.now() < deadline && !threadKey) {
    threadKey = await evaluate(String.raw`document.querySelector('[data-above-composer-conversation-id]')?.getAttribute('data-above-composer-conversation-id') ?? null`);
    if (!threadKey) await new Promise((resolve) => setTimeout(resolve, 200));
  }
  if (!threadKey) throw new Error("Disposable task did not receive a stable thread ID.");
  console.log(JSON.stringify({ ok: true, label, thread: createHash("sha256").update(threadKey).digest("hex").slice(0, 12) }));
} finally {
  socket.close();
}
