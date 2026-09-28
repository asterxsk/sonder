# Native v2 Quality Pass Design

**Date:** 2026-09-16

## Goal

Make Sonder a single, focused native Android repository; reduce avoidable runtime and release overhead; and refine the current Pixel UI v3 interface so live protection state and the next useful action are immediately clear.

## Product and technical constraints

- `android-native/` is the only shipping application after this work.
- Kotlin, Jetpack Compose, Hilt, Room, and DataStore remain the application stack.
- Existing blackjack, debt, grant, lockout, absence, and permission rules do not change.
- Pixel UI v3 remains the visual source of truth: amber accent, brown-black surfaces, hard frames, Press Start 2P headings, and DM Mono functional copy.
- No account, network service, analytics, advertising, social feature, schedule system, or new runtime dependency is added.
- Existing user work in `.gitignore` and `.ignore` is preserved.
- Repository-local SDK and reusable tooling state are not deleted merely to reduce local disk usage.

## Non-goals

- Rewriting the domain, persistence, enforcement, or permission architecture.
- Replacing the pixel identity with a conventional Material or rounded-card interface.
- Adding custom environmental artwork or a new illustration pipeline.
- Adding real application icons when doing so would require eager bitmap decoding or another image-loading dependency.
- Changing access duration, debt values, blackjack rules, or protected-app detection.

## UI reference evidence

