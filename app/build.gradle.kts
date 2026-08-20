import java.security.MessageDigest

plugins {
  id("com.android.application")
}

android {
  namespace = "com.simeo.codexmicromobile"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.simeo.codexmicromobile"
    minSdk = 26
    targetSdk = 36
    versionCode = 8
    versionName = "0.2.6"
  }

  signingConfigs {
    create("release") {
      val signingStore = System.getenv("CODEX_MICRO_KEYSTORE")
      if (!signingStore.isNullOrBlank()) {
        storeFile = file(signingStore)
        storePassword = System.getenv("CODEX_MICRO_STORE_PASSWORD")
        keyAlias = System.getenv("CODEX_MICRO_KEY_ALIAS")
        keyPassword = System.getenv("CODEX_MICRO_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (!System.getenv("CODEX_MICRO_KEYSTORE").isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  // 5.5.0 requires compileSdk 37; this project intentionally remains on the Android 16 / API 36 toolchain.
  implementation("com.squareup.okhttp3:okhttp:5.4.0")
  implementation("com.journeyapps:zxing-android-embedded:4.3.0")
  // ZXing 4.3.0 uses these internally but its published POM omits its
  // implementation dependencies. Keep the versions declared by that release.
  implementation("androidx.core:core:1.6.0")
  implementation("androidx.fragment:fragment:1.3.6")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20260814")
}

tasks.register("releaseDependencyList") {
  val output = layout.buildDirectory.file("reports/release-dependencies.txt")
  outputs.file(output)
  doLast {
    val components = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
      .map { it.moduleVersion?.toString() ?: it.id.displayName }.distinct().sorted()
    output.get().asFile.apply { parentFile.mkdirs(); writeText(components.joinToString("\n", postfix = "\n")) }
  }
}

tasks.register("releaseApkSha256") {
  dependsOn("assembleRelease")
  val output = layout.buildDirectory.file("outputs/apk/release/SHA256SUMS")
  outputs.file(output)
  doLast {
    val apks = layout.buildDirectory.dir("outputs/apk/release").get().asFile.listFiles { file -> file.extension == "apk" }.orEmpty()
    val lines = apks.sortedBy { it.name }.map { apk ->
      val hash = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
      "$hash  ${apk.name}"
    }
    output.get().asFile.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
  }
}
