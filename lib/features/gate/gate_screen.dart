import 'package:flutter/material.dart';
import '../../domain/models.dart';
import '../app/app_state.dart';
import '../shared/format.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

class GateScreen extends StatelessWidget {
  final String packageName;
  final AppState appState;
  final VoidCallback onPlay;
  final VoidCallback? onChooseAnotherApp;

  const GateScreen({
    super.key,
    required this.packageName,
    required this.appState,
    required this.onPlay,
    this.onChooseAnotherApp,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (BuildContext context, _) {
        final BlockedTarget? target = appState.targetFor(packageName);
        final String displayName = target?.displayName ?? packageName;
        final String surface = target != null
            ? surfaceLabel(target.surface.name)
            : 'Whole app';
        final AccessDecision decision = appState.decisionFor(packageName);
        final bool isLocked = decision.status == AccessStatus.locked;
        final String lockLabel = isLocked
            ? formatRemaining(decision.remainingMs ?? 0)
            : '';

        final ThemeData theme = Theme.of(context);

        return Scaffold(
          appBar: AppBar(
            title: Text('GATE', style: SonderTextStyles.displayMedium),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              Semantics(
                header: true,
                label: 'Paused $displayName $surface',
                child: SonderPanel(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primary.withValues(
                                alpha: 0.12,
                              ),
                              border: Border.all(
                                color: theme.colorScheme.outline,
                                width: 1.5,
                              ),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Icon(
                              Icons.lock_outline,
                              color: theme.colorScheme.primary,
                            ),
                          ),
                          const SizedBox(width: SonderSpacing.md),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  displayName,
                                  style: SonderTextStyles.displaySmall.copyWith(
                                    color: theme.colorScheme.onSurface,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  surface,
                                  style: SonderTextStyles.caption.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          SonderStatusChip(
                            label: isLocked ? 'LOCKED' : 'PAUSED',
                            background: isLocked
                                ? theme.colorScheme.error
                                : theme.colorScheme.primary,
                            foreground: Colors.white,
                            icon: isLocked
                                ? Icons.lock
                                : Icons.pause_circle_outline,
                          ),
                        ],
                      ),
                      const SizedBox(height: SonderSpacing.md),
                      Text(
                        isLocked
                            ? 'This target is locked. Wait for the timer or try another target.'
                            : 'Take a beat. If you want in, play one quick hand.',
                        style: SonderTextStyles.body.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      if (isLocked) ...[
                        const SizedBox(height: SonderSpacing.sm),
                        Semantics(
                          label: 'Locked $lockLabel remaining',
                          child: Row(
                            children: [
                              Icon(
                                Icons.timer_outlined,
                                size: 14,
                                color: theme.colorScheme.error,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'LOCKED  $lockLabel',
                                style: SonderTextStyles.caption.copyWith(
                                  color: theme.colorScheme.error,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              SonderPanel(
                color: theme.colorScheme.surface,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'HOW IT WORKS',
                      style: SonderTextStyles.labelMono.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    _RuleRow(
                      icon: Icons.check_circle_outline,
                      color: theme.colorScheme.tertiary,
                      text: 'Win → 5 minutes of access',
                    ),
                    const SizedBox(height: SonderSpacing.xs),
                    _RuleRow(
                      icon: Icons.lock_outline,
                      color: theme.colorScheme.error,
                      text: 'Loss → locked for 10 minutes',
                    ),
                    const SizedBox(height: SonderSpacing.xs),
                    _RuleRow(
                      icon: Icons.timer_outlined,
                      color: theme.colorScheme.onSurfaceVariant,
                      text:
                          'Leave the app for 20 seconds and the 5-minute grant expires',
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    Text(
                      'One hand, no wagers. Dealer stands on all 17.',
                      style: SonderTextStyles.caption.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              if (isLocked)
                Semantics(
                  label: 'Locked $lockLabel remaining',
                  child: SonderPanel(
                    child: Column(
                      children: [
                        Text(
                          'TRY AGAIN IN',
                          style: SonderTextStyles.caption.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: SonderSpacing.xs),
                        Text(
                          lockLabel,
                          style: SonderTextStyles.displayLarge.copyWith(
                            color: theme.colorScheme.error,
                          ),
                        ),
                        const SizedBox(height: SonderSpacing.sm),
                        Text(
                          'You can still manage targets or check time remaining.',
                          style: SonderTextStyles.body.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                            fontSize: 11,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                )
              else
                SonderPrimaryButton(
                  label: 'PLAY BLACKJACK',
                  icon: Icons.videogame_asset_outlined,
                  onPressed: onPlay,
                  semanticLabel: 'Play blackjack for $displayName',
                ),
              const SizedBox(height: SonderSpacing.sm),
              if (onChooseAnotherApp != null)
                SonderSecondaryButton(
                  label: 'CHOOSE ANOTHER APP',
                  onPressed: onChooseAnotherApp,
                  semanticLabel: 'Choose another app',
                ),
              const SizedBox(height: SonderSpacing.lg),
              _BestEffortNote(surface: surface),
            ],
          ),
        );
      },
    );
  }
}

class _RuleRow extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String text;

  const _RuleRow({required this.icon, required this.color, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 14, color: color),
        const SizedBox(width: SonderSpacing.sm),
        Expanded(
          child: Text(
            text,
            style: SonderTextStyles.body.copyWith(fontSize: 11),
          ),
        ),
      ],
    );
  }
}

class _BestEffortNote extends StatelessWidget {
  final String surface;
  const _BestEffortNote({required this.surface});

  @override
  Widget build(BuildContext context) {
    if (surface != 'Shorts' && surface != 'Reels') {
      return const SizedBox.shrink();
    }
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: 'Surface detection note',
      child: Container(
        padding: const EdgeInsets.all(SonderSpacing.md),
        decoration: BoxDecoration(
          border: Border.all(
            color: theme.colorScheme.outline.withValues(alpha: 0.6),
            width: 1.25,
          ),
          borderRadius: SonderRadii.panel,
          color: theme.colorScheme.outline.withValues(alpha: 0.08),
        ),
        child: Text(
          'Heads up: Shorts/Reels detection is best-effort and can vary by app version or device. '
          'If the wrong view was flagged, use package-level targets for reliable blocking.',
          style: SonderTextStyles.body.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            fontSize: 11,
          ),
        ),
      ),
    );
  }
}
