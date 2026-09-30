# Sonder

> Be here, not everywhere.

Sonder is an Android screen-time app with a twist: when you open an app you've
decided to limit, you must win a hand of blackjack to get in. What you win is a
**bank of time** — and it only runs down while the app is actually in front of
you.

Sonder is a single native Android app — Kotlin and Jetpack Compose in
[`android-native/`](android-native/). An earlier Flutter prototype was replaced
by the native implementation so foreground detection and the block gate run as
one native platform process.

That Flutter app is retired. Its sources are no longer in this tree, and its
last commit is preserved under the tag
[`v1-final`](https://github.com/asterxsk/sonder/releases/tag/v1-final) if the
old behaviour ever needs reading. `main` is the native app; the
[`v1.0.0`](https://github.com/asterxsk/sonder/releases/tag/v1.0.0) release is
the Flutter build and is not maintained.

---

## Native Android (Kotlin + Compose)

Pixel-art UI (amber on brown-black, Press Start 2P + DM Mono, hard frames,
stepped shadows) implementing the design system in
[`docs/design/design_v3.md`](docs/design/design_v3.md).

### Access rules

The bank lives in `time_bank` (one row per package); the rules are in
[`android-native/.../domain/AccessPolicy.kt`](android-native/app/src/main/java/com/example/sonder/domain/AccessPolicy.kt).

| Event | Result |
| --- | --- |
| Bank above zero | The app opens — no hand, no gate |
| Bank at zero | The gate: only a won hand gets you in |
| Blackjack win | **Bank + the stake**, clamped to the app's maximum |
| Blackjack loss | **Bank − the stake**, floored at zero — never a debt |
| Push | Free replay — nothing changes |
| Time in a banked app | Drained at the rate you use it, in steps of ≤ 10 s |
| Time away from it | Not billed; the bank waits, and nothing revokes it |
| Bank drained to zero | Removal of that limit is refused for **12 h** (a win clears it) |

Stakes are chips: **2:00**, **5:00**, **10:00**, or **ALL IN** (the whole bank,
unavailable at zero). So `bet 5:00, win` → 5:00 banked; `bet 5:00, lose` → back
to 0:00; `ALL IN on 15:00, win` → 30:00, up to the ceiling you set (default
**60:00**, presets 30:00 / 1:00 / 2:00 / 3:00). Every app starts each day at
zero, and nothing carries past midnight.

Dealer stands on all 17s (including soft 17). Natural blackjack is an instant
win. No wagers, wallet, ads, analytics, or account — the chips are minutes you
already won, never money.

### Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests (156; 106 of them the pure domain)
```

Requires JDK 17 (`JAVA_HOME`) and an Android SDK with platform 37. Gradle
9.3.1 comes via the wrapper (SHA-256 pinned). There are no shortened debug
timers: the only clock that matters is foreground use, which you can watch
drain. `minSdk 26`, `targetSdk 36`.

### Architecture

```text
UI (Compose) ─ ViewModels (Hilt) ─ Domain (pure, unit-tested)
                                  ├─ BlackjackRules  deck · S17 · naturals
                                  ├─ AccessPolicy    bank · ceiling · removal lock
                                  ├─ GateDecider     PASS · GRANTED · GATE
                                  └─ ShortsCatalog   Reels · Shorts · Stories
        │
Room (targets, time_bank, hands) + DataStore (settings)
        │
Platform
  ├─ AccessibilityService   foreground detection, media pause, PiP probe
  ├─ ForegroundResolver     authoritative foreground re-check (usage access)
  ├─ GateOverlayHost        the blocker: a service-owned SYSTEM_ALERT_WINDOW
  │                         overlay window hosting the blackjack gate
  ├─ PermissionAudit        checks + 10-second delayed on-open prompt
  └─ Scheduling             boot receiver (warms the bank cache)
```

Full module map: [`android-native/README.md`](android-native/README.md).

### Permissions

Granted during onboarding, each with a plain-English explanation and a
deep link: **Accessibility** (foreground app detection), **Display over other
apps** (the window the gate is drawn on), **Usage access** (the fallback that
says which app is in front), **Notifications** (one heads-up when a bank runs
dry — optional, and the wizard moves on without it). If a required one is later
revoked, the next app open re-checks after **10 seconds** (letting flaky
accessibility state settle) and pops a pixel-styled prompt with direct links.
No `QUERY_ALL_PACKAGES` — only launchable apps are visible. No root, no VPN, no
device-owner APIs.

Enforcement internals, edge cases, and the QA checklist:
[`docs/android-native-v2.md`](docs/android-native-v2.md).

### CI/CD

`.github/workflows/android-v2.yml` on every push/PR touching `android-native/`:

- **Build + unit tests** — assembles the debug APK, runs the test suite, and
  compiles the release variant as a smoke check (no artifact). Uploads the debug
  APK as the `sonder-v2-debug-apk` artifact.

`.github/workflows/release-v2.yml` on version tags (three dotted numbers, so a
marker tag like `v1-final` cannot start one) or a manual run: the only workflow
that builds the release APK. Gates on the unit tests, builds it with the tag (or
the manual `version` input) as `versionName` and the workflow run number as
`versionCode`, verifies both plus the signer, and publishes it as a GitHub
Release (`sonder-v2-<version>.apk`). **Signed automatically** when the
`SONDER_KEYSTORE_B64`, `SONDER_KEYSTORE_PASSWORD`, `SONDER_KEY_ALIAS`,
`SONDER_KEY_PASSWORD` secrets are configured (keystore as base64).

The release key lives at `tools/sonder-release.jks` (gitignored, with its
password alongside it) — **back it up somewhere outside this checkout**. Losing
it means no future release can install as an update over an existing one, which
is the whole reason it exists: without those secrets the workflow falls back to
the Android **debug key**, and a debug key is generated fresh on every runner, so
each release came out signed by a *different* key and `adb install` refused every
update with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Sideloadable, one build at a
time, never upgradeable.

### First-run setup

1. Install the APK (CI artifacts or local build).
2. Complete the onboarding wizard — grant the three required permissions (each
   step deep-links to the right settings page; notifications can be skipped).
3. Add the apps you want to limit in **Targets** — `ADD` opens a picker; tap apps to
   select them and `DONE` to confirm. Each row's `›` arrow opens that app's own
   settings, where the maximum bank, the scope and `REMOVE LIMIT` live.
4. Open a target app → the gate appears → pick a chip, play blackjack.

---

## Privacy

Sonder stores targets, banks, onboarding state, and hand history locally.
No network service, account, or analytics SDK. The accessibility service reads
the foreground window's package name and its view identifiers — which are
developer-chosen resource names, never screen content — and nothing is ever
sent off the device.

## License

No license has been declared yet. Treat the repository as all-rights-reserved
until a license is added.
