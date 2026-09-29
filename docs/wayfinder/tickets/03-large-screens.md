# Large screens, foldables, and landscape

`wayfinder:prototype` · parent: [Modernize the Sonder Pixel UI v3 interface](../MAP.md) ·
state: **open — frontier** · blocked by: none

## Question

`design_v3.md` §19 sizes the whole interface against a single baseline: 360×800dp
portrait, 16dp sides, a 96dp reserved navigation band. Nothing in the doc says what the
interface *is* at 600dp, on an unfolded inner display, or in landscape — where a
portrait phone layout either stretches into a band of empty amber-framed panel or wastes
the width it was given.

Decide the composition, not just the constraint:

- **Width-capped single column**, centred, with the pixel frame bounding the content the
  way a handheld console bounds its screen. Honest to the "tiny handheld RPG menu"
  target in §22, cheap, and never awkward.
- **Two-pane**, list beside detail, with the dock promoted to a rail. More useful on a
  tablet; abandons the console fiction and needs its own frame grammar.
- **Per-screen**, decided screen by screen. Most work, most risk of four different
  answers.

## Prototype

Run the app at 600×960dp and in landscape and screenshot the four live-state surfaces;
that render is the artifact the decision is made against. Note where the dock's 96dp
band and the frame shadows behave worst.
