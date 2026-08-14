# Job 02 — Access domain and blackjack

Read `employ/plan.md`, especially **Product contract**, **Shared contracts**, **Engineering conventions**, and **Acceptance at merge**.

## Goal
Implement and thoroughly test the pure Dart blackjack, access-policy, target configuration, and persistence boundary used by Sonder.

## Owned files
Create only `lib/domain/**`, `lib/data/**`, `test/domain/**`, `test/data/**`. Avoid widgets, `lib/main.dart`, `pubspec.yaml`, Kotlin/manifest/resources.

## Exact work
1. Implement every model and exact public policy signature from `plan.md` Shared contracts. Preserve enum case and JSON field names exactly.
2. Implement a testable blackjack engine with injectable random source/deck factory. One fresh 52-card deck per hand; Fisher–Yates shuffle with `Random.secure()` production default; ranks, values, ace soft/hard total handling, player hit/stand, dealer stands on all 17, bust/natural/push outcomes. A push is not a win: apply the locked outcome unless UI treats it as a replay before an access decision; choose one behavior, expose it in a documented result enum, and test it. Do not add money, bets, odds, or gambling language.
3. Implement access policy exactly: active `lockedUntilEpochMs > now` means `locked`; otherwise active grant means `allowed`; otherwise `needsChallenge`. `applyWin` clears lock and sets grant `now + 300000`; `applyLoss` clears grant/background then sets lock `now + 600000`; background after a grant is tracked; return within 20000 preserves grant; at 20000 ms or later continuously backgrounded revoke grant. All timestamps UTC epoch ms.
4. Create target configuration/repository interfaces and a local JSON-friendly implementation suitable for later SharedPreferences/Hive binding. Ensure built-ins are enabled by default with exact packages/surfaces from plan; manual app is a `wholeApp` package target; deduplicate package+surface entries; built-ins cannot be removed but may be disabled only if the product policy needs it—document final choice.
5. Create snapshot serialization/deserialization. Invalid/missing stored data must produce a closed/needs-challenge result, never silently grant access.
6. Write dedicated unit tests first, then implementation. Cover ace scoring, dealer policy, natural blackjack, bust, deterministic wins/losses/pushes, 5/10 minute boundaries, lock precedence, 19,999/20,000 ms abandonment boundaries, return within threshold, default targets, custom add/remove/dedup, serializer invalid data.

## Acceptance criteria
- Tests are deterministic; clock/randomness never comes from wall clock inside policy tests.
- Public models/functions exactly match `plan.md`; no Flutter UI or Android dependency needed.
- `dart format`, `flutter analyze`, relevant `flutter test` pass.

## Report
List exact public APIs, owned files, tests/commands/results, assumptions requiring integrator attention. Do not commit.
