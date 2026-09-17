# Sonder

> Be here, not everywhere.

Sonder is an Android screen-time app with a twist: when you open an app you've
decided to limit, you must win a hand of blackjack to get in. Losing doesn't
just cost the hand — it adds lockout **debt** you have to play off or wait out.

**Two implementations live in this repo:**

| | v2 (current, `main`) | v1 (legacy) |
| --- | --- | --- |
| Stack | Native Kotlin + Jetpack Compose | Flutter |
| Location | [`android-native/`](android-native/) | `lib/` + `android/` |
| Rules | **Debt model** (below) | Hard lockout, 20 s revoke |
| Docs | [`docs/android-native-v2.md`](docs/android-native-v2.md) | [`docs/android-enforcement.md`](docs/android-enforcement.md) |

---

## Sonder v2 — native Android (Kotlin + Compose)

Pixel-art UI (amber on brown-black, Press Start 2P + DM Mono, hard frames,
stepped shadows) implementing the design system in
[`docs/design/design_v3.md`](docs/design/design_v3.md).

### Access rules (v2)

All timestamps are absolute UTC epoch millis; rules live in
[`android-native/.../domain/AccessPolicy.kt`](android-native/app/src/main/java/com/example/sonder/domain/AccessPolicy.kt).

| Event | Result |
| --- | --- |
| Blackjack win with zero debt | Grant access for **5 minutes** |
| Blackjack loss | **+10 minutes of debt** (capped at **60 minutes**) |
| Win while carrying debt | Pay off 10 minutes of debt (no access yet) |
| Push | Free replay — nothing changes |
| Walk away with debt | Locked out until the full debt is served |
| Absent from a granted app for **>60 s** | Grant revoked, regardless of time left |

So `L, L, W, W, W` → debt 20 → 10 → 0 → access. You can keep gambling through
a loss; walking away is what costs you the wait.

Dealer stands on all 17s (including soft 17). Natural blackjack is an instant
win. No wagers, wallet, ads, analytics, or account — "hand", "earn", "grant",
never bet/wager/chips.

### Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # domain rules tests (27 tests)
```

Requires JDK 17 (`JAVA_HOME`) and an Android SDK with platform 37. Gradle
9.3.1 comes via the wrapper (SHA-256 pinned). Debug builds shorten timers
(`ACCESS_WINDOW_MILLIS`, `ABSENCE_REVOKE_MILLIS` in `app/build.gradle.kts`) so
enforcement can be verified on-device in seconds. `minSdk 26`, `targetSdk 36`.

### Architecture

```text
UI (Compose) ─ ViewModels (Hilt) ─ Domain (pure, unit-tested)
                                  ├─ BlackjackRules  deck · S17 · naturals
                                  └─ AccessPolicy    debt · cap · revocation
        │
Room (targets, grants, debt, lockouts, hands) + DataStore (settings)
        │
Platform
  ├─ AccessibilityService   foreground detection + surface classification
  ├─ ForegroundResolver     authoritative foreground re-check (usage access)
  ├─ GateOverlayHost        the blocker: a service-owned SYSTEM_ALERT_WINDOW
  │                         overlay window hosting the blackjack gate
  ├─ PermissionAudit        checks + 10-second delayed on-open prompt
  └─ Scheduling             boot receiver, grant-expiry alarms
```

Full module map: [`android-native/README.md`](android-native/README.md).

### Permissions

Granted during onboarding, each with a plain-English explanation and a
deep link: **Accessibility** (foreground app detection), **Display over other
apps** (instant block), **Usage access** (app picker/stats), **Notifications**
(expiry/lockout notices). If any is later revoked, the next app open re-checks
after **10 seconds** (letting flaky accessibility state settle) and pops a
pixel-styled prompt with direct links. No `QUERY_ALL_PACKAGES` — only
launchable apps are visible. No root, no VPN, no device-owner APIs.

Enforcement internals, edge cases, and the QA checklist:
[`docs/android-native-v2.md`](docs/android-native-v2.md).

### CI/CD

`.github/workflows/android-v2.yml` on every push/PR touching `android-native/`:

- **Build + unit tests** — assembles the debug APK, runs the test suite,
  uploads both as artifacts
- **Release** — builds a release APK; **signed automatically** when the
  `SONDER_KEYSTORE_B64`, `SONDER_KEYSTORE_PASSWORD`, `SONDER_KEY_ALIAS`,
  `SONDER_KEY_PASSWORD` secrets are configured (keystore as base64). Without
  them it falls back to the Android **debug key** — installable for sideloading,
  but not publishable to Play

`.github/workflows/release-v2.yml` on `v*` tags or a manual run: gates on the
unit tests, builds the release APK with the tag as `versionName` and the
workflow run number as `versionCode`, verifies both plus the signer, and
publishes it as a GitHub Release (`sonder-v2-<version>.apk`).

### First-run setup

1. Install the APK (CI artifacts or local build).
2. Complete the onboarding wizard — grant all four permissions (each step
   deep-links to the right settings page).
3. Toggle apps ON in **Targets**.
4. Open a target app → the gate appears → play blackjack.

---

## Sonder v1 — Flutter (legacy)

The original Flutter implementation is kept for reference in `lib/`,
`android/`, `ios/`, and `macos/`, with its own tests under `test/`.

- Rules differ from v2: a loss is a flat 10-minute lockout, and leaving a
  granted app for 20 consecutive seconds revokes it.
- Includes best-effort YouTube Shorts / Instagram Reels surface detection;
  whole-app targets are the reliable path.
- Quick start: `flutter pub get && flutter analyze && flutter test && flutter run`
  (application ID `app.sonder.sonder`).
- Enforcement, permissions, and Play-review notes:
  [`docs/android-enforcement.md`](docs/android-enforcement.md).

v1 receives maintenance only; new work happens in v2.

## Privacy

Both versions store targets, onboarding state, and enforcement timestamps
locally. No network service, account, or analytics SDK. The accessibility
service reads only the foreground window package — never screen content, URLs,
or view-tree text.

## License

No license has been declared yet. Treat the repository as all-rights-reserved
until a license is added.
