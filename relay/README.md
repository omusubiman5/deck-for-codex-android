# Codex Micro Relay 0.2.8

Android版Codex Micro Mobileと、Windows/macOS上のCodex Desktopを同一LAN内で接続する非公式Relayです。Relay Runtimeはヘッドレスで常駐し、Windowsでは管理UIから状態、ペアリングQR、ログ、設定を扱います。Stream Deck、M18、VSD Craftには依存しません。

Protocol 2では公式30 Keycapを同じ通常commandとして扱います。`APPR`、`REJ`、`DEL`を危険分類せず、旧`danger-arm`、confirmation nonce、Action slot拒否は使用しません。`MIC`だけはdown／upでPush-to-talkを制御します。

## セキュリティ境界

- CDPは常に`127.0.0.1`へ限定して起動します。
- Relayは選択されたRFC 1918アドレスだけへWSSでbindします。wildcard/public IPは拒否します。
- 初回設定でP-256自己署名証明書と256-bit tokenを生成します。
- WebSocketは64 KiB上限、3秒認証期限、timing-safe token比較、typed command allowlistを使います。
- QRにはtokenが含まれます。共有・公開しないでください。
- 状態と秘密鍵はWindowsでは`%LOCALAPPDATA%\CodexMicroRelay`、macOSでは`~/Library/Application Support/CodexMicroRelay`へ保存します。

## 開発

Node.js 20以上で次を実行します。

```powershell
npm install
npm run check
npm test
npm run build
node dist/src/relay-runtime.js self-test
```

Nearby設定／tokenローテーション／無効化:

```powershell
node dist/src/relay-runtime.js configure
node dist/src/relay-runtime.js rotate
node dist/src/relay-runtime.js disable
```

## Windows

`npm run package:windows`で`release/codex-micro-relay-windows-x64.zip`を生成します。ZIPを展開し、`installer/windows/Install Codex Micro Relay.cmd`を実行します。ユーザー単位でインストールされ、管理者権限は不要です。

install後はStart Menuの`Codex Micro Relay`からWindows管理UIを開きます。ペアリングQRは管理UI内に表示され、外部SVGやブラウザを使用しません。管理UIを閉じてもRelay Runtimeは常駐します。

## macOS

`npm run package:macos`で未署名の`release/codex-micro-relay-macos-universal.zip`を生成します。`installer/macos/Install Codex Micro Relay.command`でLaunchAgentを登録します。

macOS版はCI build/test/package監査までの対応であり、Mac実機未検証です。正式配布前にDeveloper ID署名と公証、Mac実機受入試験が必要です。

## 出典

RelayコアのMIT抽出元とcommitは[SOURCE_PROVENANCE.md](SOURCE_PROVENANCE.md)を参照してください。