[Opal's Blocks screen](https://uizze.com/screens/0f56b1814a743116953433edd592f70a) visibly places the current blocking state, remaining time, and affected applications before schedules and secondary actions. Its session detail gives the live timer and one primary action more visual weight than configuration.

Sonder adopts the information-priority lesson, not Opal's visual brand: Home will foreground active access or lockout state and target management while retaining Sonder's rectangular amber pixel-console language. Gradients, rounded cards, social controls, schedules, and Opal branding are not copied.

## Repository cleanup

### Remove the retired Flutter application

Delete these tracked Flutter-only paths:

- `lib/`
- `android/`
- `ios/`
- `macos/`
- `test/`
- `assets/`
- `pubspec.yaml`
- `pubspec.lock`
- `.metadata`
- `analysis_options.yaml`
- `.github/workflows/release.yml`
- `docs/android-enforcement.md`

Delete the superseded root design artifacts `design.md` and `elements.png`. Keep `docs/design/design_v3.md` and `sonder_pixel_elements_v3.png`, which describe the current native visual direction.

Delete `employ/`; its migration jobs describe completed work and include Flutter-specific ownership and obsolete visual-source references.

### Update live documentation

- Rewrite `README.md` as a single native Android project rather than a v1/v2 comparison.
- Remove statements in `android-native/README.md` and `docs/android-native-v2.md` that direct readers to live Flutter source or documentation.
- Delete `docs-site/v1.html`.
- Remove the v1 navigation item from every page in `docs-site/` and from `docs-site/assets/site.js`.
- Remove the v1 comparison table and links from `docs-site/index.html`.
- Keep concise historical wording only when it explains why the app is native; do not retain instructions or links to deleted code.

### Remove generated root clutter

Delete root-level QA screenshots, UI XML dumps, and build/emulator logs. This includes ignored `*.log` files and the 95 currently untracked root `*.png`, `*.xml`, and `*.log` artifacts. Do not delete the tracked `sonder_pixel_elements_v3.png` current design reference.

Remove `.omo/` local run-continuation state. Preserve `.freebuff/`, `graft/`, `.gradle/`, `.android/`, `android-sdk/`, and `tools/`; they are reusable local environment or analysis state, not shipping repository content.

Update `.gitignore` by removing obsolete Flutter entries while preserving the user's current Graft ignore block. Future device captures belong in the already ignored `captures/` directory rather than the repository root.

## Management UI architecture

### Shared scaffold

Add a small `PixelAppScaffold` under `android-native/app/src/main/java/com/example/sonder/ui/kit/`. It owns:

- safe drawing insets;
- the near-black screen background;
- the persistent four-tab `PixelDock`;
- content padding above the dock.

It does not own screen titles, screen-specific scrolling, status panels, or business state. Home, Targets, Stats, and Settings supply their own content and current tab. Onboarding and the blackjack gate remain separate full-screen flows without the dock.

Move the dock's fixed Home, Targets, Stats, and Settings definitions out of individual recompositions. Every dock item has a readable content description, selected state, and at least a 48dp touch target.

### Navigation behavior

`SonderRoot` keeps Home plus at most one selected secondary tab in the Nav3 back stack:

- Selecting Home clears secondary destinations and reveals `Main`.
- Selecting Targets, Stats, or Settings replaces the current secondary destination instead of appending another copy.
- Selecting the visible tab is a no-op.
- Android Back from a secondary tab returns Home.
- Android Back from Home follows normal activity behavior.

Top-level tabs no longer show redundant Back or Done buttons.

## Screen behavior

### Home

Home keeps the SONDER wordmark and tagline, then presents a compact live-status panel before the target list.

The panel has four deterministic presentations:

- **Loading:** restrained pixel progress treatment while Room state initializes.
- **No targets:** `NO APPS LIMITED`, one sentence of explanation, and a `LIMIT APPS` action opening Targets.
- **Idle:** `PROTECTION READY`, the number of enabled targets, and concise copy explaining that opening one requires a hand.
- **Active:** the highest-urgency granted or locked target, its label, state badge, and live `MM:SS` remaining time. Locked state sorts before granted state; other active rows remain visible in the list.

Below the panel, `LIMITED APPS` shows enabled targets only. Rows are ordered locked, granted, then idle, with label as a stable secondary ordering. Disabled target records remain in Room but appear only in Targets.

### Targets

Targets becomes a dock-backed top-level screen with no Back or Done controls.

- Keep search, All, and Limited filters.
- Reduce title/search/tab/footer vertical consumption while keeping interactive elements at least 48dp high.
- Make tabs fill the available row consistently.
- Keep lightweight framed glyphs instead of eagerly loading all launcher bitmaps.
- Treat each row as a toggle with a selected-state description; the whole row remains tappable.
- Show explicit states for package loading, zero launchable apps, no query results, and an empty Limited filter.
- Keep explanatory copy concise and outside the scrolling list without obscuring the last row.

### Stats

Stats uses the shared dock and removes its Back button.

- Keep wins, losses, and recent-hand history.
- Tighten summary panels without reducing number legibility.
- Add a framed empty state explaining that results appear after the first completed hand.
- Recent history remains a lazy list with stable hand IDs.

### Settings

Settings uses the shared dock and removes its Back button.

- Permission rows keep plain labels, `OK` status, and `FIX` actions.
- Content is vertically scroll-safe on small devices and large font scales.
- Permission health refreshes when the screen becomes active after returning from Android Settings; `RE-CHECK` remains available as an explicit recovery action.

### Blackjack gate

The gate remains a distraction-free full-screen intervention.

- Preserve game rules, card styling, phase actions, and status language.
- Keep the target/state panel above the table and the phase action below it.
- Reduce unnecessary empty space without crowding cards or actions.
- Dealer and player card groups must remain horizontally accessible when a hand contains more cards than fit the viewport. Off-screen cards are not eagerly composed.
- Result copy and the single next action retain stronger emphasis than decorative elements.

### Onboarding

Onboarding remains a separate permission flow. Only shared accessibility and inset corrections required by this pass apply; its sequence, explanations, and completion rules remain unchanged.

## Runtime and state design

### Lifecycle-aware collection

Use lifecycle-aware Compose collection for UI-facing flows in MainActivity, Home, Targets, Stats, onboarding, and the gate. Work stops when the corresponding UI is not lifecycle-active.

### Home clock and state mapping

Home's current remaining-time text is derived only when a Room flow emits, which can leave visible countdowns frozen. Add a one-second clock flow that runs only while Home state has subscribers. Combine it with target, grant, and lockout flows.

Map grants and lockouts with `associateBy` before processing targets. One refresh is therefore `O(targets + grants + lockouts)` rather than repeatedly searching both lists for every target.

A pure internal mapper accepts targets, grants, lockouts, and `nowMillis`, and returns the summary plus ordered enabled rows. Keeping time as an input makes countdown behavior deterministic in unit tests.

### Targets filtering

Enumerate launcher activities on `Dispatchers.IO`, not `Dispatchers.Default`. Preserve package-name keys.

Compute the enabled count and filtered list only when picks, selected tab, or normalized query changes. Do not introduce a process-wide launcher cache whose invalidation would miss newly installed or removed applications.

### Settings permission checks

Do not perform repeated permission audits during ordinary recomposition. Refresh from an explicit lifecycle event or `RE-CHECK` action and publish one screen state.

### Release optimization

- Set `isMinifyEnabled = true` for release.
- Set `isShrinkResources = true` for release.
- Continue using `proguard-android-optimize.txt` and the existing project rules file.
- Remove `androidx.work:work-runtime-ktx`; no application source imports or schedules WorkManager.
- Add no replacement dependency.

## Accessibility and resilience

- All tab, row, button, and permission actions expose readable labels or state descriptions.
- Target rows expose an on/off toggle role and selected state without requiring the package ID to understand the action.
- Text and state colors continue to use Pixel UI v3 semantic tokens.
- Interactive targets remain at least 48dp.
- Small-height screens, gesture navigation, display cutouts, and larger font scales must not hide primary actions.
- Loading and empty states are distinguishable from a failed or frozen screen.

## Test design

Add focused unit tests for pure behavior introduced by this pass:

### Home mapper

- Excludes disabled targets.
- Maps grants and lockouts to the correct state at a supplied time.
- Formats positive remaining time as `MM:SS`.
- Produces changed remaining text for timestamps one second apart.
- Orders locked before granted before idle, then by label.
- Selects the highest-urgency active row for the summary.
- Produces no-target and idle summaries correctly.

### Targets filter

- All tab includes enabled and disabled launchable apps.
- Limited tab includes enabled apps only.
- Query matches labels and package names case-insensitively.
- A nonmatching query produces the no-results state.
- Loading is distinct from an empty loaded package list.

### Navigation policy

Test the pure back-stack replacement helper:

- Home clears a secondary destination.
- A secondary tab replaces another secondary tab.
- Selecting the visible tab does not duplicate it.
- Back from a secondary destination reveals Home.

Existing access-policy and blackjack-rules tests remain unchanged.

## Validation

Run with the discovered local JDK at `C:/Users/asterxsk/.jdks/jdk-17.0.20+8`:

1. `./gradlew testDebugUnitTest`
2. `./gradlew assembleDebug`
3. `./gradlew lintDebug`
4. `./gradlew assembleRelease`
5. `./gradlew assembleDebugAndroidTest`

The first baseline attempt found the JDK and began dependency resolution but timed out after three minutes at 7%; it reported no source or test failure. Dependencies are partially cached for the implementation validation run.

If an emulator or connected device is available, inspect Home, Targets, Stats, Settings, onboarding, and the blackjack gate at the representative phone size already used by the repository's QA captures. Verify no clipped cards, hidden actions, overlapping system bars, inert dock items, or stale permission state.

Repository validation also includes:

- searching tracked files for live Flutter build commands and deleted source links;
- checking every docs-site navigation link after removing v1;
- confirming only native release workflows remain;
- confirming no root QA screenshots, XML dumps, or logs remain;
- reviewing `git status` so only changes belonging to this quality pass are present.

## Acceptance criteria

- The repository documents and builds one native Android application.
- No Flutter source, platform folders, tests, dependency files, release workflow, or live v1 documentation remain.
- Root QA clutter is removed without deleting the current Pixel UI v3 design reference or reusable local SDK/tooling state.
- Home visibly prioritizes live enforcement state and updates countdowns once per second while visible.
- Home state mapping is linear in the size of its Room lists.
- All four management screens use a consistent safe-area-aware dock and do not accumulate duplicate navigation entries.
- Targets, Stats, and Settings cover their relevant loading or empty states.
- Blackjack hands remain accessible when cards exceed the viewport width.
- Release shrinking is enabled and the unused WorkManager dependency is removed.
- New focused unit tests and existing domain tests pass; debug, release, lint, and Android test APK compilation complete successfully, or any environment-only blocker is reported with its exact command and output.
