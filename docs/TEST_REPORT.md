# Codex Micro Mobile / PC Relay テスト結果報告書

作成日: 2026-08-20
文書版: 2.2
対象テスト計画書: `docs/TEST_PLAN.md` 文書版2.0
対象実装計画書: `docs/IMPLEMENTATION_PLAN.md` 文書版2.4
対象Android版: 0.2.6（versionCode 8）
対象Relay版: 0.2.8／Protocol 2

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0～1.4 | 2026-08-19 | 廃止 | 初期試験、QR crash、Relay snapshot、Codex非破壊、応答性試験 |
| 1.5 | 2026-08-20 | 廃止 | TCP ESTABLISHEDとPIDを重視し、機能未接続を見落としたため廃止 |
| 1.6 | 2026-08-20 | 廃止 | Pixel実画面、fresh snapshot、実操作、5分連続稼働を必須にして再試験 |
| 1.7 | 2026-08-20 | 廃止 | 6キー見逃しを訂正し、27／3分離と実機再試験を記録 |
| 1.8 | 2026-08-20 | 廃止 | README鬼レビューで検出した危険Action迂回と境界外holdを修正・再判定 |
| 1.9 | 2026-08-20 | 廃止 | Pixel再接続後に安全追補APK、境界外cancel、5分安定稼働を実機確認 |
| 2.0 | 2026-08-20 | 廃止 | 通常27 handler、DEL正規実行、nonce live異常系、後続Relay不具合修正を記録 |
| 2.1 | 2026-08-20 | 廃止 | 危険分類撤回、Palette 30、Protocol 2、Windows／Pixel再導入結果を記録 |
| 2.2 | 2026-08-20 | 現行 | RelayをAndroidローカルルートへ統合し、旧ルート削除と再起動を検証 |

## 2. 判定

**Android実機受入: 受入保留**
**Palette 30復帰: 合格（条件未成立2件を除く）**
**計画全体: 受入保留**

2.1ではAndroid 0.2.6／Relay 0.2.8を実導入し、UI dumpをスクロール採取して30キー全件・全enabled、APPR／REJ／DEL各1件、Danger UI 0件を確認した。APPR／REJは承認要求なしの具体的不成立理由まで確認したが、成立条件は未発生。DELは作業中task保護のため実機tapしていない。

### 2.1 文書版2.1の実測

| ID | 結果 | 実測／証跡 |
|---|---|---|
| N-01 | PASS | Android 0.2.6 / versionCode 8をPixelへ導入 |
| N-02 | PASS | Relay 0.2.8 / Protocol 2をWindowsへ導入しLISTEN |
| N-03 | PASS | Android test／Lint／Debug／Release build、90 tasks |
| N-04 | PASS | Relay 13 unit、TypeScript、build、package 1450 files、audit 0 |
| N-05 | PASS | live capability 30件、danger=true 0件 |
| N-06 | PASS | Pixel UI dumpで30件すべて表示・enabled |
| N-07 | PASS | APPR／REJ／DELが通常Palette内、Danger UI 0件 |
| N-08 | PASS | 旧danger-arm／nonce fieldsをProtocol 2が拒否 |
| N-09 | PASS | APPR／REJ通常tapをRelay受信、キーID付き不成立理由 |
| N-10 | PASS | 実施区間のcrash／ANR 0 |
| N-11 | BLOCKED | 実承認要求0件のためAPPR／REJ成立試験なし |
| N-12 | NOT RUN | 破棄可能taskを用意できずDELの0.2.8 Pixel実機tapなし。Relay統合試験はPASS |

### 2.2 文書版2.0の実測（履歴）

