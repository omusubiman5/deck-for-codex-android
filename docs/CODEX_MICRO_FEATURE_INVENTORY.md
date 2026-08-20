# Codex Micro 全機能一覧・実装対象表

作成日: 2026-08-19
文書版: 1.1（2026-08-20現行）

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 廃止 | 通常27／Danger 3として分類 |
| 1.1 | 2026-08-20 | 現行 | 危険分類を撤回し、30キーを同一Paletteへ統合 |

## 1. この文書の位置付け

本製品の操作機能は、`codex-deck-m18`がCodex Desktopの実行時状態から取得・実行しているCodex Micro機能を基準にする。Android版で都合よく機能を6ボタンへ縮小しない。

- 本文書にある機能は、明記したハードウェア固有機能を除き、すべて実装・試験対象とする。
- `○`は、Codex Desktopのsnapshot、レイアウト、公式keycap registry、ホスト状態などに応じて、表示名、表示内容、有効状態または実行対象をアプリが動的に変えるボタンを示す。
- 「現状」は2026-08-20のAndroid 0.2.6／Relay 0.2.8コードと試験結果を再評価した値である。
- `未実装`と`一部`は受入試験不合格であり、完成扱いにしない。

## 2. 機能群一覧

| ID | 機能群 | 必須内容 | 現状 |
|---|---|---|---|
| CM-01 | Agent 1～6 | 6枠、選択、押下／解放、所有ホストへルーティング | 実装済み |
| CM-02 | Agentライブ表示 | ネイティブMicroの`title`、状態、選択、context使用率、ホスト所有、最終activity | 実装済み |
| CM-02A | Mobile追加表示 | プロジェクト名を別途取得し、ネイティブ`title`と区別して併記 | 一部（取得不能時fallback） |
| CM-03 | Agentソース | `pinned`、`recent`、`priority`、`custom`の表示と同期 | 表示実装済み |
| CM-04 | Microアクション | `ACT06`、`ACT07`、`ACT08`、`ACT09`、`ACT10_ACT11`、`ACT12`の動的割当 | 実装済み |
| CM-05 | Joystick | 上、右、下、左の押下／解放 | 実装済み |
| CM-06 | Encoder | Reasoningエンコーダの押下／解放 | 実装済み |
| CM-07 | Reasoning調整 | effort増加／減少、長押し反復 | 実装済み |
| CM-08 | 公式Keycap | 同一Palette 30、registry capability、MIC down/up | 実装済み・全件実機試験未完了 |
| CM-09 | 新規タスク | Codexのネイティブ新規タスク作成 | 実装済み・隔離実機試験未完了 |
| CM-10 | Usage Limit | 自動、5時間、週次、other、使用率、残量、期間、reset時刻 | 実装済み |
| CM-11 | Usage Overview | 複数usage windowの同時表示 | 実装済み |
| CM-12 | Rate Limit Reset | availability／applicability表示、1.2秒長押し確認、結果更新 | 実装済み・正規実行未検証 |
| CM-13 | テーマ | Codexのlight／dark状態へ追従 | 一部（表示、変更保存未実装） |
| CM-14 | Lighting | `lightingAutoOff`設定値の表示・同期 | 一部（表示のみ） |
| CM-15 | ホスト状態 | ready／connecting／degraded／offlineと理由、stale | 実装済み |
| CM-16 | 複数ホスト | Windows／Mac対象切替、Agent所有ホスト、host session、last-known表示 | 一部（Mac実機未検証） |
| CM-17 | 環境アクション | `environmentAction1`～`3`。Codexに登録がある場合だけ実行 | 実装済み・隔離実機試験未完了 |
| CM-18 | 動的レイアウト | Codex側のMicro layout変更を再起動なしで画面へ反映 | 実装済み |

## 3. Android操作ボタン全一覧

### 3.1 Agent

- ○ Agent 1
- ○ Agent 2
- ○ Agent 3
- ○ Agent 4
- ○ Agent 5
- ○ Agent 6

