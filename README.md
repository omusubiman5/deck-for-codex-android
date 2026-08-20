# Deck for Codex — Android

> **Unofficial experimental project.** This project is not created, supported, certified, or endorsed by OpenAI. Windows and Pixel 9a have been partially verified; macOS hardware is unverified and overall acceptance remains pending.

Android端末から、Windows／macOS上のCodex DesktopをCodex Micro Relay経由で操作する独立ネイティブクライアントです。OpenAI公式Codex MicroハードウェアそのものをAndroidへ移植するものではありません。

| コンポーネント | 対象版 | 検証状態 |
|---|---|---|
| Android | 0.2.6（versionCode 8） | Pixel 9aへ導入、30キー実画面確認済み |
| Windows Relay | 0.2.8 | Windowsへ導入・Protocol 2接続確認済み |
| macOS Relay | 0.2.8 | package／launcher検査のみ、Mac実機未検証 |
| Protocol | 2 | 30キー統合契約 |

このGitプロジェクトは`codex-deck-m18`から独立しており、M18／Stream Deckの制御コードを含みません。

## 機能

- Control／Palette／Usage／Hostsの4画面
- Codex Microの6 Agent表示とtask切替
- 6つの動的Micro Action、Joystick 4方向、Encoder、Reasoning
- 公式Keycap 30機能を同一Paletteで表示・操作
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

## 公式Palette：30キー

30キーを同じgrid、検索、カテゴリへ表示します。

| 行 | Keycap |
|---:|---|
| 1 | `FAST`、`APPR`、`REJ`、`SPLIT`、`MIC` |
| 2 | `CODEX`、`BUG`、`OAI`、`TERM`、`DWN` |
| 3 | `DEL`、`NEW`、`NAV`、`MAGIC`、`DIFF` |
| 4 | `PLAY`、`GIT`、`BRCH`、`MRG`、`PR` |
| 5 | `PAINT`、`LAB`、`PARTY`、`TIME`、`MIND+` |
| 6 | `MIND-`、`SETUP`、`FOLD`、`UPL`、`APPS` |

`APPR`、`REJ`、`DEL`は危険キーではなく、他のKeycapと同じ通常tapで実行します。`APPR`と`REJ`は現在の承認要求がある場合に成立します。`DEL`の実体はtaskのarchiveであり、ファイル、repository、Git履歴を削除しません。

通常キーがlive registryで解決済みでも、現在のCodex画面、選択task、repository、composer、承認状態などの条件により成立しないことがあります。この場合はキーを恒久無効化せず、最終実行結果へ不成立理由を残します。

`MIC`は通常tap commandではありません。「押している間」だけPush-to-talkを開始し、指を離す、pointer cancel、画面離脱、Activity停止、通信切断でstopします。

Rate Limit Resetは30 Keycapとは別にUsage画面へ置き、適用条件を再確認したうえで1.2秒長押しします。

## Windows／Android導入とNearbyペアリング

1. [Deck for Codex Relay](https://github.com/omusubiman5/deck-for-codex-relay)で`npm run package:windows`を実行し、`release/codex-micro-relay-windows-x64.zip`を生成／展開する。
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

30キー統合版のAndroid test／Lint／Debug／Release build、Relay 13 unit test／TypeScript／package／audit、Windows／Pixel再導入を確認済みです。Pixel UI dumpでは30件すべて表示・enabled、APPR／REJ／DELの通常配置、Danger表示0件、crash／ANR 0でした。

次は未完了のため、製品全体を最終合格とはしていません。

- 実承認要求を使ったAPPR／REJのPixel実画面実行（現在の実承認要求は0件）
- Wi-Fi復帰後のPixel実画面確認
- macOS実機試験、署名済みrelease

## 関連文書

- [実装計画書](docs/IMPLEMENTATION_PLAN.md)
- [Palette 30キー復帰実装計画書](docs/PALETTE_30_KEY_RESTORE_IMPLEMENTATION_PLAN.md)
- [実装報告書](docs/IMPLEMENTATION_REPORT.md)
- [テスト計画書](docs/TEST_PLAN.md)
- [テスト結果報告書](docs/TEST_REPORT.md)
- [Palette原因調査書](docs/PALETTE_KEYCAP_AVAILABILITY_ROOT_CAUSE.md)
- [Palette対応報告書](docs/PALETTE_KEYCAP_AVAILABILITY_FIX_REPORT.md)

## ライセンス

アプリ固有コードはMIT Licenseです。依存OSSは[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)を参照してください。CodexMacro由来コードはコピーしていません。
