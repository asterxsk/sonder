import 'package:flutter/material.dart';
import '../app/app_state.dart';
import '../shared/format.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

class HomeScreen extends StatelessWidget {
  final AppState appState;
  const HomeScreen({super.key, required this.appState});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (BuildContext context, _) {
        final int totalGranted = appState.totalGrantedRemainingMs;
        final int? lockRemaining = appState.soonestLockRemainingMs;
        return Scaffold(
          appBar: AppBar(
            title: Semantics(
              header: true,
              child: Text('SONDER', style: SonderTextStyles.displayMedium),
            ),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              _TimeBankPanel(
                totalGrantedMs: totalGranted,
                lockRemainingMs: lockRemaining,
              ),
              const SizedBox(height: SonderSpacing.lg),
              const _TodaySummaryPlaceholder(),
              const SizedBox(height: SonderSpacing.lg),
              Semantics(
                label: 'Tagline be here, not everywhere.',
                child: Text(
                  'be here, not everywhere.',
                  style: SonderTextStyles.body.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _TimeBankPanel extends StatelessWidget {
  final int totalGrantedMs;
  final int? lockRemainingMs;

  const _TimeBankPanel({required this.totalGrantedMs, this.lockRemainingMs});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String earnedLabel = totalGrantedMs > 0
        ? formatRemaining(totalGrantedMs)
        : '0m';
    final bool hasLock = lockRemainingMs != null;

    return SonderPanel(
      semanticLabel: 'Time bank',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.hourglass_bottom,
                size: 16,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: SonderSpacing.sm),
              Text(
                'TIME BANK',
                style: SonderTextStyles.labelMono.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: SonderSpacing.sm),
          Semantics(
            label: 'Earned time $earnedLabel',
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  earnedLabel,
                  style: SonderTextStyles.displayLarge.copyWith(
                    color: theme.colorScheme.onSurface,
                  ),
                ),
                const SizedBox(width: SonderSpacing.sm),
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    'earned',
                    style: SonderTextStyles.body.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: SonderSpacing.sm),
          // Explicit textual status, never color alone.
          if (totalGrantedMs > 0)
            SonderStatusChip(
              label: '5 MIN EARNED',
              background: theme.colorScheme.tertiary,
              foreground: theme.colorScheme.onTertiary,
              icon: Icons.check_circle_outline,
            )
          else
            SonderStatusChip(
              label: 'NO EARNED TIME',
              background: theme.colorScheme.surface,
              foreground: theme.colorScheme.onSurfaceVariant,
              icon: Icons.schedule,
            ),
          if (hasLock) ...[
            const SizedBox(height: SonderSpacing.sm),
            Semantics(
              label: 'Lockout ${formatRemaining(lockRemainingMs!)} remaining',
              child: Row(
                children: [
                  Icon(
                    Icons.lock_outline,
                    size: 14,
                    color: theme.colorScheme.error,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    'LOCKED  ${formatRemaining(lockRemainingMs!)}',
                    style: SonderTextStyles.caption.copyWith(
                      color: theme.colorScheme.error,
                    ),
                  ),
                ],
              ),
            ),
          ] else ...[
            const SizedBox(height: SonderSpacing.xs),
            Text(
              'No lockout',
              style: SonderTextStyles.caption.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: SonderSpacing.xs),
          Text(
            'Win a hand to earn exactly 5 minutes. A loss locks the target for 10 minutes.',
            style: SonderTextStyles.body.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
              fontSize: 11,
            ),
          ),
        ],
      ),
    );
  }
}

class _TodaySummaryPlaceholder extends StatelessWidget {
  const _TodaySummaryPlaceholder();

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SonderPanel(
      semanticLabel: 'Today summary',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'TODAY',
            style: SonderTextStyles.labelMono.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: SonderSpacing.sm),
          Text(
            'Your time, at a glance.',
            style: SonderTextStyles.displaySmall.copyWith(
              color: theme.colorScheme.onSurface,
            ),
          ),
          const SizedBox(height: SonderSpacing.xs),
          Text(
            'Detailed insights will appear here once usage history is available. No fabricated metrics are shown.',
            style: SonderTextStyles.body.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: SonderSpacing.sm),
          Container(
            height: 6,
            decoration: BoxDecoration(
              color: theme.colorScheme.outline,
              borderRadius: BorderRadius.circular(2),
            ),
            child: FractionallySizedBox(
              alignment: Alignment.centerLeft,
              widthFactor: 0.0,
              child: Container(
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class HomeBottomNav extends StatelessWidget {
  final int index;
  final ValueChanged<int> onChanged;

  const HomeBottomNav({
    super.key,
    required this.index,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return NavigationBar(
      selectedIndex: index,
      onDestinationSelected: onChanged,
      backgroundColor: theme.scaffoldBackgroundColor,
      indicatorColor: theme.colorScheme.primary.withValues(alpha: 0.15),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.home_outlined),
          selectedIcon: Icon(Icons.home),
          label: 'Home',
        ),
        NavigationDestination(
          icon: Icon(Icons.list_alt_outlined),
          selectedIcon: Icon(Icons.list_alt),
          label: 'Targets',
        ),
        NavigationDestination(
          icon: Icon(Icons.settings_outlined),
          selectedIcon: Icon(Icons.settings),
          label: 'Settings',
        ),
      ],
    );
  }
}
