# Palette Keycap Availability 対応報告書

作成日: 2026-08-20  
文書版: 1.4
原因調査書: `docs/PALETTE_KEYCAP_AVAILABILITY_ROOT_CAUSE.md` 文書版1.2
修正版Android: 0.2.6（versionCode 8）
修正版Relay: 0.2.8／Protocol 2

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 廃止 | 通常27／Danger 3、30 capability、MIC、nonce修正と実測を記録 |
| 1.1 | 2026-08-20 | 廃止 | README鬼レビューでAction迂回と境界外holdを追加修正 |
| 1.2 | 2026-08-20 | 廃止 | Pixel再接続後に安全追補APK、境界外cancel、5分安定稼働を実機確認 |
| 1.3 | 2026-08-20 | 廃止 | 通常27 handler、DEL、nonce live異常系と後続Relay修正を記録 |
| 1.4 | 2026-08-20 | 現行 | 危険分類を撤回し、同一Palette 30キーと通常commandへ復帰 |

## 1.1 現行訂正

1.0～1.3の通常27／Danger 3は、状態変更を危険性と混同した過剰設計だったため撤回した。0.2.6／0.2.8では30キーを同じPaletteへ表示し、APPR／REJ／DELを通常tap、動的Actionを通常down／upへ戻した。Danger画面、長押し、confirmation nonce、Action拒否は削除した。実装・試験・成果物は`IMPLEMENTATION_REPORT.md`文書版3.3と`TEST_REPORT.md`文書版2.1を正とする。

## 2. 文書版1.3までの修正内容（履歴）

- Relayの`availableKeycaps`を6 Action slot由来から、既知の`codex-micro-layout-*` live registry由来へ変更した。
- 不特定lazy moduleを実行せず、公式30 IDを解決する。
- `keycapCapabilities`へID、action型、ready／unsupported、dangerを追加した。
- Android通常Paletteを27キーとし、`APPR`、`REJ`、`DEL`をgrid、検索、カテゴリから除外した。
- Danger専用画面を追加し、グローバルメニューの警告ダイアログ経由だけで開く。
- Danger画面へtask、project、host、thread、承認状態、最終結果を表示する。
- Androidで1.2秒hold、release／cancel／Activity停止／切断cancelを実装した。
- Androidでpointerがview境界外へ移動した場合もhold callbackをcancelする。
- Control動的slotのAPPR／REJ／DELはgeneric Actionを送信せずDanger警告へ誘導する。
- Relayでhost、WebSocket接続、key、active thread、60秒期限へ束縛した一回限りnonceを検証する。
- Relayで現在layoutを再確認し、危険keycap割当slotのgeneric Action要求を拒否する。
- MICを明示down／upへ変更し、現在のlive layoutに割り当てられた公式Push-to-talk handlerを使用する。未割当時は現在viewの音声入力controlへ限定fallbackする。
- live smokeをRelay本体と同じ既定経路選択へ統一し、仮想NIC誤接続を解消した。
- external URLは`vscode-api-*`がない現在buildでも安全なanchor fallbackで開く。
- 画面条件不成立ではCDPを切断せず、すべての失敗をkeycap ID付きへ正規化した。
- `SPLIT`はrenderer切替後のactive thread変化を確認し、成立済み操作をtransport失敗へ誤判定しない。

## 3. 自動試験

| 項目 | 結果 | 実測 |
|---|---|---|
| Android unit | PASS | 15 tests、failure 0 |
| Android clean／Debug／Release | PASS | 91 tasks、BUILD SUCCESSFUL |
| Android Lint | PASS | fatal error 0 |
| Relay unit | PASS | 15 tests、failure 0 |
| Relay TypeScript check／build | PASS | error 0 |
| Relay package | PASS | Windows ZIP生成 |
| npm audit | PASS | vulnerability 0 |

回帰試験には「Action slotが1件でもcapability 30件」「通常27／危険3」「危険キー通常検索0件」「MICはact必須」「nonceのtask束縛、期限、一回性」を含めた。

## 4. Pixel 9a実測

