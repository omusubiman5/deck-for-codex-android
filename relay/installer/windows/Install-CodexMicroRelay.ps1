param([switch]$NoUi)
$ErrorActionPreference = 'Stop'
$source = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$destination = Join-Path $env:LOCALAPPDATA 'CodexMicroRelay\app'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
foreach ($name in @('dist', 'node_modules', 'launcher', 'package.json', 'LICENSE', 'README.md', 'SOURCE_PROVENANCE.md')) {
  $item = Join-Path $source $name
  if (-not (Test-Path -LiteralPath $item)) { throw "Package component is missing: $item" }
  Copy-Item -LiteralPath $item -Destination $destination -Recurse -Force
}
$startup = [Environment]::GetFolderPath('Startup')
$watcherCmd = Join-Path $startup 'Codex Micro Relay.cmd'
$watcher = Join-Path $destination 'launcher\Watch-CodexMicroRelay.ps1'
[IO.File]::WriteAllText($watcherCmd, "@echo off`r`nstart `"`" /min powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$watcher`"`r`n", [Text.UTF8Encoding]::new($false))
& (Join-Path $destination 'launcher\Configure-CodexMicroMobile.ps1')
Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-WindowStyle','Hidden','-File',"`"$watcher`"")
$startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) 'Codex Micro Relay.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($startMenu)
$shortcut.TargetPath = 'powershell.exe'
$shortcut.Arguments = "-NoLogo -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $destination 'launcher\Show-CodexMicroRelay.ps1')`""
$shortcut.WorkingDirectory = $destination
$shortcut.Save()
if (-not $NoUi) {
  Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$(Join-Path $destination 'launcher\Show-CodexMicroRelay.ps1')`"")
}
Write-Host "Installed for the current user: $destination"
