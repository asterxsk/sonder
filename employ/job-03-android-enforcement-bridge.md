# Job 03 — Android enforcement bridge

Read `employ/plan.md`, especially **Product contract**, **Target architecture**, **Shared contracts**, **Engineering conventions**, and **Acceptance at merge**.

## Goal
Implement Android-native best-effort enforcement: accessibility foreground detection, gate overlay, DataStore enforcement snapshot, capability/installed-app APIs, and the exact Flutter bridge contract.

## Owned files
Create/modify only Android-native files: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/**`, `android/app/src/main/res/xml/**`, `android/app/src/main/res/values/**` directly required for service declarations/strings, `android/app/build.gradle*` only if native dependencies need it, and `android/app/src/test/**`. Do not modify Flutter `lib/**`, assets, pubspec, or generic application label/icon resources belonging to Job 01.

## Exact work
1. Declare a clearly labelled `SonderAccessibilityService`; accessibility config must request minimum event types/package scope needed. Add transparent disclosure strings and service metadata. Do not attempt to hide from system UI.
2. Implement `SessionStore` using Jetpack DataStore, preserving exact snapshot JSON names from plan. Any malformed/unknown snapshot must fail closed for that target.
3. Implement `SonderAccessibilityService`: observe window/foreground events, classify custom `wholeApp` by package; classify YouTube Shorts/Instagram Reels only with defensible, version-tolerant best-effort signals. Never claim reliable individual-surface detection. Generate `targetForeground`, `targetBackground`, and `targetIntercepted` events with prescribed fields. Let locks override grants. Apply the 20-second continuous-background revocation natively.
4. Implement an accessible `TYPE_ACCESSIBILITY_OVERLAY` gate controller. It blocks interaction only while current target is locked/needs challenge, clearly announces the state/timer, offers one action that launches/deep-links into Sonder’s challenge, and removes itself immediately on eligible allowed state or target departure. Never overlay outside configured targets; prevent overlay loops.
5. Implement `sonder/access` MethodChannel and EventChannel. Methods/events/arguments must exactly match plan. `listLaunchableApps` returns only package/display/icon metadata supported by queries; avoid collecting usage data. `openAccessibilitySettings` opens system settings. `getEnforcementCapabilities` reports accessibility and overlay state. `syncSnapshots` validates all records atomically enough to avoid temporary grants.
6. Add Android 11+ package visibility `<queries>` minimally for the built-in packages and user-selectable launchable apps. Explain package visibility and accessibility policy implications in a native `README`/comment document under owned Android source.
7. Add JVM/Robolectric tests for pure JSON validation, access classification, 20-second expiration boundary, classifier safety (unknown surface must not falsely label Reels/Shorts), and channel argument validation where feasible.

## Acceptance criteria
- Native compilation/unit tests pass if local Android SDK available; otherwise report exact blocking environment error plus static verification performed.
- Service, overlay, DataStore, manifest, and channel match contracts exactly.
- Device test steps prove an enabled service intercepts a configured whole-app target; Shorts/Reels documented best-effort limitation.
- No root/VPN/device-owner/private API/stealth/bypass claims.

## Report
List files, exact bridge payloads, test/build commands/results, Android/API caveats. Do not commit.
