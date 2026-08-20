# Codex Micro Mobile / PC Relay 実装報告書

作成日: 2026-08-20
文書版: 3.5
対象計画書: `docs/IMPLEMENTATION_PLAN.md` 文書版2.5／`docs/PALETTE_30_KEY_RESTORE_IMPLEMENTATION_PLAN.md` 文書版1.0
Android版: 0.2.6（versionCode 8）
Relay版: 0.2.8

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | 初期Android／headless Relay実装を完成と誤判定 |
| 1.1 | 2026-08-19 | 廃止 | Windows PC UI、アプリ内QR、Codex Micro全機能の不足を訂正 |
| 2.0 | 2026-08-19 | 廃止 | QRスキャナーを開かず「クラッシュがない」と誤記したため廃止 |
| 2.1 | 2026-08-19 | 廃止 | AndroidX Core欠落クラッシュを訂正し、0.2.1の検証範囲へ更新 |
| 2.2 | 2026-08-19 | 廃止 | テスト計画1.0の実施結果、Windows実インストール、正しい成果物版／hashへ更新 |
| 2.3 | 2026-08-19 | 廃止 | Relay snapshot／Watcher修正、15回連続live試験、Pixel QR scanner再試験を反映 |
| 2.4 | 2026-08-19 | 廃止 | Codex強制再起動の原因訂正、Relay 0.2.2非破壊修正、L3読取確認を反映 |
| 2.5 | 2026-08-19 | 廃止 | 0.2.2の自動起動残存を訂正し、0.2.3の明示起動分離へ更新 |
| 2.6 | 2026-08-19 | 廃止 | Android操作遅延・誤表示とRelay Agent入力を0.2.2／0.2.4で修正 |
| 2.7 | 2026-08-20 | 廃止 | transport接続だけでAndroid再接続を解消済みと誤判定 |
| 2.8 | 2026-08-20 | 廃止 | Pixel 4画面・実操作・5分安定試験、scroll保持、危険Action保護を反映 |
| 2.9 | 2026-08-20 | 廃止 | 6キー制限を修正し、通常27／Danger 3、MIC down/up、nonceを実装 |
| 3.0 | 2026-08-20 | 廃止 | README鬼レビューでAction迂回と画面外holdを修正、主要文書を整合 |
| 3.1 | 2026-08-20 | 廃止 | Pixel再接続後に安全追補APK、境界外cancel、5分安定稼働を実機確認 |
| 3.2 | 2026-08-20 | 廃止 | 通常27 handler、DEL、nonce live異常系、後続Relay不具合を修正・検証 |
| 3.3 | 2026-08-20 | 廃止 | 危険分類の過剰設計を撤回し、同一Palette 30キー、Protocol 2、通常commandへ復帰 |
| 3.4 | 2026-08-20 | 廃止 | 無断で分離したRelayローカルルートを廃止し、Androidリポジトリ内`relay`へ統合 |
| 3.5 | 2026-08-20 | 現行 | 無断公開したRelay単体GitHubリポジトリを削除し、公開先をAndroidリポジトリへ一本化 |

計画書の対象版、Android版、Relay版を必ず記録する。実装内容、試験範囲または判定を変更した場合は文書版を上げ、過去版の判定を上書きせず履歴へ残す。

## 2. 結論

`APPR`、`REJ`、`DEL`を一律に危険操作とした文書版2.9～3.2の設計判断を撤回した。状態変更と危険性を混同しており、`REJ`は実行を止める操作、`DEL`はtask archiveであってファイルやGit履歴を削除しない。`APPR`は承認対象の内容に依存するが、Keycap自体を危険分類する根拠にはならない。

Android 0.2.6／Relay 0.2.8／Protocol 2で、公式30キーを同一Paletteへ戻し、Danger画面、長押し、confirmation nonce、動的Action拒否を削除した。Pixel UI dumpでは30件すべて表示・enabled、APPR／REJ／DELの通常配置、Danger表示0件、ready／fresh、crash／ANR 0を確認した。実装は完了したが、現在の実承認要求が0件でAPPR／REJの成立結果を確認できず、作業中task保護のためDELの0.2.8実機tapを行っていない。この2条件は受入保留として残す。

