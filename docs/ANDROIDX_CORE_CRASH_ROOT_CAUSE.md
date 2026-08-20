# AndroidX Core欠落クラッシュ原因調査

作成日: 2026-08-19  
対象: Codex Micro Mobile 0.2.0（versionCode 2）  
対象パッケージ: `com.simeo.codexmicromobile`

## 結論

QRスキャナーで使用する`com.journeyapps:zxing-android-embedded:4.3.0`が実行時に必要とする`androidx.core:core`と`androidx.fragment:fragment`を、アプリが直接依存関係へ宣言していなかった。公開されたZXing 4.3.0のPOMにも両依存が含まれないため、APKへ`androidx.core.content.ContextCompat`が収録されず、QRスキャナーを開くたびにメインスレッドでクラッシュした。

## 実機で確認した障害

Androidのcrash bufferを消去せず読み取った。2026-08-19 15:15:13と15:15:18に同じクラッシュが再現している。

```text
FATAL EXCEPTION: main
Process: com.simeo.codexmicromobile
java.lang.NoClassDefFoundError: androidx.core.content.ContextCompat
  at com.journeyapps.barcodescanner.CaptureManager.openCameraWithPermission(CaptureManager.java:241)
  at com.journeyapps.barcodescanner.CaptureManager.onResume(CaptureManager.java:230)
  at com.journeyapps.barcodescanner.CaptureActivity.onResume(CaptureActivity.java:41)
Caused by: java.lang.ClassNotFoundException: androidx.core.content.ContextCompat
```

## 依存関係の事実

修正前の`debugRuntimeClasspath`と`releaseRuntimeClasspath`は次だけを解決していた。

- `com.journeyapps:zxing-android-embedded:4.3.0`
- `com.google.zxing:core:3.4.1`

`androidx.core:core`と`androidx.fragment:fragment`は存在しなかった。

ZXing Android Embedded 4.3.0自身のビルド定義では、次が`implementation`として必要である。

```gradle
implementation 'androidx.core:core:1.6.0'
implementation 'androidx.fragment:fragment:1.3.6'
```

しかし同版の公開処理は`api`構成だけをPOMへ書き出すため、`implementation`の2件は利用側へ伝播しない。これはGradleが勝手に削除したのではなく、公開POMとアプリ側宣言の間に生じた依存欠落である。

参照:

- <https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0>
- <https://github.com/journeyapps/zxing-android-embedded/blob/v4.3.0/zxing-android-embedded/build.gradle>

## なぜ不完全なまま完成相当と報告したか

以前の検証はunit test、Lint、APK build、`MainActivity`の起動確認までだった。QRスキャナーを実際に開く受入操作を行わず、`CaptureActivity`のresume経路を通していない。それにもかかわらず`docs/IMPLEMENTATION_REPORT.md`へ「Activity起動とクラッシュがない」と記載した。

これは次の判定ミスである。

1. APKを生成できることと、APKが必要なruntime classを含むことを混同した。
2. ランチャーActivityの起動だけを、QRを含むアプリ機能の起動検証へ拡大解釈した。
3. 実装計画書が要求するPixel実機受入未完了の状態で、検証済み範囲を明確に限定しなかった。
4. QR操作後のcrash bufferを検査する完了ゲートを設けなかった。

## 修正方針

1. ZXing 4.3.0が宣言するAndroidX依存2件をアプリへ直接追加する。
2. Android版を0.2.1（versionCode 3）へ上げる。
3. debug／release runtime classpathに両依存があることを確認する。
4. debug／release APKを再生成し、DEX内に`ContextCompat`があることを確認する。
5. Pixelへ上書き導入し、QRスキャナーを起動する。
6. 新規クラッシュがないことをlogcatで確認する。

完全な22項目E2EとLAN接続試験が終わるまで、製品全体は完成扱いにしない。
