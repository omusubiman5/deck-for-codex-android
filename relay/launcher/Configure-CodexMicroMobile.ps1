param([switch]$Rotate)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtime = Join-Path $root 'dist\src\relay-runtime.js'
$node = Get-Command node -ErrorAction Stop
& $node.Source $runtime $(if ($Rotate) { 'rotate' } else { 'configure' })
Write-Warning 'The QR contains the authentication token. Do not share or publish it.'