| ID | 結果 | 実測／証跡 |
|---|---|---|
| P-01 | PASS | 安全追補commit `aeed172`を含むAndroid 0.2.5 / versionCode 7をPixelへ上書き導入 |
| P-02 | PASS | Relay 0.2.7追補commit `acc617e`をWindowsへ再導入、PID 11200、`<PC_PRIVATE_IP>:47653` LISTEN |
| P-03 | PASS | Android unit 15件、Debug／Release、Lint、91 tasks成功 |
| P-04 | PASS | Relay unit 15件、TypeScript、build、package、audit成功 |
| P-05 | PASS | live registryで公式30 ID、通常27／danger 3を解決 |
| P-06 | PASS | Pixel UI dumpで通常27キーを表示し、27ボタンすべてenabled |
| P-07 | PASS | 通常PaletteにAPPR／REJ／DEL 0件 |
| P-08 | PASS | global menu→警告dialog→明示ボタン→Danger 3キー |
| P-09 | PASS | DEL通常tap／600ms短時間hold（期待実行0件）でRelay実行0、画面は未実行 |
| P-10 | PASS | APPSはAndroid→Relay→native handler完了 |
| P-11 | PASS | MIC down/up各1、Relay完了2／失敗0 |
| P-12 | PASS | 実施区間のFATAL EXCEPTION／ANR 0 |
| P-13 | BLOCKED | Wi-Fi 35秒切断中にPixelがPINロック。復帰後のアプリ実画面は確認不能 |
| P-14 | BLOCKED（一部PASS） | DELはprojectless一時task／SPLIT forkを正規nonceでarchiveし再利用拒否までPASS。APPR／REJは統合経路各1回PASSだが、実承認要求0件のためnative実画面実行は条件未成立 |
| P-15 | PASS | 通常27 IDを認証済みlive WSSで全件送信。18件実行成功、projectlessで条件不成立の9件は全件キーID付き理由。DWN clipboard復元、一時task／forkをarchive |
| P-16 | PASS | 12:21:43～12:26:47、10回採取すべてPID 24970、MainActivity前面、Awake。終了時`ready`／`fresh (0s)`、crash／ANR 0 |

P-11の初回はMIC handler 2件が失敗した。誤ったVS Code module探索を公式live layoutのPush-to-talk handlerへ修正し、Relay再導入後に同じPixel操作を再実行して完了2／失敗0を確認した。

文書版1.6由来の以下の表は過去試験の証跡として残すが、Palette、対象版、hash、総合判定が1.7と衝突する箇所は本節と`PALETTE_KEYCAP_AVAILABILITY_FIX_REPORT.md`を正とする。

### 2.2 README鬼レビュー後の追加試験

| ID | 結果 | 実測／証跡 |
|---|---|---|
| R-01 | PASS | Android: test、Lint、Debug／Release build成功、91 tasks |
| R-02 | PASS | Relay: 15 unit tests、TypeScript check、build成功 |
| R-03 | PASS | server guardをunit testしAPPR／REJ／DELで例外、FASTで通過。導入済みRelayへACT07 generic Actionをlive WSS送信し`ok:false`／Danger APPR errorを確認 |
| R-04 | PASS | Android codeで動的APPR／REJ／DELを直接送信せずDanger警告へ誘導 |
| R-05 | PASS | ACTION_MOVEがview境界外ならhold callbackを除去し送信禁止。自動試験とPixel実操作で確認 |
| R-06 | PASS | DEL内から境界外へ1.5秒swipeし、Relay keycap／action受信0件、画面`DEL: 未実行`を確認 |
| R-07 | PASS | 認証済みlive WSSでnonceなし、キー違い、期限切れ、切断後、task変更後、再利用を全件拒否。DEL成功nonceの再利用も拒否 |

境界外cancel、5分安定稼働、通常27 handler応答、DEL正規実行、nonce live異常系は検証済みとなった。ただしAPPR／REJの実承認画面実行とWi-Fi復帰画面は条件未成立のため、総合判定は引き続き受入保留である。

文書版2.0の集計:

| PASS | FAIL | BLOCKED | NOT RUN |
|---:|---:|---:|---:|
| 21 | 0 | 2 | 0 |

以下2段落は文書版1.6の接続試験履歴であり、現行2.0の合否ではない。

