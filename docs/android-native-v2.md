# Sonder v2 — Android Enforcement Notes

Implementation notes for the native (Kotlin + Compose) Sonder. Read alongside
[`android-native/README.md`](../android-native/README.md).

## 1. Rules engine

The product rules live in pure Kotlin under
`android-native/app/src/main/java/com/example/sonder/domain/`: `AccessPolicy`,
`BlackjackRules`, and the enforcement policy — `GateDecider`, `BlockScope`,
`ShortsCatalog`, `ForegroundWatch`, `ForegroundSurface`. No Android imports; 106
unit tests cover them.

**`BlackjackRules.kt`**

- Single 52-card deck, reshuffled every hand
- Player actions: HIT / STAND only (no splits or doubles in scope)
- Dealer reveals the hole card and draws until 17+, standing on **all 17s**
  including soft 17
- Natural blackjack (ace + ten-value on the first two cards) is an instant
  win; two naturals push
- Push = inert: the bank is untouched, and the hand is re-dealt

**`AccessPolicy.kt`** — the time bank:

| Input | Effect |
| --- | --- |
| Any read on a later local day | reads as 0 |
| Win | `bank = min(bank + stake, maxMillis)` |
| Loss | `bank = max(bank − stake, 0)` — never a debt |
| Push | no change; the hand is re-dealt |
| Stake | 2:00 / 5:00 / 10:00, or ALL IN = the whole bank |
| Bank > 0 | the app is granted; the bank drains only while it is in front |
| Bank reaches 0 | the gate comes up, and removal is refused for 12 h |

The stake is a stake, not a claim on the balance: every chip is played at face value even
from an empty bank, so the table is reachable from nothing and a loss floors at zero. That
is what makes a win the way back in — the bank *is* access, so there is no separate grant
to earn and no debt to serve.

Worked example: `L, L, W` at a 5:00 chip → the two losses floor at 0 (a loss is never a
debt), and the win credits 5:00 — the third hand is the way in.

`maxMillis` is the only per-app rule besides the scope, so nothing else can stand between a
won hand and the app.

## 2. Time model

The bank is one row per package in the Room `time_bank` table
(`TimeBankEntity`: `remainingMillis`, `epochDay`, `lastSeenMillis`,
`emptySinceMillis`), all of it **absolute epoch millis**:

- Process death and device reboots never inflate or reset the bank
- The bank only moves while the target is in front: the coordinator refreshes
  `lastSeenMillis` every few seconds and drains by the elapsed gap, clamped to
  `MAX_BILL_MILLIS = 10_000L`. Time away is never billed
- Every app starts each local day at 0; a bank whose `epochDay` is any other day
  reads as 0. Nothing carries over
- `BootReceiver` only warms the bank cache after reboot — there is no alarm to
  re-arm, and no `SCHEDULE_EXACT_ALARM` permission is needed
- The old `GrantExpiryScheduler`, `ExpiryReceiver` and `RuleDefaults` are gone.
  The "access expired" notification now fires the moment a bank drains to 0

There are no debug-only timers any more: the `ACCESS_WINDOW_MILLIS` and
`ABSENCE_REVOKE_MILLIS` `buildConfigField`s were deleted from
`android-native/app/build.gradle.kts`, because a build that shortened a wall-clock
window has nothing left to shorten when the only clock that matters is foreground use.

## 3. Detection and gating flow

```text
blocked app reaches foreground
  │
  ▼
SonderAccessibilityService (TYPE_WINDOW_STATE_CHANGED only, 50 ms delivery)
  │  classifies the surface: TRANSIENT / HOME / OWN / APP
  ▼
EnforcementCoordinator (serialized on one dispatcher, warm cache, no DB waits)
  ├─ TRANSIENT (shade, IME, own overlay window) → ignore, blocker untouched
  ├─ HOME / OWN → release the blocker, unless the real foreground says otherwise
  ├─ bank holds time? → bill the foreground gap, touch lastSeen, no blocker
  ├─ not an enabled target? → release the blocker
  ├─ SHORTS_ONLY target, and the probe finds no Reels/Shorts marker? → PASS (no blocker)
  └─ otherwise → GateOverlayHost.showGate (the blackjack table)

every 500 ms, in parallel with the events above (foreground re-check)
  │  last resumed activity within 60 s, via ForegroundResolver — a scoped target the
  │  accessibility window list names as the focused window outranks that answer, since
  │  no window event ever names Reels or Shorts
  ▼
ForegroundWatch (pure policy)
  ├─ APP    → decide it, unless the previous pass is still holding (gate up, bank live)
  ├─ HOME / OWN → release, once two passes in a row agree
  └─ TRANSIENT → ignore
```

