import 'package:flutter/material.dart';
import 'tokens.dart';

/// Pixel-console bordered panel. Border weight + spacing carry hierarchy,
/// not shadow, per `design.md`.
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
      return Semantics(
        label: semanticLabel,
        container: semanticLabel != null,
        child: inner,
      );
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

class SonderPrimaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool expanded;
  final String? semanticLabel;

  const SonderPrimaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.expanded = true,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Widget btn = Semantics(
      button: true,
      enabled: onPressed != null,
      label: semanticLabel ?? label,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: theme.colorScheme.primary,
          foregroundColor: theme.colorScheme.onPrimary,
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(
            horizontal: SonderSpacing.lg,
            vertical: SonderSpacing.sm,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: SonderRadii.button,
            side: BorderSide(color: theme.colorScheme.outline, width: 2),
          ),
          textStyle: SonderTextStyles.displaySmall.copyWith(letterSpacing: 0.5),
        ),
        child: Row(
          mainAxisSize: expanded ? MainAxisSize.max : MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 18),
              const SizedBox(width: SonderSpacing.sm),
            ],
            Flexible(
              child: Text(
                label,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
    if (!expanded) return btn;
    return SizedBox(width: double.infinity, child: btn);
  }
}

class SonderSecondaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final String? semanticLabel;

  const SonderSecondaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      button: true,
      enabled: onPressed != null,
      label: semanticLabel ?? label,
      child: SizedBox(
        width: double.infinity,
        child: OutlinedButton(
          onPressed: onPressed,
          style: OutlinedButton.styleFrom(
            foregroundColor: theme.colorScheme.onSurface,
            minimumSize: const Size(0, 44),
            side: BorderSide(color: theme.colorScheme.outline, width: 2),
            shape: const RoundedRectangleBorder(
              borderRadius: SonderRadii.button,
            ),
            textStyle: SonderTextStyles.displaySmall,
          ),
          child: Text(label),
        ),
      ),
    );
  }
}