Android 0.2.4をPixel 9aへ上書き導入し、Control／Palette／Usage／Hosts、QR scanner、Wi-Fi WSS、fresh snapshot、Agent／Action／Joystick／Encoder／Reasoning／Keycap、危険操作保護、Wi-Fi再接続を実機で確認した。5分連続試験では同一PID、MainActivity前面、Awakeを維持し、終了時のcrash／ANRは0件だった。

文書版1.5のF-02 PASSは無効である。TCP ESTABLISHEDと`PC接続済`表示だけではCodex操作が利用できず、実画面は`waiting`だった。1.6では`ready`、`fresh (0s)`、実snapshot、操作完了を確認してF-02を再判定した。

## 3. 環境・成果物

文書版2.0の現行成果物は次のとおり。

| 項目 | 実測値 |
|---|---|
| Android | 0.2.5 / versionCode 7 / base `53d3964` / safety `aeed172` |
| Relay | 0.2.7 / base `d539641` / safety `0358eb4` / guard test `60b1464` / full live追補 `acc617e` |
| debug APK SHA-256 | `7d2b9cef03f8cf2c9b10b9c05e9f2aefc062f5a3069cffe9eb118756660e0280` |
| unsigned release APK SHA-256 | `e8192a8279ebb9f990f5fadcb57ebda0c2f20840cf07f05d4ab5dd7f8c037548` |
| Windows ZIP SHA-256 | `eef140f986df17d6670a26af4641245aa330ceadc1b7eee5dffb4393275df39d` |

以下は文書版1.6の履歴値である。

| 項目 | 実測値 |
|---|---|
| Windows | `<WINDOWS_HOST>` / `<PC_PRIVATE_IP>` |
| Pixel | Google Pixel 9a / `<ADB_SERIAL>` / `<PIXEL_PRIVATE_IP>` |
| Android | 0.2.4 / versionCode 6 / package `com.simeo.codexmicromobile` |
| Relay | 導入済み0.2.6 / LAN WSS `<PC_PRIVATE_IP>:47653` |
| Android commit | `a8eb8f6` |
| Relay commit | `2c11e89` |
| debug APK SHA-256 | `6df53059146dc2258332a8c993f562b560e965c63656de387ce0b297d1bb346d` |
| unsigned release APK SHA-256 | `8b47cea7f5819217b392c042b4b2ecfe01e29c95d56f71abe9eb2f50a0372e37` |
| Windows ZIP SHA-256 | `00058fa734c31a06ff19d6346a6c31e65a06af002210c13ca7eb64770222f073` |

## 4. 文書版1.6の集計（履歴）

| 区分 | PASS | FAIL | BLOCKED | NOT RUN |
|---|---:|---:|---:|---:|
| A. 版・成果物・静的検査 | 5 | 0 | 0 | 0 |
| B. Android自動試験 | 7 | 0 | 0 | 0 |
| C. Relay自動試験・セキュリティ | 11 | 0 | 0 | 1 |
| D. Windows実インストールと管理UI | 5 | 0 | 1 | 1 |
| E. Pixel導入・4画面・QR | 12 | 0 | 0 | 0 |
| F. Wi-Fi WSSと機能操作 | 9 | 0 | 0 | 1 |
| **合計** | **49** | **0** | **1** | **3** |

## 5. 文書版1.6の詳細結果（履歴）

### A. 版・成果物・静的検査

| ID | 結果 | 実測／証跡 |
|---|---|---|
| A-01 | PASS | Android 0.2.4 / versionCode 6と文書対象版が一致 |
| A-02 | PASS | Relay source、package、導入済み版が0.2.6 |
| A-03 | PASS | `OfficialKeycaps`は重複なし30件 |
| A-04 | PASS | 型付きallowlist、未知kind／範囲外slot拒否をunit testで確認 |
| A-05 | PASS | APK／ZIP hashを3章へ記録 |

### B. Android自動試験

