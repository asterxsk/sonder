# Sonder — Pixel UI Design System v3
## Gawkbot-inspired direction, adapted for Sonder

V3 keeps Sonder's product mechanics but moves the UI from **clean modern dark UI** toward a more authored **retro pixel-game interface**.

The reference direction is inspired by Gawkbot/WUPHF's pixel-office approach: warm amber primary accent, hard pixel frames, stepped shadows, pixel typography, environmental storytelling, and a coherent miniature world. It is **not a copy** of Gawkbot's layouts, assets, branding, or characters. citeturn0search0turn0search4

## 1. Core visual change

### V2
- cool blue/purple accents
- modern mobile cards
- restrained flat components

### V3
- warm amber/gold is the primary accent
- brown-black night palette
- chunky framed panels
- stepped pixel corners
- hard offset shadows
- tabs that look like game UI
- pixel labels
- environmental artwork integrated around the UI
- tiny character/cat motifs
- more personality, less generic productivity dashboard

Gawkbot's design system explicitly uses dark pixel-art presentation, an amber primary accent, pixel typography, grid alignment, sharp edges, and stepped animation. Those principles are the useful inspiration for Sonder. citeturn0search0

## 2. Sonder's own visual personality

Do not turn Sonder into a clone of a pixel-office website.

Sonder should feel like:
- late night
- bedroom / apartment
- quiet self-control
- a tiny game console
- a slightly lonely but comforting atmosphere
- internet distraction represented as a locked door

Recurring visual motif:
**the player sitting at night with a cat while choosing whether to waste another few minutes.**

This gives the product an emotional identity that fits its actual purpose.

## 3. Palette

| Token | Hex | Use |
|---|---|---|
| `bg` | `#0B0906` | deepest background |
| `surface` | `#15100B` | normal panels |
| `panel` | `#221A12` | raised panels |
| `border` | `#A68A52` | structural border |
| `border-dark` | `#51452F` | secondary frame |
| `primary` | `#FFB827` | main action |
| `primary-dark` | `#C88A16` | pixel shadow |
| `text` | `#EAD7A1` | warm primary text |
| `muted` | `#7F765F` | supporting text |
| `success` | `#22C55E` | access granted |
| `danger` | `#EF4444` | lockout |
| `info` | `#5A9AC8` | informational |
| `purple` | `#8B5CF6` | special state only |

**Amber is the brand accent.** Blue/purple must not compete with it.

## 4. Typography

Primary display font:
**Press Start 2P** or an equivalent original pixel display font.

Functional font:
**DM Mono** / another highly readable monospace.

Use the pixel font for:
- SONDER wordmark
- page titles
- section labels
- buttons
- timers
- game state
- navigation labels

Use mono for:
- descriptions
- package IDs
- settings details
- technical metadata

Never use a generic rounded SaaS font.

Suggested scale:
- Wordmark: 28–32px
- Screen title: 16–20px
- Section title: 10–12px
- Button: 9–11px
- Body: 13–15px
- Metadata: 9–11px

## 5. Frame language

This is the biggest V3 change.

Panels are not plain rectangles.

Use a pixel frame:

```text
┌─╴╴╴╴╴╴╴╴╴╴─┐
│             │
│   CONTENT   │
│             │
└─╴╴╴╴╴╴╴╴╴╴─┘
```

Characteristics:
- 0px radius
- 2px–3px border
- 4px stepped corners
- occasional top/bottom gold tabs
- optional hard shadow offset by 4–5px
- no blur
- no soft elevation

### Focused frame

Primary/focused controls receive:
- amber border
- amber corner pixels
- 4px dark amber offset shadow

This makes the interface feel like a physical game menu.

## 6. Buttons

### Primary

Amber fill:
`#FFB827`

Dark text:
`#0B0906`

Hard shadow:
`#C88A16`

Pressed:
- translate 4px
- remove shadow

### Secondary

Dark panel
+ gold/olive border
+ warm text

### Success

Green fill/border.

### Danger

Red fill/border.

### Icon button

Square 40–48dp control with:
- pixel frame
- icon
- optional one-character shortcut

No circular icon buttons.

## 7. Tabs

Tabs should resemble old game menu tabs rather than Material segmented controls.

Example:

`[ APPS ] [ LIMITS ] [ RULES ] [ APPEARANCE ]`

Active:
- amber fill
- dark text
- small corner pixels

Inactive:
- dark surface
- olive border
- muted text

## 8. App target rows

Target rows become small inventory-like objects.

```text
┌────────────────────────────────┐
│ [ICON] YouTube       [ ON ] →  │
│        com.google...           │
└────────────────────────────────┘
```

The row gets:
- pixel border
- tiny metadata line
- app icon in a framed 40dp box
- amber toggle
- arrow affordance

Do not use large modern cards.

## 9. Blackjack

Blackjack gets the strongest game treatment.

### Table frame

Use a large pixel frame rather than a modern card.

```text
┌─────────────────────────────┐
│ DEALER                 10   │
│                             │
│      [ 10♠ ] [ BACK ]       │
│                             │
├─────────────────────────────┤
│ YOU                    16   │
│                             │
│      [ 9♠ ] [ 7♥ ]         │
│                             │
│ [ HIT ]             [ STAND ]│
└─────────────────────────────┘
```

