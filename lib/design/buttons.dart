import 'package:flutter/material.dart';
import 'tokens.dart';

/// Primary filled pixel button.
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
    final String effectiveLabel = semanticLabel ?? label;
    final Widget btn = Semantics(
      button: true,
      enabled: onPressed != null,
      label: effectiveLabel,
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

class SonderGhostButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final String? semanticLabel;

  const SonderGhostButton({
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
      child: TextButton(
        onPressed: onPressed,
        style: TextButton.styleFrom(
          foregroundColor: theme.colorScheme.primary,
          textStyle: SonderTextStyles.labelMono,
          minimumSize: const Size(0, 44),
        ),
        child: Text(label),
      ),
    );
  }
}
