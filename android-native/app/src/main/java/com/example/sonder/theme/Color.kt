package com.example.sonder.theme

import androidx.compose.ui.graphics.Color

/*
 * 2026 modernization additions to the design_v3 §3 palette (which lives in
 * PixelPalette.kt). Same amber-on-night world; these are the tints and roles the
 * v3 doc never named but every surface needs: a deeper loading ground, secondary
 * text that passes contrast on both panel tones, and pressed/focus accents.
 */

/** Deepest panel tint — loading states recede one step below Surface. */
val PanelDeep = Color(0xFF120D08)

/** Secondary text on Surface/Panel grounds — warm parchment, ≥7:1 against both. */
val TextSoft = Color(0xFFB3A482)

/** Pressed label on the amber fill — keeps a primary button legible mid-press. */
val OnPrimaryMuted = Color(0xFF33270E)

/** Keyboard/switch-access focus ring — the v3 Text tone, readable on every ground. */
val FocusRing = Color(0xFFEAD7A1)
