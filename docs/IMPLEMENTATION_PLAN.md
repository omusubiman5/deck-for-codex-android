# Codex Micro Mobile / PC Relay 実装計画書

作成日: 2026-08-19  
版: 2.5（単一GitHubリポジトリ版）

### 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | 初期MVP。Android機能とWindows PC UIが不足 |
| 1.1 | 2026-08-19 | 廃止 | QR／Windows UI／全機能の不足を訂正 |
| 2.0 | 2026-08-19 | 廃止 | 指定UIを正本にControl／Palette／Usage／Hostsと全機能を統合 |
| 2.1 | 2026-08-20 | 廃止 | 通常Palette 27キー、Danger 3キー、capability／MIC／nonce契約を追加 |
| 2.2 | 2026-08-20 | 廃止 | 旧Palette 30記述を撤廃し、動的Action迂回拒否と画面外cancelを明記 |
| 2.3 | 2026-08-20 | 廃止 | 危険分類の過剰設計を撤回し、公式30キーを同一Paletteへ復帰。詳細は`PALETTE_30_KEY_RESTORE_IMPLEMENTATION_PLAN.md` |
| 2.4 | 2026-08-20 | 廃止 | AndroidとRelayを`C:\Projects\codex-micro-android`の単一ローカルルートへ統合 |
| 2.5 | 2026-08-20 | 現行 | 無断作成したRelay単体GitHubリポジトリを廃止し、公開先もAndroidの1リポジトリへ統一 |

文書版はアプリ版とは独立して管理する。要件、画面、protocol、受入条件を変更する場合は文書版を上げ、改訂履歴へ追記する。

## 1. 正本と完了条件

Android UIは、利用者指定の`ChatGPT Image 2026年8月19日 14_45_15.png`を視覚・画面構成の正本とする。画像内の文言はサンプルデータを含むため、固定文字列として実装せず、本書のデータ割当と操作仕様に従って動的表示する。

機能範囲は[Codex Micro全機能一覧・実装対象表](CODEX_MICRO_FEATURE_INVENTORY.md)を正本とし、画面に入りきらない機能を削除しない。スクロール、検索、カテゴリ、詳細画面、bottom sheetを使って全機能へ到達可能にする。

次のすべてを満たした場合だけ完成とする。

- Androidの4主要画面が指定UIと同じ情報設計・視覚階層になっている。
- 6 Agent、6動的slot、Joystick 4方向、Encoder、Reasoning、公式Keycap 30種、Usage、Reset、ホスト機能をすべて操作できる。
- AndroidアプリとWindows PC管理アプリの両方が存在する。
- PCアプリ内にペアリングQRを表示し、外部SVG、ブラウザ、画像ビューアを使わない。
- Pixel実機でUSBを外し、`adb reverse`なしで同一LAN WSS接続・操作試験に合格する。
- 未実装、一部実装、実機未検証の項目を完成と報告しない。

### 1.1 Palette修正の必須条件（文書版2.3）

- Relayは6 Action slotの割当ではなく、既知の`codex-micro-layout-*` live registryから公式30 IDを解決する。
- 通常Paletteは`APPR`、`REJ`、`DEL`を含む公式30キーを同一grid、検索、カテゴリへ表示する。
- Danger専用画面、危険分類、長押し、confirmation nonceは使用しない。
- snapshotは`keycapCapabilities`としてID、action型、ready／unsupportedを返し、後方互換の`danger`は全30件`false`とする。
- MICは明示的なdown／upでPush-to-talkを開始／停止し、切断時はstopをbest-effort送信する。
- APPR／REJ／DELは他の通常Keycapと同じtap commandとし、現在画面で不成立なら具体的理由を表示する。
- DELはUIと文書で`アーカイブ`と表現し、削除と誤認させない。

対象版はAndroid 0.2.6（versionCode 8）、Relay 0.2.8、Protocol 2とする。

### 1.2 ローカル配置（文書版2.4）

