import 'package:flutter/material.dart';
import '../../domain/models.dart';
import '../app/app_state.dart';
import '../app/stats_models.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

class StatsScreen extends StatelessWidget {
  final AppState appState;

  const StatsScreen({super.key, required this.appState});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (BuildContext context, _) {
        final StatsSnapshot stats = appState.stats;
        final ThemeData theme = Theme.of(context);
        return Scaffold(
          appBar: AppBar(
            title: Text('STATS', style: SonderTextStyles.displayMedium),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              Text(
                'YOUR ATTENTION, AT A GLANCE',
                style: SonderTextStyles.labelMono.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: SonderSpacing.md),
              Row(
                children: [
                  Expanded(
                    child: _MetricPanel(
                      label: 'TIME SAVED',
                      value: formatMinutes(stats.timeSavedMs),
                      detail: 'earned through wins',
                      icon: Icons.hourglass_bottom,
                      color: theme.colorScheme.tertiary,
                    ),
                  ),
                  const SizedBox(width: SonderSpacing.sm),
                  Expanded(
                    child: _MetricPanel(
                      label: 'WIN RATE',
                      value: '${stats.winRatePercent}%',
                      detail: '${stats.handsPlayed} hands',
                      icon: Icons.favorite_border,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: SonderSpacing.sm),
              Row(
                children: [
                  Expanded(
                    child: _MetricPanel(
                      label: 'APP OPENS',
                      value: '${stats.interceptedOpens}',
                      detail: 'challenge prompts',
                      icon: Icons.lock_outline,
                      color: theme.colorScheme.error,
                    ),
                  ),
                  const SizedBox(width: SonderSpacing.sm),
                  Expanded(
                    child: _MetricPanel(
                      label: 'LOCKOUTS',
                      value: '${stats.losses}',
                      detail: formatMinutes(stats.lockedMs),
                      icon: Icons.timer_outlined,
                      color: theme.colorScheme.error,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: SonderSpacing.lg),
              SonderPanel(
                semanticLabel: 'Challenge history',
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'CHALLENGE HISTORY',
                      style: SonderTextStyles.labelMono.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.md),
                    _BarRow(
                      label: 'WINS',
                      value: stats.wins,
                      max: stats.handsPlayed,
                      color: theme.colorScheme.tertiary,
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    _BarRow(
                      label: 'LOSSES',
                      value: stats.losses,
                      max: stats.handsPlayed,
                      color: theme.colorScheme.error,
                    ),
                    const SizedBox(height: SonderSpacing.md),
                    Text(
                      stats.handsPlayed == 0
                          ? 'Play your first hand to start building your history.'
                          : 'Every hand is a small choice toward being here.',
                      style: SonderTextStyles.body.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              SonderPanel(
                semanticLabel: 'Per app stats',
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'BY APP',
                      style: SonderTextStyles.labelMono.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    ...appState.targets.map(
                      (BlockedTarget target) => _AppStatRow(
                        target: target,
                        stats: appState.statsFor(target.packageName),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _MetricPanel extends StatelessWidget {
  final String label;
  final String value;
  final String detail;
  final IconData icon;
  final Color color;

  const _MetricPanel({
    required this.label,
    required this.value,
    required this.detail,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SonderPanel(
      padding: const EdgeInsets.all(SonderSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(height: SonderSpacing.sm),
          Text(label, style: SonderTextStyles.caption.copyWith(color: color)),
          const SizedBox(height: SonderSpacing.xs),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              value,
              style: SonderTextStyles.displayMedium.copyWith(
                color: theme.colorScheme.onSurface,
              ),
            ),
          ),
          const SizedBox(height: SonderSpacing.xs),
          Text(
            detail,
            style: SonderTextStyles.caption.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _BarRow extends StatelessWidget {
  final String label;
  final int value;
  final int max;
  final Color color;

  const _BarRow({
    required this.label,
    required this.value,
    required this.max,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final double fraction = max == 0 ? 0 : (value / max).clamp(0.0, 1.0);
    return Row(
      children: [
        SizedBox(
          width: 52,
          child: Text(label, style: SonderTextStyles.caption),
        ),
        Expanded(
          child: Container(
            height: 10,
            decoration: BoxDecoration(
              border: Border.all(color: theme.colorScheme.outline),
              borderRadius: SonderRadii.chip,
            ),
            child: FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: fraction,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: SonderRadii.chip,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(width: SonderSpacing.sm),
        SizedBox(width: 24, child: Text('$value', textAlign: TextAlign.end)),
      ],
    );
  }
}

class _AppStatRow extends StatelessWidget {
  final BlockedTarget target;
  final TargetStats stats;

  const _AppStatRow({required this.target, required this.stats});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: SonderSpacing.sm),
      child: Row(
        children: [
          Icon(
            target.enabled ? Icons.shield_outlined : Icons.shield_moon_outlined,
            size: 16,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(width: SonderSpacing.sm),
          Expanded(
            child: Text(target.displayName, style: SonderTextStyles.bodyBold),
          ),
          Text(
            '${stats.opens} opens  ·  ${stats.wins} wins',
            style: SonderTextStyles.caption.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

String formatMinutes(int milliseconds) {
  final int minutes = milliseconds ~/ 60000;
  if (minutes < 1) return '0m';
  return '${minutes}m';
}