| 項目 | 結果 | 証跡 |
|---|---|---|
| APK上書き | PASS | 0.2.5 / versionCode 7 |
| Control接続 | PASS | `ready`、`fresh (0s)` |
| 通常Palette | PASS | UI dumpで27キー、全27ボタンenabled |
| 危険キー非混在 | PASS | 通常PaletteのAPPR／REJ／DELは0件 |
| Danger導線 | PASS | menu→警告dialog→明示ボタン→専用画面 |
| Danger件数 | PASS | APPR／REJ／DELの3キーだけ |
| 通常tap | PASS | DEL実行ログ0、画面は未実行 |
| 600ms hold | PASS | DEL実行ログ0、画面は未実行 |
| 境界外move | PASS | DEL内から境界外へ1.5秒swipe、Relay受信0、画面は未実行 |
| APPS | PASS | Android送信、Relay受信、native完了 |
| MIC | PASS | down/up各1、完了2、失敗0 |
| 5分安定稼働 | PASS | PID 24970を維持、前面／Awake 10回中10回、終了時`ready`／`fresh (0s)`、crash／ANR 0 |
| Wi-Fi 35秒切断 | BLOCKED | 切断中にPixelがPINロック。Wi-Fi／Relay復帰後のアプリ実画面確認は未完了 |
| crash／ANR | PASS（実施区間） | FATAL EXCEPTION／ANR 0 |

最初のMIC実測は誤った`vscode-api-*`探索により2件失敗した。公式live layoutのPush-to-talk handlerへ修正し、再導入後にdown/up完了2・失敗0を確認した。失敗を隠さず、修正前後のRelayログを証跡として残した。

## 5. 未完了項目

- 実承認要求を使ったAPPR／REJのnative実画面正規実行。Relay統合経路は各1回PASSしたが、現在の実承認要求は0件。
- Wi-Fi復帰後の`ready / fresh (0～5s)`実画面確認。

未完了項目はPASSへ読み替えない。現在の総合判定は**受入保留**である。

README鬼レビュー後の境界外cancelはAndroid test／Lint／Debug／Release buildとPixel実機gestureでPASSした。generic Action迂回拒否はRelay unit testとlive WSSで確認した。

導入済みRelayへ認証済みlive WSSで`ACT07` generic Actionを直接送信し、`ok:false`とDanger APPR拒否errorを確認した。native APPRは実行されていない。

projectless一時taskで通常27 IDを全件送信した。18件は実行成功、リポジトリや現在viewを必要とする9件はkeycap ID付き不成立理由を返した。DWNのclipboardはDataObjectとして復元し、作成したtaskとSPLIT forkはDEL正規nonceでarchiveした。nonceなし、キー違い、期限切れ、切断後、task変更後、再利用はlive WSSで全件拒否した。

## 6. 成果物

| 成果物 | 値 |
|---|---|
| Android base commit | `53d3964` |
| Android safety commit | `aeed172` |
| Relay base commit | `d539641` |
| Relay safety commit | `0358eb4` |
| Relay guard test commit | `60b1464` |
| Relay全keycap追補commit | `acc617e` |
| 文書本体commit | `a2171e7` |
| README鬼レビュー文書commit | `6c97f58` |
| Pixel安全追補検証文書commit | `18bac75` |
| 全keycap実測文書commit | `3998c35` |
| debug APK SHA-256 | `7d2b9cef03f8cf2c9b10b9c05e9f2aefc062f5a3069cffe9eb118756660e0280` |
| unsigned release APK SHA-256 | `e8192a8279ebb9f990f5fadcb57ebda0c2f20840cf07f05d4ab5dd7f8c037548` |
| Windows ZIP SHA-256 | `eef140f986df17d6670a26af4641245aa330ceadc1b7eee5dffb4393275df39d` |
| UI dump | `build/pixel-palette.xml`、`build/pixel-danger-dialog.xml`、`build/pixel-danger.xml` |

文書本体commit `a2171e7` は、本原因調査書、対応報告書、計画書／報告書の版更新をAndroid／Relayコードと分離して記録した。

README鬼レビュー後のRelay配布物はWindowsへ0.2.7として再導入済み。安全追補commit `aeed172`を含むAndroid 0.2.5 / versionCode 7もPixelへ上書き導入済みである。
