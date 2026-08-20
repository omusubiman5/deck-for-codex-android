# Codex Micro Mobile / PC Relay テスト計画書

作成日: 2026-08-20
文書版: 1.9
対象実装計画書: `docs/IMPLEMENTATION_PLAN.md` 文書版2.3
対象Android版: 0.2.6（versionCode 8）
対象Relay版: 0.2.8／Protocol 2

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | Windows実インストール、Pixel実機、Wi-Fi WSS、4画面、QR、全機能契約を含む初版 |
| 1.1 | 2026-08-19 | 廃止 | Relay 0.2.1のsnapshot回帰、Watcher単一起動、30秒安定監視を追加 |
| 1.2 | 2026-08-19 | 廃止 | Relay 0.2.2のCodex非破壊要件と強制終了禁止試験を追加 |
| 1.3 | 2026-08-19 | 廃止 | Relay 0.2.3でWatcherのCodex自動起動も禁止 |
| 1.4 | 2026-08-19 | 廃止 | Android描画性能、system bar、atomic Agent tap、Keycap可用性を追加 |
| 1.5 | 2026-08-20 | 廃止 | Codex bridgeなしRelay起動とAndroid接続状態分離を追加 |
| 1.6 | 2026-08-20 | 廃止 | 物理画面表示・4画面操作・機能readinessを必須化 |
| 1.7 | 2026-08-20 | 廃止 | 通常27／Danger 3、30 capability、MIC、nonce、隔離実行を必須化 |
| 1.8 | 2026-08-20 | 廃止 | README鬼レビューでAction迂回拒否と画面外cancel試験を追加 |
| 1.9 | 2026-08-20 | 現行 | 危険分類を撤回し、同一Palette 30キーとProtocol 2の試験へ変更 |

本書の試験範囲、合否条件、対象版を変更する場合は文書版を上げ、改訂履歴へ追記する。試験結果は同じ対象版を明記した`docs/TEST_REPORT.md`へ記録する。

## 2. 目的と完了条件

実装計画書2.0の機能を、ソースコードの存在だけでなく、ビルド成果物、Windowsへの実インストール、Pixel 9a上の実画面、USB転送に依存しないWi-Fi WSS接続まで検証する。

次をすべて満たした場合だけ総合合格とする。

- Android unit test、build、Lintが合格する。
- Relay unit test、型検査、package、依存関係監査が合格する。
- Windows版が実際のユーザー領域へインストールされ、スタートメニュー、スタートアップ、Relay、管理UIが動作する。
- Windows管理UI内のPairing画面にQRが表示され、外部SVG／ブラウザへ依存しない。
- Pixel 9aへ対象版を導入し、Control／Palette／Usage／Hostsの4画面を表示・操作できる。
- `adb reverse`を使用せず、PixelとWindowsが同一LANのWSSで接続できる。
- QRスキャナー起動時にAndroidX Core欠落クラッシュが再発しない。
- Action slotが6件以下でもcapabilityとPaletteが30件、danger=trueが0件である。
- Paletteの検索／カテゴリ／UI dumpにAPPR／REJ／DELを含む30キーが存在する。
- MICのdown／up／cancel／切断stopを別々に確認する。
- APPR／REJ／DELは通常tap command、動的Action slotも通常down／upである。
- 旧danger-arm、confirmationNonce、confirmedHoldMs付きcommandを拒否する。
- DEL実機試験は専用の破棄可能taskだけを対象とし、既存taskやrepositoryを対象にしない。

## 2.1 30キー実機マトリクス

各キーについてAndroid送信、Relay受信、registry解決、native handler結果、Android最終結果を別列で記録する。現在画面で成立しない場合は理由を記録し、PASSへ読み替えない。PR、merge、push、実repository commitは確定前に閉じる。clipboard、ブラウザ、MIC、file pickerは試験後に復元または終了する。

## 3. 試験環境

| 項目 | 環境 |
|---|---|
| Windows | 接続中のWindows PC、PowerShell 5.1/7、ユーザー領域インストール |
| Android | Google Pixel 9a、USBデバッグ許可済み |
| 通信 | 同一LAN、TLS付きWebSocket（WSS） |
| Androidソース | `C:\Projects\codex-micro-android` |
| Relayソース | `C:\Projects\codex-micro-relay` |
| Windows導入先 | `%LOCALAPPDATA%\CodexMicroRelay\app` |
| 証跡 | コマンドログ、UI dump、スクリーンショット、APK/ZIP SHA-256 |

