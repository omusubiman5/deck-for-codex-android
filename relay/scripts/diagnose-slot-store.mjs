import { readFile } from "node:fs/promises";
import { join } from "node:path";
import WebSocket from "ws";

const root = join(process.env.LOCALAPPDATA, "CodexMicroRelay");
const { port } = JSON.parse(await readFile(join(root, "codex-micro-bridge.json"), "utf8"));
const targets = await fetch(`http://127.0.0.1:${port}/json`).then((response) => response.json());
const target = targets.find((item) => item.type === "page" && /^app:\/\/.*\/index\.html(?:$|\?)/.test(item.url));
if (!target?.webSocketDebuggerUrl) throw new Error("Codex main renderer target was not found.");

const expression = String.raw`(async () => {
  const urls = [...new Set([
    ...[...document.querySelectorAll('link[href], script[src]')].map((element) => element.href || element.src),
    ...performance.getEntriesByType('resource').map((entry) => entry.name)
  ])].filter((url) => url.includes('/assets/') && url.endsWith('.js'));
  const slotSignalsUrl = urls.find((url) => url.includes('/assets/codex-micro-slot-signals-'));
  const slotSignals = slotSignalsUrl ? await import(slotSignalsUrl) : {};
  const resolvers = Object.values(slotSignals).filter((candidate) =>
    candidate && typeof candidate === 'object' &&
    typeof candidate.resolve === 'function' &&
    typeof candidate.createSubscriberAtom === 'function'
  );
  const root = document.getElementById('root');
  const reactKeys = root ? Object.getOwnPropertyNames(root).filter((key) => key.startsWith('__react')) : [];
  const reactKey = reactKeys.find((key) => key.startsWith('__reactContainer$')) ?? reactKeys[0];
  let queue = reactKey ? [root[reactKey]] : [];
  const seen = new Set();
  const result = {
    documentReadyState: document.readyState,
    rootChildCount: root?.childElementCount ?? null,
    rootHtmlLength: root?.innerHTML.length ?? null,
    errorBoundaryVisible: /Oops, an error has occurred/i.test(root?.innerText ?? ''),
    bodyChildCount: document.body?.childElementCount ?? null,
    slotSignalsAsset: slotSignalsUrl?.split('/').pop() ?? null,
    resolverCount: resolvers.length,
    resolverShapes: resolvers.slice(0, 5).map((resolver) => Object.keys(resolver).sort()),
    reactKeys: reactKeys.map((key) => key.split('$')[0] + '$'),
    fibers: 0,
    contextValues: 0,
    maps: 0,
    mapNodes: 0,
    directStores: 0,
    nestedStores: 0,
    nodeShapes: [],
    slotCandidates: []
  };
  const shapes = new Set();
  while (queue.length && seen.size < 30000) {
    const fiber = queue.pop();
    if (!fiber || seen.has(fiber)) continue;
    seen.add(fiber);
    const values = [fiber.memoizedProps?.value];
    let dependency = fiber.dependencies?.firstContext;
    while (dependency) {
      values.push(dependency.memoizedValue);
      dependency = dependency.next;
    }
    for (const value of values) {
      if (value == null) continue;
      result.contextValues += 1;
      if (!(value instanceof Map)) continue;
      result.maps += 1;
      for (const node of value.values()) {
        result.mapNodes += 1;
        if (!node || typeof node !== 'object') continue;
        const shape = Object.keys(node).sort().join(',');
        if (shape && !shapes.has(shape) && result.nodeShapes.length < 20) {
          shapes.add(shape);
          result.nodeShapes.push(shape);
        }
        const stores = [node.store, node.p].filter((store) => store && typeof store.get === 'function');
        if (node.store && typeof node.store.get === 'function') result.directStores += 1;
        if (node.p && typeof node.p.get === 'function') result.nestedStores += 1;
        for (const store of stores) {
          for (const resolver of resolvers) {
            try {
              const atom = resolver.resolve(node, value);
              const slots = store.get(atom);
              if (Array.isArray(slots)) {
                const signature = { length: slots.length, ids: slots.slice(0, 8).map((slot) => slot?.id ?? null) };
                if (!result.slotCandidates.some((item) => JSON.stringify(item) === JSON.stringify(signature))) result.slotCandidates.push(signature);
              }
            } catch {}
          }
        }
      }
    }
    queue.push(fiber.child, fiber.sibling);
  }
  result.fibers = seen.size;
  return result;
})()`;

const socket = new WebSocket(target.webSocketDebuggerUrl);
const reload = process.argv.includes("--reload");
const result = await new Promise((resolve, reject) => {
  socket.once("error", reject);
  socket.once("open", () => socket.send(JSON.stringify(reload
    ? { id: 1, method: "Page.reload", params: { ignoreCache: false } }
    : { id: 1, method: "Runtime.evaluate", params: { expression, awaitPromise: true, returnByValue: true } }
  )));
  socket.on("message", (raw) => {
    const message = JSON.parse(raw.toString());
    if (message.id !== 1) return;
    if (message.result?.exceptionDetails) reject(new Error(message.result.exceptionDetails.exception?.description || message.result.exceptionDetails.text));
    else resolve(reload ? { reloadRequested: true } : message.result?.result?.value);
  });
});
socket.close();
console.log(JSON.stringify(result, null, 2));