- ローカルの正本ルートは`C:\Projects\codex-micro-android`だけとする。
- Androidはリポジトリ直下、Relayは同一ルート内の`relay`へ配置する。
- `C:\Projects\codex-micro-relay`のような別プロジェクトルートを作成・維持しない。
- Relayの`.git`、`node_modules`、`dist`、`release`は統合元から持ち込まず、必要時に同一ルート内で再生成する。
- 公開先も`omusubiman5/deck-for-codex-android`だけとし、Relay単体の別GitHubリポジトリを作成・維持しない。

## 2. 対象環境と製品構成

### 対象

- Android 8.0/API 26以上
- Windows 10以降
- macOS
- 同一LAN上のNearby WSS接続
- Windows／Macの登録、対象切替、所有ホストへのAgentルーティング
- 最大8件のPCプロファイル

### 構成

```text
Android Codex Micro Mobile
  ├─ Control
  ├─ Palette
  ├─ Usage
  └─ Hosts / Settings / Pairing
             │ authenticated WSS
             ▼
Windows / macOS Codex Micro Relay PC app
  ├─ PCアプリ内QRペアリング
  ├─ Relay／端末／LAN／ログ管理
  └─ localhost CDP bridge
             │ 127.0.0.1 only
             ▼
Codex Desktop native Micro state / commands
```

AndroidとRelayは別Gitプロジェクトにする。RelayコアはWindows／macOSで共通化し、OS別にパッケージする。

### 対象外

- M18、VSD Craft、Stream Deckの物理デバイス制御
- BLE HID、USB HID、仮想HID
- OBS、Spotify等の汎用マクロ
- 公開インターネットRelay
- Play Store、Microsoft Store、Mac App Store公開

物理デバイス制御が対象外でも、それらが呼び出しているCodex Micro機能は対象外にしない。

## 3. Android共通UI仕様

### 3.1 主要ナビゲーション

画面下部へ常時4項目を置く。

| 順 | 表示 | 画面 |
|---:|---|---|
| 1 | Control | メインダッシュボード／制御 |
| 2 | Palette | 公式30キーを同一画面で表示・操作 |
| 3 | Usage | 利用状況／制限／Reset |
| 4 | Hosts | ホスト／設定／状態詳細 |

戻る操作では現在の詳細／bottom sheetを閉じ、主要画面間の履歴を不必要に積まない。接続中のホストと押下状態は画面遷移で失わない。

### 3.2 状態色

| 色 | 意味 | テキスト／アイコン併記 |
|---|---|---|
| 緑 | ready／正常／実行可能 | 必須 |
| 紫 | working／動作中 | 必須 |
| 青 | selected／選択中 | 必須 |
| オレンジ | approval／入力待ち | 必須 |
| 赤 | error／危険／失敗 | 必須 |
| 灰 | off／unavailable／stale | 必須 |

色だけで状態を伝えない。色、アイコン、状態チップ、短いテキストを併用する。文字と背景はWCAG AA相当のコントラストを確保する。

### 3.3 動的ボタン

本書で`○`を付けたボタンは、snapshot、registry、接続状態、対象ホストに応じてタイトル、状態、色、有効状態または送信先が変わる。

- 接続喪失時は押下中の全ボタンをローカルで解除する。
- 再接続後に古いkey-upや実行要求を再送しない。
- stale snapshotの値は閲覧できるが、操作は無効にして理由を表示する。
- 44dp未満のタップ領域を作らない。
- 320dp幅と大きなフォント設定でも横切れさせず、必要箇所を縦スクロールする。

### 3.4 共通アプリメニュー

Control左上のハンバーガーから次へ移動できる。

- Control
- 公式Keycap Palette
- Usage／制限
- Hosts／設定
- PCプロファイル管理
- QRをスキャンしてPCを追加
- 通知設定
- 診断／ログ
- このアプリについて／version／ライセンス

主要4画面は下部ナビから、補助機能はこのメニューまたは各画面の文脈メニューから到達させる。

## 4. 画面1: Control――メインダッシュボード／制御

### 4.1 App bar／接続ヘッダー

- ハンバーガー
- `Codex Micro`タイトル
- ○ 接続状態チップ: ready／connecting／degraded／offline／stale
- ○ Windows／Macターゲット切替
- 最終snapshot時刻
- ○ freshness: fresh秒数／stale
- 再読込

ターゲット切替はその後のMicro操作の送信先を変更する。Agentはカードに紐づく所有ホストへ送るため、全体ターゲットより所有情報を優先する。

