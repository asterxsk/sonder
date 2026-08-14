import 'access_decision.dart';
import 'access_status.dart';
import 'enforcement_snapshot.dart';

/// Durations in milliseconds. UTC epoch ms is injected via [nowEpochMs].
const int kGrantDurationMs = 300000; // 5 minutes
const int kLockDurationMs = 600000; // 10 minutes
const int kAbandonThresholdMs = 20000; // 20 seconds

/// Evaluates access for a single [state] at [nowEpochMs].
///
/// Policy (priority order):
/// 1. Active lock (`lockedUntilEpochMs > now`) => [AccessStatus.locked].
/// 2. Active grant (`grantedUntilEpochMs > now`) => [AccessStatus.allowed].
/// 3. Otherwise => [AccessStatus.needsChallenge].
///
/// Invalid or missing stored data must already have been mapped to a
/// closed snapshot (no grant). This function never grants access on
/// malformed input; callers should drop malformed snapshots before calling.
AccessDecision evaluateAccess(EnforcementSnapshot state, int nowEpochMs) {
  final lockedUntil = state.lockedUntilEpochMs;
  if (lockedUntil != null && lockedUntil > nowEpochMs) {
    return AccessDecision(
      status: AccessStatus.locked,
      remainingMs: lockedUntil - nowEpochMs,
    );
  }

  final grantedUntil = state.grantedUntilEpochMs;
  if (grantedUntil != null && grantedUntil > nowEpochMs) {
    return AccessDecision(
      status: AccessStatus.allowed,
      remainingMs: grantedUntil - nowEpochMs,
    );
  }

  return const AccessDecision(status: AccessStatus.needsChallenge);
}

/// Grants access for exactly 5 minutes, clearing any active lock and
/// background marker. Mirrors the platform channel snapshot field
/// `grantedUntilEpochMs`.
EnforcementSnapshot applyWin(EnforcementSnapshot state, int nowEpochMs) {
  return EnforcementSnapshot(
    packageName: state.packageName,
    surface: state.surface,
    grantedUntilEpochMs: nowEpochMs + kGrantDurationMs,
    lockedUntilEpochMs: null,
    lastBackgroundEpochMs: null,
  );
}

/// Blocks access for exactly 10 minutes, clearing any active grant and
/// background marker.
///
/// A blackjack push is mapped to this same locked outcome for enforcement
/// purposes (see [BlackjackOutcome] docs). If the UI wants to treat a push
/// as a replay, it should not call [applyLoss] until a decisive outcome
/// occurs.
EnforcementSnapshot applyLoss(EnforcementSnapshot state, int nowEpochMs) {
  return EnforcementSnapshot(
    packageName: state.packageName,
    surface: state.surface,
    grantedUntilEpochMs: null,
    lockedUntilEpochMs: nowEpochMs + kLockDurationMs,
    lastBackgroundEpochMs: null,
  );
}

/// Records that the target package is in the foreground at [nowEpochMs].
///
/// Clears the abandonment timer so a grant is preserved.
EnforcementSnapshot recordForeground(
  EnforcementSnapshot state,
  int nowEpochMs,
) {
  if (state.lastBackgroundEpochMs == null) return state;
  return EnforcementSnapshot(
    packageName: state.packageName,
    surface: state.surface,
    grantedUntilEpochMs: state.grantedUntilEpochMs,
    lockedUntilEpochMs: state.lockedUntilEpochMs,
    lastBackgroundEpochMs: null,
  );
}

/// Records that the target package left the foreground at [nowEpochMs].
///
/// Only tracks abandonment while a grant is present. If no grant is active,
/// the state is unchanged. The timestamp is overwritten on each background
/// transition; duplicate background events should be deduped by the caller,
/// but overwriting is safe (it would only extend the grace window).
EnforcementSnapshot recordBackground(
  EnforcementSnapshot state,
  int nowEpochMs,
) {
  if (state.grantedUntilEpochMs == null) return state;
  return EnforcementSnapshot(
    packageName: state.packageName,
    surface: state.surface,
    grantedUntilEpochMs: state.grantedUntilEpochMs,
    lockedUntilEpochMs: state.lockedUntilEpochMs,
    lastBackgroundEpochMs: nowEpochMs,
  );
}

/// Revokes a grant if the target has remained continuously out of the
/// foreground for at least [kAbandonThresholdMs] (20 seconds).
///
/// Threshold is inclusive: 20,000 ms or more clears the grant. 19,999 ms
/// preserves it. A brief return within the window (via [recordForeground])
/// clears the timer, so only an uninterrupted 20s background span triggers
/// revocation.
EnforcementSnapshot expireAbandonedGrant(
  EnforcementSnapshot state,
  int nowEpochMs,
) {
  final grantedUntil = state.grantedUntilEpochMs;
  final lastBg = state.lastBackgroundEpochMs;
  if (grantedUntil == null || lastBg == null) return state;
  if (nowEpochMs - lastBg >= kAbandonThresholdMs) {
    return EnforcementSnapshot(
      packageName: state.packageName,
      surface: state.surface,
      grantedUntilEpochMs: null,
      lockedUntilEpochMs: state.lockedUntilEpochMs,
      lastBackgroundEpochMs: null,
    );
  }
  return state;
}