- **`GateOverlayHost`** is the blocker: a single `TYPE_APPLICATION_OVERLAY`
  window owned by the accessibility service — never an Activity. Because it is a
  window and not an Activity it belongs to no task, so it cannot appear as a
  screen inside the detox app, cannot be reached from Recents, and is unaffected
  by the app's navigation. It is opaque and touchable, so it swallows touches
  before they reach the blocked app underneath.
- **At most one window, and it is either fully up or gone.** `showGate` *ensures*
  it: a gate for the app already covered is a no-op (so bursts of foreground events
  cannot stack blockers or reset a live hand), a window already up for a different
  app is repointed in place (so switching between two blocked apps never uncovers
  either), and a window is composed before it is added (so its first frame is
  painted). `dismiss` removes it. There is deliberately no middle state — hiding a
  window by resizing it leaves a blocker no state describes and nothing can
  dismiss, which is how a blocker once ended up stranded over an unrelated
  app. A window the system takes away is noticed by `detachListener` and composed
  again on the next signal.
- **Compose in the overlay** is hosted with the blocker's own view-tree owners
  (see `OverlayLifecycleOwner`), since a Service has no Activity lifecycle to
  borrow. The gate's state machine (`GateController`) is process-owned rather
  than a ViewModel behind an Activity.
- **A release is gated, not corrected afterwards.** Home and Recents deliver their
  window events in bursts and in whatever order they please, so the launcher's own
  event can land *after* the blocked app's. Releasing on that one event uncovered an
  app the user was looking at, and the post-release verification raised the blocker
  again 400 ms later — two decisions in opposition, which is the flicker overlay →
  app → overlay → app around Recent Apps. Now a release has to survive several
  independent pieces of evidence, and is refused when any says the user has not
  actually left:
  - **`ForegroundWindows`** — the accessibility window list the service already
    receives. If the window that reported the release is still listed while another
    *application* window holds input focus, the event is trailing. This needs no
    permission and is never stale, since the list describes the screen at the moment
    of the event. (Only another application counts: a keyboard or a system window
    having focus says nothing about where the user is, and refusing on that would
    strand the blocker over Home.)
  - **`ForegroundResolver`** — the usage-stats lookup, as a second opinion. It lags
    the events by a moment and needs usage access granted, so it cannot be the only
    evidence: a device without that permission would decide every release on nothing,
    which is how a blocked app becomes usable again after a Recents round trip.
  - **`ActiveWindowPackage`** — the focused window's package, read straight off the
    window list. This is the answer to the 500 ms re-check, whose release path
    otherwise rested entirely on `ForegroundResolver`: a usage-stats lookup that had
    not yet caught up with the user's entry into Shorts reported the launcher as the
    last resumed activity, so the re-check released the blocker twice a second while
    the user was demonstrably in Shorts. The focused window has no such lag — the
    list describes the screen as it is now. It answers null when it cannot tell (no
    focused window, a system or IME window in front, an unreadable root), and null is
    treated as no corroboration *against* a release rather than as reason to refuse
    one, so a device that cannot answer does not lose the release path.
  Nothing needs undoing afterwards, so the post-release verification pass is gone.
- **`ForegroundResolver`** is that authoritative lookup (usage access, not
  content): the last *resumed activity*. Launching from the launcher, Recents,
  an app-lock unlock and a screen unlock all leave one behind, which is what makes
  it a usable second opinion against the event stream.
