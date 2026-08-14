# Job 01 — Foundation and identity

Read `employ/plan.md`, especially **Goal**, **Visual contract**, **Target architecture**, **Engineering conventions**, and **Acceptance at merge**.

## Goal
Create the Android Flutter project foundation and Sonder visual identity, including a production-ready original Android adaptive app icon.

## Owned files
Own only project/bootstrap and identity files. Create or modify: `pubspec.yaml`, `analysis_options.yaml`, `lib/main.dart`, `lib/app.dart`, `lib/design/**`, `lib/features/onboarding/**`, `assets/**`, `android/app/src/main/res/**`, Android app name/launcher metadata files. Do not create feature screens owned by Job 04, domain rules owned by Job 02, or service/channel code owned by Job 03.

## Exact work
1. If absent, create a Flutter app (`android` platform) whose visible Android application label is `Sonder`. Do not initialize Git.
2. Build reusable design primitives: semantic dark/light color schemes using every exact source token; pixel-like hard-bordered panels; primary/secondary/ghost buttons; status labels; progress/timer display; accessible typography. Use a legally usable font or fallback; do not ship unlicensed/copyrighted game assets.
3. Add app-level light/dark theme selection respecting system setting by default and reduced-motion aware duration helpers.
4. Create an original app-icon master SVG plus Android adaptive foreground/background and monochrome assets. Visual: lavender pixel heart incorporating an unmistakable but simple keyhole/lock, near-black background, no typography, no third-party mark. Configure standard adaptive and round launcher icon resources and verify Android manifest references them.
5. Create an onboarding shell. It must explain Sonder’s purpose and the exact rules: win `5 MIN`, lose `LOCKED 10 MIN`; include an intentional permissions education step with buttons/callback seams for accessibility/overlay capability but no native implementation. State explicitly that Shorts/Reels detection is best-effort and app-level custom blocking depends on Android permissions.
6. Add unit/widget tests for token/theme contrast-independent status text, visible app label/onboarding rules, and icon resource presence/configuration when testable.

## Acceptance criteria
- `flutter analyze` passes for owned Dart code.
- `flutter test` executes tests introduced here.
- App opens into an original Sonder shell using root `design.md`/`elements.png` visual rules.
- Android launcher resources include adaptive, round, and monochrome icon variants; app name is exactly `Sonder`.
- No YouTube/Instagram trademarks, copied character art, or actual access-policy logic.

## Report
List files changed, dependencies added, exact verification commands/results, plus public widgets/tokens intended for Job 04. Do not commit.
