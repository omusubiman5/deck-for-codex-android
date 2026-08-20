$ErrorActionPreference = 'Stop'
$stateRoot = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'CodexMicroRelay'))
$startupFile = Join-Path ([Environment]::GetFolderPath('Startup')) 'Codex Micro Relay.cmd'
$startMenuFile = Join-Path ([Environment]::GetFolderPath('Programs')) 'Codex Micro Relay.lnk'
Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue | Where-Object {
  $_.CommandLine -and $_.CommandLine.Contains($stateRoot, [StringComparison]::OrdinalIgnoreCase)
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Remove-Item -LiteralPath $startupFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $startMenuFile -Force -ErrorAction SilentlyContinue
if ((Split-Path -Leaf $stateRoot) -ne 'CodexMicroRelay') { throw "Unsafe uninstall target: $stateRoot" }
Remove-Item -LiteralPath $stateRoot -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'Codex Micro Relay, pairing token, certificate key, logs, and startup entry were removed.'
