param([switch]$DryRun, [switch]$AllowCodexLaunch)
$ErrorActionPreference = 'Stop'

$installRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stateRoot = Join-Path $env:LOCALAPPDATA 'CodexMicroRelay'
$bridgePath = Join-Path $stateRoot 'codex-micro-bridge.json'
$configPath = Join-Path $stateRoot 'mobile-local-relay-server.json'
$activationRequestPath = Join-Path $stateRoot 'codex-micro-activation-request.json'
$runtime = Join-Path $installRoot 'dist\src\relay-runtime.js'
$node = Get-Command node -ErrorAction SilentlyContinue
if ($null -eq $node) { throw 'Node.js 20 or newer is required.' }
$major = [int]((& $node.Source --version).TrimStart('v').Split('.')[0])
if ($major -lt 20) { throw 'Node.js 20 or newer is required.' }
if (-not (Test-Path -LiteralPath $runtime)) { throw "Relay runtime not found: $runtime" }

# The Android WSS listener is useful even while the Codex renderer bridge is
# unavailable. Start it first so reconnecting the phone never depends on how
# the already-running Codex process was launched.
New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null
if (-not (Test-Path -LiteralPath $configPath)) {
  & $node.Source $runtime configure
}
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$listening = Get-NetTCPConnection -State Listen -LocalPort $config.port -ErrorAction SilentlyContinue
if (-not $listening -and -not $DryRun) {
  $relayProcess = Start-Process -FilePath $node.Source -WindowStyle Hidden -WorkingDirectory $installRoot -ArgumentList @("`"$runtime`"", 'run') -PassThru
  $deadline = [DateTime]::UtcNow.AddSeconds(5)
  do {
    Start-Sleep -Milliseconds 100
    $listening = Get-NetTCPConnection -State Listen -LocalPort $config.port -ErrorAction SilentlyContinue
    if ($relayProcess.HasExited) { throw "Relay stopped before opening port $($config.port). See $stateRoot\relay.log" }
  } while (-not $listening -and [DateTime]::UtcNow -lt $deadline)
  if (-not $listening) { throw "Relay did not open port $($config.port) within 5 seconds. See $stateRoot\relay.log" }
}

$package = Get-AppxPackage -Name 'OpenAI.Codex' -ErrorAction SilentlyContinue |
  Sort-Object Version -Descending | Select-Object -First 1
if ($null -eq $package) { throw 'The OpenAI Codex Windows app is not installed.' }
$appRoot = Join-Path $package.InstallLocation 'app'
$executable = Join-Path $appRoot 'ChatGPT.exe'
if (-not (Test-Path -LiteralPath $executable)) { throw "Codex executable not found: $executable" }

$prefix = [IO.Path]::GetFullPath($appRoot).TrimEnd('\') + '\'
$processes = @(Get-CimInstance Win32_Process | Where-Object {
  $_.ExecutablePath -and $_.ExecutablePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
})
$port = $null
foreach ($process in $processes) {
  if ($process.CommandLine -match '--remote-debugging-port=(\d+)') {
    # Preserve the port even while Electron is still starting. A temporary
    # /json/version failure must never be interpreted as "not debug-enabled";
    # doing so caused the watcher to kill and relaunch Codex every 10 seconds.
    $port = [int]$Matches[1]
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/json/version" -TimeoutSec 1
      if ($response.StatusCode -lt 300) { break }
    } catch { }
    break
  }
}
if ($null -eq $port) {
  if ($processes.Count -gt 0) {
    # Never terminate a running Codex instance. Electron cannot be retrofitted
    # with a debugging port, so wait for the user to close it normally. The
    # watcher will retry after the next ordinary launch without disrupting work.
    if ($AllowCodexLaunch) {
      [IO.File]::WriteAllText($activationRequestPath, (@{ requestedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
      Write-Warning 'Micro activation is reserved. Close Codex normally; the watcher will reopen it once with the activation port.'
    } else {
      Write-Warning 'Relay is running, but Codex was opened without the Micro activation port. Android can connect in limited mode. Use the Codex Micro Relay window to reserve activation.'
    }
    Write-Host "Codex Micro Relay is available on the selected private LAN address, port $($config.port)."
    exit 0
  }
  $activationRequested = Test-Path -LiteralPath $activationRequestPath
  if (-not $AllowCodexLaunch -and -not $activationRequested) {
    Write-Warning 'Relay is running and waiting for Codex. Open Codex explicitly from the Codex Micro Relay window to enable controls.'
    Write-Host "Codex Micro Relay is available on the selected private LAN address, port $($config.port)."
    exit 0
  }
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start(); $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port; $listener.Stop()
  if (-not $DryRun) {
    Start-Process -FilePath $executable -ArgumentList @('--remote-debugging-address=127.0.0.1', "--remote-debugging-port=$port")
    Remove-Item -LiteralPath $activationRequestPath -Force -ErrorAction SilentlyContinue
  }
}

if ($DryRun) {
  Write-Host "Codex: $executable"
  Write-Host "CDP: 127.0.0.1:$port"
  Write-Host "Relay config: $configPath"
  exit 0
}

[IO.File]::WriteAllText($bridgePath, (@{ port = $port; updatedAt = [DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
Write-Host "Codex Micro Relay is available on the selected private LAN address, port $($config.port)."