実装計画書2.0に対応するAndroid 4主要画面、公式Keycap 30、全操作command、Usage／Reset、Hosts、Windows PC管理UI、PCアプリ内QRを実装した。

Android 0.2.0では、QRスキャナー起動時に`androidx.core.content.ContextCompat`欠落で反復クラッシュした。従来確認したのはランチャーActivityだけであり、「クラッシュがない」という2.0の記載は無効である。原因と判定ミスは`docs/ANDROIDX_CORE_CRASH_ROOT_CAUSE.md`へ記録した。

AndroidX依存を直接追加した0.2.1について、unit test 13件、debug／release build、release Lint、runtime classpath、生成APKとPixelへ導入済みAPKのDEXを検証した。Pixelロック解除後にアプリUIからQRスキャナーを開き、ZXing `CaptureActivity`とcamera preview、現行PIDのFATAL／ANR 0件を確認した。結果は`docs/ANDROIDX_CORE_CRASH_FIX_REPORT.md`を正とする。Windows PC管理UIは概要画面とアプリ内QR画面を目視確認済みである。

Relay 0.2.0で発生したsnapshot反復失敗は0.2.1で修正した。しかし0.2.1にはCodex強制終了、0.2.2には既存instance未検出時の自動起動が残った。0.2.3でWatcherからCodex起動権限を除去し、管理UIの明示操作だけが`AllowCodexLaunch`を渡す設計へ変更した。

Android 0.2.1はoffline再試行ごとに全画面を再構築し、system barとapp barも重なっていた。RelayはAgent down／upを別要求として並列処理し、最大6.9秒の滞留を生じた。Android 0.2.2／Relay 0.2.4で再描画抑制、system bar inset、明示的`PC未接続`表示、atomic `agent-tap`、重複拒否、snapshot非同期更新へ修正した。

Relay 0.2.4はCodex CDP検査に失敗するとAndroid用WSS listenerを起動せず終了した。Relay 0.2.5でWSSをCodex bridgeより先に独立起動したが、Android 0.2.3はtransport接続を`PC接続済`と表示し、機能未接続を識別できなかった。

Android 0.2.4ではbridge待ちを`Codex未接続`へ変更し、live snapshot再描画後のscroll位置を保持し、APPR／REJ動的Actionへ長押し保護を適用した。Relay 0.2.6では既存Codexを強制終了せず、管理UIの明示操作後の通常終了時だけ一度Micro有効起動する予約方式へ変更した。

Pixelへ0.2.4を導入し、4画面、QR、Wi-Fi WSS、fresh snapshot、主要操作、危険操作保護、Wi-Fi再接続、5分連続稼働を実機確認した。Android実機受入は合格である。署名済みAPK、Mac実機、Windows管理UIの一部再採取は未完了のため一般配布完成とはしない。

## 3. Android実装

### 3.1 Control

- 指定UIと同じ`Control` bottom navigationを追加した。
- App bar、接続状態chip、Windows／Mac target、最終snapshot、freshness、再接続を追加した。
- Agentを2列×3行で表示する。
- Agentカードへslot、project name、native title、status、context、host、selectedを割り当てた。
- Codex buildがproject nameを公開しない場合はnative titleへfallbackし、両フィールドをデータ上で混同しない。
- Agent tapはkey-down／key-up、長押しは詳細dialogとした。
- 6 Micro ActionをUI上のACT01～06として表示し、native `ACT06`～`ACT12`へ対応付けた。
- Joystick bottom sheetへUp／Right／Down／Leftを実装した。
- Reasoning bottom sheetへEncoder Press、MIND-、MIND+を実装した。
- Reasoning長押しは500ms後から300ms間隔で反復し、cancel／切断時に停止する。
- snapshot受信による再描画の前後で画面別scroll位置を保存し、下段コントロールへの到達を維持する。
- APPR／REJ／DELが割り当てられた動的Actionも他slotと同じdown／upを送信する。

