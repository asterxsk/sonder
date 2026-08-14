# Job 05 — Integration, QA, and compliance

Read `employ/plan.md` fully and `design.md`. This job integrates only after Jobs 01–04 artifacts are available in the target worktree. If isolated execution lacks them, create an integration checklist/test/documentation artifacts only; do not fabricate competing app/domain/native implementations.

## Goal
Wire implemented Sonder modules together, validate critical end-to-end state flow, and document Android/Play policy constraints for release review.

## Owned files
Prefer integration-only files: `test/integration/**`, `integration_test/**`, `docs/**`, `README.md`, plus minimal dependency-injection/app-routing edits strictly needed to connect existing public APIs. Do not redesign or rewrite module-owned source. Report any required cross-owner patch precisely if it cannot be safely done.

## Exact work
1. Inspect actual artifacts from Jobs 01–04. Create composition root/routing only where missing, using `AccessPlatform` channel `sonder/access`, Job 02 repository/policy public models, and Job 04 surface widgets. Ensure sync happens after target/config and blackjack outcome changes; map native events to foreground/background policy updates then re-sync snapshots.
2. Add integration/widget tests with fakes proving the sequence: default target intercepted, challenge displayed, win sets 5-minute allowed, background 19,999ms keeps it, 20,000ms expires it, subsequent interception challenges again; loss locks 10 minutes; custom package target uses same lifecycle; locked state beats stale grant.
3. Add `README.md` or `docs/android-enforcement.md`: setup steps for debug Android device, enabling accessibility service, overlays, known OEM/battery constraints, manual custom-app behavior, exact 5/10/20 timing rules, privacy/data statement, Shorts/Reels best-effort/version-localized limitation, no bypass-proof claim, and Play Console AccessibilityService declaration/review checklist.
4. Run Flutter formatting/analyze/unit+integration suite. If Android SDK/device exists, run Android unit build and give a brief manual physical-device test checklist. Do not use emulator results to claim reliable YouTube/Instagram surface detection.

## Acceptance criteria
- Existing module contracts are composed without altering their semantics.
- Test suite covers all business timing rules through feature integration.
- Documentation is candid, actionable, privacy-conscious, policy-ready.
- Exact commands/results included. No commits.

## Report
State whether full integration was possible in the isolated tree. List patches/files, test evidence, blockers, and manual device checks remaining.
