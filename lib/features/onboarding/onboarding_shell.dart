import 'package:flutter/material.dart';
import '../../design/buttons.dart';
import '../../design/panel.dart';
import '../../design/tokens.dart';
import '../shared/access_platform.dart';

/// Onboarding shell owned by Job 01. No native implementation — callbacks are seams for Job 03.
class OnboardingShell extends StatefulWidget {
  final VoidCallback onFinished;
  final VoidCallback? onOpenAccessibilitySettings;
  final Future<void> Function()? onCheckCapabilities;
  final AccessPlatform? platform;

  const OnboardingShell({
    super.key,
    required this.onFinished,
    this.onOpenAccessibilitySettings,
    this.onCheckCapabilities,
    this.platform,
  });

  @override
  State<OnboardingShell> createState() => _OnboardingShellState();
}

class _OnboardingShellState extends State<OnboardingShell> {
  final PageController _controller = PageController();
  int _page = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _go(int p) {
    setState(() => _page = p);
    _controller.animateToPage(
      p,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
    );
  }

  void _next() {
    if (_page < 2) {
      _go(_page + 1);
    } else {
      widget.onFinished();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: SonderSpacing.lg),
            _Dots(index: _page, count: 3),
            Expanded(
              child: PageView(
                controller: _controller,
                onPageChanged: (int i) => setState(() => _page = i),
                children: [
                  _WelcomePage(onNext: _next),
                  _RulesPage(onNext: _next),
                  _PermissionsPage(
                    onNext: _next,
                    onOpenAccessibility: widget.onOpenAccessibilitySettings,
                    onCheckCapabilities: widget.onCheckCapabilities,
                    platform: widget.platform,
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(SonderSpacing.lg),
              child: Row(
                children: [
                  if (_page > 0)
                    Expanded(
                      child: SonderSecondaryButton(
                        label: 'BACK',
                        onPressed: () => _go(_page - 1),
                      ),
                    ),
                  if (_page > 0) const SizedBox(width: SonderSpacing.sm),
                  Expanded(
                    flex: 2,
                    child: SonderPrimaryButton(
                      label: _page == 2 ? 'START' : 'NEXT',
                      onPressed: _next,
                      semanticLabel: _page == 2
                          ? 'Finish onboarding'
                          : 'Next onboarding page',
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Dots extends StatelessWidget {
  final int index;
  final int count;
  const _Dots({required this.index, required this.count});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: 'Onboarding step ${index + 1} of $count',
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(count, (int i) {
          final bool active = i == index;
          return Container(
            width: active ? 20 : 8,
            height: 8,
            margin: const EdgeInsets.symmetric(horizontal: 4),
            decoration: BoxDecoration(
              color: active
                  ? theme.colorScheme.primary
                  : theme.colorScheme.outline,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: theme.colorScheme.outline, width: 1),
            ),
          );
        }),
      ),
    );
  }
}

class _WelcomePage extends StatelessWidget {
  final VoidCallback onNext;
  const _WelcomePage({required this.onNext});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(SonderSpacing.lg),
      children: [
        const SizedBox(height: SonderSpacing.xl),
        Semantics(
          header: true,
          child: Text(
            'SONDER',
            style: SonderTextStyles.displayLarge.copyWith(
              color: theme.colorScheme.onSurface,
            ),
          ),
        ),
        const SizedBox(height: SonderSpacing.sm),
        Text(
          'be here, not everywhere.',
          style: SonderTextStyles.displaySmall.copyWith(
            color: theme.colorScheme.primary,
          ),
        ),
        const SizedBox(height: SonderSpacing.xl),
        SonderPanel(
          semanticLabel: 'Sonder promise',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'A small game console for your attention.',
                style: SonderTextStyles.displaySmall.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: SonderSpacing.sm),
              Text(
                'Sonder adds a moment of intention before distracting apps. One quick challenge decides whether you continue — or step away.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: SonderSpacing.lg),
        Text(
          'You remain in control. Sonder never blocks without your permission.',
          style: SonderTextStyles.body.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _RulesPage extends StatelessWidget {
  final VoidCallback onNext;
  const _RulesPage({required this.onNext});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(SonderSpacing.lg),
      children: [
        const SizedBox(height: SonderSpacing.lg),
        Text(
          'HOW IT WORKS',
          style: SonderTextStyles.labelMono.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: SonderSpacing.sm),
        Text(
          'One hand decides.',
          style: SonderTextStyles.displayMedium.copyWith(
            color: theme.colorScheme.onSurface,
          ),
        ),
        const SizedBox(height: SonderSpacing.lg),
        SonderPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _RuleRow(
                label: 'WIN  5 MIN',
                description: 'A win grants exactly 5 minutes in that app.',
                color: theme.colorScheme.tertiary,
                icon: Icons.check_circle_outline,
              ),
              const SizedBox(height: SonderSpacing.lg),
              _RuleRow(
                label: 'LOSE  LOCKED 10 MIN',
                description: 'A loss locks that app for exactly 10 minutes.',
                color: theme.colorScheme.error,
                icon: Icons.lock_outline,
              ),
              const SizedBox(height: SonderSpacing.lg),
              _RuleRow(
                label: 'STEP AWAY  20 SEC',
                description:
                    'If the app stays out of the foreground for 20 consecutive seconds, the grant is revoked. Brief returns inside 20s preserve it.',
                color: theme.colorScheme.onSurfaceVariant,
                icon: Icons.timer_outlined,
              ),
            ],
          ),
        ),
        const SizedBox(height: SonderSpacing.sm),
        Text(
          'No money, no bets — just a fair hand of blackjack.',
          style: SonderTextStyles.caption.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _RuleRow extends StatelessWidget {
  final String label;
  final String description;
  final Color color;
  final IconData icon;
  const _RuleRow({
    required this.label,
    required this.description,
    required this.color,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: '$label. $description',
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              border: Border.all(color: theme.colorScheme.outline, width: 1.5),
              borderRadius: SonderRadii.chip,
            ),
            child: Icon(icon, size: 16, color: color),
          ),
          const SizedBox(width: SonderSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: SonderTextStyles.caption.copyWith(
                    color: color,
                    letterSpacing: 1.0,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  style: SonderTextStyles.body.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PermissionsPage extends StatelessWidget {
  final VoidCallback onNext;
  final VoidCallback? onOpenAccessibility;
  final Future<void> Function()? onCheckCapabilities;
  final AccessPlatform? platform;
  const _PermissionsPage({
    required this.onNext,
    this.onOpenAccessibility,
    this.onCheckCapabilities,
    this.platform,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.all(SonderSpacing.lg),
      children: [
        const SizedBox(height: SonderSpacing.lg),
        Text(
          'PERMISSIONS',
          style: SonderTextStyles.labelMono.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: SonderSpacing.sm),
        Text(
          'Sonder needs your permission to help.',
          style: SonderTextStyles.displayMedium.copyWith(
            color: theme.colorScheme.onSurface,
          ),
        ),
        const SizedBox(height: SonderSpacing.lg),
        SonderPanel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Accessibility Service',
                style: SonderTextStyles.displaySmall.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'To notice when a target app comes to the foreground, Sonder uses an Accessibility Service. You must explicitly enable it in system settings. Sonder never reads screen content beyond detecting that a configured package is in the foreground.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: SonderSpacing.sm),
              SonderSecondaryButton(
                label: 'OPEN ACCESSIBILITY SETTINGS',
                semanticLabel: 'Open accessibility settings',
                onPressed:
                    onOpenAccessibility ??
                    () async {
                      if (platform != null) {
                        try {
                          await platform!.openAccessibilitySettings();
                        } catch (_) {}
                      }
                    },
              ),
              const SizedBox(height: SonderSpacing.lg),
              Text(
                'Overlay permission',
                style: SonderTextStyles.displaySmall.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'On some Android versions a “Display over other apps” permission is required to show the gate. Sonder will request it only if your device needs it.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              Text(
                'Installed apps visibility',
                style: SonderTextStyles.displaySmall.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'To let you add other apps as custom targets, Sonder queries only apps that have a launcher icon. It does not use broad package visibility.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: SonderSpacing.lg),
        SonderPanel(
          borderWidth: 1.5,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 16,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    'IMPORTANT LIMITATIONS',
                    style: SonderTextStyles.caption.copyWith(
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: SonderSpacing.sm),
              Text(
                'YouTube Shorts and Instagram Reels detection is best-effort. Android cannot guarantee individual in-app surface detection across app versions, languages, or OEMs. Custom apps added manually are blocked at the package level, which is reliable. Sonder cannot make itself bypass-proof.',
                style: SonderTextStyles.body.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: SonderSpacing.sm),
        Text(
          'You can revisit these settings anytime in Sonder Settings.',
          style: SonderTextStyles.caption.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}
