import 'package:flutter/material.dart';
import '../app/app_state.dart';
import '../shared/access_platform.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

class SettingsScreen extends StatefulWidget {
  final AppState appState;
  final AccessPlatform platform;
  final ValueNotifier<ThemeMode> themeMode;

  const SettingsScreen({
    super.key,
    required this.appState,
    required this.platform,
    required this.themeMode,
  });

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([widget.appState, widget.themeMode]),
      builder: (BuildContext context, _) {
        final ThemeData theme = Theme.of(context);
        final EnforcementCapabilities caps = widget.appState.capabilities;
        return Scaffold(
          appBar: AppBar(
            title: Text('APP SETTINGS', style: SonderTextStyles.displayMedium),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              Text(
                'APPEARANCE',
                style: SonderTextStyles.labelMono.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: SonderSpacing.sm),
              SonderPanel(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Theme',
                      style: SonderTextStyles.displaySmall.copyWith(
                        color: theme.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.xs),
                    Text(
                      'Sonder supports light and dark pixel-console themes.',
                      style: SonderTextStyles.body.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.md),
                    SegmentedButton<ThemeMode>(
                      segments: const [
                        ButtonSegment(
                          value: ThemeMode.light,
                          label: Text('LIGHT'),
                          icon: Icon(Icons.light_mode_outlined),
                        ),
                        ButtonSegment(
                          value: ThemeMode.dark,
                          label: Text('DARK'),
                          icon: Icon(Icons.dark_mode_outlined),
                        ),
                        ButtonSegment(
                          value: ThemeMode.system,
                          label: Text('SYSTEM'),
                          icon: Icon(Icons.settings_brightness_outlined),
                        ),
                      ],
                      selected: <ThemeMode>{widget.themeMode.value},
                      onSelectionChanged: (Set<ThemeMode> s) =>
                          widget.themeMode.value = s.first,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              Text(
                'PERMISSIONS',
                style: SonderTextStyles.labelMono.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: SonderSpacing.sm),
              _CapabilityRow(
                title: 'Accessibility Service',
                subtitle: caps.accessibilityEnabled
                    ? 'Enabled — Sonder can detect foreground targets.'
                    : 'Not enabled — blocking is inactive until you enable it.',
                enabled: caps.accessibilityEnabled,
                actionLabel: 'OPEN SETTINGS',
                onAction: () => widget.platform.openAccessibilitySettings(),
                semanticsLabel: caps.accessibilityEnabled
                    ? 'Accessibility enabled'
                    : 'Accessibility not enabled',
              ),
              const SizedBox(height: SonderSpacing.sm),
              _CapabilityRow(
                title: 'Overlay / Display over other apps',
                subtitle: caps.overlayAvailable
                    ? 'Available — the gate overlay can appear over blocked apps.'
                    : 'Unavailable — enable display over other apps for full enforcement.',
                enabled: caps.overlayAvailable,
                actionLabel: 'OPEN SETTINGS',
                onAction: () => widget.platform.openAccessibilitySettings(),
                semanticsLabel: caps.overlayAvailable
                    ? 'Overlay available'
                    : 'Overlay unavailable',
              ),
              const SizedBox(height: SonderSpacing.sm),
              Semantics(
                button: true,
                label: 'Refresh permission status',
                child: SonderSecondaryButton(
                  label: 'REFRESH STATUS',
                  onPressed: () => widget.appState.refreshCapabilities(),
                  semanticLabel: 'Refresh permission status',
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _CapabilityRow extends StatelessWidget {
  final String title;
  final String subtitle;
  final bool enabled;
  final String actionLabel;
  final VoidCallback onAction;
  final String semanticsLabel;

  const _CapabilityRow({
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.actionLabel,
    required this.onAction,
    required this.semanticsLabel,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: semanticsLabel,
      child: SonderPanel(
        padding: const EdgeInsets.all(SonderSpacing.md),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              enabled ? Icons.check_circle_outline : Icons.error_outline,
              color: enabled
                  ? theme.colorScheme.tertiary
                  : theme.colorScheme.error,
              size: 20,
            ),
            const SizedBox(width: SonderSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: SonderTextStyles.displaySmall.copyWith(
                      fontSize: 13,
                      color: theme.colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: SonderTextStyles.body.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontSize: 11,
                    ),
                  ),
                  const SizedBox(height: SonderSpacing.sm),
                  SizedBox(
                    height: 36,
                    child: OutlinedButton(
                      onPressed: onAction,
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          color: theme.colorScheme.outline,
                          width: 1.5,
                        ),
                        shape: const RoundedRectangleBorder(
                          borderRadius: SonderRadii.button,
                        ),
                        textStyle: SonderTextStyles.caption,
                      ),
                      child: Text(actionLabel),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: SonderSpacing.sm),
            SonderStatusChip(
              label: enabled ? 'READY' : 'ACTION NEEDED',
              background: enabled
                  ? theme.colorScheme.tertiary
                  : theme.colorScheme.error,
              foreground: Colors.white,
            ),
          ],
        ),
      ),
    );
  }
}
