# Sonder

> Be here, not everywhere.

Sonder is an Android-first Flutter app for intentional screen-time friction. When a user opens a selected distracting app, Sonder presents one fair hand of blackjack before access is granted.

- **Win:** 5 minutes of access
- **Lose:** 10-minute lockout
- **Leave during a grant:** access expires after 20 consecutive seconds
- **No wagers, wallet, ads, analytics, or remote account required**

Sonder is designed to interrupt automatic app opening without treating the user like a child.

## Highlights

- Blackjack-based access gate with deterministic, testable policy rules
- Built-in YouTube Shorts and Instagram Reels targets
- Custom package-level app targets
- Android Accessibility Service and overlay integration
- Light and dark themes with a compact, game-inspired visual system
- Local persistence for targets, onboarding, and enforcement snapshots
- Fail-closed decoding for malformed enforcement data

## Requirements

- Flutter SDK compatible with Dart `^3.12.2`
- Android SDK configured through Flutter
- Android device or emulator running the project-supported minimum SDK
- A physical Android device recommended for Accessibility Service and overlay QA

Sonder is currently Android-first. Surface detection for Shorts and Reels is best-effort; custom whole-app targets provide the reliable enforcement path.

## Quick start

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

Build a debug APK:

```bash
flutter build apk --debug
```

The application ID is `app.sonder.sonder`.

## First-run setup

1. Launch Sonder on an Android device.
2. Complete onboarding and review the access explanation.
3. Open Android Accessibility settings from Sonder.
4. Enable Sonder's Accessibility Service.
5. Grant overlay access if Android requests it.
6. Return to Sonder and verify the service status in Settings.
7. Open a configured target app to trigger the gate.

For physical-device QA, permission details, Play Console review preparation, and OEM limitations, see [`docs/android-enforcement.md`](docs/android-enforcement.md).

## Access rules

All policy timestamps use UTC epoch milliseconds. The canonical rules live in [`lib/domain/access_policy.dart`](lib/domain/access_policy.dart).

| Event | Result |
| --- | --- |
| Blackjack win | Grant access for 300,000 ms (5 minutes) |
| Blackjack loss | Lock access for 600,000 ms (10 minutes) |
| Background for at least 20,000 ms during a grant | Revoke the grant |
| Return before 20,000 ms | Preserve the grant |

A non-expired lock takes priority over a stale grant. Expiration uses strict `<` comparisons, so a timer ending exactly at the current instant is expired.

## Architecture

```text
Flutter UI
  └── AppState
        ├── pure access policy and domain models
        ├── local repositories and persistence
        └── AccessPlatform
              └── MethodChannel: sonder/access
                    ├── Accessibility Service
                    ├── overlay gate
                    └── native session storage
```

Key boundaries:

- `lib/domain/` contains pure access, target, and blackjack rules.
- `lib/data/` contains local persistence and serialization.
- `lib/features/` contains product screens and application state.
- `lib/design/` contains shared visual primitives and theme tokens.
- `android/app/src/main/kotlin/` contains the native enforcement bridge.
- `test/domain/` verifies policy and game behavior.
- `test/integration/` verifies lifecycle, outcomes, and bridge synchronization.

The Flutter/native contract uses the `sonder/access` method channel and `sonder/access.events` event channel. Snapshot field names are intentionally stable:

```text
packageName
surface
grantedUntilEpochMs
lockedUntilEpochMs
lastBackgroundEpochMs
```

## Testing

Run static analysis and the complete Dart test suite:

```bash
flutter analyze
flutter test
```

The test suite covers:

- Win, loss, lock, grant, and expiration boundaries
- Inclusive 20-second abandonment behavior
- Lock-over-grant precedence
- Blackjack rules and result handling
- Target configuration and persistence
- Foreground/background event handling
- Native bridge synchronization
- Widget and feature flows

Use a real Android device for Accessibility Service, overlay, OEM battery-management, and Shorts/Reels surface-detection checks. Flutter unit and widget tests do not prove behavior inside third-party apps.

## Privacy

Sonder's baseline implementation stores targets, onboarding state, and enforcement snapshots locally. It does not require a network service, account, or analytics SDK. The native bridge syncs package names, target surfaces, and enforcement timestamps; it does not log URLs, view-tree text, or personal content.

Review [`docs/android-enforcement.md`](docs/android-enforcement.md) before changing permissions, adding telemetry, or preparing a store submission.

## Known limitations

- YouTube Shorts and Instagram Reels classification depends on third-party accessibility view hierarchies and is best-effort across app versions, languages, and OEMs.
- Custom targets use package-level (`wholeApp`) enforcement and are the reliable blocking option.
- Battery optimizers and OEM background restrictions may suspend the service or overlay.
- Work profiles, managed devices, secure windows, and split-screen behavior can limit enforcement.
- Sonder does not use root, VPN, device-owner APIs, hidden APIs, or private Android APIs.
- Release builds currently use the debug signing configuration and require production signing setup before distribution.

Do not describe Shorts/Reels protection as bypass-proof.

## Project structure

```text
lib/
  data/       Local storage and codecs
  design/     Themes and reusable UI primitives
  domain/     Pure product rules and models
  features/   App state and user-facing screens
android/      Accessibility Service, overlay, and channel bridge
test/         Unit, widget, feature, and integration tests
docs/         Android enforcement and release-review notes
assets/       Brand assets
```

## Contributing

1. Keep access policy pure and independently testable.
2. Preserve the Flutter/native channel names and payload fields.
3. Add or update tests for policy boundary changes.
4. Run `flutter analyze` and `flutter test` before opening a pull request.
5. Keep user-facing copy direct, fair, and free of gambling language.

## License

No license has been declared yet. Treat the repository as all-rights-reserved until a license is added.
