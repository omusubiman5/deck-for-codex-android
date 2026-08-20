# Android Codex Micro MVP: OSS転用調査

調査日: 2026-08-19

## 結論

Androidアプリは`codex-deck-m18`から分離した独立Gitプロジェクトとし、Mac／Windows共通のCodex Deck Relayを唯一の必須通信経路にする。
AndroidをCodex Micro互換BLE HIDとして見せる[`Patchself/CodexMacro`](https://github.com/Patchself/CodexMacro)はUI・ジェスチャ・テスト設計の転用候補だが、macOS優先でWindows非対応のため、アプリ基盤や必須トランスポートには採用しない。

接続構成は次の1系統に固定する。

```text
Android app (independent project)
        |
        +-- pinned WSS / Relay protocol 1
                  |
                  +-- Codex Deck Relay / Windows
                  +-- Codex Deck Relay / macOS
```

BLE DirectはMac限定になるためMVP対象外とする。

## 候補評価

| 候補 | ライセンス | 転用できる部分 | 適合度 | 判断 |
|---|---|---|---:|---|
| [Patchself/CodexMacro](https://github.com/Patchself/CodexMacro) | LGPL-3.0 | Compose UI、ジェスチャ、状態色、テスト | 8/10 | UI部品のみ選択的に転用 |
| [Macro Deck Client App](https://github.com/Macro-Deck-App/Macro-Deck-Client-App) | MIT | QR UX、Angular/Ionic画面、WebSocket接続の構造 | 7/10 | UX参考またはQR部分のみ |
| [OpenMacropadKMP](https://github.com/Kapcode/OpenMacropadKMP) | GPL-3.0 | KMP構成、Ktor WebSocket、自動探索 | 5/10 | GPL境界と機能過多のため不採用 |
| [Bitfocus Companion](https://github.com/bitfocus/companion) | MIT | Web Buttons、押下／解放、状態フィードバック | 4/10 | サーバー全体がMVPには過剰 |
| [Deckboard](https://github.com/rivafarabi/deckboard) | リポジトリ上で未確認 | QR接続の製品UX | 2/10 | 公開リポジトリはREADME／静的資料中心。コード転用元にしない |

### 添付調査メモからの重要な訂正

- Deckboardは利用製品としては有力だが、確認できたGitHubリポジトリにアプリ／サーバーの実装本体と明示的なLICENSEがない。OSS転用候補として扱わない。
- OpenMacropadKMPはLinux専用ではなく、現行READMEではWindows、macOS、Linux向けデスクトップ配布を案内している。
- OpenMacropadKMPのライセンスは「未確認」ではなくGPL-3.0。
- CodexMacroは実在し、Android 9以上、BLE Peripheral対応端末、macOS版ChatGPT Desktopを対象にする実装である。ライセンスはLGPL-3.0。
- Macro Deckのモバイルクライアント本体は別リポジトリで公開されており、Angular + Ionic、MITライセンスである。

## CodexMacroから転用する単位

優先して取り込む:

- `ui/MicroBoard.kt`
- `ui/ControllerLayouts.kt`
- `ui/components/HardwareKeys.kt`
- `ui/components/JoystickControl.kt`
- `ui/components/DialControl.kt`
- `ui/theme/*`
- Agent状態色・キー押下／解放・ダイヤル操作のテスト

MVPでは取り込まない:

- `bluetooth/CodexMicroService.kt`
- `protocol/CodexProtocol.kt`
- `protocol/CodexRpcEngine.kt`
- HID report descriptorとフレーミング資料

そのまま持ち込まない:

- Layer 2〜6の汎用キーボードマクロ
- 起動時自動復帰
- Bluetooth名を`Codex Micro`へ一時変更する互換設定
- Android SDK 37への固定

これらはMVPの目的外、または端末差・OS更新による不安定性を増やす。

## 採用するOSSライブラリ

| 用途 | ライブラリ | ライセンス | 方針 |
|---|---|---|---|
| UI | AndroidX Jetpack Compose | Apache-2.0 | CodexMacro由来UIの土台 |
| WebSocket/TLS | [OkHttp](https://github.com/lysine-dev/okhttp) | Apache-2.0 | 現MVPで採用済み。再接続と証明書ピンニングを担当 |
| JSON | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache-2.0 | `org.json`を型付きモデルへ置換 |
| QR読取 | [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) | Apache-2.0 | アプリ内ペアリングスキャン |
| 鍵保存 | Android Keystore | Android platform | トークンをAES-GCMで保存。外部ライブラリ不要 |
| LAN探索 | Android `NsdManager` | Android platform | Bonjour `_codexdeck._tcp`探索。外部依存不要 |

## ライセンス境界

CodexMacroのソースをコピー・改変するAndroidアプリ部分はLGPL-3.0として扱い、次を配布物へ含める。

- LGPL-3.0本文
- CodexMacroの著作権・変更箇所・取得元
- CodexMacroが参照する`imliubo/codex-micro-4-core2`のMIT notice
- 対応するAndroidソースと再ビルド手順

リポジトリ全体を一律LGPLへ変更せず、`android/`を独立したLGPL配布単位にする。既存のTypeScript／M18部分は現在のMIT等のライセンスを維持する。実際の配布前にはライセンス専門家による確認が望ましい。

LGPLを避けたい場合は、CodexMacroのコードをコピーせず、公開されている挙動だけを参考にCompose UIを独自実装する。その場合の直接依存はApache-2.0／MITのライブラリだけに限定できるが、OSS転用量は減り実装工数は増える。

## MVP範囲

必須:

1. QRペアリング
2. 証明書ピンニング付きWSS接続
3. 6 Agent状態表示と押下／解放
4. Fast、Approve、Decline、Fork、Mic、Send
5. Plan、Back、Forward
6. Reasoning増減
7. 接続状態とエラー表示
8. トークンのKeystore保存

実験機能:

- なし。MacとWindowsで機能差を作らない

MVP外:

- 汎用マクロ編集
- 複数レイヤー
- OBSやSpotify統合
- Companion／Macro Deckプラグイン互換
- バックグラウンド常時接続、通知、ウィジェット

## 実装順

1. AndroidアプリをComposeへ移行する。
2. `ControllerTransport`インターフェースを作り、現行OkHttpリレーを`RelayTransport`へ分離する。
3. CodexMacroのUIコンポーネントをLGPL notice付きで移植し、Relayのsnapshotへ接続する。
4. ZXingでアプリ内QR読取を追加する。
5. `NsdManager`でNearbyホストを発見し、QRに含まれるhost IDと証明書fingerprintが一致する場合だけ接続先更新を許可する。
6. WindowsとmacOSの両方で同じ適合テストを通す。

## Go / No-Go

**Go。** 独立Androidプロジェクトとして、Apache-2.0／MIT系ライブラリとCodex Deck Relayを使う。CodexMacroはUI部品を選択的に転用する場合に限りLGPL境界を設ける。Deckboard、Companion、OpenMacropadKMPをアプリ基盤として丸ごと転用する案は採らない。
