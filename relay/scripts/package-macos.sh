#!/bin/bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
release="$root/release"
[[ "$release" == "$root/release" ]] || { echo 'Unsafe release path.' >&2; exit 1; }
npm run build
stage="$release/codex-micro-relay-macos-universal"
rm -rf "$stage"
mkdir -p "$stage"
for name in dist launcher installer package.json package-lock.json LICENSE README.md SOURCE_PROVENANCE.md THIRD_PARTY_NOTICES.md; do
  cp -R "$root/$name" "$stage/"
done
npm install --omit=dev --ignore-scripts --prefix "$stage"
node "$root/scripts/audit-package.mjs" "$stage" macos
chmod +x "$stage/launcher/start-codex-micro-relay.sh" "$stage/installer/macos/"*.command
zip_path="$release/codex-micro-relay-macos-universal.zip"
rm -f "$zip_path"
(cd "$stage" && /usr/bin/zip -qry "$zip_path" .)
shasum -a 256 "$zip_path" | sed 's|.*/|  |' > "$release/codex-micro-relay-macos-universal.zip.sha256"
echo "$zip_path"
