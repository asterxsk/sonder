plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

// The CD workflow (.github/workflows/release-v2.yml) passes the version and, when
// the keystore secrets are configured, the signing config in as -P properties.
// Without them these fall back to the local defaults below. A blank property
// counts as absent, so `-Psonder.version.name=` can't ship an empty versionName.
val releaseStoreFile = providers.gradleProperty("sonder.release.storeFile").orNull?.takeIf(String::isNotBlank)
val releaseVersionName = providers.gradleProperty("sonder.version.name").orNull?.takeIf(String::isNotBlank)
val releaseVersionCode = providers.gradleProperty("sonder.version.code").orNull?.takeIf(String::isNotBlank)
val hasReleaseKeystore = releaseStoreFile != null

android {
    namespace = "com.example.sonder"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "com.example.sonder"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode?.toIntOrNull() ?: 1
        versionName = releaseVersionName ?: "1.0"
    }

    signingConfigs {
        // Only defined when a keystore is supplied, so the release build can fall
        // back to the debug key (see buildTypes.release) instead of failing.
        releaseStoreFile?.let { storePath ->
            create("release") {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("sonder.release.storePassword").orNull
                keyAlias = providers.gradleProperty("sonder.release.keyAlias").orNull
                keyPassword = providers.gradleProperty("sonder.release.keyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug key keeps assembleRelease producing an installable APK locally;
            // the CD workflow overrides it with the real keystore via -P properties.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Debug builds shorten timers so enforcement rules can be verified on-device in seconds.
            buildConfigField("long", "ABSENCE_REVOKE_MILLIS", "20000L")
            buildConfigField("long", "ACCESS_WINDOW_MILLIS", "60000L")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // DI
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  // Persistence
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)

  // Background work (periodic permission audit)
  implementation(libs.androidx.work.runtime.ktx)
}
