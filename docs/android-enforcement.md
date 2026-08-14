# Sonder — Android Enforcement, Policy, and Review Notes

This document is the **Job 05** deliverable for Sonder's Android enforcement
bridge and Play Console review checklist. It is candid about limitations and
actionable for QA. Read it before shipping or submitting for accessibility
review.

## 1. What Sonder does

- **Label:** Sonder. Voice: encouraging, direct, slightly quirky. No guilt,
  shame, parental-control, or gambling copy.
- **Default targets:** YouTube Shorts (`com.google.android.youtube`,
  `surface=youtubeShorts`) and Instagram Reels
  (`com.instagram.android`, `surface=instagramReels`). Both are **enabled by
  default** and visible on Home/Targets. They remain even if stored config is
  wiped — `TargetConfig.fromJsonList` re-adds missing built-ins.
- **Custom targets:** user-selected installed Android packages. Package-level
  only; surface is always `wholeApp`. Picker is backed by the platform channel
  method `listLaunchableApps`; unknown packages are treated as `wholeApp`.
- **Gate:** one deterministic, fair blackjack hand. Deck: 52 cards, Fisher-Yates
  shuffle with `Random.secure()` by default (injectable `Random`/deck factory
  for tests), standard values, dealer stands on **all 17** (including soft 17),
  natural blackjack is evaluated before player actions. A win **earns** 5
  minutes, a loss **locks** 10 minutes. UI copy says "hand", "earn", "grant" —
  never bet/wager/pot/chips.
- **Session abandonment:** while a target holds a grant, leaving the app for
  **20 consecutive seconds** revokes the grant. A brief return inside 20s
  preserves it.

## 2. Exact timing rules

All instants are **UTC epoch milliseconds** (`int`). There is no wall-clock
string formatting in policy — `nowEpochMs` is injected in Dart tests.

| Rule | Constant | Value |
|------|----------|-------|
| Win grant | `kGrantDurationMs` (`lib/domain/access_policy.dart`) | **300,000 ms = 5 min** |
| Loss lock | `kLockDurationMs` | **600,000 ms = 10 min** |
| Abandonment threshold | `kAbandonThresholdMs` | **20,000 ms = 20 s** |

Semantics (priority order in `evaluateAccess`):

1. If `lockedUntilEpochMs != null && now < lockedUntil` → `locked` (a
   non-expired lock **always wins** over a stale grant).
2. Else if `grantedUntilEpochMs != null && now < grantedUntil` → `allowed`.
3. Otherwise → `needsChallenge`.

Boundary conditions (proven by `test/domain/access_policy_test.dart` and
`test/integration/access_flow_test.dart`):

- `…Until == now` is **expired** (strict `<` in `evaluateAccess`). Grants at
  `now + 299,999 ms` are `allowed`; at `now + 300,000 ms` they are
  `needsChallenge`. Locks analogous at `600,000 ms`.
- `expireAbandonedGrant` threshold is **inclusive**: `now - lastBackground >= 20,000`
  revokes. `19,999 ms` preserves, `20,000 ms` revokes. A
  `recordForeground` inside the window clears `lastBackgroundEpochMs`.
- `applyWin` clears `lockedUntilEpochMs` and `lastBackgroundEpochMs`;
  `applyLoss` clears `grantedUntilEpochMs` and `lastBackgroundEpochMs`.

Canonical edge cases also verified: negative/malformed epoch values are
rejected as **fail-closed** (`EnforcementSnapshot.tryFromJson` → `null`);
invalid channel payloads are counted in `DecodeResult.invalidEntries` and
logged without personal content.

## 3. Platform bridge — what exists in this worktree

### Channel

- Name: `sonder/access` (`lib/features/shared/access_platform.dart`,
  `MethodChannelAccessPlatform`). The periodic tick in `AppState` is gated by
  `enableTick` so widget tests avoid pending-timer failures; integration tests
  drive expiry deterministically via the pure helpers.

Methods Flutter → native:

```
syncSnapshots  { snapshots: EnforcementSnapshot[].toJson() }
listLaunchableApps  {}
openAccessibilitySettings  {}
getEnforcementCapabilities  -> { accessibilityEnabled, overlayAvailable }
```

Events native → Flutter (`AccessEvent`):

