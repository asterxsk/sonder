# System font scale on a pixel type ramp

`wayfinder:grilling` · parent: [Modernize the Sonder Pixel UI v3 interface](../MAP.md) ·
state: **open — frontier** · blocked by: none

## Question

Pixel fonts do not scale like text fonts. Press Start 2P is a bitmap-designed face with
fixed pixel geometry: at 1.0× it is already dense at 9–11sp, and a user at 1.3× or 2.0×
gets glyph blocks that either clip inside a hard frame or force frames to grow until the
360dp layout breaks. The app currently uses `sp` throughout, so it inherits the system
scale with no policy behind it.

Decide the policy:

- **Cap the pixel face.** Navigation labels, badges, and button captions stay at a fixed
  size or a capped multiplier, and DM Mono carries the scaled load. Keeps the world;
  risks failing the user who needs large text on the controls they must read.
- **Scale everything and grow the containers.** Frames become content-sized, the dock
  reflows, and the design accepts fewer rows per screen at large scale.
- **Hybrid.** A legibility floor — anything the user must read is DM Mono above a
  threshold, the pixel face is reserved for display roles that have space to grow.

The answer must say what happens at 2.0× on the four surfaces that carry live state:
Home status, the blackjack table, the block screen, and the lockout timer.

## Method

Grilling — a live exchange. Probe the accessibility requirement against the world's
constraint rather than deciding on the user's behalf; a comfortable answer that silently
drops large-text users is a failure, and so is one that abandons the pixel face entirely.