ネイティブCodex Microの6枠が提供する表示文字列は`title`である。既存のCodex Deck描画は、この`title`を1～2行へ折り返してボタン中央に表示する。プロジェクト名はネイティブslotの標準フィールドではない。

スマホ版では追加情報としてプロジェクト名を取得し、ネイティブ`title`と混同せず併記する。

Agentボタンの表示優先順位は次のとおりとする。

1. 1行目・主表示: プロジェクト名（Mobile追加表示）
2. 2行目・副表示: ネイティブMicroの`title`
3. 補助表示: 状態、context使用率、所有ホスト

`Agent 1`や`AGENTS`などのセクション名をボタンの主表示には使わない。プロジェクト名を取得できない場合も、ネイティブ互換表示として`title`は必ず表示する。

### 3.2 Codex Micro動的アクションスロット

- ○ `ACT06`（既定: `FAST`）
- ○ `ACT07`（既定: `APPR`）
- ○ `ACT08`（既定: `REJ`）
- ○ `ACT09`（既定: `SPLIT`）
- ○ `ACT10_ACT11`（既定: `MIC`）
- ○ `ACT12`（既定: `CODEX`）

動的slotへ`APPR`／`REJ`／`DEL`が割り当てられた場合も、他のslotと同じdown／up操作を実行する。

ここは名称固定のマクロボタンではない。Codex側で割当が変われば、表示、アイコン、有効状態、実行内容を同じslotの最新snapshotへ追従させる。

### 3.3 Joystick／Reasoning

- Joystick Up（Plan）
- Joystick Right（Forward）
- Joystick Down（Sidebar）
- Joystick Left（Back）
- Reasoning Encoder Press
- Reasoning Decrease
- Reasoning Increase

Reasoning Decrease／Increaseは、押下直後に1回送り、500 ms後から300 ms間隔で長押し反復する。

### 3.4 公式Keycap 30種（同一Palette）

次のボタンは公式keycap registryの解決結果により有効状態が変わるため、すべて`○`対象とする。
次の30キーを同じPaletteのgrid、検索、カテゴリへ配置する。

| ○ | ID | 表示名 | 実行内容 |
|---|---|---|---|
| ○ | `FAST` | Fast Mode | Fast mode切替 |
| ○ | `APPR` | Approve | 現在の承認要求を承認 |
| ○ | `REJ` | Reject | 現在の承認要求を拒否 |
| ○ | `SPLIT` | Fork Chat | 現在のtaskを分岐 |
| ○ | `MIC` | Push-to-talk | 押下中だけ開始、release／cancel／切断でstop |
| ○ | `CODEX` | Codex / Submit | composerを送信 |
| ○ | `BUG` | Bug / Feedback | feedbackを開く |
| ○ | `OAI` | OpenAI Docs | OpenAI developer docsを開く |
| ○ | `TERM` | Terminal | Codex terminalを切替 |
| ○ | `DWN` | Copy Chat Markdown | 会話をMarkdownとしてコピー |
| ○ | `DEL` | Archive Chat | 現在のtaskをarchive |
| ○ | `NEW` | New Task | 新規taskを作成 |
| ○ | `NAV` | Browser | Codex browser tabを開く |
| ○ | `MAGIC` | Pin / Unpin Chat | pin状態を切替 |
| ○ | `DIFF` | Review | review表示を切替 |
| ○ | `PLAY` | Run Environment Action | 最初の環境アクションを実行 |
| ○ | `GIT` | Git Commit | native commit flowを開く |
| ○ | `BRCH` | Branch Review | branch reviewを開く |
| ○ | `MRG` | Merge Review | merge reviewを開く |
| ○ | `PR` | Create Pull Request | native pull-request flowを開く |
| ○ | `PAINT` | Add Photos | composerへ写真を追加 |
| ○ | `LAB` | Lab / Settings | lab/settingsを開く |
| ○ | `PARTY` | Side Chat | side chatを開く |
| ○ | `TIME` | Manage Tasks | task管理を開く |
| ○ | `MIND+` | Reasoning Up | reasoning effortを上げる |
| ○ | `MIND-` | Reasoning Down | reasoning effortを下げる |
| ○ | `SETUP` | Settings | Codex settingsを開く |
| ○ | `FOLD` | Open Folder | folderを開く |
| ○ | `UPL` | Add Files | composerへファイルを追加 |
| ○ | `APPS` | Skills | Codex Skillsを開く |

