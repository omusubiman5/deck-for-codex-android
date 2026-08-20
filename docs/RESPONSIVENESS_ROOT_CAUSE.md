# Android操作遅延・誤表示 原因調査報告書

作成日: 2026-08-19
文書版: 1.1
対象Android版: 0.2.1（versionCode 3）
対象Relay版: 0.2.3
後続回帰対象: Android 0.2.4（versionCode 6）／Relay 0.2.6

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | 接続誤認、offline再描画、Agent並列要求、Keycap可用性誤判定を確定 |
| 1.1 | 2026-08-20 | 現行 | 6 Action slotだけをavailabilityにした後続回帰を追記 |

## 2. 結論

反応の悪さはL3遅延ではなく、アプリとRelayの実装によるものだった。実測時はUSBでPixelがPCへ接続されていたが、WSS RelayのTCP `47653`とCodex CDPは待受0であり、アプリは`offline / waiting`だった。USB接続とアプリ接続を区別しにくい表示が誤認を招いた。

接続中だった過去ログではAgentのdown／upを別々のWSS commandとして並列処理していた。連打時に同じslotのdownが最大6.9秒滞留し、upも複数並行した。さらにcommand完了ごとにsnapshot取得をawaitし、次の操作処理と競合していた。

Androidは再接続のたびに`connecting`と`offline`を通知し、Control画面全体を`setContentView`から再構築していた。6 Agent、6 Action、下部navigationを周期的に作り直すため、未接続時にもスクロールとタップを妨げた。

Keycap Paletteはlive可用性を取得できない場合に全30キーを有効化し、Relay snapshotも公式30キーをすべて利用可能と申告していた。実際には現在のCodex viewで利用できず、`VS Code event module is unavailable`または`command is not active`になるキーが多数あった。表示と実動作が一致していなかった。

この対策でRelay 0.2.4以降は逆に、6 Action slotへ設定済みのキーだけをavailabilityとした。安全なlive registry探索へ置き換えず機能を6キーへ縮退させたため、Android上段相当以外がすべてdisabledになる後続回帰を作った。詳細は`PALETTE_KEYCAP_AVAILABILITY_ROOT_CAUSE.md`を正とする。

## 3. 実測証拠

- PC `<PC_PRIVATE_IP>:47653`: listener 0、live smokeは`ECONNREFUSED`。
- CDP `127.0.0.1:52172`: listener 0。
- Pixel画面: `offline / waiting`、全slot未割当。
- Relay過去ログ: Agent down完了が最大6920ms、5586ms、4778ms、4287ms。
- Keycap失敗: 3～16msで現在view非対応を反復。
- 修正前画面: `docs/assets/diagnostics/pixel-current.png`。

## 4. UIレイアウト不備

targetSdk 36のedge-to-edge表示にsystem bar insetを適用していなかったため、時刻・通知アイコンとapp barが重なり、接続chipも欠けていた。下部gesture領域もbottom navigationと重なっていた。

## 5. 判定

接続誤認、描画負荷、Agent入力設計、Keycap可用性のすべてが製品側の設計不備である。対応は`docs/RESPONSIVENESS_FIX_REPORT.md`文書版1.0を正とする。
