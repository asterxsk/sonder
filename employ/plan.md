# Sonder Android Flutter — Work Plan

## Goal
Build **Sonder**, an Android-first Flutter screen-time intervention app. By default, YouTube Shorts and Instagram Reels require one blackjack hand before access. A win grants 5 minutes; a loss locks the target for 10 minutes. Users may add other installed apps with the same rule. A won session expires if its target app stays out of the foreground for 20 seconds.

## Product contract
- App label: `Sonder`.
- Voice: encouraging, direct, slightly quirky. No guilt, shame, parental-control, or aggressive gambling language.
- Default targets: YouTube (`com.google.android.youtube`, Shorts surface) and Instagram (`com.instagram.android`, Reels surface).
- Custom target: an installed Android package selected manually. Package-level protection is the supported custom-app granularity.
- Gate: one deterministic, fair blackjack round using a cryptographically secure shuffled deck. Standard values; dealer stands on all 17; natural blackjack is evaluated before player actions. No bets, money, wallet, or gambling copy.
- Result: win grants exactly 5 minutes (`grantedUntil`); lose blocks exactly 10 minutes (`lockedUntil`). A non-expired lock always wins over a grant.
- Abandonment: while a target has a grant, record foreground/background transitions. If it has remained out of foreground for 20 consecutive seconds, revoke the grant. Brief returns inside 20 seconds preserve it.
- Interception: an Android `AccessibilityService`, explicitly enabled by the user, detects foreground target packages and known Shorts/Reels surfaces. It shows an accessibility overlay with a Flutter-deep-linkable gate. Never claim bypass-proof protection: Android cannot guarantee individual in-app surface detection across app versions, languages, or OEMs. If Shorts/Reels detection is uncertain, fail safe only after user-visible explanation; package-level custom-app rules remain reliable.
- Permissions: request only with explanatory education screens: Accessibility Service, overlay capability as required by Android version, and package visibility queries for manual selection. Do not use root, VPN, device-owner APIs, or hidden/private APIs. Include Play policy review notes because AccessibilityService use requires compliant disclosure/declaration.

## Visual contract
Source of truth: `design.md` and `elements.png` in repository root.
- Original visual identity: dark/light pixel-console system; no copied Undertale assets, artwork, logos, characters, or font files lacking a redistribution license.
- Tokens: dark `#080B0F/#181A22/#202033/#7E6CFF/#9B8CFF/#FF609A/#86EFA6/#E6E6E6`; light `#F8F8F2/#ECEBE4/#D6D6C8/#6B5CFF/#9B8CFF/#FF609A/#22C55E/#1A1A1A`.
- Use original vector/pixel-style geometry, hard borders, compact pixel display typography, monospace supporting text, explicit textual status plus color.
- App icon: original adaptive icon, lavender pixel heart fused with a small keyhole/lock silhouette on near-black background. Produce foreground/background assets plus monochrome Android icon. It must remain legible at 48dp, have no text, no third-party character/art, and not look like YouTube/Instagram branding.
- Support light/dark themes, TalkBack labels, strong contrast, large touch targets, and reduced motion.

## Target architecture
Flutter app runs Dart UI/domain/persistence. Android-native Kotlin owns system integration.

```text
Flutter presentation
  ├─ features/home, targets, blackjack, settings, onboarding
  ├─ domain: BlackjackEngine, AccessPolicy, SessionReducer
  ├─ data: SharedPreferences/Hive-style local repository, platform bridge
  └─ MethodChannel/EventChannel `sonder/access`
Android Kotlin
  ├─ SonderAccessibilityService: foreground events, best-effort Shorts/Reels detection
  ├─ GateOverlayController: blocks interaction, links to Flutter gate
  ├─ SessionStore: DataStore mirror of enforceable access state
  └─ AppTargetCatalog: installed launchable packages
```

Flutter owns the canonical game and policy model. Native owns only a compact, serialized enforcement snapshot so it can enforce while Flutter is suspended. Every outcome writes the same snapshot transactionally through the bridge.

## Shared contracts

### IDs and times
- Store all instants as UTC epoch milliseconds (`int`). Clock injection is required in Dart tests.
- `TargetId` equals Android package name; built-ins also carry a `surface` enum: `youtubeShorts`, `instagramReels`, `wholeApp`.

