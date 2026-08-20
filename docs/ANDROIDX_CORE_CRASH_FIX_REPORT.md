# AndroidX Core欠落クラッシュ対応報告

作成日: 2026-08-19  
対象: Codex Micro Mobile 0.2.1（versionCode 3）  
関連: [AndroidX Core欠落クラッシュ原因調査](ANDROIDX_CORE_CRASH_ROOT_CAUSE.md)

## 対応結果

`androidx.core.content.ContextCompat`がAPKへ入らない依存欠落を修正した。0.2.1 debug APKを接続中のPixelへ上書き導入し、端末に実際にインストールされた`base.apk`のDEXにも`ContextCompat`が収録されていることを確認した。

端末は指紋ロック画面のため、アプリUIからQRスキャナーを開く最終操作は未実施である。したがって「QRスキャナー実機受入完了」や「アプリ完成」とは判定しない。

## 変更

`app/build.gradle.kts`を変更した。

- `versionCode`: 2 → 3
- `versionName`: 0.2.0 → 0.2.1
- `androidx.core:core:1.6.0`を直接追加
- `androidx.fragment:fragment:1.3.6`を直接追加

AndroidXのバージョンは、ZXing Android Embedded 4.3.0自身のビルド定義と一致させた。無関係な依存更新は混在させていない。

## 検証

| 検証 | 結果 |
|---|---|
| debug runtime classpath | `androidx.core:core:1.6.0`あり |
| release runtime classpath | `androidx.core:core:1.6.0`あり |
| fragment runtime dependency | `androidx.fragment:fragment:1.3.6`あり |
| unit test | 13件成功、failure 0、error 0 |
| release Lint | error 0、更新通知warning 4 |
| debug APK build | 成功 |
| release APK build／R8 | 成功 |
| debug APK DEX | `Landroidx/core/content/ContextCompat;`を確認 |
| Pixel上書きinstall | 0.2.0 → 0.2.1、成功 |
| Pixel内`base.apk` DEX | `ContextCompat`を確認 |
| MainActivity起動 | 成功、既存crash count増加なし |
| QR Activity直接CLI起動 | 非exported ActivityのためAndroidが拒否。アプリ異常ではない |
| アプリUIからQR起動 | 端末が指紋ロック中のため未検証 |

生成物:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/apk/release/SHA256SUMS`
- `app/build/reports/release-dependencies.txt`
- `app/build/reports/lint-results-release.html`

## ハッシュ

```text
debug APK SHA-256:
95abd3d96d4cfcaf9c153ed95c40679f6196c145571d1896a296583df42700d3

unsigned release APK SHA-256:
58b01be609ed02efb06d4282d1a3a49ce4c81153387723629e8982757707574c
```

## 未完了

端末を本人が解除した後、アプリ内の「QRをスキャン」から`CaptureActivity`を開き、カメラ許可画面またはプレビュー到達と、新規`FATAL EXCEPTION`がないことを確認する必要がある。

この確認に加えて、計画書のPixel 4画面確認、22項目E2E、USBを外したLAN WSS試験、署名済みrelease APKが未完了である。これらを終えるまで製品全体を完成と報告しない。

外部公開、送信、認証情報変更、アプリデータ削除、logcat消去は行っていない。