| ID | 結果 | 実測／証跡 |
|---|---|---|
| B-01 | PASS | 14 tests、failure 0、error 0、skipped 0 |
| B-02 | PASS | clean後のDebug／Release APK生成成功 |
| B-03 | PASS | Lint完走、fatal error 0 |
| B-04 | PASS | AndroidX Core／Fragment直接依存を解決 |
| B-05 | PASS | QR起動後も`ContextCompat`例外なし |
| B-06 | PASS | system bar inset正常。ライブ更新後もscroll位置を保持 |
| B-07 | PASS | bridge利用不能時のchipを誤解を招く`PC接続済`から`Codex未接続`へ修正 |

実行コマンドは`gradlew clean test lint assembleRelease assembleDebug`、結果は90 tasks、BUILD SUCCESSFUL。最初の1回はSDK環境変数未設定で試験開始前に停止したため、Android Studio JDKとSDKを明示して全項目を再実行した。

### C. Relay自動試験・セキュリティ

| ID | 結果 | 実測／証跡 |
|---|---|---|
| C-01 | PASS | 11/11 pass |
| C-02 | PASS | `tsc --noEmit` error 0 |
| C-03 | PASS | Windows package 1449 files、runtime dependency 4件 |
| C-04 | PASS | `npm audit` vulnerability 0 |
| C-05 | PASS | pinned TLS、token、ready、snapshotをlive経路で確認 |
| C-06 | PASS | 許可外`shell` commandを拒否 |
| C-07 | PASS | Watcher named mutexと単一processを確認 |
| C-08 | PASS | Codex CDP `127.0.0.1:54808`、6 slots、error boundaryなし |
| C-09 | PASS | WatcherはCodexを強制終了・無断起動しない |
| C-10 | PASS | Agent atomic tapと重複抑止をunit／実操作で確認 |
| C-11 | PASS | Codex bridgeなしでもWSS listenerを先行起動 |
| C-12 | NOT RUN | 一度限りの明示起動予約はunit／parse合格。稼働中Codexを試験都合で終了させる実動作は未実施 |

### D. Windows実インストールと管理UI

| ID | 結果 | 実測／証跡 |
|---|---|---|
| D-01 | PASS | `%LOCALAPPDATA%\CodexMicroRelay\app`へ0.2.6導入 |
| D-02 | PASS | Startup cmdとStart Menu shortcutあり |
| D-03 | PASS | `<PC_PRIVATE_IP>:47653`で導入済みRelayがLISTEN |
| D-04 | PASS | Overview実画面証跡あり |
| D-05 | PASS | Pairing画面内PictureBoxにQRを表示。外部SVG起動なし |
| D-06 | BLOCKED | WinForms windowを現行UI automationが列挙できず再採取不能 |
| D-07 | NOT RUN | 同理由でUIボタン再操作は未実施。Relay起動自体はCLI／実機接続で確認 |

PC UI証跡はRelay repoの`release/pc-ui-verification.png`と`release/pc-ui-pairing-verification.png`。

### E. Pixel 9a導入・4画面・QR

| ID | 結果 | 実測／証跡 |
|---|---|---|
| E-01 | PASS | ADB `device`、0.2.4 / 6のinstall-r成功 |
| E-02 | PASS | MainActivity起動、Controlを物理画面表示 |
| E-03 | PASS | Agents 6、Actions 6、Joystick、Reasoningを表示。下段へscroll可能 |
| E-04 | PASS | Palette 30キー、カテゴリ、検索UIを表示 |
| E-05 | PASS | Usage 67%／33%、reset UIを表示 |
| E-06 | PASS | Hosts 1/8、Windows profile、ready、pairing入口を表示 |
| E-07 | PASS | ZXing `CaptureActivity`とcamera previewを表示、crashなし |
| E-08 | PASS | ユーザーのQR読取でhost profileを登録済み。証跡へtoken非露出 |
| E-09 | PASS | 4画面を`docs/assets/diagnostics/android-0.2.4/`へ保存 |
| E-10 | PASS | 現行packageのcrash 0、ANR 0 |
| E-11 | PASS | `topResumedActivity=com.simeo.codexmicromobile/.MainActivity`と同時画面を採取 |
| E-12 | PASS | 01:29:28～01:34:30 JST、PID 6753、前面Activity、Awake、`mStayOn=true`を維持 |

