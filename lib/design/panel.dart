import 'package:flutter/material.dart';
import 'tokens.dart';

/// Hard-bordered console panel. Hierarchy via border + spacing, not shadow.
class SonderPanel extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? color;
  final double borderWidth;
  final VoidCallback? onTap;
  final String? semanticLabel;

  const SonderPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(SonderSpacing.lg),
    this.color,
    this.borderWidth = 2,
    this.onTap,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Widget inner = Container(
      decoration: BoxDecoration(
        color: color ?? theme.colorScheme.surface,
        border: Border.all(
          color: theme.colorScheme.outline,
          width: borderWidth,
        ),
        borderRadius: SonderRadii.panel,
      ),
      padding: padding,
      child: child,
    );
    if (onTap == null) {
      if (semanticLabel != null) {
        return Semantics(label: semanticLabel, container: true, child: inner);
      }
      return inner;
    }
    return Semantics(
      button: true,
      label: semanticLabel,
      child: InkWell(
        onTap: onTap,
        borderRadius: SonderRadii.panel,
        child: inner,
      ),
    );
  }
}

class SonderStatusChip extends StatelessWidget {
  final String label;
  final Color? background;
  final Color? foreground;
  final IconData? icon;

  const SonderStatusChip({
    super.key,
    required this.label,
    this.background,
    this.foreground,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color bg = background ?? theme.colorScheme.primary;
    final Color fg = foreground ?? theme.colorScheme.onPrimary;
    return Semantics(
      label: label,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: SonderSpacing.sm,
          vertical: 4,
        ),
        decoration: BoxDecoration(
          color: bg,
          border: Border.all(color: theme.colorScheme.outline, width: 1.5),
          borderRadius: SonderRadii.chip,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 12, color: fg),
              const SizedBox(width: 4),
            ],
            Text(
              label,
              style: SonderTextStyles.caption.copyWith(
                color: fg,
                letterSpacing: 1.0,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
