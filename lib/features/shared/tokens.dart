import 'package:flutter/material.dart';

/// Design tokens sourced verbatim from `design.md` and `employ/plan.md`.
/// Dark / light palettes are theme-driven; components read from Theme
/// extensions rather than hard-coding hex values outside this file.

class SonderPalette {
  // Dark
  static const Color darkBg = Color(0xFF080B0F);
  static const Color darkSurface = Color(0xFF181A22);
  static const Color darkBorder = Color(0xFF202033);
  static const Color darkPrimary = Color(0xFF7E6CFF);
  static const Color darkPrimaryMuted = Color(0xFF9B8CFF);
  static const Color darkLoss = Color(0xFFFF609A);
  static const Color darkWin = Color(0xFF86EFA6);
  static const Color darkText = Color(0xFFE6E6E6);

  // Light
  static const Color lightBg = Color(0xFFF8F8F2);
  static const Color lightSurface = Color(0xFFECEBE4);
  static const Color lightBorder = Color(0xFFD6D6C8);
  static const Color lightPrimary = Color(0xFF6B5CFF);
  static const Color lightPrimaryMuted = Color(0xFF9B8CFF);
  static const Color lightLoss = Color(0xFFFF609A);
  static const Color lightWin = Color(0xFF22C55E);
  static const Color lightText = Color(0xFF1A1A1A);
}

// Minimal typography scale per design.md: display = pixel, body = mono.
class SonderTextStyles {
  static const TextStyle displayLarge = TextStyle(
    fontSize: 32,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.5,
    height: 1.0,
  );
  static const TextStyle displayMedium = TextStyle(
    fontSize: 20,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.2,
    height: 1.1,
  );
  static const TextStyle displaySmall = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.1,
    height: 1.2,
  );
  static const TextStyle body = TextStyle(
    fontFamily: 'monospace',
    fontFamilyFallback: <String>['Courier', 'monospace'],
    fontSize: 12,
    fontWeight: FontWeight.w400,
    height: 1.5,
  );
  static const TextStyle bodyBold = TextStyle(
    fontFamily: 'monospace',
    fontFamilyFallback: <String>['Courier', 'monospace'],
    fontSize: 12,
    fontWeight: FontWeight.w700,
    height: 1.5,
  );
  static const TextStyle caption = TextStyle(
    fontFamily: 'monospace',
    fontFamilyFallback: <String>['Courier', 'monospace'],
    fontSize: 10,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.6,
    height: 1.4,
  );
  static const TextStyle labelMono = TextStyle(
    fontFamily: 'monospace',
    fontFamilyFallback: <String>['Courier', 'monospace'],
    fontSize: 11,
    fontWeight: FontWeight.w700,
    letterSpacing: 1.0,
    height: 1.2,
  );
}

class SonderRadii {
  static const BorderRadius panel = BorderRadius.all(Radius.circular(8));
  static const BorderRadius button = BorderRadius.all(Radius.circular(6));
  static const BorderRadius chip = BorderRadius.all(Radius.circular(4));
}

class SonderSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

ThemeData buildSonderTheme({required bool isDark}) {
  final Color bg = isDark ? SonderPalette.darkBg : SonderPalette.lightBg;
  final Color surface = isDark
      ? SonderPalette.darkSurface
      : SonderPalette.lightSurface;
  final Color border = isDark
      ? SonderPalette.darkBorder
      : SonderPalette.lightBorder;
  final Color primary = isDark
      ? SonderPalette.darkPrimary
      : SonderPalette.lightPrimary;
  final Color primaryMuted = SonderPalette.darkPrimaryMuted;
  final Color loss = SonderPalette.darkLoss;
  final Color win = isDark ? SonderPalette.darkWin : SonderPalette.lightWin;
  final Color text = isDark ? SonderPalette.darkText : SonderPalette.lightText;

  final ColorScheme scheme = ColorScheme(
    brightness: isDark ? Brightness.dark : Brightness.light,
    primary: primary,
    onPrimary: Colors.white,
    primaryContainer: primaryMuted,
    onPrimaryContainer: Colors.white,
    secondary: primaryMuted,
    onSecondary: Colors.white,
    error: loss,
    onError: Colors.white,
    surface: surface,
    onSurface: text,
    surfaceContainerHighest: surface,
    onSurfaceVariant: text.withValues(alpha: 0.7),
    outline: border,
    outlineVariant: border.withValues(alpha: 0.6),
    scrim: Colors.black54,
    inverseSurface: isDark ? SonderPalette.lightBg : SonderPalette.darkBg,
    onInverseSurface: isDark ? SonderPalette.lightText : SonderPalette.darkText,
    inversePrimary: primaryMuted,
    tertiary: win,
    onTertiary: isDark ? SonderPalette.darkBg : Colors.white,
  );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: bg,
    appBarTheme: AppBarTheme(
      backgroundColor: bg,
      foregroundColor: text,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: SonderTextStyles.displayMedium.copyWith(color: text),
    ),
    textTheme: TextTheme(
      displayLarge: SonderTextStyles.displayLarge.copyWith(color: text),
      displayMedium: SonderTextStyles.displayMedium.copyWith(color: text),
      displaySmall: SonderTextStyles.displaySmall.copyWith(color: text),
      bodyMedium: SonderTextStyles.body.copyWith(color: text),
      bodySmall: SonderTextStyles.caption.copyWith(
        color: text.withValues(alpha: 0.7),
      ),
      labelLarge: SonderTextStyles.labelMono.copyWith(color: text),
      labelSmall: SonderTextStyles.caption.copyWith(color: text),
    ),
    dividerColor: border,
  );
}
