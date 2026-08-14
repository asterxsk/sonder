plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android plugin.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "app.sonder.sonder"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    // Keep aapt happy when strings contain apostrophes — handled via XML escaping.
    // No extra SDK constraint beyond Flutter's defaults (compileSdk 36, minSdk 24).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "app.sonder.sonder"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // DataStore for EnforcementSnapshot persistence (present in Gradle cache: 1.1.7)
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Tests: pure JVM (no robolectric) so they run offline. JUnit is pulled via
    // AGP transitive if not cached; hard dependency is optional for offline builds.
    // testImplementation("junit:junit:4.13.2")
    // testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.20")
    // Keep build offline-friendly — pure-Kotlin tests use Dart-side tests as well.
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
