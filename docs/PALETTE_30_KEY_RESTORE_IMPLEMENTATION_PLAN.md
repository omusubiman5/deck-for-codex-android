# Palette 30キー復帰 実装計画書

作成日: 2026-08-20
文書版: 1.0
状態: 実装基準
対象: Android 0.2.6（versionCode 8）／Relay 0.2.8／Protocol 2

## 1. 改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 現行 | APPR／REJ／DELの危険分類を撤回し、公式30キーを同一Paletteへ復帰する計画を制定 |

## 2. 設計訂正

従来は`APPR`、`REJ`、`DEL`を一律に危険操作と分類し、通常27キーとDanger専用3キーへ分離した。この分類は、状態を変更する操作と危険操作を混同した過剰設計である。

- `APPR`は承認対象の内容によって影響が変わるが、Keycap自体を危険キーとは分類しない。現在承認要求がなければnative handlerの具体的な不成立結果を表示する。
- `REJ`は要求を拒否して実行を止める操作であり、危険操作ではない。
- `DEL`の実体はtaskの削除ではなくarchiveであり、ファイル、repository、Git履歴を削除しない。表示名は`アーカイブ`を維持する。

30キーすべてを同一の公式Keycapとして扱い、危険分類、Danger専用画面、1.2秒hold、confirmation nonceを撤去する。Rate Limit Resetは30 Keycapとは別機能のためUsage画面に残す。

## 3. Android UI

- Paletteタイトルを`公式 Keycap 30`へ変更する。
- `OfficialKeycaps.all`の30キーをgrid、検索、カテゴリ件数へ含める。
- `APPR`、`REJ`、`DEL`も他の通常Keycapと同じ1回tapで`kind:keycap`を送信する。
- `MIC`だけは従来どおりdown／up／cancelでPush-to-talkを制御する。
- Danger画面、赤いApp bar、グローバルメニューの危険操作項目、警告dialog、nonce更新UIを削除する。
- 選択中キー詳細はregistry解決状態、action型、Codex画面条件、最終結果を表示する。
- 現在状態で成立しないKeycapは恒久無効化せず、Relay/native handlerが返した具体的理由を最終結果へ残す。
- Controlの動的Action slotへ`APPR`、`REJ`、`DEL`が割り当てられていても、他slotと同じdown／up操作を送信する。

## 4. Relay／Protocol

- Protocolを2へ上げ、`danger-arm` commandを削除する。
- `keycap`は公式30 IDを受理する。`MIC`は`act:0|1`必須、それ以外29キーは`act`なしの通常commandとする。
- `confirmationNonce`と`confirmedHoldMs`をprotocolから削除し、付与された要求は拒否する。
- Relay serverのconfirmation保管、期限、task束縛、一回性検証を削除する。
- dynamic Action slotのAPPR／REJ／DEL拒否guardを削除する。
- `keycapCapabilities`は後方互換のため`danger`フィールドを残すが、30件すべて`false`とする。
- `availableKeycaps`と`keycapCapabilities`はAction slot割当数に依存せず30件を返す。
- WebSocket切断時のMIC best-effort stopは維持する。

## 5. 自動試験

### Android

- 公式Keycapが30件である。
- APPR／REJ／DELを含む30件が検索・カテゴリ対象になる。
- APPR／REJ／DELの通常`keycap` commandがallowlistを通る。
- danger-arm、nonce、confirmedHoldMs付きcommandを生成・許可しない。
- MICだけがact必須である。
- capability 30件の`danger=false`をparseできる。

### Relay

- Protocol 2で認証、snapshot、commandを処理する。
- APPR／REJ／DELをnonceなしの通常Keycapとして受理する。
- APPR／REJ／DEL割当Action slotを通常処理する。
- danger-arm、nonce、confirmedHoldMs付きcommandを拒否する。
- capability 30件、`danger=true` 0件である。
- test、TypeScript check、build、Windows package、auditを通す。

## 6. 実機確認

- Android 0.2.6をPixelへ上書き導入する。
- Relay 0.2.8をWindowsへ上書き導入する。
- PaletteのUI dumpで30キー、APPR／REJ／DEL各1件、Danger画面0件を確認する。
- APPR／REJは承認要求が存在する場合だけ実行結果まで確認し、要求を捏造しない。条件不成立はBLOCKEDまたは具体的不成立として記録する。
- DELは破棄可能な一時taskだけをarchiveし、既存taskを対象にしない。
- crash、ANR、接続状態、MIC押下残存を確認する。

## 7. 文書・版管理

- `IMPLEMENTATION_PLAN.md`を文書版2.3へ更新する。
- `IMPLEMENTATION_REPORT.md`を文書版3.3へ更新する。
- README、機能inventory、protocol説明の現行記述を30キー仕様へ統一する。
- AndroidとRelayを別commitにし、各commit ID、APK／ZIP SHA-256、試験結果、未実施条件を報告書へ記録する。
- Mac実機未検証を維持し、検証済みと表現しない。

## 8. 完了条件

- Android UI、Android command allowlist、Relay parser、Relay server、capabilityが同じ30キー仕様で一致する。
- Danger専用経路とnonce実装が実行コードから消えている。
- 自動試験とbuildがPASSする。
- 実機未実施項目をPASSへ読み替えず、実装報告書へ残す。
