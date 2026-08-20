# Palette上段以外のKeycapが使用できない 原因調査報告書

作成日: 2026-08-20
文書版: 1.1
対象Android版: 0.2.4（versionCode 6）
対象Relay版: 0.2.6

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-20 | 廃止 | 通常Paletteで上段相当の6キー以外が無効になる原因を確定 |
| 1.1 | 2026-08-20 | 現行 | 27キー全件試験で判明したlive smoke NIC誤選択、OAI fallback、CDP切断を追補 |

## 2. 現象

Androidの公式Keycap Paletteには30キーが表示されるが、PC側の6つのMicro Action slotへ割り当てられたキーだけが通常濃度かつ操作可能になり、それ以外は灰色で無効になる。

Pixel実画面では`FAST`、`APPR`、`REJ`、`SPLIT`、`MIC`、`CODEX`が利用可能になり、残り24キーを操作できなかった。

## 3. 直接原因

Androidは次の条件でPaletteボタンを有効化していた。

```text
接続がreadyである
かつ
keycap IDがsnapshot.availableKeycapsに含まれる
```

Relay 0.2.6は`availableKeycaps`をCodexの公式Keycap registryから生成せず、現在の6 Action slotの`keycapId`だけから生成していた。そのためAndroidへ最大6件しか通知されず、Androidは通知されない24件を設計どおり無効化した。

該当する設計はRelayの`codex-micro-renderer-bridge.ts`にあり、コメントと自動試験でも「configured action slot is the only keycap availability」を正しい条件として固定していた。

## 4. 背景原因

Relayの旧実装には、snapshot取得時に多数のCodex lazy moduleを探索し、型が確定していない関数へ`FAST`を渡す処理があった。この処理がCodex rendererのerror boundaryやsnapshot不安定化を引き起こしたため、Relay 0.2.4で探索を撤廃した。

その修正時に、既知の`codex-micro-layout-*` moduleだけから公式registryを安全に読む方式へ置き換えず、「Action slotに設定済みなら存在を証明できる」という最小情報へ縮退した。安全性修正が製品機能を6キーへ制限する回帰になった。

## 5. 現行Codexの実測

CDPを読み取り専用で調査し、現行Codexの`codex-micro-layout-*` registryについて次を確認した。

| 種別 | 件数 | 内容 |
|---|---:|---|
| `command` | 28 | Codex内部command |
| `external-url` | 1 | `OAI` |
| `named` Push-to-talk | 1 | `MIC` |
| **合計** | **30** | 公式30 IDすべてregistry解決成功 |

したがって、残り24キーがCodexに存在しないことは原因ではない。Relayがavailabilityを6 Action slotへ限定したことが原因である。

## 6. UI設計上の追加不備

- 通常Paletteへ`APPR`、`REJ`、`DEL`を混在させていた。
- registry解決状態、現在画面の実行条件、最終実行結果を区別して表示していなかった。
- Push-to-talkである`MIC`を通常の1回commandとして扱い、押下／解放の契約を持っていなかった。
- 通常Palette 27キーとDanger専用画面3キーを分離する受入試験がなかった。

## 7. 試験判定の不備

`TEST_REPORT.md`文書版1.6では、Palette 30キーの表示と`FAST` 1キーの実行だけで関連項目をPASSにした。24キーが無効であること、危険3キーが通常Paletteに混在すること、MICの押下／解放を検証していないため、この範囲のPASS判定は不十分だった。

修正版では次を別々に合格判定する必要がある。

1. 通常Paletteに安全な27キーだけが表示され、すべて操作可能である。
2. `APPR`、`REJ`、`DEL`は通常Paletteに存在しない。
3. 危険3キーは警告を経たDanger専用画面だけに存在する。
4. Relayが公式registryの30件を解決し、Action slot割当をavailability条件にしない。
5. MICの開始／停止、危険キーの長押し／nonceを検証する。

## 8. 判定

本件はAndroid端末、通信L3、Pixelのスリープによる問題ではない。Relayのavailability生成条件、AndroidのPalette構成、受入試験の設計不備による製品側回帰である。

修正内容と実測結果は`docs/PALETTE_KEYCAP_AVAILABILITY_FIX_REPORT.md`文書版1.3へ記録する。

## 9. 全27キー試験で判明した後続原因

6キー制限修正後に全27 IDをlive WSSで実行した結果、次の3件を追加検出した。

1. `launcher/live-smoke.mjs`だけが最初に列挙されたprivate IPv4を選び、Relay本体が選ぶ既定経路と一致しなかった。`<VIRTUAL_NIC_IP>`へ接続して`ECONNREFUSED`となり、実待受`<PC_PRIVATE_IP>`を試験していなかった。
2. `OAI`はlive registryで`ready`だったが、現在buildに`vscode-api-*` resourceがない場合のexternal URL fallbackを持たず、実行時に失敗した。
3. 現在画面で不成立のkeycapまでCDP接続を切断していたため、直後の無関係なkeycapがbridge未接続エラーになる連鎖不具合があった。また画面遷移を伴う`SPLIT`では、native command成立後のrenderer切替を失敗応答としていた。

直接原因は、capability解決と実行時依存関係の差、操作不成立とtransport障害の未分離、画面遷移後の成功確認不足である。AndroidやL3は原因ではない。