USBはAPK導入、ADB操作、ログ取得、画面証跡の取得にだけ使用する。製品通信はWi-Fi WSSを使用し、`adb reverse`は設定しない。

PixelはUSB給電中の`mStayOn=true`を試験条件とし、スリープを未実施理由にしない。PIDとTCP ESTABLISHEDだけで合格にせず、物理画面の前面表示、UI text、snapshot freshness、操作結果を証跡とする。

## 4. 判定規則

- **PASS**: 期待結果を証跡で確認した。
- **FAIL**: 実行できたが期待結果と異なる。
- **BLOCKED**: ロック解除、外部サービス、未用意の実機など、試験対象外の前提が満たされず実行不能。
- **NOT RUN**: 今回の対象外、または破壊的影響を避けて意図的に未実施。

FAILまたはBLOCKEDが1件でも残る場合、総合判定を「合格」にしない。テスト中に修正した場合は、修正内容を実装報告書へ反映し、関係する試験を再実行する。

## 5. 試験項目

### A. 版・成果物・静的検査

| ID | 試験 | 期待結果 |
|---|---|---|
| A-01 | Android版、versionCode、報告書対象版を照合 | 0.2.5 / 7で一致 |
| A-02 | Relay版を照合 | 0.2.7で一致 |
| A-03 | Keycap定義を集計 | 公式30キーが重複なく存在 |
| A-04 | Relay command allowlistを照合 | 計画対象commandのみ受理可能 |
| A-05 | release APKとWindows ZIPのSHA-256を記録 | 成果物を一意に識別可能 |

### B. Android自動試験

| ID | 試験 | 期待結果 |
|---|---|---|
| B-01 | `testDebugUnitTest` | 全件合格 |
| B-02 | `assembleDebug` / `assembleRelease` | 両APK生成成功 |
| B-03 | `lintRelease` | fatal errorなし |
| B-04 | release runtime classpath | `androidx.core`を直接解決 |
| B-05 | APK DEX検査 | `ContextCompat`を収録 |
| B-06 | Pixel offline描画性能／inset | jank 0、system barとUIが重ならない |
| B-07 | bridge待ちhealth表示 | 機能不可時は`Codex未接続`と表示 |

### C. Relay自動試験・セキュリティ

| ID | 試験 | 期待結果 |
|---|---|---|
| C-01 | unit test | 全件合格 |
| C-02 | TypeScript型検査 | errorなし |
| C-03 | Windows package生成 | 配布ZIP生成成功 |
| C-04 | `npm audit` | high/criticalを含む既知脆弱性0 |
| C-05 | WSS live smoke | TLS fingerprint、token認証、ready、snapshotを確認 |
| C-06 | 許可リスト外command送信 | Relayが拒否し、任意shell実行しない |
| C-07 | Windows Watcher排他 | Watcher実プロセスが1件だけ存在 |
| C-08 | 30秒安定監視 | Codex CDP port不変、error boundary非表示、6-slot store検出 |
| C-09 | Codex非破壊監視 | WatcherがCodexを終了も新規起動もせず、安全待機する |
| C-10 | Agent atomic tap | 1要求化、処理中連打を蓄積せず、snapshot待ちをcommand結果から分離 |
| C-11 | Codex bridgeなしRelay起動 | WSS listenerがCodex検査より先に起動し、Codexを終了・再起動しない |
| C-12 | 明示的Micro起動予約 | UI操作後の通常終了に限り一度だけ起動し、強制終了しない |
| C-13 | 危険Action迂回 | APPR／REJ割当slotのgeneric actionをRelayが拒否 |

### D. Windows実インストールと管理UI

| ID | 試験 | 期待結果 |
|---|---|---|
| D-01 | 配布物のInstall scriptを実行 | `%LOCALAPPDATA%`配下へ0.2.7を導入 |
| D-02 | Start Menu／Startupを検査 | ショートカットと起動cmdが存在 |
| D-03 | 導入済みRelayを起動 | LAN `:47653`で待受し、導入先コードで稼働 |
| D-04 | 管理UI Overview | service、LAN、port、接続数、操作ボタンを表示 |
| D-05 | 管理UI Pairing | QRを管理UI内に表示し、SVG外部表示なし |
| D-06 | Connections／Logs／Settings | 各タブへ到達し情報を表示 |
| D-07 | 管理UIから接続テスト／再読み込み | UIが応答し状態を更新 |

