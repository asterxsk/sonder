# Sonder v2 — native Android (Kotlin + Compose)

Pixel-art screen-time blocking. You choose the apps; opening one forces a hand of
blackjack. Win → 5 minutes of access. Lose → +10 minutes of lockout **debt**
(capped at 60 minutes). Keep playing to pay debt off — win at zero debt grants
access. Walk away with debt and you're locked out until it's served. Leave a
granted app for more than 60 seconds and access is revoked, time remaining or not.

**v2 is a full native rewrite of the Flutter v1** (which still lives at `android/`
via `lib/` in this repo) using Jetpack Compose, Hilt, Room and WorkManager-free
scheduling. It implements the Pixel UI v3 design system in `docs/design/`.

## Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # domain rules tests
```

Debug builds shorten timers (`ACCESS_WINDOW_MILLIS`, `ABSENCE_REVOKE_MILLIS` in
`build.gradle.kts`) so enforcement rules can be verified on-device in seconds.

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
│   ├── accessibility/ foreground detection (window-state events only)
│   ├── block/         BlockActivity — the blackjack gate
│   ├── enforcement/   coordinator + instant SYSTEM_ALERT_WINDOW overlay
│   ├── notifications/ channels + expiry notifications
│   ├── permissions/   audit + 10-second delayed on-open check + deep links
│   └── scheduling/    boot receiver, grant-expiry alarms
└── ui/
    ├── kit/           PixelKit: panels, buttons, tabs, timer, badges, toasts
    └── screens/       onboarding, home, targets, blackjack, stats, settings
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
`sonder-v2`/`main`, uploads the debug APK, and on `sonder-v2` pushes builds a
release APK — signed automatically when the `SONDER_KEYSTORE_B64`,
`SONDER_KEYSTORE_PASSWORD`, `SONDER_KEY_ALIAS`, `SONDER_KEY_PASSWORD` secrets
are configured (keystore as base64).