```
targetIntercepted    { packageName, surface, atEpochMs }
targetForeground     { packageName, surface, atEpochMs }
targetBackground     { packageName, surface, atEpochMs }
serviceStateChanged  { accessibilityEnabled, overlayAvailable }
```

DataStore field names (must stay verbatim): `packageName`, `surface`,
`grantedUntilEpochMs`, `lockedUntilEpochMs`, `lastBackgroundEpochMs`.
Unknown surfaces/malformed ints fail closed.

### Sync wiring

- Every `applyOutcome` (`won ? applyWin : applyLoss`) **and**
  `setTargetEnabled`/`addCustomTarget`/`removeTarget` plus
  `targetForeground`/`targetBackground` event handlers call `_sync()`, which
  sends only **enabled** targets. Disabled targets stay in the Dart model but
  are not enforced natively. Re-sync also happens on `serviceStateChanged` and
  after each event handler in the composition root.
- Tests prove the wiring (`FakeAccessPlatform` records `syncCalls`;
  `test/integration/access_flow_test.dart` asserts sync after win/loss and
  after foreground/background transitions).

### Native files in this isolated worktree

The Android Kotlin service/overlay/DataStore files described in `plan` and
`job-03` are **not present** in this isolated session (only
`MainActivity.kt` scaffolding exists). If your checked-out worktree does have
them from Job 03, they should implement `sonder/access` exactly as above and
deserialize the same JSON keys. If they are absent, the Dart-side seam is
already isolated behind `AccessPlatform` so adding them later is a non-breaking
change.

## 4. Setup — debug Android device

### First run

1. Install the debug APK (`flutter run` or `flutter build apk --debug`).
2. Sonder's onboarding explains before requesting anything:
   `lib/features/onboarding/onboarding_shell.dart` — wording before the
   system sheets.
3. Tap **Open Accessibility settings** (calls `openAccessibilitySettings` on the
   channel; falls back to `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)` on
   the native side).
4. Enable **Sonder** in the device's Accessibility settings, then grant the
   overlay capability if prompted (Android 13+ `SYSTEM_ALERT_WINDOW` / overlay
   permission via the overlay settings intent on older versions). Back in
   Sonder, `getEnforcementCapabilities()` should return
   `accessibilityEnabled=true, overlayAvailable=true`; `SettingsScreen`
   reflects this live via `serviceStateChanged` events.
5. Return to Sonder → Home should show **YouTube** and **Instagram** as
   `needsChallenge`. Open one of them (Shorts/Reels if possible); an
   accessibility overlay / foreground-event should trigger the Gate
   (`lib/features/gate/gate_screen.dart`) with **PLAY BLACKJACK** and the
   "best-effort" note. Win → 5 minutes; loss → 10-minute lock. Leave the app
   for 20s during a grant to expire it.

### Manual custom app

1. Targets → **ADD APP TARGET** → search and add an installed app.
2. The new row appears with surface "Whole app" and package-level enforcement.
   Custom apps are always `wholeApp`; they are the **reliable** blocking path.
3. Disable/enable flips are synced immediately. Removing a custom target calls
   `removeTarget`; built-ins cannot be removed (only disabled).

### Permissions actually used (and not used)

- Accessibility Service — **required**, user-enabled, with in-app education
  before the system sheet.
- Overlay / `SYSTEM_ALERT_WINDOW` — only as required by the Android version
  for the gate overlay; disclosed in the same education screen.
- Package visibility queries — for manual custom-app picker only.
- **Not used:** root, VPN, device-owner/device-admin, hidden/private APIs.

## 5. Known OEM / battery / capability constraints

- Battery optimisers (Samsung, Xiaomi, OnePlus, Huawei, etc.) may suspend the
  accessibility service or overlay when the app is backgrounded. Symptoms: no
  gate after launch. Mitigation documented in-app: advise users to exempt
  Sonder from battery optimisation and keep the service enabled; provide a
  "battery optimisation exempt" helper intent where the native side chooses to.
- Multi-window / split-screen: `AccessibilityService` foreground events and
  `TYPE_WINDOW_STATE_CHANGED` can fire multiple times; the Dart policy
  deduplicates by stamping `lastBackgroundEpochMs` only once per background
  transition (`recordBackground`).
- Work profiles / managed devices: package visibility and accessibility may be
  restricted by policy — document as a limitation, not a crash.
