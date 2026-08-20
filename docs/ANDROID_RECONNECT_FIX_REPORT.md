# Android再接続後も未接続 対応報告書

作成日: 2026-08-20
文書版: 1.1
原因調査書: `docs/ANDROID_RECONNECT_ROOT_CAUSE.md` 文書版1.1
修正版Android: 0.2.4（versionCode 6）
修正版Relay: 0.2.6

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 廃止 | Relay独立起動とtransport接続結果を記録したが、機能未接続を解消済みと誤判定 |
| 1.1 | 2026-08-20 | 現行 | Codex機能接続、Android実操作、scroll保持、危険Action保護、5分安定試験を反映 |

## 2. 修正内容

### Windows Relay

- Android用WSS listenerをCodex bridge検査より先に起動する順序へ変更した。
- listener起動を最大5秒確認し、起動プロセス終了またはtimeoutを明示エラーにした。
- Codexがdebug portなしで起動中でもRelayを停止せず、Android接続を制限モードで維持する。
- Codexを強制終了・自動再起動する処理は追加していない。
- unit testへ「Relay起動がCodex検査より前」を追加した。

### Android

- Relayの`health.reason=native-signals-unavailable`を専用状態`bridge_waiting`として扱う。
- 状態chipを`Codex未接続`、詳細を`PC Relayは接続済みですが、Codex操作は未接続です。`と表示する。
- PC未接続と、PC接続済みだがCodex操作待ちの状態を分離した。
- live snapshotによる画面再構築の前後で画面別scroll位置を保存・復元した。
- APPR／REJの動的ActionにもKeycapの長押し時間を適用し、通常tapでは送信しない。

### Codex明示起動

- 管理UIの明示操作で一度限りのMicro起動予約を保存する。
- 稼働中Codexを強制終了せず、利用者が通常終了した後だけ予約を消費してMicro有効状態で起動する。
- Watcher単独では予約なしにCodexを起動しない。

## 3. 導入結果

| 項目 | 結果 |
|---|---|
| Windows Relay上書き導入 | PASS、0.2.6 |
| Pixel APK上書き導入 | PASS、0.2.4 / versionCode 6 |
| Relay listener | PASS、`<PC_PRIVATE_IP>:47653` |
| Android TCP | PASS、`<PIXEL_PRIVATE_IP>`からESTABLISHED |
| Android機能状態 | PASS、`ready` / `fresh (0s)` |
| Pixel現行PID | 6753（5分間不変） |
| FATAL／ANR | PASS、0件 |
| Codex強制終了／再起動 | 0件 |

実画面証跡: `docs/assets/diagnostics/android-0.2.4/`

## 4. 自動試験

| 試験 | 結果 |
|---|---|
| Relay unit test | PASS、11/11 |
| Relay TypeScript check／build | PASS |
| Relay PowerShell parse | PASS |
| Relay package audit | PASS、1449 files |
| npm audit | PASS、脆弱性0 |
| Android unit test | PASS、14件 |
| Android debug build／Lint | PASS |
| Android release build／Lint | PASS、Lint 0 errors / 4 warnings |

## 5. 成果物

| 項目 | 値 |
|---|---|
| Android commit | `a8eb8f6` |
| Relay commit | `2c11e89` |
| debug APK SHA-256 | `6df53059146dc2258332a8c993f562b560e965c63656de387ce0b297d1bb346d` |
| unsigned release APK SHA-256 | `8b47cea7f5819217b392c042b4b2ecfe01e29c95d56f71abe9eb2f50a0372e37` |
| Windows ZIP SHA-256 | `00058fa734c31a06ff19d6346a6c31e65a06af002210c13ca7eb64770222f073` |

## 6. 判定

「Androidアプリは繋ぎ直しても未接続」は機能接続まで解消した。Pixel実画面で`ready`、`fresh (0s)`、6 Agent、Usageを確認し、Agent／Action／Joystick／Encoder／Reasoning／FAST操作がRelayで完了した。Wi-Fiを35秒停止すると`PC未接続`へ遷移し、再有効化後に`ready`かつ`fresh (0s)`へ復帰した。

5分間の前面連続試験は同一PID、Awake、crash 0、ANR 0で合格した。詳細結果は`docs/TEST_REPORT.md`文書版1.6を正とする。
