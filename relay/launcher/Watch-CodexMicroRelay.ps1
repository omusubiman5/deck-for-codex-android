$ErrorActionPreference = 'Continue'
$start = Join-Path $PSScriptRoot 'Start-CodexMicroRelay.ps1'
$mutex = [Threading.Mutex]::new($false, 'Local\CodexMicroRelayWatcher')
if (-not $mutex.WaitOne(0, $false)) { $mutex.Dispose(); exit 0 }
try {
  while ($true) {
    try { & $start | Out-Null } catch {
      $log = Join-Path $env:LOCALAPPDATA 'CodexMicroRelay\watcher.log'
      New-Item -ItemType Directory -Force -Path (Split-Path -Parent $log) | Out-Null
      Add-Content -LiteralPath $log -Value "$([DateTimeOffset]::UtcNow.ToString('o')) $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 10
  }
} finally {
  $mutex.ReleaseMutex()
  $mutex.Dispose()
}