### 4.2 AGENTS（6動的slot）

2列×3行のカードとして表示する。

各カードの内容:

- slot番号 1～6
- ○ プロジェクト名
- ○ ネイティブCodex Micro `title`
- ○ statusチップ
- ○ context使用率とprogress bar
- ○ 所有／実行ホスト Windows／Mac
- ○ selectedマーク

指定UI画像の`Planner`、`Builder`、`Observer`等は固定role名ではなく表示例である。実際はプロジェクト名を主表示する。プロジェクト名を取得できない場合はネイティブ`title`を主表示し、詳細画面に`project unavailable`を表示する。

操作:

- タップ: Agentのkey-down／key-upを送り、そのtaskを開く。
- 長押し: Agent詳細bottom sheetを開く。project、native title、thread ID短縮表示、status、context、所有ホスト、最終activityを確認できる。
- 空slot: 無効表示。タップしてもcommandを送らない。

状態は最低でもoff、idle、working、unread、approval、errorを区別する。

### 4.3 MICRO ACTIONS（6動的slot）

画像どおり横6列で表示し、各ボタンへUI上の連番、現在のkeycap名、状態を表示する。

| UI表示 | native slot | 既定keycap |
|---|---|---|
| ○ ACT01 | `ACT06` | `FAST` |
| ○ ACT02 | `ACT07` | `APPR` |
| ○ ACT03 | `ACT08` | `REJ` |
| ○ ACT04 | `ACT09` | `SPLIT` |
| ○ ACT05 | `ACT10_ACT11` | `MIC` |
| ○ ACT06 | `ACT12` | `CODEX` |

`ACT01～06`は画面上の連番であり、Relay protocolではnative slot IDを必ず使う。Codex側レイアウト変更時は、表示名、色、アイコン、有効状態、実行commandを再起動なしで同期する。

`APPR`／`REJ`／`DEL`が動的slotへ割り当てられた場合も、他のslotと同じgeneric `action` down／upを送る。

### 4.4 JOYSTICK / REASONING

画像どおり2枚の大きなカードを置く。

Joystickカード:

- 現在の操作モードを表示
- タップで4方向bottom sheetを開く
- Up=Plan、Right=Forward、Down=Sidebar、Left=Back
- 押下中はkey-down、離した時はkey-upを送る

Reasoningカード:

- 現在のreasoning mode／effortを表示
- タップでEncoder Press、LESS、MOREを含むbottom sheetを開く
- LESS／MOREは押下直後に1回、500ms後から300ms間隔で長押し反復
- 切断、画面離脱、pointer cancel時は反復を停止

## 5. 画面2: 公式Palette 30

### 5.1 App bar／検索／カテゴリ

- 戻る
- `公式 Keycap 30`
- 検索
- カテゴリタブ: すべて／アクション／ナビゲーション／開発／その他
- 件数表示

検索対象はkeycap ID、表示名、日本語名、説明。カテゴリと検索語は同時適用する。

### 5.2 公式Palette 30キー

Paletteのグリッドには次の30キーを表示する。

| 行 | Keycap |
|---:|---|
| 1 | `FAST`、`APPR`、`REJ`、`SPLIT`、`MIC` |
| 2 | `CODEX`、`BUG`、`OAI`、`TERM`、`DWN` |
| 3 | `DEL`、`NEW`、`NAV`、`MAGIC`、`DIFF` |
| 4 | `PLAY`、`GIT`、`BRCH`、`MRG`、`PR` |
| 5 | `PAINT`、`LAB`、`PARTY`、`TIME`、`MIND+` |
| 6 | `MIND-`、`SETUP`、`FOLD`、`UPL`、`APPS` |

各キーは`○`動的ボタンとし、Codex Desktopのlive keycap registryからcapabilityを解決する。現在画面でcommandが成立しない場合も恒久無効化せず、具体的な最終結果を表示する。

操作:

- タップ: 選択状態と下部のキー情報を更新し、通常キーを実行する。
- MIC: 明示downで開始し、release／cancel／画面離脱／切断でstopする。
- 実行不能時: 未接続、stale、registry未解決、現在状態不適合を区別して表示する。

