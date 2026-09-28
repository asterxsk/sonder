# Decorative pixel world: ship sprites or ship none

`wayfinder:grilling` · parent: [Modernize the Sonder Pixel UI v3 interface](../MAP.md) ·
state: **open — frontier** · blocked by: none

## Question

`design_v3.md` §17 asks for a reusable environment kit — bedroom, characters, exterior,
cats — and §10–12 put a rendered room behind the block, win, and loss screens. None of
it exists. The quality-pass design simultaneously rules out new artwork and an
image-loading pipeline, and the craft floor says an illustration that cannot be done
properly reads worse than no illustration: "real illustration or none."

Decide which of these this effort is:

- **None.** Frames and type carry the mood; the framed content is the whole screen. The
  doc's §17 is deferred as a separate effort and the screens are designed to be complete
  without it.
- **Hand-authored sprite kit.** A small set of Compose vector or image assets on one
  palette, with a stated inventory and an owner, shipped as part of this effort.
- **Geometry only.** Night-window, moon, rooftops — shapes a session can specify exactly,
  which the craft floor explicitly permits — with no figures, no faces, no shading.

Whichever is chosen, the block, win, and loss screens are re-decided against it, since
their current composition assumes decoration that may never arrive.

## Method

Grilling — a live exchange. This is a scope question wearing an aesthetic costume: get
the appetite stated in writing before anyone draws a cat.