主要証跡:

- `control-0.2.4-final.png`
- `palette-final.png`
- `usage-final.png`
- `hosts-final.png`
- `qr-scanner.png`
- `stability-5min.png`

### F. Wi-Fi WSSと機能操作

| ID | 結果 | 実測／証跡 |
|---|---|---|
| F-01 | PASS | `adb reverse --list`は空 |
| F-02 | PASS | chip `ready`、`fresh (0s)`、実Agent／Usage snapshotをPixelで確認 |
| F-03 | PASS | Agent 2 tap完了、選択card更新、Relay 505 ms |
| F-04 | PASS | ACT01 down/up到達、5 ms／54 ms |
| F-05 | PASS | Joystick 4方向、down/up計8 command、全件完了 |
| F-06 | PASS | Encoder down/up、MIND−、MIND+がRelayで完了 |
| F-07 | PASS | FAST keycapが9 msで完了 |
| F-08 | PASS | APPR／REJ／DELは通常tapでRelay増分0。Action表示を`長押し`へ修正。Resetは`1.2秒長押し`表示、非適用時disable |
| F-09 | NOT RUN | new task／environment actionは現行タスクを変更するため、破棄可能な専用状態なしでは実行しない |
| F-10 | PASS | Wi-Fiを35秒停止して`PC未接続`を確認。再有効化後`ready`かつ`fresh (0s)`へ復帰 |

## 6. 文書版1.6で発見・修正した不具合（履歴）

1. 文書版1.5はPID／TCPだけで接続を合格とし、Androidが`waiting`で操作不能な事実を見落とした。
2. live snapshot受信ごとの全画面再構築でscroll位置が先頭へ戻り、Joystick／Reasoningへ到達できなかった。画面別scroll位置を保存・復元した。
3. 動的ActionのAPPR／REJが通常tapで送信可能だった。Keycap定義の`holdMillis`をAction slotにも適用し、表示を`長押し`へ変更した。
4. `bridge_waiting`を`PC接続済`と表示していたため、機能接続と誤認した。`Codex未接続`へ変更した。

各修正後に全Android build/test/Lint、Pixel再導入、関連実操作を再実行した。

## 7. ローカル単一ルート是正試験（文書版2.2）

| ID | 結果 | 実測／証跡 |
|---|---|---|
| L-01 | PASS | 旧RelayのGit追跡40ファイルと`relay`配下をGit blob hashで照合し全件一致 |
| L-02 | PASS | `relay/.git`なし。親のAndroidリポジトリで40ファイルを追跡可能 |
| L-03 | PASS | `relay`で`npm ci`成功、audit脆弱性0 |
| L-04 | PASS | Relay unit test 13/13、TypeScript check、build成功 |
| L-05 | PASS | 旧`C:\Projects\codex-micro-relay`をWindowsごみ箱へ移動し、同パス不存在を確認 |
| L-06 | PASS | Watcherをインストール先から再起動。PID 9544、port 47653 LISTEN 1件 |

GitHubの`omusubiman5/deck-for-codex-relay`は利用者確認により維持した。ローカル開発の正本は`C:\Projects\codex-micro-android`のみである。

## 8. 文書版1.6時点の残件（履歴）

- Android実機範囲にはFAIL／BLOCKEDはない。
- 全計画を合格にするには、D-06のWindows管理UI 3タブ実画面、D-07のUIボタン操作、C-12の通常終了後一度限り起動を別途確認する。
- F-09は破棄可能な専用Codex task／environmentを用意した場合だけ実行する。
- unsigned release APKは一般配布用署名成果物ではない。