### 5.3 選択中キー情報

- keycap IDと日本語名
- カテゴリ
- 説明／用途
- Windows／Mac互換性
- availability／無効理由
- 操作方式（MICだけ押下中）
- 最終command結果

色カテゴリの凡例をグリッド下へ表示するが、色だけで分類しない。

### 5.4 APPR／REJ／DELの意味

- `APPR`: 現在の承認要求を承認する。承認要求がない場合は不成立理由を表示する。
- `REJ`: 現在の承認要求を拒否する。危険操作には分類しない。
- `DEL`: 現在のtaskをarchiveする。ファイル、repository、Git履歴の削除ではない。

3キーとも通常tapで実行し、Danger画面、長押し、confirmation nonceは設けない。

## 6. 画面3: Usage――利用状況／制限／Reset

### 6.1 使用量モード

- ○ 自動（推奨）
- ○ 5時間制限
- ○ 週間制限
- ○ その他／カスタム

自動は利用可能なwindowから現在重要なwindowを選ぶ。5時間windowがなければ週次へfallbackする。

### 6.2 全体サマリー

- 現在のmodeと期間
- 使用率／残量progress
- 総リクエスト
- 使用量／token／推定時間
- usage取得元ホスト
- 更新時刻とfresh／stale

### 6.3 Windowカード

snapshotに含まれる全windowをカードとして表示する。

- kind: 5-hour／weekly／other
- usedPercent／remainingPercent
- windowDurationMins
- resetsAt
- host／取得元
- observedAt／freshness

window数を固定せず、`other`が複数ある場合もすべて表示する。

### 6.4 Rate Limit Reset

- ○ availability／applicability
- 使用可能credit数
- 適用対象ホスト
- 1.2秒長押しprogress
- 実行直前の再検証
- request ID単位の結果
- 成功後のusage再取得

通常タップ、1.2秒未満、利用不可、適用不可、offline、staleでは実行しない。失敗理由を赤いbannerと診断ログへ残す。

Usage右上メニュー:

- 最新状態へ更新
- 自動更新の有効／無効
- usage取得元を表示
- 診断情報をコピー

## 7. 画面4: Hosts――ホスト／設定／状態詳細

### 7.1 ターゲット切替

- ○ Windows
- ○ Mac

現在ターゲットを青で表示し、切替時に押下中状態を解除する。

### 7.2 ホスト／プロファイル一覧

最大8件を表示する。各行にはOS、PC表示名、状態、現在ターゲット、行メニューを置く。

行タップで選択ホスト詳細を更新する。行メニュー:

- ターゲットに設定
- 接続／再接続
- 接続テスト
- 編集
- QRで再ペアリング
- 資格情報を失効
- 削除

資格情報失効と削除は確認dialogを必須にする。

### 7.3 選択中ホストのセッション情報

- host name／host ID短縮表示
- 接続状態とhealth reason
- 最終snapshot時刻／freshness
- local address／Relay endpoint
- Agent source
- protocol version／Codex version／Relay version
- 実行環境／host sessions

health reasonは`awaiting-snapshot`、`native-signals-unavailable`、`snapshot-stale`、`relay-disconnected`、`local-bridge-unavailable`を区別する。

### 7.4 ホスト接続の編集

- profile表示名
- endpoint
- host ID
- certificate fingerprint短縮表示
- mode: nearby／remote
- 保存

token全文と秘密鍵は表示しない。同じhost IDでfingerprintが変わった場合は自動更新しない。

### 7.5 設定とアクション

- テーマ切替: System／Light／Dark
- 通知設定
- 再読み込み（10秒timeout）
- 接続テスト
- Agent source表示: pinned／recent／priority／custom
- Lighting auto-off表示／対応可能な場合は変更
- Usage詳細
- 接続詳細
- PC一覧・追加・削除

Hosts右上メニュー:

- PCを追加
- QRスキャナーを開く
- Relay／protocol診断
- ログを表示
- 診断情報をコピー
- アプリ設定

## 8. ペアリングとPCプロファイル

### Android