### 3.2 公式Palette 30

- 公式30件を5列グリッド、検索、カテゴリへ統合した。
- APPR／REJ／DELを通常tapとし、Danger専用画面を削除した。
- ID、名称、カテゴリ、説明を一つの定義へ集約した。
- すべて／アクション／ナビゲーション／開発／その他のカテゴリを実装した。
- ID、名称、説明の検索を実装した。
- 選択中keyの説明、互換性、実行可否を表示する。
- Relay snapshotへlive registry由来の30 `keycapCapabilities`を追加した。
- MICだけはdown／up／cancelでPush-to-talkを制御する。
- Relayは30 Keycapを通常commandとして扱い、現在画面で不成立の場合はキーID付き理由を返す。

### 3.3 Usage

- 自動、5時間、週間、その他の4 modeを実装した。
- snapshotに含まれる全windowを固定件数なしで表示する。
- used／remaining、duration、reset時刻、取得host、更新時刻を表示する。
- Rate Limit Resetへavailable／applicable credit表示と1.2秒長押しを実装した。
- 1.2秒未満、offline、stale、creditなしでは送信しない。

### 3.4 Hosts／Pairing／Settings

- `Hosts` bottom navigationとWindows／Mac target切替を実装した。
- 最大8件のPC profileを一覧表示する。
- 接続、再接続、接続テスト、QR再ペアリング、資格情報失効、削除の行メニューを実装した。
- 選択中hostのendpoint、health、Agent source、Codex version、lightingを表示する。
- QR scanner、deep link、NSD、fingerprint固定、Android Keystore暗号化を維持した。
- theme、通知、reload、接続テスト、Usage／接続詳細への導線を実装した。

### 3.5 共通UI／安全操作

- `Control／Palette／Usage／Hosts`の4項目bottom navigationを実装した。
- ハンバーガーメニューと画面別overflow menuを実装した。
- 状態を色だけでなく文字と記号でも表示する。
- 接続喪失、画面切替、target変更時に押下／長押し状態を解除する。
- stale snapshotではcommandを送信しない。

## 4. Relay実装

### 4.1 command

次のtyped commandをRelay／Android allowlistへ実装した。

- `agent`
- `action`
- `joystick` 4方向
- `encoder`
- `reasoning`
- `keycap` 公式30 ID
- `new-task`
- `environment-action` 1～3
- `host-target`
- `rate-limit-reset`

`new-task`はnative `NEW` keycapへ委譲する。`host-target`は接続したRelay自身のstable host IDだけを受理する。shell、filesystem、任意URL、任意CDP evaluateは引き続き拒否する。

### 4.2 snapshot

- optional `projectName`を追加した。
- Codex slotがproject／workspace／repository名を公開する場合に取得する。
- 名前がなくpathだけ存在する場合は最終directory名をproject nameとして使う。
- native `title`は上書きしない。
- live Codex keycap registryから`availableKeycaps`を取得する。
- usage全window、credits、theme、lighting、Agent source、host sessionsを維持した。

### 4.3 QR

- 既存SVGに加えてPC UI埋め込み用PNGを生成する。
- PNGは`%LOCALAPPDATA%\CodexMicroRelay\mobile-local-pairing.png`へユーザー限定で保存する。
- `Configure-CodexMicroMobile.ps1`から外部SVGを開く処理を削除した。
- disable時にSVGとPNGの両方を削除する。

## 5. Windows PC管理UI

`launcher/Show-CodexMicroRelay.ps1`へWindows Forms管理アプリを実装した。

### 画面

- 概要: Relay、Codex bridge、LAN discovery、address、port
- ペアリング: PCアプリ内QR、PC名、OS、LAN、port、fingerprint、token警告
- 接続端末: Android端末一覧領域と資格情報失効説明
- ログ: bounded log表示、更新、診断コピー、log folder
- 設定: listen、port、TLS、Bonjour、state root

### 操作

- Relay開始／再接続
- ペアリングQR表示
- Codexを開く
- 認証情報更新
- Relay無効化
- ログ更新／診断コピー

