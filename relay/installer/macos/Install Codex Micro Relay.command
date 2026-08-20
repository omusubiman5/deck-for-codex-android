#!/bin/zsh
set -euo pipefail
source_root="${0:A:h:h:h}"
destination="$HOME/Library/Application Support/CodexMicroRelay/app"
mkdir -p "$destination" "$HOME/Library/LaunchAgents"
for name in dist node_modules launcher package.json LICENSE README.md SOURCE_PROVENANCE.md; do
  [[ -e "$source_root/$name" ]] || { print -u2 "Missing package component: $name"; exit 1; }
  cp -R "$source_root/$name" "$destination/"
done
chmod +x "$destination/launcher/start-codex-micro-relay.sh"
plist="$HOME/Library/LaunchAgents/com.simeo.codex-micro-relay.plist"
sed "s|__PROGRAM__|$destination/launcher/start-codex-micro-relay.sh|g" "$source_root/installer/macos/com.simeo.codex-micro-relay.plist" > "$plist"
chmod 600 "$plist"
"$destination/launcher/start-codex-micro-relay.sh" configure
open "$HOME/Library/Application Support/CodexMicroRelay/mobile-local-pairing.svg"
launchctl bootout "gui/$UID/com.simeo.codex-micro-relay" 2>/dev/null || true
launchctl bootstrap "gui/$UID" "$plist"
print "Installed for the current user: $destination"