### Dart models
```dart
enum TargetSurface { youtubeShorts, instagramReels, wholeApp }
enum AccessStatus { allowed, needsChallenge, locked }

class BlockedTarget {
  final String packageName;
  final String displayName;
  final TargetSurface surface;
  final bool enabled;
}

class EnforcementSnapshot {
  final String packageName;
  final TargetSurface surface;
  final int? grantedUntilEpochMs;
  final int? lockedUntilEpochMs;
  final int? lastBackgroundEpochMs;
}

class AccessDecision {
  final AccessStatus status;
  final int? remainingMs;
}
```

```dart
AccessDecision evaluateAccess(EnforcementSnapshot state, int nowEpochMs);
EnforcementSnapshot applyWin(EnforcementSnapshot state, int nowEpochMs); // +5m
EnforcementSnapshot applyLoss(EnforcementSnapshot state, int nowEpochMs); // +10m
EnforcementSnapshot recordForeground(EnforcementSnapshot state, int nowEpochMs);
EnforcementSnapshot recordBackground(EnforcementSnapshot state, int nowEpochMs);
EnforcementSnapshot expireAbandonedGrant(EnforcementSnapshot state, int nowEpochMs); // 20s
```

### Platform channel
Channel name: `sonder/access`.

Methods sent Flutter-to-native:
```json
{"method":"syncSnapshots","arguments":{"snapshots":[{"packageName":"com.google.android.youtube","surface":"youtubeShorts","grantedUntilEpochMs":null,"lockedUntilEpochMs":null,"lastBackgroundEpochMs":null}]}}
{"method":"listLaunchableApps","arguments":{}}
{"method":"openAccessibilitySettings","arguments":{}}
{"method":"getEnforcementCapabilities","arguments":{}}
```

Events native-to-Flutter:
```json
{"type":"targetIntercepted","packageName":"...","surface":"youtubeShorts","atEpochMs":0}
{"type":"targetForeground","packageName":"...","surface":"wholeApp","atEpochMs":0}
{"type":"targetBackground","packageName":"...","surface":"wholeApp","atEpochMs":0}
{"type":"serviceStateChanged","accessibilityEnabled":true,"overlayAvailable":true}
```

Native DataStore must deserialize the same field names. Unknown/malformed snapshots must fail closed for that target and log locally without personal content.

## Files and ownership
| Job | Owns | Depends on |
|---|---|---|
| 01 Foundation and identity | Flutter bootstrap, theme/design primitives, assets, icon, onboarding shell | None |
| 02 Access domain and blackjack | Dart models, game engine, policy/repository tests | 01 file layout/contracts |
| 03 Android enforcement bridge | Kotlin service, overlay, DataStore, manifest, channel implementation | contracts only; may create native files without 01 |
| 04 Flutter product surfaces | Home, target manager, blocked-app/challenge/result/settings screens | 01 primitives; 02 public API |
| 05 Integration, QA, compliance | App wiring, integration tests, docs, permissions/capability states | 01–04 artifacts |

Jobs are dispatched together because sessions are isolated. Each worker must limit edits to its owned files, create missing parent directories, and must not rewrite another job's files. Where a dependency is absent in its isolated session, write against the exact contract above; report the resulting expected integration point rather than changing another job's module.

## Engineering conventions
- Flutter stable, Dart null safety, Kotlin. Start from `flutter create --platforms=android --org app.sonder sonder` only if `pubspec.yaml` is absent.
- Prefer Flutter SDK components. Add dependencies only when necessary; pin them in `pubspec.yaml` only in Job 01.
- State lives behind repositories; widgets do not calculate blackjack or expiry policy.
- Tests: Dart unit tests for all timing/game edge cases; widget tests for state labels/actions; Kotlin JVM/Robolectric tests for pure enforcement classification/storage where setup permits. No one-off verification scripts.
- Run formatter and relevant tests. Report exact commands/results. Repository currently has no Git metadata; do not create commits.
- Document device limitations, permission path, localization/version fragility, and Play policy review before release.

## Acceptance at merge
1. `flutter analyze` passes.
2. Dart tests prove 5-minute wins, 10-minute losses, 20-second out-of-foreground expiry, priority of lock over grant, blackjack outcomes, and custom package rule behavior.
3. First run explains permissions; default Shorts/Reels targets are visible/enabled; manual picker adds/removes apps.
4. Gate flow is understandable: block reason, one blackjack game, explicit result/timer, return path.
5. Android accessibility service can intercept configured package foreground events and display an accessible gate overlay on a physical Android device; Shorts/Reels classification is explicitly marked best-effort.
6. Icon builds into Android adaptive, round, and monochrome variants.
7. Documentation states no bypass-proof guarantee and required Play-policy review.
