$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$release = [IO.Path]::GetFullPath((Join-Path $root 'release'))
if ((Split-Path -Leaf $release) -ne 'release' -or (Split-Path -Parent $release) -ne $root) { throw "Unsafe release path: $release" }
& npm.cmd run build
$stage = Join-Path $release 'codex-micro-relay-windows-x64'
Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($name in @('dist','launcher','installer','package.json','package-lock.json','LICENSE','README.md','SOURCE_PROVENANCE.md','THIRD_PARTY_NOTICES.md')) {
  Copy-Item -LiteralPath (Join-Path $root $name) -Destination $stage -Recurse -Force
}
& npm.cmd install --omit=dev --ignore-scripts --prefix $stage
& node (Join-Path $root 'scripts\audit-package.mjs') $stage windows
$zip = Join-Path $release 'codex-micro-relay-windows-x64.zip'
Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip -CompressionLevel Optimal
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([IO.File]::ReadAllBytes($zip))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
[IO.File]::WriteAllText((Join-Path $release 'codex-micro-relay-windows-x64.zip.sha256'), "$hash  codex-micro-relay-windows-x64.zip`n", [Text.UTF8Encoding]::new($false))
Write-Host $zip
