# android-native/app/build.gradle.kts

- releaseStoreFile · variable · L17-L17 — val releaseStoreFile = providers.gradleProperty("sonder.release.storeFile").orNull?.takeIf(String::isNotBlank)
- releaseStorePassword · variable · L18-L18 — val releaseStorePassword = providers.gradleProperty("sonder.release.storePassword").orNull?.takeIf(String::isNotBlank)
- releaseKeyAlias · variable · L19-L19 — val releaseKeyAlias = providers.gradleProperty("sonder.release.keyAlias").orNull?.takeIf(String::isNotBlank)
- releaseKeyPassword · variable · L20-L20 — val releaseKeyPassword = providers.gradleProperty("sonder.release.keyPassword").orNull?.takeIf(String::isNotBlank)
- releaseVersionName · variable · L21-L21 — val releaseVersionName = providers.gradleProperty("sonder.version.name").orNull?.takeIf(String::isNotBlank)
- releaseVersionCode · variable · L22-L22 — val releaseVersionCode = providers.gradleProperty("sonder.version.code").orNull?.takeIf(String::isNotBlank)
- hasReleaseKeystore · variable · L23-L24 — val hasReleaseKeystore = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }
- composeBom · variable · L100-L100 — val composeBom = platform(libs.androidx.compose.bom)
