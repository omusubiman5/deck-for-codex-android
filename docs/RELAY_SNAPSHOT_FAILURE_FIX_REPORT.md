# Relay snapshot不合格 対応報告書

作成日: 2026-08-19
文書版: 1.1
修正前Relay版: 0.2.0
修正版Relay版: 0.2.1
原因調査書: `docs/RELAY_SNAPSHOT_FAILURE_ROOT_CAUSE.md` 文書版1.0

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | renderer探索、target fallback、Windows launcher／Watcherを修正し再試験結果を記録 |
| 1.1 | 2026-08-19 | 現行 | 0.2.1にCodex強制終了経路が残った訂正と0.2.2報告書への参照を追加 |

対応内容、対象版または判定を変更する場合は文書版を上げ、旧版を削除せず履歴へ残す。

## 2. 対応結果

Relayを0.2.1へ更新し、総合不合格の直接原因だったC-05 WSS live snapshotを修正した。導入済み0.2.1に対し、TLS pin、token認証、ready、任意shell command拒否、snapshotを15回連続で確認し、全15回成功した。途中に30秒の安定待機を置いてもCDP portは変化しなかった。

Pixel実機のQRスキャナー起動と現行アプリPIDのクラッシュ監視も実施した。Pixelへの秘密tokenをADBで直接注入する自動化は実行環境のセキュリティポリシーにより禁止されたため、Pixelからの最終WSSペアリングはPCアプリ内QRを人がカメラへ提示して読む操作として残る。この未実施項目をRelay修正の合格へ読み替えない。

## 3. 修正内容

### 3.1 renderer snapshot

- snapshot時に全lazy assetをimportする処理を廃止した。
- `app-initial-*`と`codex-micro-slot-signals-*`だけを特定してimportする。
- available keycap取得のために未知のexported functionへ`FAST`を渡す探索を廃止した。
- keycap一覧は公式allowlistを使用し、実行時はnative handler側の可用性判定を維持した。
- rate limit resetと汎用dispatchも`app-initial-*`だけをimportするよう限定した。

### 3.2 renderer target fallback

- DevTools pageを除外し、全main rendererを候補として保持する。
- slot store未初期化など再試行可能な初期化エラー時だけ、最大4 targetを順に試す。
- 実行エラーやcommand拒否をtarget切替で隠さない。

### 3.3 Windows launcher／Watcher

- launcherは起動中Codexのcommand lineからdebug portを先に保存する。
- CDP endpointが起動直後に未応答でも、debug port付きCodexを停止しない。
- Watcherへ名前付きmutex `Local\CodexMicroRelayWatcher`を追加し、1ユーザー1プロセスに制限した。
- 終了時はmutexをrelease／disposeする。

### 3.4 診断と回帰試験

- `scripts/diagnose-slot-store.mjs`を追加した。
- 診断出力はDOM寸法、error boundary有無、fiber数、resolver形状、6-slot候補だけとし、task本文やtokenを出さない。
- unit testへtarget fallback、再試行条件、Watcher singleton、起動中port保持、全lazy asset非importの回帰試験を追加した。

## 4. 自動試験

| 試験 | 結果 |
|---|---|
| Relay unit test | PASS、11/11 |
| TypeScript `tsc --noEmit` | PASS |
| Relay build | PASS |
| `npm audit --omit=dev` | PASS、脆弱性0 |
| Android unit／Lint／debug build | PASS、Gradle BUILD SUCCESSFUL |

Android試験はシェル既定の`JAVA_HOME`とSDK pathが未設定だったため、Android Studio同梱JDKと`%LOCALAPPDATA%\Android\Sdk`を試験プロセスへ明示して実行した。製品コードの失敗ではない。

## 5. 導入済み環境の再試験

| 確認 | 結果 |
|---|---|
| Relay install root | `%LOCALAPPDATA%\CodexMicroRelay\app` |
| Relay待受 | PASS、`<PC_PRIVATE_IP>:47653` |
| CDP待受 | PASS、`127.0.0.1:52172` |
| Watcher実プロセス数 | PASS、1 |
| renderer ready | PASS、`documentReadyState=complete` |
| Codex error boundary | PASS、非表示 |
| 6-slot store | PASS、ID 0～5を検出 |
| WSS live smoke | PASS、15/15連続 |
| 30秒安定待機 | PASS、CDP port変化なし |
| 追加最終live smoke | PASS、5/5（15回に含む） |
| Pixel 9a接続 | PASS、serial `<ADB_SERIAL>` |
| Android 0.2.1 QR scanner | PASS、ZXing `CaptureActivity`／camera preview起動 |
| Android現行PIDログ | PASS、FATAL／ANR 0 |
| `adb reverse --list` | PASS、空 |
| PixelからWSS pairing | BLOCKED、人によるPCアプリ内QR読取が必要 |

## 6. 成果物

| 項目 | 値 |
|---|---|
| Relay version | `0.2.1` |
| Relay Git commit | `0fda39f` |
| Windows ZIP | `C:\Projects\codex-micro-relay\release\codex-micro-relay-windows-x64.zip` |
| ZIP SHA-256 | `e03c19637022aaf518b043b725c9a3936287812053032e90821e40d31693b656` |
| 診断script | `C:\Projects\codex-micro-relay\scripts\diagnose-slot-store.mjs` |

## 7. 判定

Relay snapshot不具合は修正済みで、C-05はFAILからPASSへ変更する。ただし0.2.1にはdebug portなしのCodexを強制終了する別経路が残っていたため、Windows launcher／Watcherの安全対応完了という1.0の判定は撤回する。追加原因と0.2.2の修正は`docs/CODEX_FORCED_RESTART_ROOT_CAUSE.md`、`docs/CODEX_FORCED_RESTART_FIX_REPORT.md`を正とする。

製品全体はPixelからのQR pairingと実操作受入、署名APK、Mac実機試験が残るため「Relay不具合対応完了、製品受入保留」とする。
