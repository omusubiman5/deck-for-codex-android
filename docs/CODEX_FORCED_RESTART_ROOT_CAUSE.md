# Codex強制再起動 原因調査報告書

作成日: 2026-08-19
文書版: 1.1
対象Relay版: 0.2.1～0.2.2
対象事象: Relay復旧中にCodex Desktopが終了・再起動した

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | debug portなしで起動中のCodexをlauncherが強制終了した原因を確定 |
| 1.1 | 2026-08-19 | 現行 | 0.2.2にも既存Codex未検出時の自動起動経路が残ったことを追記 |

原因または影響範囲を変更する場合は文書版を上げ、旧版を削除せず履歴へ残す。

## 2. 結論

Relay 0.2.1の`Start-CodexMicroRelay.ps1`には、起動中Codexから`--remote-debugging-port`を検出できない場合、Codex関連プロセスを`Stop-Process -Force`で終了し、debug port付きで再起動する処理が残っていた。

0.2.1で修正したのは「debug port付きで起動途中のCodexをCDP応答遅延だけで停止する問題」であり、「通常起動されdebug portを持たないCodexを停止する問題」は未修正だった。Watcherを復旧するとこの分岐が実行され、ユーザー作業中のCodexを中断した。

0.2.2では`Stop-Process`を削除したが、既存Codexをpackage path照合で検出できない場合に`Start-Process ChatGPT.exe`を自動実行する経路が残った。Electronの既存instanceが動作中にWatcherが新instanceを反復起動し得るため、「Codexを終了しない」だけでは非破壊要件を満たさなかった。

## 3. 証拠

- 障害時、Codex／ChatGPTプロセスは20:27:21に新規生成され、すべてdebug portなしだった。
- RelayのTCP `47653`待受は存在しなかった。
- 0.2.1 launcherには`$processes | ... | Stop-Process -Force`が存在した。
- Watcher停止後は追加の自動終了は発生しなかった。
- WindowsスタートアップのRelay entryが`.disabled`になっており、過去に再起動ループ回避のため無効化されていた可能性が高い。

## 4. L3分離との関係

L3分離は今回のCodex強制再起動原因ではない。QRにはPCの`<PC_PRIVATE_IP>:47653`が含まれるため、読取後はmDNSではなくIP直指定で接続する。必要条件はスマホ側からPCのTCP 47653への経路とACL許可である。

QR読取後のRelay logにはPixel操作によるAgent／Action commandが複数記録されており、その時点でスマホからPCまでの通信は成立していた。Windows FirewallもPublic profileでNode.js受信TCPをremote address制限なしで許可していた。失敗確認時の直接状態はL3遮断ではなくPC側Relay待受0だった。

## 5. 判定

これはRelay launcherの安全設計不備であり、ユーザー操作やL3構成を原因としない。対応内容は`docs/CODEX_FORCED_RESTART_FIX_REPORT.md`文書版1.1を正とする。