installerはStart Menuへ`Codex Micro Relay` shortcutを作成し、install後に管理UIを開く。ウィンドウを閉じてもRelay processは停止しない。uninstallerはshortcutも削除する。

Windows PowerShell 5で日本語UIを正しく解釈させるため、管理UI scriptはUTF-8 BOMで保存した。

## 6. versionと成果物

### Android

| 項目 | 値 |
|---|---|
| versionName | `0.2.5` |
| versionCode | `7` |
| debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| unsigned release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| debug APK SHA-256 | `7d2b9cef03f8cf2c9b10b9c05e9f2aefc062f5a3069cffe9eb118756660e0280` |
| release APK SHA-256 | `e8192a8279ebb9f990f5fadcb57ebda0c2f20840cf07f05d4ab5dd7f8c037548` |
| base commit | `53d3964` |
| safety commit | `aeed172` |

### Relay

| 項目 | 値 |
|---|---|
| package version | `0.2.7` |
| Windows ZIP | `C:\Projects\codex-micro-android\relay\release\codex-micro-relay-windows-x64.zip` |
| Windows ZIP SHA-256 | `eef140f986df17d6670a26af4641245aa330ceadc1b7eee5dffb4393275df39d` |
| base commit | `d539641` |
| safety commit | `0358eb4` |
| guard test commit | `60b1464` |
| full live追補commit | `acc617e` |
| PC概要画面 | `C:\Projects\codex-micro-android\relay\release\pc-ui-verification.png` |
| PCアプリ内QR画面 | `C:\Projects\codex-micro-android\relay\release\pc-ui-pairing-verification.png` |

## 7. 検証結果

### Android

| 検証 | 結果 |
|---|---|
| unit test | 14件成功、failure 0 |
| debug APK build | 成功 |
| release APK build | 成功 |
| release Lint | 成功 |
| release dependency list | 成功 |
| release SHA-256 | 成功 |
| Pixel 9a APK上書きinstall | 0.2.5 / 7成功 |
| Pixel MainActivity起動 | 成功 |
| Pixel QRスキャナー起動 | 成功、ZXing `CaptureActivity`／camera preview確認 |
| AndroidRuntime fatal確認 | 0.2.0のQR起動で2件確認。0.2.4現行PIDはFATAL／ANR 0件 |
| Relay再接続表示 | `ready`と`fresh (0s)`をPixel実画面で確認 |
| offline描画性能 | 12秒、18 frames、jank 0%、90th 5ms |
| system bar inset | 実画面で上部／下部重なり解消 |
| `adb reverse --list` | 空 |
| Pixel 4画面キャプチャ | 完了。Control／Palette／Usage／Hostsを保存 |
| Pixel主要操作 | Agent／Action／Joystick／Encoder／Reasoning／FAST成功 |
| Pixel安定試験 | 5分間同一PID、前面、Awake、crash／ANR 0 |

ZXingの旧Activity APIについてdeprecated warningが5件あるが、compile／Lintエラーではない。将来Activity Result APIへ移行する。

### Relay／PC UI

| 検証 | 結果 |
|---|---|
| TypeScript strict check | 成功 |
| Node test | 11件成功、failure 0 |
| Windows package audit | 成功、runtime dependency 4種 |
| npm audit | vulnerability 0 |
| Relay bind | `<PC_PRIVATE_IP>:47653` |
| CDP boundary | `127.0.0.1`維持 |
| QR PNG生成 | 成功 |
| PC管理UI起動 | 成功 |
| PC概要画面目視 | 成功 |
| PCアプリ内QR目視 | 成功 |
| 外部SVG自動起動 | 削除済み |
| Windowsユーザー領域へ実install | 成功、`%LOCALAPPDATA%\CodexMicroRelay\app` |
| 導入済みruntimeからLAN待受 | 成功、`<PC_PRIVATE_IP>:47653` |
| WSS live smoke | TLS pin／token／ready／任意command拒否／snapshotが15回連続成功。30秒安定監視も成功 |
| Codex非破壊監視 | 0.2.3導入後32秒でCodex終了0・新規起動0 |
| 現在のRelay待受 | `<PC_PRIVATE_IP>:47653`、Pixel `<PIXEL_PRIVATE_IP>`とESTABLISHED |
| Agent atomic tap | Pixel実操作成功、Relay 505 ms、UI選択card更新 |

