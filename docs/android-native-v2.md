# Sonder v2 — Android Enforcement Notes

Implementation notes for the native (Kotlin + Compose) Sonder. Read alongside
[`android-native/README.md`](../android-native/README.md).

## 1. Rules engine

Two pure Kotlin objects under `android-native/app/src/main/java/com/example/sonder/domain/`
carry the product rules. No Android imports; 27 unit tests cover them.

**`BlackjackRules.kt`**

- Single 52-card deck, reshuffled every hand
- Player actions: HIT / STAND only (no splits or doubles in scope)
- Dealer reveals the hole card and draws until 17+, standing on **all 17s**
  including soft 17
- Natural blackjack (ace + ten-value on the first two cards) is an instant
  win; two naturals push
- Push = free replay: debt and grants are untouched

**`AccessPolicy.kt`** — the debt model:

| Input | Effect |
| --- | --- |
| Win at zero debt | `grantedUntil = now + 5 min` |
| Loss | `debt = min(debt + 10 min, 60 min)` — the cap is hard |
| Win with debt | `debt = max(debt − 10 min, 0)`; no grant until it hits 0 |
| Push | no change |
| Walk away with debt | lockout until `now + debt` |
| Absence > 60 s during a grant | grant revoked regardless of remaining time |

Worked example: `L, L, W, W, W` → debt 20 → 10 → 0 → access granted.
At the 60-minute cap, further losses change nothing (`55 + 10 → 60`, not 65).

## 2. Time model

All timers are **absolute UTC epoch millis** stored in Room
(`GrantEntity.endAtMillis`, `LockoutEntity.untilMillis`, `DebtEntity.debtMillis`):

- Process death and device reboots never inflate or reset timers
- `BootReceiver` purges expired grants/lockouts after reboot
- `GrantExpiryScheduler` sets an inexact `AlarmManager` alarm at grant end;
  `ExpiryReceiver` revokes and notifies (no `SCHEDULE_EXACT_ALARM` permission
  needed — second-level precision is irrelevant for a 5-minute window)

Debug builds override two constants via `buildConfigField`
(`android-native/app/build.gradle.kts`): `ACCESS_WINDOW_MILLIS = 60_000` and
`ABSENCE_REVOKE_MILLIS = 20_000`, so the full loop is verifiable on a device
in about a minute.

## 3. Detection and gating flow

```text
blocked app reaches foreground
  │
  ▼
SonderAccessibilityService (TYPE_WINDOW_STATE_CHANGED only, debounced 800 ms)
  │  ignores: our own package, systemui, settings, launchers
  ▼
EnforcementCoordinator (on a background scope)
  ├─ active grant? → absence check (now − lastSeen > 60 s ⇒ revoke) → touch lastSeen
  ├─ not an enabled target? → dismiss any stale overlay, stop
  ├─ lockout active? → overlay only ("WAIT IT OUT")
  └─ otherwise → BlockOverlay (instant) + BlockActivity (the gate)
```

- **`BlockOverlay`** is a `TYPE_APPLICATION_OVERLAY` window shown the moment a
  target surfaces — before the activity can finish launching. Besides instant
  blocking, a visible overlay makes Sonder "visible" for background-activity-
  launch purposes on API 29+, which is what lets the gate start reliably.
- **`BlockActivity`** hosts the blackjack table. It dismisses the overlay the
  moment it is in front (`onGateShown`). Backing out without a grant leaves
  state untouched — the next window event re-gates.
- Absence tracking: `lastSeenMillis` is updated on every window event for a
  granted package. Absence is only *detected* on the next event (e.g. the user
  returning), which is exactly when revocation matters.

## 4. Permissions

| Permission | Type | Why | Deep link |
| --- | --- | --- | --- |
| Accessibility | special | foreground app detection | `ACTION_ACCESSIBILITY_SETTINGS` + `EXTRA_COMPONENT_NAME` |
| Display over other apps | special | instant block overlay | `ACTION_MANAGE_OVERLAY_PERMISSION` + package URI |
| Usage access | special | app picker, stats | `ACTION_USAGE_ACCESS_SETTINGS` |
| Notifications | runtime (33+) | expiry/lockout notices | app notification settings |

- **Onboarding wizard** (first launch): four steps, each with plain-English
  copy and a direct link; completes only when all are granted
- **10-second delayed audit**: every `MainActivity` open schedules a check
  10 s out — deliberately late because accessibility state is flaky right
  after boot/enabling. Anything missing pops the pixel prompt
  (`PermissionPromptActivity`) with per-permission deep links
- `SettingsScreen` mirrors the same audit with a manual re-check
- Package visibility uses a `<queries>` block for `MAIN`/`LAUNCHER` intents —
  **no `QUERY_ALL_PACKAGES`**

The accessibility service config declares `canRetrieveWindowContent="false"`
and only listens to window-state events. It reads the foreground package name
and nothing else — no text, no view tree, no content.

## 5. Persistence

Room database `sonder.db` (schema v1):

- `targets` — gated packages (package name PK, label, enabled)
- `grants` — one per package: `endAtMillis`, `lastSeenMillis`
- `debt` — one row per package: accumulated millis (cap applied by policy)
- `lockouts` — one per package: `untilMillis`
- `hands` — history: package, outcome, debt-after, timestamp (Stats screen)

`EnforcementRepository` is the single mutation point; ViewModels and the
platform layer never touch DAOs directly. Domain models stay pure; mapping
happens at the repository boundary.

## 6. Testing

```bash
cd android-native && ./gradlew testDebugUnitTest
```

- `AccessPolicyTest` (12 tests): your L,L,W,W,W example, the 60-minute cap,
  pay-down wins, push neutrality, lockout windows, absence boundaries
  (59 s safe / 61 s revoked), grant expiry, state mapping
- `BlackjackRulesTest` (15 tests): hard/soft totals, ace degradation, bust,
  naturals, deal order, dealer stands on hard and soft 17, settlement

CI (`.github/workflows/android-v2.yml`) runs the same suite on every push/PR
touching `android-native/`, plus a release-variant compile check (no artifact,
no signing). `.github/workflows/release-v2.yml` is the only workflow that builds
the signed release APK: on `v*` tags or a manual run it runs the same suite,
builds with the tag as `versionName` and the run number as `versionCode`,
verifies both plus the signer, and publishes `sonder-v2-<version>.apk` as a
GitHub Release.

## 7. Known limitations

- Whole-app enforcement only — no Shorts/Reels surface detection. Whole-app
  targets are the reliable path.
- Absence revocation is event-driven: if the user stays inside the granted
  app, nothing needs to fire; revocation lands when they return or the window
  changes. Grant end is alarm-driven, not absence-driven
- Battery optimizers / OEM killers can suspend the accessibility service; the
  10-second audit catches a disabled service at next app open. Doze may delay
  the inexact expiry alarm (grant may outlive its window by a few minutes in
  extreme Doze — acceptable tradeoff, no exact-alarm permission)
- Splits/doubles are out of scope; the table is HIT/STAND by design
- Release builds fall back to the Android debug key until signing secrets are
  configured — fine for sideloading, not accepted by Play

## 8. Play-review notes

- No gambling imagery: no chips, coins, felt, or money. Copy says "hand",
  "earn", "grant" — never bet/wager/pot. The debt is time, not money
- Accessibility disclosure: foreground package only, stated in the service
  description string and during onboarding
- Data safety: all storage is local (Room + DataStore); no network, no
  analytics, no account
