# Android再接続後も未接続 原因調査報告書

作成日: 2026-08-20
文書版: 1.1
対象Android版: 0.2.3（versionCode 5）
対象Relay版: 0.2.5

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 廃止 | Android再接続後もPC Relayへ接続できない起動順序を確定 |
| 1.1 | 2026-08-20 | 現行 | TCP接続だけを合格とした試験判定不備と、機能接続の誤表示を追加 |

## 2. 現象

Pixel 9aはUSBでPCに認識され、Androidアプリで再接続しても`PC未接続`のままだった。PCではWindows管理UIとWatcherが動作していたが、Relay TCP `<PC_PRIVATE_IP>:47653`とCodex CDPのlistenerはともに0だった。

## 3. 原因

Windowsの`Start-CodexMicroRelay.ps1`は、次の順序で処理していた。

1. CodexプロセスとCDP portを検査する。
2. Codexがdebug portなしで起動中なら例外終了する。
3. その後でAndroid用WSS Relayを起動する。

実行中CodexにCDP portがなかったため、処理は2で終了し、3へ到達しなかった。管理UIの「Relayを開始 / 再接続」も同じスクリプトを呼ぶため、押し直してもAndroid接続先そのものが起動しなかった。

Androidの再接続処理、USB、PixelのWi-Fi、PCとPixel間のL3到達性が直接原因ではない。修正前はPC側にTCP listenerが存在せず、Androidから接続できない状態だった。

## 4. 設計不備

Android用WSS RelayとCodex renderer bridgeは別の可用性単位である。Codex bridgeが利用できなくても、Relayは認証接続を受け付け、Androidへ`ready`と`health: degraded`を返せる実装になっていた。それにもかかわらずlauncherだけが両者を直列依存させていた。

またAndroidは`native-signals-unavailable`を一般的な`制限あり`として表示しており、「PC Relayへは接続済み、Codex操作だけが待機中」という状態を識別できなかった。

## 5. 実測証跡

- 修正前Relay listener: 0
- 修正前Codex CDP listener: 0
- Pixel ADB状態: `device`
- Pixel導入版: Android 0.2.2 / versionCode 4
- Windows導入版: Relay 0.2.4
- Watcher log: `Codex is running without the Micro activation port`を反復
- Relay log: 新しいlisten記録なし

## 6. 判定

原因はPC Relay launcherの起動順序、Android状態表示、試験の合格条件の設計不備である。対応は`docs/ANDROID_RECONNECT_FIX_REPORT.md`文書版1.1を正とする。

## 7. 文書版1.5テスト判定の不備

Relay 0.2.5導入後、PixelからPCへのTCP ESTABLISHEDとAndroid PIDを確認してF-02をPASSにした。しかし実画面はCodex bridge未接続の`waiting`であり、Agent／Actionを操作できなかった。TCPはPixelとRelay間のtransportだけを証明し、RelayとCodex renderer間の機能接続を証明しない。

さらにAndroidはこの状態を`PC接続済`と表示したため、利用者には全機能が接続済みに見えた。正しい合格条件は次の4点である。

1. MainActivityが物理画面の前面にある。
2. 状態chipが`ready`である。
3. `fresh (0～5s)`の実snapshotが継続して届く。
4. 安全な操作commandがRelayで完了し、Android UIが更新される。

PID、listener、TCPだけでF-02を合格にした文書版1.5の判定は無効とする。