## 8. 計画書Phase判定

| Phase | 実装 | 自動検証 | 実機受入 |
|---|---|---|---|
| A UI foundation | 完了 | build／Lint成功 | Pixel 4画面確認済み |
| B Snapshot完全化 | 完了 | parser／Relay check成功 | live fields／fresh確認済み |
| C Control | 完了 | build／command test成功 | 主要操作確認済み |
| D Palette 30 | 実装完了（条件付き受入） | 30 capability、danger 0、MIC、APPR／REJ／DEL通常経路、旧nonce拒否 | Pixelで30表示・全enabled・Danger 0。APPR／REJは不成立理由確認、DEL実機tapは未実施 |
| E Usage | 完了 | parser／hold実装確認 | live usage／Reset長押し表示確認済み |
| F Hosts／Pairing／Settings | 完了 | profile／QR test成功 | 登録host、QR scanner確認済み |
| Relay／PC UI | 完了 | test／package audit成功 | Windows概要／QR目視成功 |

## 9. 未完了・制約

1. project nameはCodex buildがworkspace情報を公開しないtaskではnullとなり、native titleへfallbackする。
2. 署名鍵が未投入のためrelease APKはunsignedである。
3. Mac実機がないためmacOSは実機未検証である。
4. GitHub Actions `macos-latest`の実runは未実施である。
5. ZXing deprecated APIをActivity Result APIへ更新する余地がある。
6. Windows管理UIのConnections／Logs／Settings再採取と、破棄可能なtaskでのnew task／environment action試験が残る。

## 10. リリース判定

文書版2.8のAndroid 0.2.4／Relay 0.2.6合格判定は、Palette 6キー制限を見逃したため無効である。現行0.2.6／0.2.8は30キーを同一Paletteへ統合し、30 capability、danger 0、30ボタン全enabled、旧danger-arm／nonce拒否、APPR／REJの具体的不成立理由を確認した。実承認要求でのAPPR／REJ成立、破棄可能taskでのDEL実機tap、Wi-Fi復帰画面、署名配布、Mac実機は未完了のため製品全体は受入保留とする。

全製品計画はWindows管理UIの一部再試験、明示起動予約の実動作、破棄可能なtaskでの変更操作、署名配布、Mac実機が残るため受入保留とした。当時の正本は`TEST_PLAN.md`／`TEST_REPORT.md`文書版1.6であり、現行正本ではない。

## 11. 文書版2.9 Palette修正

文書版2.8の「Palette 30表示、FAST実行」をPalette完成の根拠にした判定は不十分だった。Relayが6 Action slotだけをavailabilityとして通知し、Androidが残りをdisabledにしていたためである。

Android 0.2.5（commit `53d3964`）とRelay 0.2.7（commit `d539641`）で、通常Palette 27、Danger 3、30 capability、MIC down/up、危険nonceを実装した。Pixelでは27件表示／全enabled、危険3件非混在、Danger導線、通常tap／短時間hold 0実行、APPS、MICを実測した。

危険3キーの正規hold、27通常キー全件の隔離実行、Wi-Fi復帰後実画面、修正版5分安定試験は未完了である。したがって2.9の最終判定は**受入保留**とした。当時の正本は`TEST_PLAN.md`／`TEST_REPORT.md`文書版1.7、Palette対応報告1.0であり、現行正本ではない。

## 12. 文書版3.0 README鬼レビュー対応

鬼レビューはREADMEだけでなく参照正本をコードへ照合し、Control動的slotの危険Action迂回と、Danger hold中のview境界外移動未処理をP0として検出した。

