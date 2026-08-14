import 'package:flutter/material.dart';
import '../../domain/models.dart';
import '../app/app_state.dart';
import '../shared/format.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

/// Standalone result view used when returning via the platform seam after a win.
/// Kept separate from the inline blackjack result so the gate overlay / deep
/// link can land directly on a result with timers.
class ResultScreen extends StatelessWidget {
  final String packageName;
  final bool won;
  final AppState appState;
  final VoidCallback onDone;

  const ResultScreen({
    super.key,
    required this.packageName,
    required this.won,
    required this.appState,
    required this.onDone,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (BuildContext context, _) {
        final BlockedTarget? target = appState.targetFor(packageName);
        final String displayName = target?.displayName ?? packageName;
        final ThemeData theme = Theme.of(context);
        final int? remaining = appState.decisionFor(packageName).remainingMs;
        final String timer = remaining != null
            ? formatRemaining(remaining)
            : (won ? '5m' : '10m');

        final Color accent = won
            ? theme.colorScheme.tertiary
            : theme.colorScheme.error;
        final IconData icon = won
            ? Icons.check_circle_outline
            : Icons.lock_outline;
        final String headline = won ? '5 MIN EARNED' : 'LOCKED';
        final String title = won ? 'You earned it.' : 'Locked for now.';
        final String sub = won
            ? 'Returning to $displayName. Your grant lasts $timer.'
            : '$displayName is locked for $timer. Leaving it for 20 seconds will also end any future grant early.';
        final String semanticLabel = won
            ? 'Win, 5 minutes earned, $timer'
            : 'Loss, locked 10 minutes, $timer';

        return Scaffold(
          appBar: AppBar(
            title: Text('RESULT', style: SonderTextStyles.displayMedium),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              Semantics(
                liveRegion: true,
                label: semanticLabel,
                child: SonderPanel(
                  borderWidth: 2.25,
                  child: Column(
                    children: [
                      Icon(icon, size: 36, color: accent),
                      const SizedBox(height: SonderSpacing.sm),
                      Text(
                        headline,
                        style: SonderTextStyles.displayMedium.copyWith(
                          color: accent,
                        ),
                      ),
                      const SizedBox(height: SonderSpacing.xs),
                      Text(
                        title,
                        style: SonderTextStyles.displaySmall.copyWith(
                          color: theme.colorScheme.onSurface,
                        ),
                      ),
                      const SizedBox(height: SonderSpacing.sm),
                      Text(
                        sub,
                        style: SonderTextStyles.body.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: SonderSpacing.md),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          border: Border.all(
                            color: theme.colorScheme.outline,
                            width: 1.5,
                          ),
                          borderRadius: SonderRadii.chip,
                          color: theme.colorScheme.outline.withValues(
                            alpha: 0.08,
                          ),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.timer_outlined,
                              size: 14,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              timer,
                              style: SonderTextStyles.displaySmall.copyWith(
                                fontSize: 14,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: SonderSpacing.lg),
                      SonderPrimaryButton(
                        label: won ? 'RETURN TO $displayName' : 'DONE',
                        onPressed: onDone,
                        icon: won ? Icons.arrow_forward : Icons.check,
                        semanticLabel: won ? 'Return to $displayName' : 'Done',
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              Text(
                won
                    ? 'If you leave the app for 20 consecutive seconds, the grant expires and you will need another hand.'
                    : 'A non-expired lock always wins over a grant. Manage targets in Settings to disable blocking.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontSize: 11,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