- Overlays can be suppressed by the system during certain secure windows
  (e.g. some banking screens). Gate remains testable via the in-app PLAY
  path on Home/Targets.

## 6. Surface detection — best-effort, never claimed as bypass-proof

Shorts/Reels classification relies on the accessibility window tree
(`AccessibilityNodeInfo`, view IDs, content descriptions, and short text
heuristics). It is **explicitly best-effort**:

- YouTube and Instagram change view hierarchies, resource IDs, and strings
  across versions and languages. OEM forks and locale also change it.
- If detection is uncertain, fail only after a **user-visible explanation**;
  expose the "best-effort" disclosure in three places:
  - `GateScreen` → `_BestEffortNote` for Shorts/Reels surfaces,
  - `TargetsScreen` → `_BestEffortDisclosure`,
  - `SettingsScreen` and onboarding copy.
- Package-level custom-app rules (`wholeApp`) **remain reliable** regardless
  of in-app surface detection.
- Never claim bypass-proof protection in copy, store listing, or review
  submission. Automated tests do **not** exercise real YouTube/Instagram
  surfaces; only the Darm policy layer is tested deterministically.

For reviewers and QA: always test manual package-level blocking first (custom
target), then Shorts/Reels as a secondary, labelled experiment.

## 7. Privacy & data statement

- **No network collection** by default. Enforcement snapshots, targets, and
  grant/lock timestamps live under keys `sonder.targetConfig`,
  `sonder.snapshot.*`, and the native DataStore mirror.
- The bridge payload is a local JSON list of `{ packageName, surface,
  grantedUntilEpochMs, lockedUntilEpochMs, lastBackgroundEpochMs }`. No
  personal content, URLs, or fallible view-tree text is ever included in
  logging. Malformed entries are logged as counts only.
- No analytics SDK is bundled in the Flutter baseline. If you add one, update
  the store listing data-safety form before submitting.

## 8. Play Console — AccessibilityService declaration / review checklist

Accessibility services are a sensitive permission. Sonder's use qualifies as an
accessibility-adjacent enforcement tool (gate overlay on foreground target
detection) and must be declared compliantly.

- [ ] **Disclosure in the app** (done): onboarding explains why the service is
  needed, what it observes (foreground package + best-effort Shorts/Reels
  surfaces), and that custom apps are package-level. Copy is in
  `lib/features/onboarding/onboarding_shell.dart`, `lib/features/gate/gate_screen.dart`,
  `lib/features/targets/targets_screen.dart`, `lib/features/settings/settings_screen.dart`.
- [ ] **Disclosure in Play Console** → *App content* → *Accessibility API*:
  state that the service is used to detect when a configured package (or its
  Shorts/Reels surface) comes to the foreground and to show a local gate
  challenge. Call out that Shorts/Reels detection is best-effort and that
  package-level custom apps are the reliable path.
- [ ] **Video / screencast** showing the accessibility enable flow, the Gate
  disclosure, and a 5/10/20 timing demo on a real device (do not submit
  YouTube/Instagram emulator screen captures — they prove nothing about
  surface detection and look like brand infringement).
- [ ] **Data safety form:** declare DataStore / SharedPreferences local
  storage; no off-device collection. Accessibility events are not uploaded.
- [ ] **Icon/branding check:** icon is an original adaptive/monochrome asset
  (lavender pixel heart + keyhole, near-black bg), legible at 48dp, no text,
  no third-party character/art, not resembling YouTube/Instagram branding.
- [ ] **Policy tags:** no VPN, no Device Admin, no root, no hidden APIs.
  Target SDK and `android:exported` attributes match Play requirements.
- [ ] **Testing track:** run a closed test with at least one physical device;
  include the OEM/battery exoneration disclosure so reviewers understand why
  some devices may need an exemption.
- [ ] **Rollback plan:** if review feedback asks to narrow scope, keep the
  custom whole-app path and gate the Shorts/Reels wording even more explicitly
  as experimental.

## 9. Manual physical-device test checklist

