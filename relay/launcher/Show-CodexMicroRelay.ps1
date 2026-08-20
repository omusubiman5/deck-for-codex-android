param([switch]$Configure)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()

$appRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stateRoot = Join-Path $env:LOCALAPPDATA 'CodexMicroRelay'
$runtime = Join-Path $appRoot 'dist\src\relay-runtime.js'
$configPath = Join-Path $stateRoot 'mobile-local-relay-server.json'
$qrPath = Join-Path $stateRoot 'mobile-local-pairing.png'
$logPath = Join-Path $stateRoot 'relay.log'
$node = (Get-Command node -ErrorAction Stop).Source

function Invoke-Runtime([string]$Command) {
  $output = & $node $runtime $Command 2>&1
  if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
  return ($output -join [Environment]::NewLine)
}

function Read-Config {
  if (-not (Test-Path -LiteralPath $configPath)) { return $null }
  return Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
}

function New-Label([string]$Text, [int]$X, [int]$Y, [int]$Width = 700, [int]$Height = 28, [switch]$Bold) {
  $label = [Windows.Forms.Label]::new()
  $label.Text = $Text; $label.Location = [Drawing.Point]::new($X, $Y); $label.Size = [Drawing.Size]::new($Width, $Height)
  $size = [single]$(if ($Bold) { 11 } else { 9 })
  $style = $(if ($Bold) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular })
  $label.Font = [Drawing.Font]::new('Segoe UI', $size, $style)
  return $label
}

function New-Button([string]$Text, [int]$X, [int]$Y, [int]$Width = 150) {
  $button = [Windows.Forms.Button]::new()
  $button.Text = $Text; $button.Location = [Drawing.Point]::new($X, $Y); $button.Size = [Drawing.Size]::new($Width, 38)
  return $button
}

$form = [Windows.Forms.Form]::new()
$form.Text = 'Codex Micro Relay'
$form.Size = [Drawing.Size]::new(920, 700)
$form.MinimumSize = [Drawing.Size]::new(820, 620)
$form.StartPosition = 'CenterScreen'
$form.Font = [Drawing.Font]::new('Segoe UI', [single]9, [Drawing.FontStyle]::Regular)
$form.BackColor = [Drawing.Color]::FromArgb(246, 247, 249)

$header = New-Label 'Codex Micro Relay' 22 16 600 38 -Bold
$header.Font = [Drawing.Font]::new('Segoe UI', [single]20, [Drawing.FontStyle]::Bold)
$form.Controls.Add($header)
$statusChip = New-Label '● 確認中' 700 23 170 28 -Bold
$form.Controls.Add($statusChip)

$tabs = [Windows.Forms.TabControl]::new()
$tabs.Location = [Drawing.Point]::new(18, 64); $tabs.Size = [Drawing.Size]::new(865, 570); $tabs.Anchor = 'Top,Bottom,Left,Right'
$form.Controls.Add($tabs)

$overview = [Windows.Forms.TabPage]::new('概要')
$pairing = [Windows.Forms.TabPage]::new('ペアリング')
$connections = [Windows.Forms.TabPage]::new('接続端末')
$logs = [Windows.Forms.TabPage]::new('ログ')
$settings = [Windows.Forms.TabPage]::new('設定')
$tabs.TabPages.AddRange(@($overview, $pairing, $connections, $logs, $settings))

$overviewTitle = New-Label 'Relay / Codex Desktop / LAN' 24 24 700 32 -Bold
$overview.Controls.Add($overviewTitle)
$overviewState = New-Label '' 24 72 760 210
$overviewState.Font = [Drawing.Font]::new('Consolas', [single]11, [Drawing.FontStyle]::Regular)
$overview.Controls.Add($overviewState)
$startButton = New-Button 'Relayを開始 / 再接続' 24 300 190
$showQrButton = New-Button 'ペアリングQRを表示' 230 300 190
$openCodexButton = New-Button 'Codexを開く' 436 300 150
$overview.Controls.AddRange(@($startButton, $showQrButton, $openCodexButton))

