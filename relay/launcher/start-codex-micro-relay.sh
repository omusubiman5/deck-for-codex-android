#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
root="${script_dir:h}"
runtime="$root/dist/src/relay-runtime.js"
state_root="$HOME/Library/Application Support/CodexMicroRelay"
mkdir -p "$state_root"
chmod 700 "$state_root"

node_bin=""
for candidate in "$(command -v node 2>/dev/null || true)" /opt/homebrew/bin/node /usr/local/bin/node "$HOME"/.nvm/versions/node/*/bin/node(N); do
  [[ -x "$candidate" ]] || continue
  version=$($candidate --version 2>/dev/null || true)
  major=${${version#v}%%.*}
  if [[ "$major" == <-> && "$major" -ge 20 ]]; then node_bin="$candidate"; break; fi
done
[[ -n "$node_bin" ]] || { print -u2 'Node.js 20 or newer is required.'; exit 1; }

command_name="${1:-run}"
if [[ "$command_name" == configure || "$command_name" == rotate || "$command_name" == disable || "$command_name" == self-test ]]; then
  exec "$node_bin" "$runtime" "$command_name"
fi

port=$($node_bin -e 'const s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close()})')
print -r -- "{\"port\":$port,\"updatedAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}" > "$state_root/codex-micro-bridge.json"
chmod 600 "$state_root/codex-micro-bridge.json"
app=""
[[ -d /Applications/Codex.app ]] && app="Codex"
[[ -z "$app" && -d /Applications/ChatGPT.app ]] && app="ChatGPT"
[[ -n "$app" ]] || { print -u2 'Codex/ChatGPT app was not found in /Applications.'; exit 1; }
open -na "$app" --args --remote-debugging-address=127.0.0.1 --remote-debugging-port="$port"
[[ -f "$state_root/mobile-local-relay-server.json" ]] || "$node_bin" "$runtime" configure
exec "$node_bin" "$runtime" run