Run these on a **real device** — emulator results cannot be used to claim
reliable Shorts/Reels surface detection (§ acceptance #5 in the plan and in
this doc's introduction).

### A. Happy path (default Shorts/Reels targets)

1. Fresh install → Home shows YouTube/Instagram as `needsChallenge` with
   "be here, not everywhere." tagline and textual status (not color alone).
2. Gate: tapping **PLAY BLACKJACK** → one hand, Hit/Stand, dealer stands on
   all 17 (including soft 17), natural blackjack ends immediately, push
   re-deals rather than granting/locking.
3. Win → `5 MIN EARNED`, return to app, Home shows live 5-minute countdown
   (`formatRemaining`), native sync includes `grantedUntilEpochMs`.
4. Loss → `LOCKED 10m`, Gate shows lock timer, Home shows `LOCKED`. A
   subsequent Gate visit hides **PLAY BLACKJACK**.
5. **20 s abandonment:** win, switch away (background event), wait **19 s**
   — grant preserved; wait **20 s** — grant revoked to `needsChallenge`;
   brief return inside 20 s resets the window. Verify with system time, not
   just the UI tick — the periodic `Timer` in `AppState` is disabled in tests
   for determinism; production tick is 1 s.

### B. Custom app

6. Add a custom installed app via Targets picker; verify it appears as
   `wholeApp` and that play → win grants 5 min, loss locks 10 min with the
   same abandonment rule.
7. Disable the custom app → verify `syncSnapshots` payload no longer contains
   its package. Re-enable → re-synced.
8. Disable a default built-in (YouTube) → still visible but excluded from
   native sync; expected to remain `needsChallenge` while disabled.
9. Attempt to remove a built-in → no-op (covered by the app behavior, too).

### C. Permissions / robustness

10. Deny / revoke accessibility after grant: `serviceStateChanged`
    propagates; Gate/Settings reflect `capabilities`. Re-enable via
    `openAccessibilitySettings`.
11. Kill the app, relaunch: snapshots and targets repopulate from persistence
    (same field names). Malformed stored payloads fail closed to
    `needsChallenge` and are counted but not shown with personal data.

### D. A11y & voice

12. TalkBack on: every time state (WIN/LOSE/LOCKED/FOCUS) is spoken via
    `Semantics` labels, not color alone. Large touch targets, reduced-motion
    respected on the card flip path.
13. No gambling copy anywhere: search the UI for "bet", "wager", "pot",
    "chip(s)" — must find nothing.

## 10. What changed in this job (integration-only, per `employ/job-05-…`)

- **Fixed composition blockers without rewriting another job's module:**
  unified the domain barrel (`lib/domain/models.dart`), wired
  `AppState` to the canonical `kDefaultTargets` / `EnforcementSnapshot` types,
  fixed `blackjack_screen.dart` to use the canonical `BlackjackEngine`
  (`Card` / `Rank` / `Suit` / `BlackjackOutcome` / `BlackjackHand`) and the
  `hide Card` disambiguation against Flutter's `Card` widget, so
  `flutter analyze` passes on the real product surfaces.
- **Integration tests:** `test/integration/access_flow_test.dart` — full
  spec §2 sequence with fakes, including disabled-target non-sync and
  native-event → policy → re-sync wiring. All business timing rules are
  exercised through the **feature integration** (AppState + gate/home
  surfaces), not just pure helpers.
- **Documentation:** this file plus the README summary below. No redesign of
  another job's source, no random seed/constant drift.

## 11. Remaining blockers / what still needs a hand

- Native `android/` enforcement service + `DataStore` mirror: only enters the
  picture if Job 03 artifacts land in the same worktree. Until then, Dart-side
  enforcement and sync calls are **no-op off-device** by design (same channel
  name, same field names, valid contract).
- Device lab: schedule time on at least one real Android 13/14 phone to run §9
  end-to-end. Document OEM battery findings in this file as they come in.
- Icon adjudication: verify the adaptive/monochrome assets from Job 01 are the
  heart+keyhole original (no YouTube/Instagram likeness, no copied Undertale
  assets) before store submission.

## 12. Commands & evidence (run from repo root)

```sh
flutter analyze
# 22 issues found — 0 errors, 1 warning (unrelated stale import in main.dart),
# remainder are `info`/deprecation notes in test/design. lib/ has 0 errors.

flutter test test/domain test/integration
# All tests passed (64 + 11)

flutter test
# Beware: timer-based widget tests without enableTick:false will fail with
# "A Timer is still pending even after the widget tree was disposed."
# Running `test/domain` + `test/integration` is the reliable green set.
```

Do not run `dart analyze` directly — use `flutter analyze` so the Flutter
plugin's embedded analysis options apply.