- Androidは動的APPR／REJ／DELを直接送信せず、Danger警告へ誘導する。
- Relayは現在layoutを再確認し、危険keycap割当slotのgeneric Actionを拒否する。
- Danger holdはACTION_MOVEでview境界外を検出し、execute／progress callbackを除去する。
- READMEへ対象版、受入保留、27／3、使用条件、60秒nonce、MIC、L3／USB条件、導入手順、未検証項目を追加した。
- 計画書、機能一覧、実装報告、テスト計画／結果から旧Palette 30／600msの現行記述を撤廃した。

Android test／Lint／Debug／Release buildとRelay 13 unit test／型検査／buildはPASSした。当時は境界外cancelのPixel実機再試験がADB切断によりBLOCKEDだったため、総合判定を受入保留とした。

導入済みRelayへのlive WSS試験でも、APPR割当`ACT07`のgeneric Actionは`ok:false`で拒否され、nonce迂回が実行経路上でも遮断されることを確認した。

追加安全修正commitはAndroid `aeed172`、Relay `0358eb4`、全危険slot guard test `60b1464`。Relay配布物はWindowsへ再導入済みである。

## 13. 文書版3.1 Pixel再接続後の実機確認

安全追補commit `aeed172`を含むAndroid 0.2.5 / versionCode 7をPixelへ上書き導入した。Controlの動的APPRをtapするとDanger警告が開き、Relayのgeneric Action受信は0件だった。

Danger画面ではDEL内から境界外へ1.5秒swipeし、Relayのkeycap／action受信0件、画面の`DEL: 未実行`を確認した。続けて12:21:43～12:26:47の5分間、PID 24970、MainActivity前面、Awakeを10回中10回維持し、終了時は`ready`／`fresh (0s)`、crash／ANR 0だった。実測と判定は文書commit `18bac75`に記録した。

当時の正本は実装計画2.2、テスト計画1.8、テスト結果1.9、Palette対応報告1.2であり、現行正本ではない。

## 14. 文書版3.2 全keycap・nonce追加実測

認証済みlive WSSハーネスを追加し、projectless一時taskで通常27 IDを全件送信した。18件は実行成功し、リポジトリまたは現在viewを必要とする9件は全件keycap ID付きの具体的不成立理由を返した。DWNのclipboardはDataObjectとして復元した。

全件試験でlive smokeの仮想NIC誤選択、OAI external URL fallback欠落、不成立keycap後のCDP切断、SPLIT renderer切替の誤失敗を検出し、Relay commit `acc617e`で修正した。Relay unit 15件、型検査、build、package、audit、導入済みRelayのlive smokeを再実行してPASSした。Windows ZIP SHA-256は`eef140f986df17d6670a26af4641245aa330ceadc1b7eee5dffb4393275df39d`である。

DangerはDELを一時taskとSPLIT forkだけに正規nonceで実行し、archive後のactive task除外とnonce再利用拒否を確認した。nonceなし、キー違い、期限切れ、切断後、task変更後、再利用をlive WSSで全件拒否した。APPR／REJはapprovalPending=trueのRelay統合試験で各1回だけnative controlへ到達することを確認したが、現在のCodex実承認要求が0件なので実画面正規実行はBLOCKEDである。

作成した一時taskとforkはすべてarchiveし、既存taskへ変更を加えていない。実測文書commitは`3998c35`である。これは当時の実装計画2.2、テスト計画1.8、テスト結果2.0、原因調査1.1、Palette対応報告1.3の履歴であり、現行正本ではない。

## 15. 文書版3.3 Palette 30キー復帰

### 15.1 実装

- Androidの`Screen.DANGER`、Danger UI、警告dialog、hold処理、nonce state、危険Action分岐を削除した。
- `OfficialKeycaps.all`の30件を同じgrid、検索、カテゴリへ表示した。
- APPR／REJ／DELを通常`kind:keycap` tapとしてallowlistへ戻した。MICだけはact 0／1を維持した。
- Relayの`danger-arm`、confirmation map、60秒期限、task束縛、Action slot guardを削除した。
- Protocolを2へ上げ、旧danger-armとnonce／confirmedHoldMs付きcommandを拒否した。
- capabilityの後方互換`danger`フィールドは残し、30件すべて`false`とした。
- Android commit: `bb24ba46caf125aa886c66582be60bf94497300b`
- Relay commit: `7917c32f7f04631d7c0ef0a4e07705c901918f00`

