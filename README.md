<p align="center">
  <img src="docs/assets/app-icon.png" width="132" height="132" alt="Sonder — an amber poker chip on a dark tile">
</p>

<h1 align="center">Sonder</h1>

<p align="center"><em>Be here, not everywhere.</em></p>

<p align="center">
  An Android screen-time app where the apps you limit are won back a hand of blackjack at a time.
  <br>
  <strong>by Wrong Turn Studios</strong>
</p>

<p align="center">
  <a href="https://github.com/asterxsk/sonder/releases/latest"><strong>Latest release — v1.10.1</strong></a>
  · Android 8.0+ · Kotlin + Jetpack Compose · no network, no account
</p>

---

## The idea

Pick the apps you lose time to. When you open one, Sonder steps in front of it and deals
you a hand. Win, and the chip you staked becomes time you can actually spend; the app's
own use drains it back, second by second. **Nothing else opens an app.** A limit you can
walk through, or unlock for free, is not a limit — so the only door is a hand, and the
only currency is time you already hold.

Sonder is a single native Android app, Kotlin and Jetpack Compose in
[`android-native/`](android-native/). An earlier Flutter prototype was replaced by the
native implementation, so foreground detection and the block gate run as one native
platform process. Its sources are no longer in this tree; the last commit is preserved
under the tag [`v1-final`](https://github.com/asterxsk/sonder/releases/tag/v1-final) if
the old behaviour ever needs reading. `main` is the native app.

## The bank and the window

Two ideas carry the whole model, and keeping them apart is what makes it work.

**The bank** is the day's allowance per app — how much time you may *wager*. It is
refilled at local midnight without an alarm (a row stamped with an earlier day simply
reads as the ceiling again), and it is capped at a ceiling you set for that app
(default **1 h**; presets 30 min / 1 h / 2 h / 3 h). Stakes come out of the bank, so you
can only ever bet time you hold — there is no free seat.

**The window** is usable access, and it is what a won hand buys. A win does not open the
app for the rest of the day: it turns the chip you staked into a window of exactly that
much time. Using the app spends the bank as you go, in steps of no more than 10 seconds.
Leave, and the window **parks for 60 seconds** — come back inside that minute and it
resumes, with the seconds you were away never charged. Stay away longer and the window is
gone: your bank keeps every second you did not spend, and the next open raises the table
again.

So a fresh target is **locked**, not free. It holds the day's allowance as a bank to bet
from, but holding time is not the same as being allowed in — the first open is the table.

## Access rules

The bank lives in `time_bank` (one row per package); the rules are in
[`AccessPolicy.kt`](android-native/app/src/main/java/com/example/sonder/domain/AccessPolicy.kt)
and the live session in
[`AccessSession.kt`](android-native/app/src/main/java/com/example/sonder/domain/AccessSession.kt).

| Event | Result |
| --- | --- |
| A target is added | Gated at once — a bank to bet from, but **no window**, so the first open is the table |
| Opening a target | The gate: the **table** while the bank holds time, the **wall** when it does not |
| Win a hand | The staked chip becomes usable time — the only thing that opens an app |
| Loss | The bank takes the stake, floored at zero — never a debt |
| Push | An inert re-deal; it opens nothing |
| Using an unlocked app | The bank drains in steps of ≤ 10 s, and only while the app is in front |
| Leaving the app | Billing stops; the window parks for 60 s |
| Back within 60 s | The window resumes; the time away is not charged |
| Away longer than 60 s | The window is spent — the next open raises the table again |
| Bank drained to zero | The wall, counting down to the refill; removal refused for **12 h**, or until the refill |
| Local midnight | Every bank reads its full allowance again |

### The table

Stakes are chips — **2:00**, **5:00**, **10:00**, or **ALL IN** (the whole bank). A chip is
**backed by the bank**: one the bank cannot cover is greyed out, so `bet 5:00, win` →
5:00 of window; `bet 5:00, lose` → back to 0:00; `ALL IN on 15:00, win` → 30:00, up to the
ceiling you set. The table opens with **no chip on it** — pick one, and the deal control
stops asking for it and becomes a hand. Dealer stands on all 17s (including soft 17);
natural blackjack is an instant win. No wagers, wallet, ads, analytics, or account — the
chips are minutes you already won, never money.

### Scope

A target can gate its **whole app**, or only its **short-form video** surface — Reels,
Shorts, Stories — leaving the rest of the app open. The two apps people actually lose
time to are mostly a feed and a search box; gating the whole app to reach the shorts
inside it blocks the message and the tutorial the user came for, and a gate that is wrong
about what it is protecting is a gate the user switches off.

## The look

Pixel art, drawn the hard way: amber on brown-black, **Press Start 2P** and **DM Mono**,
0 px frames, stepped shadows, and motion that lands on discrete frames rather than easing
into them. The design system is
[`docs/design/design_v3.md`](docs/design/design_v3.md).

- **The mark is a chip.** The launcher icon and the loading mark are drawn through the
  same `ChipIcon` the stakes are bet with, at the same palette — the mark the app is
  recognised by is the mark it bets with, so the two cannot drift. The adaptive icon
  carries a monochrome layer for themed launchers.
- **Loading steps, it does not spin.** The loader is the chip turning **five hard 9°
  steps** — 0°, 9°, 18°, 27°, 36° — each held. 9° and not 90°, because the rim's eight
  inserts make a quarter turn bit-for-bit identical and a quarter-turn loader would look
  frozen. It is announced to screen readers, so a decorative mark standing in for a screen
  that has not answered leaves TalkBack something to say.
- **Screens slide, they do not crossfade.** Push enters from the right and leaves left;
  pop reverses. A fade says a screen replaced another with no relationship to it; a slide
  says which way the user is going and which way Back comes home. 180 ms.
- **A list that is still reading shows the rows it is about to become** — same frame, same
  icon slot, same controls — not a spinner, and not a panel in the middle of an empty
  screen.
- **The dock** is a floating console pill; the lit tab fills its quarter and rounds off at
  the ends. It is the one deliberate rounded surface in an otherwise zero-radius world.

## Build

```bash
cd android-native
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests (193; 131 of them the pure domain)
```

Requires JDK 17 (`JAVA_HOME`) and an Android SDK with platform 37. Gradle 9.3.1 comes via
the wrapper (SHA-256 pinned). There are no shortened debug timers: the only clock that
matters is foreground use, which you can watch drain. `minSdk 26`, `targetSdk 36`.

## Architecture

```text
UI (Compose) ─ ViewModels (Hilt) ─ Domain (pure, unit-tested)
                                  ├─ BlackjackRules  deck · S17 · naturals
                                  ├─ AccessPolicy    bank · ceiling · removal lock
                                  ├─ AccessSession   the won window, capped at 60 s away
                                  ├─ GateDecider     PASS · GRANTED · GATE · LOCKED
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

The **bank** is persisted (Room); the **window** is in-memory (`AccessSessionStore`) —
permission is live state, not a stored balance. Every timestamp is absolute epoch millis,
so process death and reboots never corrupt the bank: elapsed is measured only between
heartbeats that actually saw the app in front. Full module map:
[`android-native/README.md`](android-native/README.md).

## Permissions

Granted during onboarding, each with a plain-English explanation and a deep link:
**Accessibility** (foreground app detection), **Display over other apps** (the window the
gate is drawn on), **Usage access** (the fallback that says which app is in front),
**Notifications** (one heads-up when a bank runs dry — optional, and the wizard moves on
without it). If a required one is later revoked, the next app open re-checks after **10
seconds** (letting flaky accessibility state settle) and pops a pixel-styled prompt with
direct links. No `QUERY_ALL_PACKAGES` — only launchable apps are visible. No root, no VPN,
no device-owner APIs.

Enforcement internals, edge cases, and the QA checklist:
[`docs/android-native-v2.md`](docs/android-native-v2.md).

## Privacy

Sonder stores targets, banks, onboarding state, and hand history locally. No network
service, account, or analytics SDK. The accessibility service reads the foreground
window's package name and its view identifiers — developer-chosen resource names, never
screen content — and nothing is ever sent off the device.

## CI/CD

`.github/workflows/android-v2.yml` on every push/PR touching `android-native/`:

- **Build + unit tests** — assembles the debug APK, runs the test suite, and compiles the
  release variant as a smoke check (no artifact). Uploads the debug APK as the
  `sonder-v2-debug-apk` artifact.

`.github/workflows/release-v2.yml` on version tags (three dotted numbers, so a marker tag
like `v1-final` cannot start one) or a manual run: the only workflow that builds the
release APK. It gates on the unit tests, builds it with the tag (or the manual `version`
input) as `versionName` and the workflow run number as `versionCode`, verifies both plus
the signer, and publishes it as a GitHub Release (`sonder-v2-<version>.apk`). **Signed
automatically** when the `SONDER_KEYSTORE_B64`, `SONDER_KEYSTORE_PASSWORD`,
`SONDER_KEY_ALIAS`, `SONDER_KEY_PASSWORD` secrets are configured (keystore as base64), with
the certificate organization set to **Wrong Turn**.

The release key lives at `tools/sonder-release.jks` (gitignored, with its password
alongside it) — **back it up somewhere outside this checkout**. Losing it means no future
release can install as an update over an existing one, which is the whole reason it
exists: without those secrets the workflow falls back to the Android **debug key**, and a
debug key is generated fresh on every runner, so each release came out signed by a
*different* key and `adb install` refused every update with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## First-run setup

1. Install the APK (CI artifacts or a local build).
2. Complete the onboarding wizard — grant the three required permissions (each step
   deep-links to the right settings page; notifications can be skipped).
3. Add the apps you want to limit in **Targets** — `ADD` opens a picker; tap apps to
   select them and `DONE` to confirm. Each row's `›` arrow opens that app's own settings,
   where the bank ceiling, the scope and `REMOVE LIMIT` live.
4. Open a target app → the gate appears → pick a chip, play blackjack, win your way in.

## License

No license has been declared yet. Treat the repository as all-rights-reserved until a
license is added.
