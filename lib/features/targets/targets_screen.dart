import 'package:flutter/material.dart';
import '../../domain/models.dart';
import '../app/app_state.dart';
import '../shared/access_platform.dart';
import '../shared/format.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

class TargetsScreen extends StatelessWidget {
  final AppState appState;
  final AccessPlatform platform;

  const TargetsScreen({
    super.key,
    required this.appState,
    required this.platform,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: appState,
      builder: (BuildContext context, _) {
        final List<BlockedTarget> targets = appState.targets;
        return Scaffold(
          appBar: AppBar(
            title: Text(
              'APP CONFIGURATION',
              style: SonderTextStyles.displayMedium,
            ),
          ),
          body: ListView(
            padding: const EdgeInsets.all(SonderSpacing.lg),
            children: [
              const _BestEffortDisclosure(),
              const SizedBox(height: SonderSpacing.lg),
              SonderPanel(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'DEFAULT TARGETS',
                      style: SonderTextStyles.labelMono.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    Text(
                      'YouTube Shorts and Instagram Reels are enabled by default. Disable or remove them anytime.',
                      style: SonderTextStyles.body.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              ...targets.map(
                (BlockedTarget t) => Padding(
                  padding: const EdgeInsets.only(bottom: SonderSpacing.sm),
                  child: _TargetRow(appState: appState, target: t),
                ),
              ),
              const SizedBox(height: SonderSpacing.lg),
              Semantics(
                button: true,
                label: 'Add app target',
                child: SonderPrimaryButton(
                  label: 'ADD APP TARGET',
                  icon: Icons.add,
                  onPressed: () => _openPicker(context),
                  semanticLabel: 'Add app target',
                ),
              ),
              const SizedBox(height: SonderSpacing.sm),
              Text(
                'Custom apps use package-level protection (whole app).',
                style: SonderTextStyles.caption.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _openPicker(BuildContext context) async {
    final List<LaunchableApp> apps;
    try {
      apps = await platform.listLaunchableApps();
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not load apps. Try again.')),
      );
      return;
    }
    if (!context.mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (BuildContext ctx) =>
          _AppPickerSheet(apps: apps, appState: appState),
    );
  }
}

class _TargetRow extends StatelessWidget {
  final AppState appState;
  final BlockedTarget target;

  const _TargetRow({required this.appState, required this.target});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDefault = kDefaultTargets.any(
      (d) => d.packageName == target.packageName,
    );
    final String surface = surfaceLabel(target.surface.name);
    final String subtitle = isDefault
        ? '$surface  · default target'
        : '$surface  · ${target.packageName}';

    return Semantics(
      label:
          '${target.displayName} $surface ${target.enabled ? 'enabled' : 'disabled'}',
      child: SonderPanel(
        padding: const EdgeInsets.all(SonderSpacing.md),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    target.displayName,
                    style: SonderTextStyles.displaySmall.copyWith(
                      color: theme.colorScheme.onSurface,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: SonderTextStyles.caption.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Switch(
                        value: target.enabled,
                        onChanged: (bool v) =>
                            appState.setTargetEnabled(target.packageName, v),
                      ),
                      const SizedBox(width: SonderSpacing.xs),
                      Text(
                        target.enabled ? 'ENABLED' : 'DISABLED',
                        style: SonderTextStyles.caption.copyWith(
                          color: target.enabled
                              ? theme.colorScheme.primary
                              : theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
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

class _BestEffortDisclosure extends StatelessWidget {
  const _BestEffortDisclosure();

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: 'Best-effort Shorts and Reels detection disclosure',
      child: Container(
        padding: const EdgeInsets.all(SonderSpacing.md),
        decoration: BoxDecoration(
          color: theme.colorScheme.primary.withValues(alpha: 0.08),
          border: Border.all(
            color: theme.colorScheme.primary.withValues(alpha: 0.25),
            width: 1.5,
          ),
          borderRadius: SonderRadii.panel,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.info_outline,
              size: 16,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: SonderSpacing.sm),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'BEST-EFFORT DETECTION',
                    style: SonderTextStyles.caption.copyWith(
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Shorts/Reels detection is best-effort. If the surface cannot be identified, Sonder uses a package-level fallback so an enabled target is not silently skipped. Custom apps are always package-level (whole app).',
                    style: SonderTextStyles.body.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontSize: 11,
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

class _AppPickerSheet extends StatefulWidget {
  final List<LaunchableApp> apps;
  final AppState appState;

  const _AppPickerSheet({required this.apps, required this.appState});

  @override
  State<_AppPickerSheet> createState() => _AppPickerSheetState();
}

class _AppPickerSheetState extends State<_AppPickerSheet> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final List<LaunchableApp> filtered = widget.apps.where((a) {
      if (_query.isEmpty) return true;
      final String q = _query.toLowerCase();
      return a.displayName.toLowerCase().contains(q) ||
          a.packageName.toLowerCase().contains(q);
    }).toList();

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom,
        ),
        child: SizedBox(
          height: MediaQuery.of(context).size.height * 0.75,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(SonderSpacing.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'ADD APP TARGET',
                      style: SonderTextStyles.labelMono.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: SonderSpacing.sm),
                    TextField(
                      onChanged: (String v) => setState(() => _query = v),
                      decoration: const InputDecoration(
                        hintText: 'Search apps',
                        prefixIcon: Icon(Icons.search),
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: filtered.isEmpty
                    ? Center(
                        child: Text(
                          'No apps match “$_query”.',
                          style: SonderTextStyles.body.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      )
                    : ListView.separated(
                        itemCount: filtered.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (BuildContext context_, int i) {
                          final LaunchableApp app = filtered[i];
                          final bool alreadyAdded = widget.appState.targets.any(
                            (t) => t.packageName == app.packageName,
                          );
                          return Semantics(
                            button: true,
                            label: 'Add ${app.displayName} ${app.packageName}',
                            child: ListTile(
                              title: Text(
                                app.displayName,
                                style: SonderTextStyles.displaySmall.copyWith(
                                  fontSize: 14,
                                ),
                              ),
                              subtitle: Text(
                                app.packageName,
                                style: SonderTextStyles.caption,
                              ),
                              trailing: alreadyAdded
                                  ? Text(
                                      'ADDED',
                                      style: SonderTextStyles.caption.copyWith(
                                        color: theme.colorScheme.primary,
                                      ),
                                    )
                                  : const Icon(Icons.add),
                              onTap: alreadyAdded
                                  ? null
                                  : () {
                                      widget.appState.addCustomTarget(app);
                                      Navigator.of(context_).pop();
                                    },
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