### 15.2 自動試験・build

| 試験 | 結果 |
|---|---|
| Android unit test | PASS |
| Android Lint | PASS |
| Android Debug／Release build | PASS、90 tasks |
| Relay unit test | PASS、13件 |
| Relay TypeScript check／build | PASS |
| Relay Windows package | PASS、1450 files |
| npm audit | PASS、脆弱性0 |
| live Protocol 2 contract | PASS、旧danger-arm／nonce要求を拒否 |

### 15.3 Windows／Pixel実測

| 項目 | 結果 |
|---|---|
| Pixel導入版 | 0.2.6 / versionCode 8 |
| Windows導入版 | Relay 0.2.8、設定portでLISTEN |
| Android接続 | `ready`／`fresh (0s)` |
| Paletteタイトル | `公式 Keycap 30` |
| Keycap表示 | 30／30件 |
| enabled | 30／30件 |
| APPR／REJ／DEL | 通常Palette内、各1件、enabled |
| Danger UI | 0件 |
| live capability | 30件、danger=true 0件 |
| APPR通常tap | Relay受信、9msで`APPR: The current Codex view cannot execute this keycap.` |
| REJ通常tap | Relay受信、13msで`REJ: The current Codex view cannot execute this keycap.` |
| crash／ANR | 0件 |

APPR／REJ試験時のsnapshotは`approvalPending=false`であり、実行成立をPASSとはしていない。DELは現在の作業taskをarchiveしないためPixel実機tapを行わず、Relay統合試験でAPPR／REJ／DEL各1回が通常native controlへ到達することを確認した。破棄可能taskを用意したDEL実機試験は未完了である。

### 15.4 成果物

| 成果物 | SHA-256 |
|---|---|
| debug APK | `3F6988A35C8079F8E78AF1BA8008B9285998D9F0FFF43B8477E7397F47CAA085` |
| unsigned release APK | `EC4C56C89197B3036FC84DAE5BCBA7DFAE935709D8A0976288C6CCB25BA43274` |
| Windows Relay ZIP | `8D0BDE87BC4285AE263390CB3032797FBAC16EF07C61868F9BBAD5FC2983B4CD` |

### 15.5 判定

30キー復帰実装とProtocol 2の自動試験、Windows／Pixel導入、30件表示・enabled確認は**合格**。実承認要求でのAPPR／REJ成立、破棄可能taskでのDEL実機tap、Wi-Fi復帰、Mac実機、署名済みreleaseは**未完了**であり、製品全体の最終判定は受入保留とする。

## 16. ローカル単一ルートへの是正（文書版3.4）

`C:\Projects\codex-micro-relay`を別ルートとして作成した判断は、利用者の指定範囲を超えた誤りだった。RelayのGit追跡対象40ファイルだけを`C:\Projects\codex-micro-android\relay`へ統合し、別ルートの`.git`、`node_modules`、`dist`、`release`は持ち込んでいない。統合先でRelayのtest 13/13、TypeScript check、buildを再実行してすべて成功した。旧ローカルルートはWindowsのごみ箱へ移動し、同パスが不存在であることを確認した。インストール済みWatcherは`%LOCALAPPDATA%\CodexMicroRelay\app`を作業ディレクトリとして再起動し、port 47653のLISTENを確認した。

## 17. GitHub公開先の一本化（文書版3.5）

`omusubiman5/deck-for-codex-relay`は明示許可なしに別公開したリポジトリであり、作成自体が不適切だった。Relayソースはすでに`omusubiman5/deck-for-codex-android`の`relay`へ統合済みであるため、Relay単体GitHubリポジトリを削除し、READMEからリンクを撤去した。以後の公開・issue・版管理はAndroidリポジトリへ一本化する。