Cards remain clean white/off-white because readability matters.

Card backs can contain a tiny Sonder pixel motif:
- cat
- crescent
- `S` sprite

No gambling chips, coins, poker tables or money.

## 10. Blocked screen

The block screen should feel like the user has entered a tiny room.

Background:
- dark apartment
- desk/lamp
- night window
- small character/cat

Foreground:
- framed target card

Example:

```text
        YouTube

       is blocked.

   Play one hand of
   blackjack to earn
     05:00 access.

  ┌──────────────────┐
  │ PLAY BLACKJACK → │
  └──────────────────┘

        Not Now
```

The environment is decoration; the framed content is the functional layer.

## 11. Win screen

Win should feel like a small pixel-game reward.

```text
✦       ✦       ✦

       YOU WIN!

    + 05:00 ACCESS

     [ CONTINUE ]

   Don't waste it.
```

Character sprite can celebrate.

Do not make it flashy or casino-like.

## 12. Loss screen

Use the same frame language, but red.

```text
       YOU LOSE

   TRY AGAIN IN

       09:58

 ┌───────────────────┐
 │  WAIT IT OUT      │
 └───────────────────┘
```

A sleeping cat or character can reinforce the quiet mood.

## 13. Timer

The timer is a major branded element.

Frame:

```text
┌─────────────────────┐
│ ◷  09:58             │
│    TIME REMAINING    │
└─────────────────────┘
```

Use:
- amber for warnings
- green for granted access
- red for lockout

Timer digits should use pixel typography.

## 14. Status badges

Four canonical badges:

`✓ GRANTED`
`▣ LOCKED`
`♠ PLAYING`
`⌛ COOLDOWN`

Each has:
- icon
- label
- colored border
- dark fill
- pixel frame

## 15. Navigation

Bottom navigation becomes a tiny console dock.

```text
┌───────────────────────────────────┐
│  HOME    TARGETS    STATS SETTINGS│
└───────────────────────────────────┘
```

Active tab:
- amber frame
- amber icon
- tiny top/bottom marker

No floating navigation.

## 16. Toasts

Toasts should resemble small RPG dialogue/status boxes.

Success:
`✓ Access granted. You have 5 minutes.`

Error:
`▣ Better luck next time. Try again in 10 minutes.`

Info:
`i Left the app for too long. Access revoked.`

Use hard borders and small pixel shadows.

## 17. Decorative pixel world

Create a small reusable environment kit:

### Bedroom
- bed
- bedside table
- lamp
- window
- plant
- laptop
- floor
- wall poster

### Characters
- hooded player
- sitting player
- sleeping player
- cat
- sleeping cat
- cat in box

### Exterior
- moon
- apartment windows
- rooftops
- street light

These assets should share one pixel-art palette.

## 18. Motion

Follow Gawkbot's useful pixel-motion principle: **discrete steps rather than smooth interpolation.** citeturn0search0

Use:
- `steps()` / discrete frames
- 2–4 frame button press
- 2-frame card flip
- 3-frame character idle
- 3–5 frame win sparkle
- instant pixel-menu selection

Avoid:
- ease-in-out
- spring physics
- smooth floating
- continuous gradients/glows

## 19. Layout and anti-clipping rules

The V2 anti-clipping rules remain mandatory.

Baseline:
**360×800dp portrait**

Safe area:
- 16dp sides
- 16dp top
- 16dp bottom
- 80dp reserved navigation area

Minimum:
- 48dp touch target
- 8dp related spacing
- 12dp unrelated spacing

Pixel art is always inside a bounded frame.

Text never determines the position of decorative sprites.

At narrow widths:
1. wrap content
2. stack controls
3. reduce decorative spacing
4. only then reduce non-critical visual scale

Never clip text or interactive controls.

## 20. Android implementation

Recommended architecture:

`Jetpack Compose`
→ `ViewModel`
→ `Domain`
→ `Repository`
→ `Accessibility Service / Overlay`

Keep:
- BlackjackRules
- AccessPolicy
- Target
- Grant
- Lockout
- EnforcementState

pure and testable.

Native Android owns:
- accessibility
- overlay
- foreground detection
- lifecycle
- persistence

Core rules remain:
- win = 5 minutes
- loss = 10 minutes
- leaving for 20 seconds = revoke
- no root
- no VPN
- no device-owner requirement
- no private APIs

## 21. Anti-patterns

Do NOT use:
- purple-blue SaaS gradients
- glass cards
- rounded Material cards
- circular floating action buttons
- smooth animated UI
- emoji as icons
- casino chips
- casino green felt
- fake 3D bevels everywhere
- excessive particle effects
- giant dashboard statistics
- generic stock illustrations

## 22. Design target

The desired feeling is:

**Gawkbot's authored pixel-world discipline + Sonder's quiet nighttime self-control + a tiny handheld RPG menu.**

The UI should look like it belongs to a small game that happens to control app access—not like a productivity app with a pixel-art skin.
