import java.util.*

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  kotlin("plugin.serialization")
}

val secret = Properties().apply {
  if (project.rootProject.file("local.properties").exists())
    load(project.rootProject.file("local.properties").inputStream())
}

android {
  namespace = "com.peco2282.sigman"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.peco2282.sigman"
    minSdk = 29
    targetSdk = 36
    versionCode = 1
    versionName = System.getenv("GITHUB_REF_NAME") ?: "v1.3.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "signing-key.jks")
      storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: secret["store.password"].toString()
      keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: secret["key.alias"].toString()
      keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: secret["key.password"].toString()
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
  }

  lint {
    // エラーが見つかってもビルドを中断しない
    abortOnError = false
    // チェックをスキップしたい場合は以下も有効
    checkReleaseBuilds = false
  }

  sourceSets {
    getByName("main") {
      assets.srcDirs("src/main/assets")
    }
  }
}

dependencies {

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.activity:activity-compose:1.12.4")
  implementation(platform("androidx.compose:compose-bom:2024.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.google.android.gms:play-services-location:21.3.0")
}