アンインストール実行は既存ペアリング情報を削除するため今回の実環境ではNOT RUNとし、scriptの存在と静的検査のみ行う。

### E. Pixel 9a導入・4画面・QR

| ID | 試験 | 期待結果 |
|---|---|---|
| E-01 | `adb devices`、導入済み版照合 | Pixelがdevice、0.2.6 / 8 |
| E-02 | MainActivity起動 | crashせずControl表示 |
| E-03 | Control | Agents 6、Actions 6、Joystick、Encoder、Reasoning表示 |
| E-04 | Palette 30 | 30キー全表示・enabled、APPR／REJ／DEL各1件、Danger UI 0件 |
| E-05 | Usage | usage mode、windows、reset UI表示 |
| E-06 | Hosts | host一覧、target、設定、pairingへ到達 |
| E-07 | QRスキャナー起動 | scanner表示、`ContextCompat`例外なし |
| E-08 | deep link／QR登録 | host設定を取り込み、秘密tokenを画面証跡へ露出しない |
| E-09 | 4画面スクリーンショット | 対象版の実画面証跡4枚を保存 |
| E-10 | logcat検査 | FATAL EXCEPTION、ANR、対象package crashなし |
| E-11 | 前面Activity／物理画面 | MainActivityがtop-resumedで、同時刻の画面キャプチャにUIが表示 |
| E-12 | スリープなし継続試験 | `mStayOn=true`のまま5分間前面表示とプロセスを維持 |

### F. Wi-Fi WSSと機能操作

| ID | 試験 | 期待結果 |
|---|---|---|
| F-01 | `adb reverse --list` | 空である |
| F-02 | PixelからLAN WSS接続 | TCPだけでなくready／fresh snapshotを受信し、`waiting`が消える |
| F-03 | Agent slot操作 | key-down/upがRelayへ到達しUI更新 |
| F-04 | Action slot操作 | 動的タイトル／状態を表示しcommand到達 |
| F-05 | Joystick 4方向 | 4方向すべてcommand到達 |
| F-06 | Encoder／Reasoning | press/repeat/releaseが到達 |
| F-07 | 通常Keycap | 安全なキーでcommand到達 |
| F-08 | APPR／REJ／DEL | 通常tapでRelay／native handlerへ到達し、成立または具体的不成立理由を返す |
| F-08A | 動的Action | APPR／REJ／DEL割当slotも通常down／upをRelayが受理する |
| F-08B | Rate Limit Reset | Usageでavailable／applicable時だけ1.2秒hold |
| F-09 | host target／new task／environment action | UI経由で許可commandとして到達 |
| F-10 | Wi-Fi再接続 | 切断表示後、自動または手動で再接続しfreshへ復帰 |

APPR／REJは実承認要求がある場合だけ成立結果を確認する。DELはtask archiveであり、専用の破棄可能taskが確認できた場合だけ実行する。Rate Limit Resetは従来どおりUsage画面の確認UIを試験する。

## 6. 試験手順と証跡

1. Git差分と対象版を記録する。
2. AndroidとRelayの自動試験を実行する。
3. 配布ZIPからWindows版を実インストールする。
4. 導入先Relayを起動し、WSS live smokeと管理UIを確認する。
5. 対象debug APKをPixelへ再導入する。
6. Pixelの`mStayOn=true`を確認し、4画面を実際にタップ遷移し、各画面のUI dumpとscreen captureを採取する。
7. `adb reverse`なしのWi-Fi WSS接続と安全な機能操作を確認する。
8. 結果、失敗原因、未実施理由、成果物hashを`docs/TEST_REPORT.md`へ記録する。

スクリーンショットは秘密tokenや完全なpairing URLを含まない状態で保存する。ログにもtokenを出力しない。

## 7. リリース判定

- A～Fの必須項目がすべてPASS: **合格**
- FAILなし、外部前提だけBLOCKED: **条件付き合格ではなく受入保留**
- FAILあり: **不合格**
- Mac実機は本計画のWindows＋Android受入とは別試験。Mac対応を完成と称する場合はMac実機計画を追加する。