`APPR`、`REJ`、`DEL`を危険分類しない。`APPR`／`REJ`は承認要求がない場合、`DEL`はactive taskがない場合にnative handlerの具体的不成立理由を表示する。`DEL`の実体はtask archiveであり、ファイルやGit履歴を削除しない。現在画面で成立しないキーは恒久無効化しない。

### 3.5 Usage／リセット

- ○ Usage Limit mode（Automatic／5-hour／Weekly／Other）
- ○ Usage 5-hour detail
- ○ Usage Weekly detail
- ○ Usage Other window × snapshot件数
- ○ Usage Overview
- ○ Rate Limit Reset

Usageボタンは使用率だけでなく、残量、期間、reset時刻、取得元ホスト、stale／unavailableを表示する。Rate Limit Resetは利用可能かつ適用可能な場合だけ有効にし、1.2秒長押しを必須とする。

### 3.6 ホスト／設定

- ○ Windows／Mac操作対象切替
- ○ ホスト／PCプロファイル選択 × 登録数
- ○ Agent source表示（Pinned／Recent／Priority／Custom）
- ○ Theme表示（Light／Dark）
- ○ Lighting auto-off表示
- 公式Keycapパレットを開く
- Usage詳細を開く
- 接続詳細を開く
- PC一覧・追加・削除

## 4. 状態表示全一覧

### Agent状態

- `off`: 未割当
- `idle`: 待機
- `working`: 作業中
- `unread`: 完了未読
- `approval`: 承認／入力待ち
- `error`: エラー

### ホスト状態

- `connecting`
- `ready`
- `degraded`
- `offline`
- `stale`（受信時刻からクライアント側でも判定）

少なくとも次の理由を区別して表示する。

- `awaiting-snapshot`
- `native-signals-unavailable`
- `snapshot-stale`
- `relay-disconnected`
- `local-bridge-unavailable`

### snapshot表示項目

- host ID、host name、platform
- thread key、ネイティブtitle、Mobile追加のproject name、status、selected、activity時刻、所有host
- context使用率
- 6 action slotのkeycap ID／表示
- Agent source
- lighting auto-off
- theme
- usage windows、reset credits
- host sessions
- snapshot観測時刻、protocol version、health

## 5. Relay typed command全一覧

AndroidからRelayへ送れるcommandは次の型だけに限定する。

- `agent`: slot、threadKey、act
- `action`: `ACT06`～`ACT12`、act。割当Keycapによる特別拒否なし
- `joystick`: up／right／down／left、distance
- `encoder`: act
- `reasoning`: increase／decrease
- `keycap`: 公式30 ID。MICはact 0／1、それ以外はactなし
- `new-task`
- `environment-action`: 1／2／3
- `host-target`: Windows／Macまたはstable host ID
- `rate-limit-reset`

shell、filesystem、任意URL、任意CDP evaluate、任意command IDは受け付けない。

## 6. ハードウェア固有で移植しないもの

次はCodex Micro操作機能ではなくM18／Stream Deck固有のため、Androidボタンとしては対象外とする。

- M18 LCDへの画像転送
- VSD Craftのscene切替
- Stream Deck SDKのaction登録／property inspector
- USB HID制御、M18 firmware、物理キー番号

ただし、それらが呼び出していたCodex側機能は本書の一覧どおり移植対象である。

## 7. 完了判定

- CM-01～CM-18およびCM-02Aがすべて実装済みになっている。
- 公式Keycap 30 IDの各送信テストと、未解決時の無効表示テストがある。
- Android実機からJoystick 4方向、encoder、reasoning長押し、全Usage、reset長押しを確認する。
- Windows／Mac対象切替とAgent所有ホストへのルーティングをprotocol契約テストで確認する。
- 画面に存在する全ボタンが本書へ記載され、動的ボタンには`○`が付いている。
