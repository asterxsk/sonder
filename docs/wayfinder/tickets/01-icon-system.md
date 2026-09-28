# Icon system: glyphs or drawn pixel icons

`wayfinder:prototype` · parent: [Modernize the Sonder Pixel UI v3 interface](../MAP.md) ·
state: **open — frontier** · blocked by: none

## Question

`design_v3.md` §14 defines the four canonical status badges as `✓ GRANTED`, `▣ LOCKED`,
`♠ PLAYING`, `⌛ COOLDOWN`, and §7–15 lean on unicode throughout; the frame language in
§5 assumes an icon sits in a 40–48dp framed box. The Impeccable craft floor bans unicode
glyphs and emoji standing in for an icon system — "icons are drawn, from a real library
or authored SVG, in one consistent stroke and weight."

Either the badge glyphs earn their place as part of the committed world, or they are a
costume and the app needs an authored pixel icon set. Decide which, and if it is an icon
set, what its grammar is: pixel grid size, palette (one colour per state, or two for
depth), stroke weight, and the inventory of icons actually required by the shipped
screens.

## Prototype

Make the cheap artifact the decision needs: render the same three-or-four badges and
buttons two ways — unicode glyphs as specified, versus a hand-authored pixel icon set —
side by side in the real app, and react to it. The prototype lands as a throwaway
`prototype/icon-system` branch, linked here.

## Evidence to bring

- Which glyphs are actually reachable on the shipped fonts, and how they render at 9sp
  in Press Start 2P versus DM Mono.
- The complete icon inventory the current screens imply, so the answer cannot be
  "glyphs everywhere except the one place we looked".
- Whether an authored set can be built as Compose vector geometry with no new
  image-loading dependency, per the quality-pass non-goals.
