# Sonder — Native enforcement notes (for Job 03)

This document lives under owned Android source and satisfies the plan item:
> Explain package visibility and accessibility policy implications in a native
> `README`/comment document under owned Android source, and include Play policy
> review notes because AccessibilityService use requires compliant disclosure/declaration.

## What Sonder does on Android

- Flutter owns the canonical game and policy model (grant 5 min, lock 10 min,
  20 s abandonment). Native owns a compact `EnforcementSnapshot` DataStore mirror
  so enforcement works while Flutter is suspended.
- An `AccessibilityService` (user must explicitly enable it in system settings)
  observes `TYPE_WINDOW_STATE_CHANGED | TYPE_WINDOWS_CHANGED | TYPE_WINDOW_CONTENT_CHANGED`
  and `flagRetrieveInteractiveWindows` to detect when a **configured target package**
  comes to the foreground.
- For package-level custom apps (`wholeApp`) detection is reliable: any foreground
  of that package is an intercept.
- For built-ins (YouTube Shorts / Instagram Reels) the service uses a **best-effort,
  conservative view-id classifier** (`SurfaceClassifier`). Only a tiny allow-list
  of resource ids (e.g. `com.google.android.youtube:id/reel_watch_fragment`) is
  trusted. Inconclusive results do **not** produce a Shorts/Reels intercept — this
  avoids false positives. The product contract requires that we never claim
  bypass-proof individual-surface detection; the overlay disclaimer states the
  limitation inline. Entire-app custom rules remain the reliable fallback.
- A `TYPE_ACCESSIBILITY_OVERLAY` gate, scoped to the accessibility service, blocks
  interaction only while the current target is `locked` or `needsChallenge`, shows
  accessible status/timer, and offers one action that deep-links to Sonder's
  challenge. It is removed immediately on `allowed` or when the target leaves the
  foreground, and never overlays outside configured targets or Sonder itself.

## Permissions

- `AccessibilityService` — declared in `AndroidManifest.xml` with
  `android:accessibilityFeedbackType="feedbackGeneric"` and a transparent
  `@string/sonder_accessibility_service_description`. The user enables it via
  system Accessibility settings; `SonderChannelPlugin.openAccessibilitySettings()`
  opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. No stealth or hidden declaration.
- `TYPE_ACCESSIBILITY_OVERLAY` overlay — used via the accessibility service
  window type, which does **not** require `SYSTEM_ALERT_WINDOW` on Android. No
  `SYSTEM_ALERT_WINDOW` permission is requested.
- Package visibility — `AndroidManifest.xml` declares a minimal `<queries>` block:
  built-ins (`com.google.android.youtube`, `com.instagram.android`) plus a
  generic `<intent><action MAIN/><category LAUNCHER/></intent>` query so
  `PackageManager.queryIntentActivities` can enumerate launchable apps for the
  manual picker. No broad `QUERY_ALL_PACKAGES`.
  Required for Android 11+ (`targetSdk 36`): without the `<queries>`, launcher
  queries would return empty. This is minimal and policy-safe.

## What Sonder does NOT do

- No root, VPN, device-owner, device-admin, `BIND_DEVICE_ADMIN`, or hidden/private
  APIs. No `WRITE_SECURE_SETTINGS`.
- No keystroke logging, no reading passwords/messages/page content, no click
  automation on behalf of the user.
- No network exfiltration of accessibility data; snapshots are local DataStore JSON.
- No bypass-proof claims; docs and overlay UI state the limitation explicitly.
- `listLaunchableApps` returns only `packageName` + `displayName` of launchable
  apps; no usage history, install time, or traffic is collected.

## DataStore snapshot

- Single `Preferences DataStore` key `sonder_snapshots_json` holds a JSON array
  of objects with **exact** field names: `packageName`, `surface`, `grantedUntilEpochMs`,
  `lockedUntilEpochMs`, `lastBackgroundEpochMs`. Native and Dart share these names
  verbatim. Unknown/malformed snapshots fail closed per target; a malformed
  `syncSnapshots` batch fails the entire sync so no temporary grant is introduced.
- The 20 s continuous-background revocation is enforced natively (timers + periodic
  sweep) so it works while Flutter is suspended; Dart mirrors the same rule.

## Play Policy review checklist (before release)

1. **AccessibilityService declaration** — In Play Console, declare the
   AccessibilityService usage under *App content → Accessibility* and justify it
   as a screen-time self-control tool that the user voluntarily enables. Upload
   screenshots showing the disclosure string and the system toggle.
2. **Disclosure** — The in-app onboarding/education screen (Job 01) must show
   the same disclosure that appears in `strings.xml` before directing the user
   to enable the service. Do not request the toggle without that screen.
3. **No alternative API** — Document that `UsageStats` + `SYSTEM_ALERT_WINDOW`
   is intentionally not used because it cannot reliably attribute foreground
   transitions to a user intent without accessibility window events, and it would
   require a more sensitive permission. Accessibility is the least-privileged
   path that supports overlay-gated interception with TalkBack compatibility.
4. **Package visibility** — Justify the `<queries>` in review notes (listing
   launchable apps for manual target selection). The generic launcher query is
   the Play-blessed alternative to `QUERY_ALL_PACKAGES`.
5. **Localization / version fragility** — The classifier allow-list is version-
   tolerant but not future-proof. Note in review that Shorts/Reels detection
   is best-effort and that package-level custom apps are the supported
   "reliable" granularity.
6. **Testing** — Provide a test account / video showing: enable service →
   foreground a configured whole-app target → overlay appears → challenge win
   grants 5 min → lock 10 min → background 20 s revokes grant.

## Device / OEM caveats

- Some OEMs (notably certain Xiaomi/Huawei/Samsung builds) aggressively restrict
  background accessibility services or kill overlays under battery optimization.
  Users should be guided to disable battery optimization for Sonder if the service
  is observed to be disabled after reboot. We do not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  proactively; if needed, route through system settings with user consent.
- `TYPE_ACCESSIBILITY_OVERLAY` availability varies by Android version/vendor.
  `getEnforcementCapabilities()` reports `overlayAvailable`; Flutter should
  surface a "service enabled but overlay unavailable" education state when false.
- Dark/light pixel-console theming, TalkBack labels, contrast, large touch
  targets, and reduced motion are provided by Job 01/04; the overlay keeps a
  minimal high-contrast scrim and explicit textual status.
