# Job 04 — Flutter product surfaces

Read `employ/plan.md`, especially **Goal**, **Visual contract**, **Shared contracts**, **Files and ownership**, and **Acceptance at merge**. Root `design.md` and `elements.png` are visual source of truth.

## Goal
Create Sonder’s Flutter home, blocked-app, blackjack, result, manual target manager, and settings/onboarding-adjacent product surfaces, using the exact public contracts planned for Jobs 01–03.

## Owned files
Create only feature UI/state files under `lib/features/**`, `test/features/**`, and feature-local assets if essential. Do not change `main.dart`, app/theme primitives, pubspec, domain/data, Android native files, or app icons. Code against planned APIs if dependency files do not exist in this isolated session.

## Exact work
1. Create focused feature folders/controllers for home, targets, gate, blackjack, and settings. Widgets delegate game/policy calculations to Job 02 public API; platform integration goes through a narrow `AccessPlatform` interface using the exact channel name/method/event shapes in plan.
2. Home: time bank/remaining earned time, today summary placeholder without fabricated metrics, earned time, next lockout, enabled target list, bottom navigation. Use explicit `LOCKED`, `5 MIN EARNED`, durations—not color alone.
3. Targets: prepopulate exact YouTube Shorts/Instagram Reels defaults, show `best-effort` surface-detection disclosure, manual launchable-app picker seam, enable/disable and removal behavior per repository policy. Custom apps must be package-level `wholeApp`.
4. Gate/blocked screen: focused explanation, target app name, exact reward rules, primary `PLAY BLACKJACK`, alternate target option, timer/lockout status, accessible semantics.
5. Blackjack: cards/totals, Hit/Stand, concise persistent rule, immediate deterministic outcome state. Support reduced motion: no card-flip/shake necessary; normal mode only short purposeful transitions. Do not use gambling/monetary vocabulary. Result screen returns to target via platform seam after win and visibly shows 5 minutes; loss visibly shows 10-minute lockout/countdown.
6. Settings/capability screen: theme control; clear current Android permission/service state; action seams to settings; honest guidance that enforcement and Shorts/Reels detection have Android/version limitations.
7. Write widget/controller tests using fake repository/platform/clock for core labels/actions: default targets shown, challenge win/loss display, lock countdown, 20s expired grant returns challenge, manual app target UI, capability disclosures, semantic labels.

## Acceptance criteria
- Visual output follows pixel-console source: token-driven dark/light colors, pixel-like hard borders, display/mono hierarchy, no copied third-party assets.
- All screen states are usable via TalkBack/keyboard and reduced motion.
- No domain duplication beyond view formatting.
- `dart format`, `flutter analyze`, relevant `flutter test` pass against available scaffold/contracts.

## Report
List owned files, exact imports/public contracts expected, commands/tests/results, integration assumptions. Do not commit.