$qrPicture = [Windows.Forms.PictureBox]::new()
$qrPicture.Location = [Drawing.Point]::new(26, 32); $qrPicture.Size = [Drawing.Size]::new(390, 390)
$qrPicture.SizeMode = 'Zoom'; $qrPicture.BorderStyle = 'FixedSingle'
$pairing.Controls.Add($qrPicture)
$pairingInfo = New-Label '' 445 38 360 230
$pairing.Controls.Add($pairingInfo)
$warning = New-Label '⚠ QRには認証tokenが含まれます。共有・公開しないでください。' 445 278 360 52 -Bold
$warning.ForeColor = [Drawing.Color]::Firebrick
$pairing.Controls.Add($warning)
$rotateButton = New-Button '認証情報を更新' 445 348 165
$disableButton = New-Button 'Relayを無効化' 626 348 150
$pairing.Controls.AddRange(@($rotateButton, $disableButton))

$connections.Controls.Add((New-Label '認証済みAndroid端末' 24 24 500 30 -Bold))
$connectionsList = [Windows.Forms.ListView]::new()
$connectionsList.Location = [Drawing.Point]::new(24, 65); $connectionsList.Size = [Drawing.Size]::new(790, 340)
$connectionsList.View = 'Details'; $connectionsList.FullRowSelect = $true
[void]$connectionsList.Columns.Add('端末', 240); [void]$connectionsList.Columns.Add('状態', 140); [void]$connectionsList.Columns.Add('最終接続', 220)
$connections.Controls.Add($connectionsList)
$connections.Controls.Add((New-Label '接続端末情報はRelay稼働中に更新されます。token更新で既存資格情報を一括失効できます。' 24 420 790 50))

$logBox = [Windows.Forms.TextBox]::new()
$logBox.Location = [Drawing.Point]::new(18, 18); $logBox.Size = [Drawing.Size]::new(810, 430); $logBox.Multiline = $true
$logBox.ScrollBars = 'Both'; $logBox.ReadOnly = $true; $logBox.Font = [Drawing.Font]::new('Consolas', [single]9, [Drawing.FontStyle]::Regular); $logBox.Anchor = 'Top,Bottom,Left,Right'
$logs.Controls.Add($logBox)
$refreshLogButton = New-Button '最新へ更新' 18 464 130
$copyLogButton = New-Button '診断情報をコピー' 164 464 160
$openLogButton = New-Button 'ログフォルダを開く' 340 464 170
$logs.Controls.AddRange(@($refreshLogButton, $copyLogButton, $openLogButton))

$settings.Controls.Add((New-Label 'Relay設定' 24 24 500 30 -Bold))
$settingsInfo = New-Label '' 24 66 760 170
$settingsInfo.Font = [Drawing.Font]::new('Consolas', [single]10, [Drawing.FontStyle]::Regular)
$settings.Controls.Add($settingsInfo)
$settings.Controls.Add((New-Label 'Nearby Relayは選択されたprivate LAN IPv4だけで待受し、CDPは127.0.0.1から外へ公開しません。' 24 250 760 52))
$saveButton = New-Button '設定を再読込' 24 320 150
$settings.Controls.Add($saveButton)

function Load-QrImage {
  if ($qrPicture.Image) { $qrPicture.Image.Dispose(); $qrPicture.Image = $null }
  if (-not (Test-Path -LiteralPath $qrPath)) { return }
  $source = [Drawing.Image]::FromFile($qrPath)
  try { $qrPicture.Image = [Drawing.Bitmap]::new($source) } finally { $source.Dispose() }
}

