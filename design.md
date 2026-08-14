# Sonder — Design System

## 1. Product

**Sonder** is a screen-time limiting app built around deliberate friction.

When a user opens a blocked app, they must play one hand of blackjack:
- **Win:** unlock the app for **5 minutes**
- **Lose:** the app remains locked for **10 minutes**

The product should feel less like a conventional productivity app and more like a small personal game console.

## 2. Design Direction

**References:** Undertale-like emotional pixel art, vintage handheld consoles, 8-bit interfaces, CRT-era games.

**Do not directly copy existing characters, artwork, logos, or UI.** Use the references as a visual vocabulary: pixel geometry, sparse sprites, game-state feedback, compact typography, and playful friction.

### Core principles

1. **Intentional** — friction should make opening a distracting app a conscious choice.
2. **Fair** — the blackjack interaction must be simple, legible, and predictable.
3. **Personal** — the app should feel like a companion rather than a punishment system.
4. **Minimal** — information is structured clearly; decoration never competes with the task.

## 3. Visual Language

### Dark mode
- Near-black background
- Warm off-white text
- Muted gray secondary surfaces
- Lavender/purple as the primary accent
- Small amounts of pink/green for state feedback

Suggested tokens:
- `#080B0F` background
- `#181A22` surface
- `#202033` border/surface
- `#7E6CFF` primary
- `#9B8CFF` secondary accent
- `#FF609A` loss/warning
- `#86EFA6` win/success
- `#E6E6E6` primary text

### Light mode
- Warm cream background
- Charcoal text and borders
- Purple remains the primary brand accent

Suggested tokens:
- `#F8F8F2` background
- `#ECEBE4` surface
- `#D6D6C8` border
- `#6B5CFF` primary
- `#9B8CFF` secondary accent
- `#FF609A` loss/warning
- `#22C55E` win/success
- `#1A1A1A` primary text

Both themes use the same component geometry and semantic color roles.

## 4. Typography

Use a **pixel display font** for headings, numbers, buttons, and game-state labels.

Use a **monospace UI font** for supporting text and data.

Hierarchy:
- H1: 32px
- H2: 20px
- H3: 16px
- Body: 12px
- Caption: 10px

Typography should be compact, high-contrast, and slightly game-like without becoming difficult to read.

## 5. Components

### Buttons
- Pixel-stepped or hard rectangular borders
- Strong primary fill in purple
- High-contrast text
- Minimal radius
- Clear pressed/disabled states

Variants:
- Primary
- Secondary
- Tertiary / ghost
- Icon button

### Panels
Use bordered rectangular panels for:
- Time bank
- Usage summaries
- Blackjack state
- Lockout state
- Insights
- Settings

Panel hierarchy should come from border weight, contrast, and spacing rather than excessive shadows.

### Status labels
Use concise game-state language:
- `WIN  +5 MIN`
- `LOSE  LOCKED 10 MIN`
- `LOCKED`
- `FOCUS MODE`
- `5 MIN EARNED`

### Progress
Use pixel-compatible bars and compact timer displays.

## 6. Iconography & Game Elements

Primary visual vocabulary:
- Hearts / lives
- Locks
- Hourglass / timer
- Playing cards
- Stars
- Moon
- Small pixel characters
- Skull/challenge motif
- Game controller
- Music note
- Stats bars
- City/night silhouettes

Icons should use a consistent pixel grid, thin/high-contrast outlines, and minimal internal detail.

## 7. Key Screens

### Welcome
Purpose: establish Sonder's identity and promise.

Elements:
- Sonder wordmark
- Pixel night scene
- Heart/star motif
- Short tagline: **“be here, not everywhere.”**
- Primary start action

### Home
Purpose: show the user's current relationship with time.

Elements:
- Time bank
- Remaining available time
- Today's screen-time summary
- Time earned through blackjack
- Upcoming lockout
- Bottom navigation

### Blocked App
Purpose: explain the interruption without feeling punitive.

Elements:
- Locked-app indicator
- App name
- Clear explanation
- Blackjack reward rules
- Primary **PLAY BLACKJACK** action
- Option to choose another app

### Blackjack
Purpose: make the interruption engaging but fast.

Elements:
- Dealer hand
- Player hand
- Hit / Stand
- Current totals
- Win/loss outcome
- Persistent reward rule

The game should prioritize clarity over casino realism.

## 8. Interaction Rules

### Opening a blocked app
`Blocked → Blackjack → Result → Access / Lockout`

### Win
- Show clear win state
- Award exactly **5 minutes**
- Return to the previously blocked app
- Display remaining earned time

### Loss
- Show clear loss state
- Apply exactly **10 minutes** of lockout
- Prevent bypass through the blocked-app flow
- Show remaining lockout time

### Feedback
- Card flip: short 2-frame pixel animation
- Button press: small downward pixel shift
- Timer: subtle pixel tick/pulse
- Win: restrained sparkle/heart feedback
- Loss: brief shake or state transition

Animations should remain fast and purposeful.

## 9. Voice & Tone

**Encouraging, direct, slightly quirky.**

The product should sound like a game that wants the user to win at real life, not like a parental-control utility.

Good:
- “time is limited. make it meaningful.”
- “wanna try your luck?”
- “small choices lead to big changes.”

Avoid:
- guilt
- shame
- overly clinical productivity language
- aggressive gambling language

## 10. Layout & Spacing

Use a compact, deliberate grid.

- Strong alignment
- Consistent horizontal padding
- Generous separation between major panels
- Dense information inside clearly bounded modules
- Avoid unnecessary cards-within-cards

Pixel decoration should live around content, not behind important controls.

## 11. Accessibility

- Maintain strong contrast in both themes
- Never communicate win/loss/lockout using color alone
- Keep timers and rewards numerically explicit
- Use readable monospace body text
- Make Hit / Stand and primary actions visually distinct
- Respect reduced-motion preferences

## 12. Design System Checklist

Every new Sonder screen should answer:

- Is the user's current state immediately obvious?
- Is the next action unmistakable?
- Does the UI use the same pixel geometry and typography?
- Does dark/light mode preserve the same semantic hierarchy?
- Does decoration support the game-console identity without reducing usability?
- Does the screen reinforce **“be here, not everywhere”**?

## 13. Reference Asset Sheet

The accompanying reference image contains:
- Dark/light color swatches
- Pixel typography scale
- Buttons and panels
- Status labels
- Hearts and locks
- Playing cards
- Timer/hourglass
- Character/challenge motifs
- Data visualizations
- Borders and decorative motifs

Treat the asset sheet as a visual source of truth for future UI work while keeping implementation components reusable and theme-driven.
