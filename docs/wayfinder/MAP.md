# Modernize the Sonder Pixel UI v3 interface

`wayfinder:map` · tracker: local markdown

## Destination

A Pixel UI v3 that keeps its identity and modernizes its craft: the same amber-on-night
world, hard frames, and pixel typography, with a real spacing and type ramp, authored
motion, legible live state, and an accessibility floor that holds under system font
scale and on large screens. Frontend target: `android-native/`, landed on `sonder-v2`.

## Notes

- **Refinement, not redesign.** `docs/design/design_v3.md` is the committed visual world
  and wins over any default in the craft floor, including on hard offset shadows and
  glyph badges. Scope decision taken 2026-09-28.
- **Platform:** Android, Jetpack Compose. Skills every session should consult:
  `impeccable` (craft floor, operate mode), `mobile-android-design`, `android-native-dev`,
  `android-cli` for device work, `graft` for locating code.
- **Not a ticket:** everything `design_v3.md` already decides is execution, not wayfinding.
  Spacing values (§19), the type scale (§4), frame language (§5), button and tab
  treatments (§6–7), motion *character* (§18), and the anti-pattern list (§21) are all
  settled by the doc; they are built directly and never charted here.
- **Verification:** the UI is Android-native, so the batched inspection round is one pass
  over the shipped device class (phone) plus the large-width case, not a browser loop.

## Tickets

Frontier is unmarked; **blocked** lists its blockers.

| Ticket | Type | State | Blocked by |
|---|---|---|---|
| [Icon system: glyphs or drawn pixel icons](tickets/01-icon-system.md) | prototype | open — frontier | — |
| [System font scale on a pixel type ramp](tickets/02-system-font-scale.md) | grilling | open — frontier | — |
| [Large screens, foldables, and landscape](tickets/03-large-screens.md) | prototype | open — frontier | — |
| [Decorative pixel world: ship sprites or ship none](tickets/04-decorative-world.md) | grilling | open — frontier | — |
| [Home status hierarchy under a real lockout](tickets/05-home-status-hierarchy.md) | prototype | open | 01 |

## Decisions so far

- **Scope, 2026-09-28:** refine Pixel UI v3 in place — preserve palette, frame language,
  and pixel type; modernize spacing rhythm, type hierarchy, motion, state legibility,
  and accessibility. A replacement visual world and a structural-shell-only pass were
  both explicitly rejected.

## Not yet specified

- **Debt and debt-repayment surfaces.** The app tracks debt from lost hands; whether that
  has a dedicated visual treatment or stays folded into stats is not yet sharp — it hangs
  on what Home ends up foregrounding.
- **Onboarding as a world introduction.** §17 describes an apartment the app never shows.
  Whether onboarding is where the pixel world is introduced, rather than decorative
  fragments scattered per screen, depends on the decorative-world ticket.
- **Toast and error copy voice.** §16 fixes the tone in three examples; whether every
  string in the app follows it, and who owns that sweep, is coarser than a ticket yet.

## Out of scope

- **New illustration pipeline or image-loading dependency.** Ruled out by the quality-pass
  design's non-goals (`docs/superpowers/specs/2026-09-16-native-v2-quality-pass-design.md`).
- **Theming beyond the single night world** — no light mode, no user-selectable palette.
  `design_v3.md` §2 fixes the scene as late night; a second world is a different effort.
- **Domain, persistence, enforcement, and permission behaviour.** Unchanged by this effort.
