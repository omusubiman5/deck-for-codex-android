# Deck for Codex — Android

> **Unofficial experimental project.** This project is not created, supported, certified, or endorsed by OpenAI. Windows and Pixel 9a have been partially verified; macOS hardware is unverified and overall acceptance remains pending.

Android端末から、Windows／macOS上のCodex DesktopをCodex Micro Relay経由で操作する独立ネイティブクライアントです。OpenAI公式Codex MicroハードウェアそのものをAndroidへ移植するものではありません。

| コンポーネント | 対象版 | 検証状態 |
|---|---|---|
| Android | 0.2.5（versionCode 7） | Pixel 9aで部分検証、総合受入保留 |
| Windows Relay | 0.2.7 | Windows実機へ導入・接続検証済み |
| macOS Relay | 0.2.7 | package／launcher検査のみ、Mac実機未検証 |
| Protocol | 1 | Android／Windows間で検証 |

このGitプロジェクトは`codex-deck-m18`から独立しており、M18／Stream Deckの制御コードを含みません。

## 機能

- Control／Palette／Usage／Hostsの4画面
- Codex Microの6 Agent表示とtask切替
- 6つの動的Micro Action、Joystick 4方向、Encoder、Reasoning
- 公式Keycap 30機能：通常Palette 27キー＋Danger専用画面3キー
- Usage window表示とRate Limit Reset
- Windows／Macプロファイルを最大8件保存・切替
- PCアプリ内QR、Androidアプリ内カメラ、Nearby WSS接続
- 証明書SHA-256 pinningとAndroid Keystoreによるtoken暗号化
- 接続、stale、protocol不整合、実行結果の画面表示

## 使用条件

操作可能になるには、次の条件がすべて必要です。

1. Codex Micro Relay 0.2.7がPCへインストールされ、LAN WSS portで待受中である。既定／現行実測portは`47653`で、設定変更時はQR endpointのportに従う。
2. Codex Desktopが起動し、RelayのローカルbridgeがCodexへ接続している。
3. AndroidがPCのRelay address／portへIP到達できる。
4. QRで登録したtokenと証明書fingerprintが現在のRelayと一致する。
5. Androidの接続表示が`ready`で、snapshotが`fresh`である。
6. 対象Keycapがlive registryで`ready`として解決され、現在のCodex画面で実行条件を満たす。

AndroidとPCが同一L2セグメントであること自体は必須ではありません。別VLAN／別subnetでも、AndroidからPCのprivate addressとRelay portへL3 routingされ、ACL／Windows Firewall／無線client isolationで遮断されていなければ接続できます。ただしBonjourによる自動発見は通常subnetを越えないため、QRに含まれるendpointへ直接接続します。

USBはAPK導入、ADB試験、ログ／UI dump取得にだけ使用します。製品通信はWi-Fi／LAN WSSであり、USBを挿したままにする必要はありません。`adb reverse`にも依存しません。

## 通常Palette：27キー

通常Paletteには次の27キーだけを表示します。

| 行 | Keycap |
|---:|---|
| 1 | `FAST`、`SPLIT`、`MIC`、`CODEX`、`BUG` |
| 2 | `OAI`、`TERM`、`DWN`、`NEW`、`NAV` |
| 3 | `MAGIC`、`DIFF`、`PLAY`、`GIT`、`BRCH` |
| 4 | `MRG`、`PR`、`PAINT`、`LAB`、`PARTY` |
| 5 | `TIME`、`MIND+`、`MIND-`、`SETUP`、`FOLD` |
| 6 | `UPL`、`APPS` |

`APPR`、`REJ`、`DEL`は通常Paletteのgrid、検索結果、カテゴリ件数へ出しません。Relayの危険分類とAndroid内蔵定義が一致しない場合も実行できません。

通常キーがlive registryで解決済みでも、現在のCodex画面、選択task、repository、composer、承認状態などの条件により成立しないことがあります。この場合はキーを恒久無効化せず、最終実行結果へ不成立理由を残します。

`MIC`は通常tap commandではありません。「押している間」だけPush-to-talkを開始し、指を離す、pointer cancel、画面離脱、Activity停止、通信切断でstopします。

## Danger専用画面：3キー

3キー共通で、Relay capabilityが`ready`、Android／Relayの危険分類が一致し、60秒以内の未使用nonceが必要です。

| Keycap | 影響 | 使用条件 |
|---|---|---|
| `APPR` | 現在の承認要求を承認 | `ready / fresh`、active taskとstable thread IDあり、現在taskに承認要求あり |
| `REJ` | 現在の承認要求を拒否 | `ready / fresh`、active taskとstable thread IDあり、現在taskに承認要求あり |
| `DEL` | 現在のtaskをarchive | `ready / fresh`、archive対象のactive taskとstable thread IDあり |

