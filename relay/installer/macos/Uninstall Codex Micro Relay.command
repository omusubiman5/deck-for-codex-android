#!/bin/zsh
set -euo pipefail
plist="$HOME/Library/LaunchAgents/com.simeo.codex-micro-relay.plist"
target="$HOME/Library/Application Support/CodexMicroRelay"
launchctl bootout "gui/$UID/com.simeo.codex-micro-relay" 2>/dev/null || true
rm -f "$plist"
[[ "$target" == "$HOME/Library/Application Support/CodexMicroRelay" ]] || { print -u2 'Unsafe uninstall target.'; exit 1; }
rm -rf "$target"
print 'Codex Micro Relay, pairing secrets, logs, and LaunchAgent were removed.'
