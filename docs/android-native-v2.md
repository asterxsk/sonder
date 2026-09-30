# Sonder v2 — Android Enforcement Notes

Implementation notes for the native (Kotlin + Compose) Sonder. Read alongside
[`android-native/README.md`](../android-native/README.md).

## 1. Rules engine

The product rules live in pure Kotlin under
`android-native/app/src/main/java/com/example/sonder/domain/`: `AccessPolicy`,
`BlackjackRules`, and the enforcement policy — `GateDecider`, `BlockScope`,
`ShortsCatalog`, `ForegroundWatch`, `ForegroundSurface`. No Android imports; 105
unit tests cover them.

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
| Debt at the ceiling | no table: the app waits the debt out (`GateDecider` → LOCKOUT) |
| Lockout served | the debt it was serving is cleared with it |
| Absence > 60 s during a grant | grant revoked regardless of remaining time |

Worked example: `L, L, W, W, W` → debt 20 → 10 → 0 → access granted.
At the 60-minute cap, further losses change nothing (`55 + 10 → 60`, not 65) — and
at the cap the table closes: winning hands are the fast way to pay debt down, so the
loop stays open below the ceiling, but at the ceiling there is nothing another loss
could change and nothing another hand could win back. The wait is what serves a debt
from then on, which is why an expired debt lockout clears the debt row along with
itself (`EnforcementRepository.serveDebtIfLockoutElapsed`). Both the fresh decision and
the session already on screen respect it — `GateController` reports the remaining wait
in `TableState.debtLockRemainingMillis`, so the hand that reaches the ceiling ends the
game instead of dealing another.

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
SonderAccessibilityService (TYPE_WINDOW_STATE_CHANGED only, 50 ms delivery)
  │  classifies the surface: TRANSIENT / HOME / OWN / APP
  ▼
EnforcementCoordinator (serialized on one dispatcher, warm cache, no DB waits)
  ├─ TRANSIENT (shade, IME, own overlay window) → ignore, blocker untouched
  ├─ HOME / OWN → release the blocker, unless the real foreground says otherwise
  ├─ active grant? → absence check (now − lastSeen > 60 s ⇒ revoke) → touch lastSeen
  ├─ not an enabled target? → release the blocker
  ├─ SHORTS_ONLY target, and the probe finds no Reels/Shorts marker? → PASS (no blocker)
  ├─ lockout active? → lockout blocker only ("WAIT IT OUT")
  └─ otherwise → GateOverlayHost.showGate (the blackjack table)

every 500 ms, in parallel with the events above (foreground re-check)
  │  last resumed activity within 10 s, via ForegroundResolver
  ▼
ForegroundWatch (pure policy)
  ├─ APP    → decide it, unless the previous pass is still holding (gate up, grant live)
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
  dismiss, which is how a lockout screen once ended up stranded over an unrelated
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
  removed, a grant that lapsed or was revoked mid-use, and a target enabled while
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
  `ShortsCatalog.defaultScopeFor`: Instagram and YouTube arrive
  `SHORTS_ONLY`, everything else `WHOLE_APP`. The user can override it in the
  edit tab, and the stored value is a raw `"WHOLE_APP" | "SHORTS_ONLY"` string
  read through `BlockScope.fromStored`, so an unrecognised value degrades to the
  conservative whole-app block rather than to no block at all.
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

Accessibility reads differ by target scope. A `WHOLE_APP` target needs only the
foreground package name. A `SHORTS_ONLY` target additionally needs view
identifiers, so the service config declares `canRetrieveWindowContent="true"`
and advertises `flagReportViewIds`; without the flag `viewIdResourceName` comes
back null and the probe can never match. The node walk is bounded (400 nodes,
breadth-first, first match wins) and reads **only** `viewIdResourceName` on the
nodes of the one package being decided — no text, no content descriptions, no
images. The service description string and onboarding state this.

## 5. Persistence

Room database `sonder.db` (schema v3):

- `targets` — gated packages (package name PK, label, enabled, `blockScope`)
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

- `AccessPolicyTest` (29 tests): the L,L,W,W,W example, the 60-minute cap,
  pay-down wins, push neutrality, lockout windows, absence boundaries
  (59 s safe / 61 s revoked), grant expiry, state mapping
- `BlackjackRulesTest` (15 tests): hard/soft totals, ace degradation, bust,
  naturals, deal order, dealer stands on hard and soft 17, settlement
- `GateDeciderTest` (21 tests): grant/lockout/pass precedence, the lockout
  ceiling, and the scoped-surface rule — a `SHORTS_ONLY` target with no surface
  present passes even while a grant is live, so an Instagram grant never unlocks
  Reels-only gating for the feed
- `ShortsCatalogTest` (12) and `BlockScopeTest` (4): marker matching both ways,
  `defaultScopeFor` for Instagram/YouTube/everything else, and `fromStored`
  mapping null and garbage to `WHOLE_APP`
- `ForegroundWatchTest` (14) and `ForegroundSurfaceTest` (10): the pure
  re-check policy and the surface classifier

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
  descriptions, no images.
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
- Accessibility disclosure: the foreground package for every target, plus view
  identifiers for `SHORTS_ONLY` targets, stated in the service description
  string and during onboarding
- Data safety: all storage is local (Room + DataStore); no network, no
  analytics, no account
