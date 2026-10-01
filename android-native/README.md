# Sonder v2 — native Android (Kotlin + Compose)

Pixel-art screen-time blocking. You choose the apps; opening one forces a hand of
blackjack. Access is a per-app, per-day **time bank**: win a hand and the bank
grows by the chip you staked; lose and it shrinks by the same. The bank only
drains while the app is actually in front, so time away is never billed. Run it
to zero and the gate comes up again, the app locked. Nothing carries over: an app
added a moment ago and a bank left over from yesterday both read zero, and both
open to the gate. What keeps that from being a wall is the table's own stake —
the **2:00** chip is dealt for whatever the bank holds, including nothing, so the
way back in is always a hand rather than a wait.

Built with Jetpack Compose, Hilt, and Room. It implements the Pixel UI v3
design system in [`../docs/design/design_v3.md`](../docs/design/design_v3.md).
Enforcement deep-dive: [`../docs/android-native-v2.md`](../docs/android-native-v2.md).

## Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests (160; 110 in the pure domain layer)
```

Requires JDK 17 via `JAVA_HOME` and an Android SDK with platform 37. Gradle
9.3.1 comes via the wrapper (SHA-256 pinned). The committed `gradle.properties`
never pins a JDK path — set `JAVA_HOME` in your environment instead.

There are no debug-only timers left to shorten: the only clock that matters is
foreground use, which is measured the same in every build. `minSdk 26`,
`targetSdk 36`.

## Rules

| Event | Result |
| --- | --- |
| A target is added | bank 0 — locked, like any bank at the start of a day |
| Stake a chip | 2:00 / 5:00 / 10:00, or ALL IN (the whole bank) — only while the bank covers it, except 2:00, which the table always deals |
| Win | bank += stake, capped at the app's max (default 60:00) |
| Loss | bank -= stake, floored at 0 — never a debt |
| Push | inert re-deal |
| Bank reaches 0 | gate up, chips above 2:00 greyed; removal refused for 12 h |

## Architecture

```
app/src/main/java/com/example/sonder/
├── domain/            pure rules — no Android (fully unit-tested)
│   ├── BlackjackRules.kt    deck, deal, dealer-stands-on-17, settle
│   ├── AccessPolicy.kt      the time bank: chips, billing, the table's stake
│   ├── AccessRules.kt       the one per-app rule: the bank's max size
│   └── model/               Card, Hand, EnforcementState, snapshots
├── data/
│   ├── db/            Room: targets, time_bank, hands
│   ├── repo/          EnforcementRepository (single mutation point)
│   └── settings/      DataStore (onboarding flag)
├── platform/
│   ├── accessibility/ foreground detection + surface classification
│   ├── foreground/    authoritative foreground re-check (usage access)
│   ├── overlay/       GateOverlayHost — the blocker window hosting the gate
│   ├── enforcement/   coordinator: decisions → show/hide the blocker
│   ├── notifications/ channels + the "time's up" heads-up
│   ├── permissions/   audit + 10-second delayed on-open check + deep links
│   └── scheduling/    boot receiver (warms the bank cache)
└── ui/
    ├── gate/          the blackjack gate: state machine + composable UI
    ├── kit/           PixelKit: panels, buttons, tabs, timer, badges, toasts
    └── screens/       onboarding, home, targets, targetpicker, blackjack, stats, settings
```

Every timestamp is absolute epoch millis, so process death and reboots never
corrupt the bank: elapsed is measured only between heartbeats that actually saw
the app in front.

## Permissions

Granted during onboarding (each step explains why and deep-links to its settings
page): Accessibility, Display over other apps, Usage access, Notifications.
If any is later revoked, the next app open re-checks after 10 seconds (letting
flaky accessibility state settle) and pops a pixel-styled prompt with direct
links. No `QUERY_ALL_PACKAGES` — only launchable apps are visible.

## CI/CD

`.github/workflows/android-v2.yml` builds and unit-tests every push/PR touching
`android-native/` on `main`/`sonder-v2`: it assembles the debug APK, runs the
unit tests, compiles the release variant as a smoke check (no artifact), and
uploads the debug APK as the `sonder-v2-debug-apk` artifact.

`.github/workflows/release-v2.yml` is the only workflow that builds the signed
release APK. On `v*` tags or a manual run it gates on the unit tests, builds
with the tag (or the manual `version` input) as `versionName` and the workflow
run number as `versionCode`, verifies both plus the signer, and publishes it as
a GitHub Release (`sonder-v2-<version>.apk`). It is signed automatically when
the `SONDER_KEYSTORE_B64`, `SONDER_KEYSTORE_PASSWORD`, `SONDER_KEY_ALIAS`, and
`SONDER_KEY_PASSWORD` secrets are configured (keystore as base64) — the Android
debug key otherwise, which installs for sideloading but isn't publishable to
Play.