- QRスキャン、`codexdeck://pair` deep link
- 最大8プロファイル
- PC選択、編集、削除、再ペアリング
- 同一host ID＋fingerprintは安全なendpoint更新
- 同一host ID＋異なるfingerprintは自動更新禁止
- tokenはプロファイル別Android Keystore AES-GCM鍵で暗号化
- Bonjour／NSD `_codexdeck._tcp`探索

### Windows／macOS PCアプリ

- 概要: Relay、Codex bridge、LAN、接続Android数
- ペアリング: アプリ内QR、PC名、OS、LAN、port、fingerprint、token警告
- 接続端末: 認証済みAndroid、最終接続、切断、資格情報失効
- 設定: private LAN interface、自動起動、token／証明書更新、Relay無効化
- ログ: bounded log、最新へ移動、診断情報コピー

QR表示、再表示、認証情報更新、無効化はPCアプリ内で完結させる。QRを外部SVGファイルとして開かせない。PCアプリを閉じてもRelayは常駐し、タスクトレイ／メニューバーから再表示できる。

## 9. データモデル

```text
AgentSlot
  slot: 0..5
  threadKey: String?
  nativeTitle: String?
  projectName: String?       // Mobile追加表示
  status: String
  selected: Boolean
  activityAt: Instant?
  ownerHostId: String?
  contextUsedPercent: Double?
```

`nativeTitle`はCodex Micro slotの`title`をそのまま保持する。`projectName`と混同、上書きしない。取得元がない場合はnullを許容する。

Host snapshotはhost identity／versions、6 Agent、active thread、6 action layout、analog stick、Agent source、lighting、theme、usage／credits、health、host sessions、observedAtを持つ。

Pairing profileはid、display name、host ID、platform、endpoint、mode、fingerprint、encrypted token reference、selected、last connectedを持つ。

## 10. Relay protocolとcommand allowlist

| command | 必須値 |
|---|---|
| `agent` | slot、threadKey、act |
| `action` | native slot ID、act |
| `joystick` | up／right／down／left、distance |
| `encoder` | act |
| `reasoning` | increase／decrease |
| `keycap` | 公式30 ID。MICはact必須、それ以外はactなし |
| `new-task` | なし |
| `environment-action` | 1／2／3 |
| `host-target` | stable host ID |
| `rate-limit-reset` | request ID |

shell、filesystem、任意URL、任意CDP evaluate、任意command IDを許可しない。payloadは64KiB以下、認証は接続後3秒以内、command timeoutは10秒とする。

CDPは`127.0.0.1`だけへbindする。Nearby Relayは選択したprivate LAN addressだけへbindし、wildcard／public IPを拒否する。P-256証明書、256-bit token、certificate pinningを使用する。

## 11. Android実装フェーズ

### Phase A: UI foundation

- 指定UIの色、余白、カード、chip、bottom navigationをtheme化
- 4主要画面のnavigation shell
- accessibility semantics、320dp／大フォント／dark mode検証

### Phase B: Snapshot完全化

- native titleとproject nameを分離
- status、context、owner host、activity
- layout、analog stick、Agent source、lighting、theme
- usage全window、credits、health、host sessions

### Phase C: Control

- target／freshness／再読込
- Agent 6カード、6動的Micro Action
- Joystick 4方向、Encoder／Reasoning bottom sheet

### Phase D: Palette

- 同一Paletteの公式30キー
- 検索、カテゴリ、選択詳細
- capability、MIC down/up、30キー通常command、動的Action

### Phase E: Usage

- 4 mode、summary、全window
- freshness／source host
- Rate Limit Reset 1.2秒長押し

### Phase F: Hosts／Pairing／Settings

- 最大8プロファイル、QR、deep link、NSD
- target切替、一覧、詳細、行メニュー
- theme、通知、reload、接続テスト、診断

各Phaseは対応するunit／UI testとPixel画面確認が通るまで完了にしない。

## 12. Relay／PCアプリ実装フェーズ

Relay RuntimeはCodex bridge、host identity、snapshot poll、typed command、pairing config、authenticated WSS、graceful shutdown、bounded logだけを所有し、物理デバイスSDKへ依存しない。

Windows PC管理UIを先行し、アプリ内QR、Relay／bridge／Bonjour状態、接続Android管理、LAN選択、credential rotate、自動起動、ログ／診断、タスクトレイ常駐を実装する。

