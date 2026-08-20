# Android操作遅延・誤表示 対応報告書

作成日: 2026-08-19
文書版: 1.1
修正版Android版: 0.2.2（versionCode 4）
修正版Relay版: 0.2.4
後続訂正版: Android 0.2.5（versionCode 7）／Relay 0.2.7
原因調査書: `docs/RESPONSIVENESS_ROOT_CAUSE.md` 文書版1.0

## 1. 文書改訂履歴

| 文書版 | 日付 | 状態 | 内容 |
|---|---|---|---|
| 1.0 | 2026-08-19 | 廃止 | 再描画抑制、system bar inset、Agent atomic tap、Keycap可用性を修正 |
| 1.1 | 2026-08-20 | 現行 | 6キー制限を後続不具合として訂正し、30 capabilityへ修正 |

## 2. 対応内容

- Androidのoffline表示を`PC未接続`へ変更し、USB接続とWSS接続を区別した。
- 自動再試行時は毎回`connecting`を通知せず、offline画面全体の周期的再構築を止めた。
- system bar insetをrootへ適用し、app bar／bottom navigationの重なりを解消した。
- Agent選択をdown／up 2要求から`agent-tap` 1要求へ変更した。
- Relay内部でdown→16ms→up→対象thread確認を一操作として実行する。
- Agent選択処理中の追加Agent tapは即時拒否し、要求を蓄積しない。
- command結果送信後のsnapshot更新を非同期化し、次commandをsnapshot待ちに巻き込まない。
- Keycap Paletteの「可用性不明なら全キー有効」を撤廃した。
- Relay 0.2.4の「設定済みaction slotだけを返す」対応は後続不具合だったため撤回した。
- Relay 0.2.7は既知のlive registryから30 capabilityを返し、Android 0.2.5は通常27／Danger 3へ分離した。

## 3. 試験結果

| 試験 | 結果 |
|---|---|
| Android unit／Lint／debug build | PASS |
| Relay unit test | PASS、11/11 |
| Relay TypeScript check／build | PASS |
| npm audit | PASS、脆弱性0 |
| Pixel上書きinstall | PASS、0.2.2 / 4 |
| Pixel現行PID FATAL／ANR | PASS、0件 |
| offline 12秒gfxinfo | PASS、18 frames、jank 0% |
| frame time | 50th 5ms、90th 5ms、95th/99th 14ms |
| system bar重なり | PASS、修正後実画面で解消 |
| WSS統合Agent tap | BLOCKED、PC側CDP／Relay待受0 |

修正後画面は`docs/assets/diagnostics/pixel-after-0.2.2.png`を証跡とする。

## 4. 成果物

| 項目 | 値 |
|---|---|
| Android Git commit | `cded240` |
| Relay Git commit | `8ea9561` |
| debug APK SHA-256 | `33d1dec74d1ead8f62238d2d8943c85be372b2b0ae16077d540d9c6004609184` |
| Relay ZIP SHA-256 | `fcb3eaf043634b3f2f268f6e68b0ebfbadbbda4f9a8413d63778cd2df28b62cd` |

## 5. 判定

offline時の操作遅延と画面重なりは実機で解消した。Agent atomic tapとKeycap可用性は自動試験済みだが、現在PC側にCDP／Relay待受がないためWSS統合操作は未完了である。未実施を合格へ読み替えず、製品全体は受入保留とする。