- **The foreground re-check is the safety net under the event stream**, which is
  the fast path but not a guarantee. Window events can be dropped, coalesced or
  delivered late, and an app that locks itself (an in-app PIN screen, an OEM
  app-lock activity, a third-party locker's overlay) puts a window in front of
  the target that raises no event for the target at all. Events alone therefore
  leave the app visible for seconds, or until something unrelated raises a fresh
  event — and sometimes for good. Every 500 ms the coordinator asks
  `ForegroundResolver` for the last *resumed activity*, which no way into an app
  can skip (launcher tap, Recents, app-lock unlock, screen unlock), and applies
  `ForegroundWatch` to the answer: an app is decided on every pass, a release
  waits for two agreeing passes so a lookup trailing a launch cannot uncover the
  app. Held state is re-examined rather than assumed — a blocker the system
  removed, a bank that drained mid-use, and a target enabled while
  its app is open all reach a blocker within one interval.
- **A blocker window that goes away is noticed**: `GateOverlayHost` tracks the
  attach state of its window, so a window the system removed (revoked overlay
  permission, a window token that died with the app underneath) stops counting as
  "already showing" and is raised again by the next event or re-check.
- **Self-events are ignored**: the blocker's own window reports the detox app's
  package, so the classifier only treats the app's *Activities* as "the detox app
  opened" — otherwise the blocker would dismiss itself the moment it appears.
- **Scope is chosen when the target is born.** `TargetsFilter.newTarget` is the
  single place a target is created, and it sets `blockScope` from
  `ShortsCatalog.defaultScopeFor`: Instagram, YouTube and Facebook arrive
  `SHORTS_ONLY`, everything else `WHOLE_APP`. The user can override it with the
  scope control on the app's settings screen, and the stored value is a raw
  `"WHOLE_APP" | "SHORTS_ONLY"` string
  read through `BlockScope.fromStored`, so an unrecognised value degrades to the
  conservative whole-app block rather than to no block at all.
- Billing heartbeat: `lastSeenMillis` is refreshed every few seconds while a
  banked package is in front and is never touched while it is not. The gap since
  the last stamp is the drain, clamped to 10 s so a dead process or a reboot
  cannot bill time nobody spent.

## 4. Permissions

| Permission | Type | Why | Deep link |
| --- | --- | --- | --- |
| Accessibility | special | foreground app detection | `ACTION_ACCESSIBILITY_SETTINGS` + `EXTRA_COMPONENT_NAME` |
| Display over other apps | special | instant block overlay | `ACTION_MANAGE_OVERLAY_PERMISSION` + package URI |
| Usage access | special | authoritative foreground re-check (`ForegroundResolver`) | `ACTION_USAGE_ACCESS_SETTINGS` |
| Notifications | runtime (33+) | "time's up" notices | app notification settings |

- **Onboarding wizard** (first launch): four steps, each with plain-English
  copy and a direct link; completes only when all are granted — or, for
  notifications, when they are skipped
- **10-second delayed audit**: every `MainActivity` open schedules a check
  10 s out — deliberately late because accessibility state is flaky right
  after boot/enabling. Anything missing pops the pixel prompt
  (`PermissionPromptActivity`) with per-permission deep links
- `SettingsScreen` mirrors the same audit with a manual re-check

Notifications are the one **optional** grant (`SonderPermission.optional`), and
that flag is the whole rule. Blocking runs on the accessibility service and the
overlay; with notifications off the only loss is the "time's up" heads-up, so:

- the wizard's notification step carries a `SKIP` control and the other three do not
- a skip is recorded in DataStore (`SettingsRepository.skippedPermissionNames`)
  and `outstandingPermissions(missing, skippedNames)` — one pure rule, in
  `SonderPermission.kt` — is what both the wizard and the 10 s reminder ask, so a
  declined permission stops being nagged about on one surface and not the other
- `SettingsScreen` shows it as `(OPTIONAL)` in the muted tone rather than as a
  failure, with `TURN ON` instead of `FIX`; taking it back on clears the skip
- Package visibility uses a `<queries>` block for `MAIN`/`LAUNCHER` intents —
  **no `QUERY_ALL_PACKAGES`**

Accessibility reads differ by target scope. A `WHOLE_APP` target needs only the
foreground package name. A `SHORTS_ONLY` target additionally needs view
identifiers, so the service config declares `canRetrieveWindowContent="true"`
and advertises `flagReportViewIds`; without the flag `viewIdResourceName` comes
back null and the probe can never match. The node walk is bounded (400 nodes,
breadth-first, first match wins) and reads **only** `viewIdResourceName` on the
nodes of the one package being decided — no text, no content descriptions, no
images. The service description string and onboarding state this.

## 5. Persistence

Room database `sonder.db` (schema v5):

- `targets` — gated packages (package name PK, label, enabled, `blockScope`,
  `maxMillis`)
- `time_bank` — one row per package: `remainingMillis`, `epochDay`,
  `lastSeenMillis`, `emptySinceMillis`
- `hands` — history: package, outcome, stake, bank-after, timestamp, plus `label`,
  `playerCards`, `dealerCards` (Stats screen)

The three columns `MIGRATION_3_4` adds are snapshots taken at the moment the hand
settled: the app's label as the target held it, and both hands as one line each
(`handNotation` → `A♠ K♥ · 21`). They are stored rather than joined or re-derived
because neither survives otherwise — a target can be renamed and the gate drops
the cards the moment the table resets. They default to the empty string, which is
the honest value for a hand played before they were kept; the Stats row omits a
line it has nothing to say about.

`EnforcementRepository` is the single mutation point; ViewModels and the
platform layer never touch DAOs directly. Domain models stay pure; mapping
happens at the repository boundary.

## 6. Testing

```bash
cd android-native && ./gradlew testDebugUnitTest
```

- `AccessPolicyTest` (29 tests): the bank arithmetic, chip stakes at face value
  from an empty bank, the ceiling clamp and the zero floor, the 10 s bill clamp,
  the daily reset and the 12 h removal lock, state mapping
- `BlackjackRulesTest` (15 tests): hard/soft totals, ace degradation, bust,
  naturals, deal order, dealer stands on hard and soft 17, settlement
- `GateDeciderTest` (12 tests): pass/granted/gate precedence, and the
  scoped-surface rule — a `SHORTS_ONLY` target with no surface present passes
  even while the bank holds time, so time won in an Instagram Reel never unlocks
  Reels-only gating for the feed
- `ShortsCatalogTest` (17) and `BlockScopeTest` (4): marker matching both ways,
  `defaultScopeFor` for Instagram/YouTube/Facebook/everything else, and `fromStored`
  mapping null and garbage to `WHOLE_APP`
- `ForegroundWatchTest` (14) and `ForegroundSurfaceTest` (10): the pure
  re-check policy and the surface classifier
- `HandNotationTest` (5): the card line stored with a settled hand
  (`A♠ K♥ · 21`)

CI (`.github/workflows/android-v2.yml`) runs the same suite on every push/PR
touching `android-native/`, plus a release-variant compile check (no artifact,
no signing). `.github/workflows/release-v2.yml` is the only workflow that builds
the signed release APK: on `v*` tags or a manual run it runs the same suite,
builds with the tag as `versionName` and the run number as `versionCode`,
verifies both plus the signer, and publishes `sonder-v2-<version>.apk` as a
GitHub Release.

## 7. Known limitations

- Reels/Shorts detection is a view-id probe, not a content parse. It reads
  `AccessibilityNodeInfo.viewIdResourceName` on the nodes of the one app it was
  asked about, breadth-first, capped at 400 nodes, and matches them against the
  marker list in `ShortsCatalog`. Nothing else is read — no text, no content
  descriptions, no images. The tree it walks is the app's *focused* window's,
  falling back to its top-most one: an app can hold more than one window, and the
  first one the list happens to name is not always the one with the screen.
  Because no window event names a Reels or Shorts surface, the 500 ms re-check is
  the only thing that can notice the user opening one, and it decides on the
  window list when that names a scoped target the usage-stats probe has not
  caught up with — see `EnforcementCoordinator.scopedAppInFront`.
- The probe cannot see past our own blocker, and that is measured, not assumed:
  on YouTube, `reel_recycler` reports `isVisibleToUser=false` while the blocker
  covers the app and `true` the moment it goes, same node, same bounds. A probe
  that treated the covered answer as "the surface is gone" released the blocker
  and let the next pass raise it again, twice a second. So while our own window
  covers the target the surface counts as present, and leaving the surface from
  *behind* the blocker — pressing Back out of Shorts onto the feed — is not
  noticed. The blocker comes down when the user leaves the app, or on CLOSE.
- Whole-app targets are the reliable path. A scoped target also degrades to
  "never gated" rather than "always gated" if the accessibility service is not
  connected, since a missing probe answers false.
- Billing is foreground-driven: the bank drains only on the heartbeats the
  coordinator sees while the app is in front, so time away — and a gap left by a
  dead process or a reboot — is never charged. There is no absence threshold and
  no wall-clock deadline left to fire
- Battery optimizers / OEM killers can suspend the accessibility service; the
  10-second audit catches a disabled service at next app open. A drain that lands
  while the service is down is simply deferred to the next heartbeat rather than
  billed against wall-clock time
- Splits/doubles are out of scope; the table is HIT/STAND by design
- Release builds fall back to the Android debug key until signing secrets are
  configured — fine for sideloading, not accepted by Play

## 8. Play-review notes

- No gambling imagery: no coins, cash, or felt, and no money changes hands. The
  table names its stakes as **chips** — 2:00 / 5:00 / 10:00 and ALL IN — but a
  chip is minutes of the user's own time, never a bet of money
- Accessibility disclosure: the foreground package for every target, plus view
  identifiers for `SHORTS_ONLY` targets, stated in the service description
  string and during onboarding
- Data safety: all storage is local (Room + DataStore); no network, no
  analytics, no account