function Refresh-Ui {
  $config = Read-Config
  if ($null -eq $config) {
    $statusChip.Text = '● 未設定'; $statusChip.ForeColor = [Drawing.Color]::DarkOrange
    $overviewState.Text = "Relay                 ○ 未設定`r`nChatGPT Desktop       ○ 未確認`r`nLAN自動検出            ○ 未設定`r`nAndroid               0台"
    $pairingInfo.Text = 'ペアリング設定がありません。認証情報を更新してください。'
    $settingsInfo.Text = 'Relay disabled'
  } else {
    $listening = Get-NetTCPConnection -State Listen -LocalPort $config.port -ErrorAction SilentlyContinue | Select-Object -First 1
    $running = $null -ne $listening
    $statusChip.Text = $(if ($running) { '● 稼働中' } else { '● 停止中' })
    $statusChip.ForeColor = $(if ($running) { [Drawing.Color]::ForestGreen } else { [Drawing.Color]::Firebrick })
    $address = $(if ($listening) { $listening.LocalAddress } else { 'auto / 未待受' })
    $overviewState.Text = "Relay                 $(if ($running) {'● 稼働中'} else {'● 停止中'})`r`nChatGPT Desktop       ローカルbridge監視`r`nLAN自動検出            ● 有効`r`nAndroid               認証接続待ち`r`n`r`nLAN: $address`r`nPort: $($config.port)"
    $fingerprint = [string]$config.tls.fingerprintSha256
    $pairingInfo.Text = "PC: $env:COMPUTERNAME`r`nOS: Windows`r`nLAN: $address`r`nPort: $($config.port)`r`nFingerprint: $($fingerprint.Substring(0, [Math]::Min(18, $fingerprint.Length)))…"
    $settingsInfo.Text = "Listen: $($config.listenHost)`r`nPort: $($config.port)`r`nTLS: pinned P-256`r`nBonjour: $($config.discovery.enabled)`r`nState: $stateRoot"
  }
  Load-QrImage
  if (Test-Path -LiteralPath $logPath) { $logBox.Text = Get-Content -LiteralPath $logPath -Tail 500 | Out-String; $logBox.SelectionStart = $logBox.TextLength; $logBox.ScrollToCaret() }
}

$startButton.Add_Click({
  try {
    & (Join-Path $appRoot 'launcher\Start-CodexMicroRelay.ps1') -AllowCodexLaunch
    if (Test-Path -LiteralPath (Join-Path $stateRoot 'codex-micro-activation-request.json')) {
      [Windows.Forms.MessageBox]::Show('CodexのMicro起動を予約しました。Codexを通常終了すると、一度だけ自動で開き直します。', 'Codex接続待ち', 'OK', 'Information') | Out-Null
    }
    Refresh-Ui
  }
  catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Relay開始エラー', 'OK', 'Error') | Out-Null }
})
$showQrButton.Add_Click({ $tabs.SelectedTab = $pairing; Refresh-Ui })
$openCodexButton.Add_Click({
  try {
    & (Join-Path $appRoot 'launcher\Start-CodexMicroRelay.ps1') -AllowCodexLaunch
    if (Test-Path -LiteralPath (Join-Path $stateRoot 'codex-micro-activation-request.json')) {
      [Windows.Forms.MessageBox]::Show('CodexのMicro起動を予約しました。Codexを通常終了すると、一度だけ自動で開き直します。', 'Codex接続待ち', 'OK', 'Information') | Out-Null
    }
    Refresh-Ui
  }
  catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Codex起動エラー', 'OK', 'Error') | Out-Null }
})
$rotateButton.Add_Click({
  if ([Windows.Forms.MessageBox]::Show('既存のAndroid資格情報は無効になります。更新しますか？', '認証情報を更新', 'YesNo', 'Warning') -eq 'Yes') {
    try { [void](Invoke-Runtime 'rotate'); Refresh-Ui } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message) | Out-Null }
  }
})
$disableButton.Add_Click({
  if ([Windows.Forms.MessageBox]::Show('RelayとペアリングQRを無効化しますか？', 'Relayを無効化', 'YesNo', 'Warning') -eq 'Yes') {
    try { [void](Invoke-Runtime 'disable'); Refresh-Ui } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message) | Out-Null }
  }
})
$refreshLogButton.Add_Click({ Refresh-Ui })
$copyLogButton.Add_Click({ [Windows.Forms.Clipboard]::SetText($overviewState.Text + "`r`n`r`n" + $logBox.Text) })
$openLogButton.Add_Click({ Start-Process explorer.exe -ArgumentList @("`"$stateRoot`"") })
$saveButton.Add_Click({ Refresh-Ui })
$tabs.Add_SelectedIndexChanged({ Refresh-Ui })
$form.Add_FormClosed({ if ($qrPicture.Image) { $qrPicture.Image.Dispose() } })

if ($Configure -or -not (Test-Path -LiteralPath $configPath)) { [void](Invoke-Runtime 'configure') }
Refresh-Ui
[void]$form.ShowDialog()
