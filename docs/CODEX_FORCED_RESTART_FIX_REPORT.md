# Codex強制再起動 対応報告書

作成日: 2026-08-19
文書版: 1.1
修正前Relay版: 0.2.1
修正版Relay版: 0.2.3
原因調査書: `docs/CODEX_FORCED_RESTART_ROOT_CAUSE.md` 文書版1.1

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | Codex強制終了処理を削除したが、自動起動経路が残ったため廃止 |
| 1.1 | 2026-08-19 | 現行 | WatcherからCodex起動権限を除去し、明示UI操作だけへ分離 |

対応内容、対象版または判定を変更する場合は文書版を上げ、旧版を削除せず履歴へ残す。

## 2. 修正内容

- Windows launcherから`Stop-Process`を完全削除した。
- WatcherからCodexの自動起動権限を完全に除去した。
- Codexを起動できる`-AllowCodexLaunch`はWindows管理UIの明示ボタン操作だけが渡す。
- Codexがdebug portなしで起動中の場合、Codexへ一切変更を加えず安全にエラー終了する。
- Watcherは10秒後に再確認するが、Codexを終了・再起動しない。
- Codexが通常終了した後、次のWatcher周期でdebug port付きCodexとRelayを起動する。
- インストーラーへ`-NoUi`を追加し、作業中の画面を邪魔せず更新できるようにした。
- unit testへ`Start-CodexMicroRelay.ps1`に`Stop-Process`が存在しないことを固定する回帰検査を追加した。

## 3. 試験結果

| 試験 | 結果 |
|---|---|
| Relay unit test | PASS、11/11 |
| TypeScript check | PASS |
| build／Windows package | PASS、1449 files |
| npm audit | PASS、脆弱性0 |
| 非表示インストール | PASS、0.2.3 |
| スタートアップentry | PASS、正規`.cmd`を作成 |
| Watcher | PASS、1プロセス |
| インストール前後のCodex PID | PASS、全PID保持 |
| 32秒Watcher監視 | PASS、Codex終了0・新規起動0 |
| 現在のRelay待受 | 安全待機、Codexがdebug portなしのため0 |

現在Relayが待受しないのは0.2.3の安全動作である。WatcherはCodexを起動も終了もしない。Micro activationが必要な場合はWindows管理UIの明示ボタンからのみ起動する。

## 4. ネットワーク読み取り専用確認

`network-readonly-monitor`に従って`changedConfig: false`で確認した。

- RTX、HPE、全監視AP、VLAN99 transitは到達可能。
- LAN経由Internet HTTPSは204で成功。
- Windows FirewallはPublic profileでNode.js受信TCPを許可。
- 監視証跡: `<USER_PROFILE>\network-backup\monitor\<timestamp>\health-result.json`
- ネットワーク設定、ACL、VLAN、registryは変更していない。

## 5. 成果物

| 項目 | 値 |
|---|---|
| Relay version | `0.2.3` |
| Relay Git commit | `7e44c1e` |
| Windows ZIP SHA-256 | `d85bf756f8b7a082bd025608555800d0041f789bc5b75924ec9d5ebdbb3326e4` |

## 6. 判定

Codexを自動停止または自動起動する設計はWatcherから撤廃済みで、再発防止テストも合格した。接続再開はユーザー都合のよい時点でWindows管理UIの明示起動操作として行う。バックグラウンド監視はCodexへ干渉しない。