macOSは同じ情報設計で実装し、Mac実機試験までは「実機未検証」と表示する。

## 13. テスト計画

### Android unit／UI test

- 4画面navigation
- Agent 6状態、project／native title fallback
- UI ACT01～06とnative slot IDの対応
- Joystick 4方向、Encoder、Reasoning長押し／cancel
- 通常30、検索、カテゴリ、無効理由、MIC down/up
- Usage 4 mode、複数other、reset長押し
- Host一覧、状態、メニュー、最大8件
- QR、private IP、fingerprint、protocol mismatch
- 320dp、大フォント、TalkBack、色以外の状態識別

### Relay unit／integration test

- snapshot全フィールド、全typed command、公式30 allowlist
- auth、timeout、64KiB上限、bind拒否
- rate reset再検証
- host routing、ownership、target切替
- degraded／stale／offline／recovery

### Pixel＋Windows実機受入

1. Windows PCアプリでRelay／bridge／LAN状態を確認する。
2. PCアプリ内QRをPixelでスキャンする。
3. USBを外し、`adb reverse --list`が空の状態でWSS接続を確認する。
4. Controlのtarget、ready、freshnessを確認する。
5. Agent 6カードのproject、native title、status、context、host、selectedを確認する。
6. Agent 6枠を操作する。
7. 6動的Micro Actionのlayout変更追従を確認する。
8. Joystick 4方向を操作する。
9. Encoder Press、Reasoning増減、長押し反復を確認する。
10. Paletteの検索、カテゴリ、30キー全件表示とenabled状態を確認する。
11. APPR／REJ／DELの通常tap command、MIC down/up、旧danger-arm／nonce要求拒否を確認する。
12. Usageの自動、5時間、週次、otherを確認する。
13. Resetが1.2秒未満で実行されないことを確認する。
14. Resetのavailable／applicable／成功／失敗を確認する。
15. Windows／Mac targetとAgent所有ホストへのroutingを確認する。
16. Hostsの追加、編集、再接続、再ペアリング、失効、削除を確認する。
17. Wi-Fi切断、stale、再接続、証明書不一致を確認する。
18. Android／PC再起動後の自動復旧を確認する。
19. PCアプリ内だけでQR再表示、rotate、無効化を完了する。
20. アンインストール後に常駐と秘密情報が残らないことを確認する。
21. 指定UI画像とAndroid実機4画面を並べて視覚レビューする。
22. TalkBack、大フォント、320dp幅で全機能へ到達できることを確認する。

## 14. リリース判定

Windows検証済みとする条件:

- Android test／Lint／release build成功
- Relay check／test／package audit成功
- Pixel＋Windows実機受入22項目成功
- Android実機4画面とWindows PC管理UIのキャプチャを保存
- APK／Windows ZIPのSHA-256、SBOM、ライセンス一覧を生成

macOSはCI、launcher、LaunchAgent、package監査を通しても、Mac実機受入まで「macOS対応・実機未検証」とする。

## 15. 成果物

- Android署名済みAPK
- Windows／macOS Relay PCアプリ、installer、uninstaller
- Android実機4主要画面キャプチャ
- Windows PC管理UIキャプチャ
- Windows実機受入報告、macOS CI／package報告
- 原因調査／対応報告
- SHA-256 checksums
- LICENSE、THIRD_PARTY_NOTICES、SBOM
- インストール、ペアリング、更新、アンインストール手順

## 16. 旧版策定時の差分（履歴）

2026-08-19時点のAndroidコードは、6 Agent、6動的slot、Joystick 3方向、Reasoning単発、Usageの2数値、PCプロファイル／QR／WSSの一部までである。次が未実装または不足している。

- 指定UIの4画面構成とbottom navigation
- Agent project name、詳細bottom sheet
- Joystick Down
- Encoder Press、Reasoning長押し
- 公式Palette 30、検索、カテゴリ、詳細
- Usage mode、全window、Rate Limit Reset
- Hostsの状態詳細、行メニュー、各種設定
- Windows PC管理UIとアプリ内QR
- 全typed commandと実機受入22項目

従来の自動テスト成功は既存部分の回帰確認に限られ、本計画の完成を意味しない。
