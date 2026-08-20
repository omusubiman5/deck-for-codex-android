# Relay snapshot不合格 原因調査報告書

作成日: 2026-08-19
文書版: 1.0
対象Relay版: 0.2.0
対象事象: 総合判定「不合格」／`Codex Micro slot store was not found`

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 現行 | snapshot反復失敗、Codex renderer異常、Watcher再起動ループの原因を確定 |

調査結果または原因判定を変更する場合は文書版を上げ、旧版を削除せず履歴へ残す。

## 2. 結論

`slot store was not found`は単なる待機不足ではなかった。Relay 0.2.0のsnapshot取得処理がCodex rendererの全lazy assetを動的importし、用途不明のexported functionへ`FAST`を渡して探索していたため、初回snapshot後にCodex DesktopをReact error boundaryへ遷移させていた。error boundary状態ではslot storeが存在せず、2回目以降のsnapshotが失敗した。

同時にWindows Watcherにも二つの不備があった。インストールのたびにWatcherを重複起動でき、Codex起動直後のCDP `/json/version`が1秒以内に応答しないと、すでに`--remote-debugging-port`付きで起動中のCodexを停止して再起動していた。このためCDP portが短時間で変化し、rendererの初期化途中またはerror boundary状態を増幅した。

さらにRelayは最初のmain rendererだけを選び、初期化途中のtargetだった場合に別main windowへfallbackしなかった。これは主原因ではないが、復旧性を下げる副原因だった。

## 3. 再現事象

1. 導入済みRelayへWSS接続する。
2. TLS fingerprint固定、token認証、`ready`までは成功する。
3. 初回snapshotが成功する場合がある。
4. 以降は`health: degraded / native-signals-unavailable`となり、Relay logへ`Codex Micro slot store was not found`を記録する。
5. Codex rendererは`Oops, an error has occurred / Try again`のerror boundaryへ遷移し、DOMとReact fiberが通常時から急減する。
6. Watcher重複時はCDP portが連続して変化し、再起動後だけ一時的に成功する。

## 4. 原因別の証拠

### 4.1 全lazy asset importと未知関数実行

- 0.2.0のsnapshot式はrendererが参照するasset群を順番にimportしていた。
- available keycap探索のため、型も用途も確定していないexported functionを`candidate('FAST')`として実行していた。
- snapshot実行後、renderer rootはerror boundary表示へ変化し、slot store探索が失敗した。
- インストール済みCodexのASARを静的確認すると、必要なevent bus／usage定義は`app-initial-*`、slot resolverは`codex-micro-slot-signals-*`に限定できた。

### 4.2 Watcherの多重起動

- 修正前はインストール操作ごとにWatcherが新規起動し、同時に4プロセスまで存在した。
- 排他制御がなく、複数Watcherが同じCodex／Relayを監視していた。

### 4.3 起動中Codexの誤停止

- launcherはCDP endpointへ1秒以内に接続できないと「debug無効」と判定した。
- 実際には起動プロセスのcommand lineへdebug portが設定済みでも、port番号を保存する前にprobe失敗処理へ進んでいた。
- Watcherが10秒周期でCodexを停止・再起動し、CDP portが`63891`、`54485`、`57415`、`52224`などへ変化した。

### 4.4 renderer target固定

- DevTools pageは除外していたが、main windowが複数ある場合に先頭targetだけを使用していた。
- 先頭targetが初期化途中でも、他のready targetへ切り替えなかった。

## 5. なぜ再起動で一時復旧したか

再起動直後はrendererがerror boundaryへ入る前なので、最初のsnapshotだけslot storeを取得できる場合があった。しかし0.2.0の探索処理を再度実行するとrendererを再び壊すため、再起動だけでは恒久対策にならない。Watcherの再起動ループも同時に存在したため、成功と失敗が不規則に見えた。

## 6. 影響範囲

- AndroidアプリのQR実装やAndroidX依存が本事象の原因ではない。
- TLS fingerprint固定、token認証、command allowlistは機能していた。
- 影響対象はRelay snapshot、Usage、slot表示、snapshotに依存するAndroid各画面である。
- Watcher不備はCodex Desktopの安定稼働にも影響した。

## 7. 判定

原因はRelay 0.2.0のrenderer探索設計とWindows監視設計の複合不備で確定した。修正内容と再試験結果は`docs/RELAY_SNAPSHOT_FAILURE_FIX_REPORT.md`文書版1.0を正とする。
