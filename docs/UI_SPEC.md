# Codex Micro Mobile / PC UI 画面仕様

作成日: 2026-08-19

> 2026-08-19更新: Android UIは利用者指定の`ChatGPT Image 2026年8月19日 14_45_15.png`を視覚・画面構成の正本とする。Control／Palette／Usage／Hostsの画面、機能、メニュー、動的ボタン、操作方法は[実装計画書 v2](IMPLEMENTATION_PLAN.md)の3～10章を優先する。以下の旧ASCII wireframeは初期案であり、指定UIとの相違部分には使用しない。

## 1. 製品の二つの画面

MVPはAndroidアプリだけでは成立しない。次の二つを一組として提供する。

1. Android操作アプリ: PC登録、Agent／Command操作、Usage／接続状態表示
2. Windows PCアプリ: Relay管理、QRペアリング、LAN選択、接続Android管理、ログ

macOS PCアプリはWindows PCアプリと同じ情報設計を使う。

## 2. Android画面

### A-1 PC一覧／初回起動

```text
┌ Codex Micro Mobile ────────────┐
│ PCプロファイル                  │
│                                │
│ [ Windows  WORKSTATION   ● ]   │
│ [ Mac      STUDIO        ○ ]   │
│                                │
│ [ QRをスキャンして追加 ]        │
│ [ ペアリングリンクから追加 ]     │
└────────────────────────────────┘
```

### A-2 操作画面

```text
┌ CODEX MICRO ───────────────────┐
│ Windows · WORKSTATION · Nearby │
│ ● 接続済み  最終snapshot 14:12 │
│ 5時間 24% / 週 41%             │
│                                │
│ [Project A] [Project B]        │
│ [task名…  ] [task名…  ]        │
│ [Project C] [Project D]        │
│ [task名…  ] [task名…  ]        │
│ [Project E] [Project F]        │
│ [task名…  ] [task名…  ]        │
│                                │
│ [FAST] [承認] [拒否]            │
│ [分岐] [音声] [送信]            │
│                                │
│ [戻る] [Plan] [進む] [Sidebar] │
│ [Encoder] [LESS] [MORE]        │
│ [全Keycap] [Usage詳細]          │
│                                │
│ [PC一覧・追加・削除]             │
└────────────────────────────────┘
```

## 3. Windows PCアプリ画面

### W-1 概要

```text
┌ Codex Micro Relay ─────────────────────────────┐
│ 概要  ペアリング  接続端末  ログ  設定          │
├────────────────────────────────────────────────┤
│ Relay                 ● 稼働中                 │
│ ChatGPT Desktop       ● 接続済み               │
│ LAN自動検出            ● 利用可能               │
│ Android               1台接続                  │
│                                                │
│ LAN: Wi-Fi  192.168.100.6                      │
│ Port: 47653                                   │
│                                                │
│ [ペアリングQRを表示] [ChatGPTを開く]            │
└────────────────────────────────────────────────┘
```

### W-2 ペアリング

```text
┌ モバイルをペアリング ──────────────────────────┐
│ ┌──────────────┐  PC: WORKSTATION              │
│ │              │  OS: Windows                  │
│ │  QR CODE     │  LAN: Wi-Fi 192.168.100.6     │
│ │              │  Port: 47653                  │
│ └──────────────┘  Fingerprint: e5ff…242c       │
│                                                │
│ ⚠ QRには認証tokenが含まれます。共有禁止。       │
│                                                │
│ [認証情報を更新] [Relayを無効化] [閉じる]       │
└────────────────────────────────────────────────┘
```

QRはPCアプリ内で描画する。外部SVG、画像ビューア、ブラウザをペアリング導線に使用しない。

## 4. 状態表示

