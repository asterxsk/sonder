import 'package:flutter/material.dart';
import 'tokens.dart';

/// Explicit textual status badge — never communicates state by color alone.
class SonderStateLabel extends StatelessWidget {
  final String text;
  final Color? color;
  final IconData? icon;

  const SonderStateLabel({
    super.key,
    required this.text,
    this.color,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color c = color ?? theme.colorScheme.onSurface;
    return Semantics(
      label: text,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: c),
            const SizedBox(width: 4),
          ],
          Text(text, style: SonderTextStyles.caption.copyWith(color: c)),
        ],
      ),
    );
  }
}

class SonderTimerDisplay extends StatelessWidget {
  final int remainingMs;
  final String? prefix;

  const SonderTimerDisplay({super.key, required this.remainingMs, this.prefix});

  String get formatted {
    final int totalSeconds = (remainingMs / 1000).ceil().clamp(0, 1 << 31);
    final int minutes = totalSeconds ~/ 60;
    final int seconds = totalSeconds % 60;
    if (minutes == 0) return '${seconds}s';
    if (seconds == 0) return '${minutes}m';
    return '${minutes}m ${seconds}s';
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Semantics(
      label: prefix != null ? '$prefix $formatted' : formatted,
      child: Text(
        prefix != null ? '$prefix $formatted' : formatted,
        style: SonderTextStyles.displayMedium.copyWith(
          color: theme.colorScheme.onSurface,
        ),
      ),
    );
  }
}

class SonderProgressBar extends StatelessWidget {
  final double fraction; // 0..1

  const SonderProgressBar({super.key, required this.fraction});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final double f = fraction.clamp(0.0, 1.0);
    return Semantics(
      label: 'Progress ${(f * 100).round()} percent',
      child: Container(
        height: 8,
        decoration: BoxDecoration(
          color: theme.colorScheme.outline,
          border: Border.all(color: theme.colorScheme.outline, width: 1.5),
          borderRadius: BorderRadius.circular(2),
        ),
        child: FractionallySizedBox(
          alignment: Alignment.centerLeft,
          widthFactor: f,
          child: Container(
            decoration: BoxDecoration(
              color: theme.colorScheme.primary,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
      ),
    );
  }
}