操作手順:

1. Control左上のグローバルメニューから`⚠ 危険操作`を選ぶ。
2. 警告を読み、`Danger画面を開く`を明示的に選ぶ。
3. 画面上部のtask／project／host／threadを確認する。
4. 対象ボタンを1.2秒連続長押しする。
5. 成功／拒否／不成立理由を画面内で確認する。
6. `DEL`成功時はControlへ戻り、archiveされたtaskがAgent slotから外れたことを確認する。

通常tap、1.2秒未満のhold、途中release、pointer cancel、画面外移動、Activity停止、通信切断では実行しません。RelayはDanger画面を開いた後に発行したconfirmation nonceを要求します。nonceの有効期限は60秒で、host、WebSocket接続、Keycap、active threadへ束縛され、一度使用、期限切れ、task変更、切断後は再利用できません。

Controlの動的Micro Actionへ`APPR`／`REJ`／`DEL`のいずれかが割り当てられている場合も、そのボタンから直接実行せずDanger画面へ誘導します。Relayもgeneric Action経路による危険キー実行を拒否します。

Rate Limit ResetはDanger画面には置かず、Usage画面で適用条件を再確認したうえで1.2秒長押しします。

## Windows／Android導入とNearbyペアリング

1. Relay repositoryで`npm run package:windows`を実行し、`release/codex-micro-relay-windows-x64.zip`を生成／展開する。
2. 展開先の`Install Codex Micro Relay.cmd`を実行する。Relayは`%LOCALAPPDATA%\CodexMicroRelay\app`へ導入される。
3. Start Menuの「Codex Micro Relay」から管理UIを開き、Relay／Codex bridge／LAN状態を確認する。
4. 開発用Android APKは`adb install -r app/build/outputs/apk/debug/app-debug.apk`で上書き導入する。
5. PC管理UIの「ペアリング」画面を開き、アプリ内QRをAndroidアプリ内カメラで読み取る。
6. AndroidのControl画面で`ready`と`fresh`を確認する。

QR表示に外部SVG、ブラウザ、画像ビューアは使用しません。QRには認証tokenが含まれるため共有・公開しないでください。失効させる場合はPC側で資格情報をrotate／無効化し、Android側のprofileを削除して再ペアリングします。

Android 13以降では「付近のデバイス」権限を許可してください。Android 16のローカルネットワーク保護オプトイン時にもLAN接続に必要です。

## ビルドと試験

Android Studioでこのプロジェクトを開くか、JDK 17以上とAndroid SDK 36を用意して実行します。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\<user>\AppData\Local\Android\Sdk'
.\gradlew.bat clean test lint assembleDebug assembleRelease
```

debug APKは`app/build/outputs/apk/debug/app-debug.apk`、unsigned release APKは`app/build/outputs/apk/release/app-release-unsigned.apk`へ生成されます。署名済みreleaseには次の環境変数を使用し、鍵とpasswordをGitへ保存しません。

- `CODEX_MICRO_KEYSTORE`
- `CODEX_MICRO_STORE_PASSWORD`
- `CODEX_MICRO_KEY_ALIAS`
- `CODEX_MICRO_KEY_PASSWORD`

## 現在の検証状態

Android自動試験、Debug／Release build、Lint、Relay試験／型検査／package／audit、Pixelへの安全追補APK導入、通常27キー表示・全enabled、通常27キーのRelay／native handler応答、危険3キー非混在、Danger導線、短時間hold／画面外移動 0実行、DEL正規nonce実行、nonce live異常系、APPS、MIC down/up、5分安定稼働は確認済みです。通常27キーはprojectless一時taskで18件が実行成功、リポジトリ等を必要とする9件がキーID付きの具体的不成立理由を返しました。

次は未完了のため、製品全体を最終合格とはしていません。

- 実承認要求を使ったAPPR／REJ正規実行（Relay統合試験は各1回PASS、現在の実承認要求は0件）
- Wi-Fi復帰後のPixel実画面確認
- macOS実機試験、署名済みrelease

## 関連文書

- [実装計画書](docs/IMPLEMENTATION_PLAN.md)
- [実装報告書](docs/IMPLEMENTATION_REPORT.md)
- [テスト計画書](docs/TEST_PLAN.md)
- [テスト結果報告書](docs/TEST_REPORT.md)
- [Palette原因調査書](docs/PALETTE_KEYCAP_AVAILABILITY_ROOT_CAUSE.md)
- [Palette対応報告書](docs/PALETTE_KEYCAP_AVAILABILITY_FIX_REPORT.md)

## ライセンス

アプリ固有コードはMIT Licenseです。依存OSSは[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)を参照してください。CodexMacro由来コードはコピーしていません。