| 状態 | Android | Windows PCアプリ |
|---|---|---|
| 未設定 | PC追加画面 | Relay無効、設定開始 |
| QR表示中 | スキャナー | QR、警告、fingerprint |
| 認証中 | 接続中 | 認証要求受信 |
| ready | 操作画面 | Android接続済み |
| degraded | 制限あり | bridge詳細と復旧操作 |
| stale | stale警告 | 最終snapshot遅延 |
| 証明書不一致 | 常時エラー | token／証明書更新履歴 |
| offline | 再接続中 | Relay／LAN／Codex別の障害表示 |

## 5. UI受入条件

- Android実機とWindows実機の両方の画面キャプチャをレビューする。
- 初回インストールからQR表示までCLIやファイル操作を要求しない。
- QRの表示、再表示、更新、無効化がWindows PCアプリ内で完結する。
- AndroidはUSBを外した状態でWi-Fi WSS接続できる。
- 320dp幅のAndroidと1280×720のWindowsで主要操作が欠けない。

## 6. 全ボタン一覧

`○`は、Relay snapshot、登録PC、接続端末などの状態に応じて、アプリが表示名・有効状態・対象を動的に変えるボタンを表す。

### Android: PC一覧／初回画面

- ○ PCプロファイル選択 × 最大8台: PC名、OS、接続状態、最終接続時刻が動的
- 削除 × 各PCプロファイル
- QRをスキャンして追加
- ペアリングリンクから追加
- 証明書変更警告の「PC一覧へ」
- 削除確認の「キャンセル」
- 削除確認の「削除」

### Android: 操作画面

- ○ Agent 1
- ○ Agent 2
- ○ Agent 3
- ○ Agent 4
- ○ Agent 5
- ○ Agent 6
- ○ Command ACT06: 初期表示`FAST`
- ○ Command ACT07: 初期表示`承認`
- ○ Command ACT08: 初期表示`拒否`
- ○ Command ACT09: 初期表示`分岐`
- ○ Command ACT10_ACT11: 初期表示`音声`
- ○ Command ACT12: 初期表示`送信`
- 戻る
- Plan
- 進む
- Sidebar
- Reasoning Encoder Press
- Reasoning LESS
- Reasoning MORE
- 公式Keycapパレットを開く
- Usage詳細を開く
- ○ Windows／Mac操作対象切替
- PC一覧・追加・削除

ネイティブCodex MicroはAgent枠に`title`を表示する。スマホ版は追加情報としてプロジェクト名を上段、ネイティブ`title`を下段に併記し、状態、context使用率、選択状態、有効／無効をsnapshotで変える。`AGENTS`はグリッド外のセクション見出しであり、ボタン内には表示しない。CommandボタンはPC側layoutの`keycapId`で表示名が変わる。

公式Keycap 30種、Usage／Reset、環境アクション、ホスト／設定ボタンを含む完全な操作一覧は[Codex Micro全機能一覧・実装対象表](CODEX_MICRO_FEATURE_INVENTORY.md)を正本とする。公式Keycapボタンはregistryの解決結果で有効状態が変わるため、すべて`○`付き動的ボタンである。

### Android: QRスキャナー

- スキャナーを閉じる
- カメラ権限の許可／拒否はAndroid OSのボタンであり、アプリ固有ボタンには含めない

### Windows: 共通ナビゲーション

- 概要
- ペアリング
- 接続端末
- ログ
- 設定
- ウィンドウを閉じる
- タスクトレイから開く
- タスクトレイの終了

### Windows: 概要

- ペアリングQRを表示
- ChatGPTを開く
- Relayを開始
- Relayを停止
- 再接続

### Windows: ペアリング

- 認証情報を更新
- Relayを無効化
- 閉じる
- QRを再表示

### Windows: 接続端末

- ○ Android端末選択 × 接続／登録端末数
- ○ 切断 × 接続端末数: 接続中だけ有効
- ○ 資格情報を失効 × 登録端末数

### Windows: ログ

- 最新へ移動
- 診断情報をコピー
- ログフォルダを開く

### Windows: 設定

- ○ LAN interface選択肢 × 検出したprivate IPv4 interface数
- 自動起動を有効化／無効化
- 設定を保存
- token／証明書を更新
- Relay設定を初期化
