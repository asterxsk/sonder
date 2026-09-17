# Sonder v2 — native Android (Kotlin + Compose)

Pixel-art screen-time blocking. You choose the apps; opening one forces a hand of
blackjack. Win → 5 minutes of access. Lose → +10 minutes of lockout **debt**
(capped at 60 minutes). Keep playing to pay debt off — win at zero debt grants
access. Walk away with debt and you're locked out until it's served. Leave a
granted app for more than 60 seconds and access is revoked, time remaining or not.

**v2 is a full native rewrite of the Flutter v1** (which still lives at `lib/`
and `android/` in the repo root) using Jetpack Compose, Hilt, and Room. It
implements the Pixel UI v3 design system in [`../docs/design/design_v3.md`](../docs/design/design_v3.md).
Enforcement deep-dive: [`../docs/android-native-v2.md`](../docs/android-native-v2.md).

## Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # domain rules tests (27 tests)
```

Requires JDK 17 via `JAVA_HOME` and an Android SDK with platform 37. Gradle
9.3.1 comes via the wrapper (SHA-256 pinned). The committed `gradle.properties`
never pins a JDK path — set `JAVA_HOME` in your environment instead.

Debug builds shorten timers (`ACCESS_WINDOW_MILLIS = 60_000`,
`ABSENCE_REVOKE_MILLIS = 20_000` in `build.gradle.kts`) so enforcement rules
can be verified on-device in seconds. `minSdk 26`, `targetSdk 36`.

## Rules

| Event | Result |
| --- | --- |
| Win with zero debt | +5:00 access |
| Loss | +10:00 debt (cap 60:00) |
| Win with debt | pays 10:00 of debt |
| Push | free replay |
| Walk away with debt | locked until served |
| Absent >60 s during a grant | revoked |

## Architecture

```
app/src/main/java/com/example/sonder/
├── domain/            pure rules — no Android (fully unit-tested)
│   ├── BlackjackRules.kt    deck, deal, dealer-stands-on-17, settle
│   ├── AccessPolicy.kt      debt model, 60-min cap, absence revocation
│   └── model/               Card, Hand, EnforcementState, snapshots
├── data/
│   ├── db/            Room: targets, grants, debt, lockouts, hands
│   ├── repo/          EnforcementRepository (single mutation point)
│   └── settings/      DataStore (onboarding flag)
├── platform/
│   ├── accessibility/ foreground detection + surface classification
│   ├── foreground/    authoritative foreground re-check (usage access)
│   ├── overlay/       GateOverlayHost — the blocker window hosting the gate
│   ├── enforcement/   coordinator: decisions → show/hide the blocker
│   ├── notifications/ channels + expiry notifications
│   ├── permissions/   audit + 10-second delayed on-open check + deep links
│   └── scheduling/    boot receiver, grant-expiry alarms
└── ui/
    ├── gate/          the blackjack gate: state machine + composable UI
    ├── kit/           PixelKit: panels, buttons, tabs, timer, badges, toasts
    └── screens/       onboarding, home, targets, stats, settings
```

Timers are absolute epoch millis everywhere: grants, lockouts and debt survive
process death and reboots.

## Permissions

Granted during onboarding (each step explains why and deep-links to its settings
page): Accessibility, Display over other apps, Usage access, Notifications.
If any is later revoked, the next app open re-checks after 10 seconds (letting
flaky accessibility state settle) and pops a pixel-styled prompt with direct
links. No `QUERY_ALL_PACKAGES` — only launchable apps are visible.

## CI/CD

`.github/workflows/android-v2.yml` builds and unit-tests every push to
`main`/`sonder-v2`, uploads the debug APK, and builds a release APK — signed
automatically when the `SONDER_KEYSTORE_B64`, `SONDER_KEYSTORE_PASSWORD`,
`SONDER_KEY_ALIAS`, `SONDER_KEY_PASSWORD` secrets are configured (keystore as
base64) — the Android debug key otherwise, which installs for sideloading but
isn't publishable to Play. The release job fires on `sonder-v2` pushes.

`.github/workflows/release-v2.yml` publishes that release APK as a GitHub
Release on `v*` tags or a manual run: it gates on the unit tests, takes
`versionName` from the tag (`versionCode` from the workflow run number), checks
the built APK's version and signer, then uploads `sonder-v2-<version>.apk`.
