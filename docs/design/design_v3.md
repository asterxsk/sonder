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
| `muted` | `#8A8168` | supporting text (lifted from `#7F765F`, which fell below 4.5:1 on Panel — see §3 contrast note) |
| `success` | `#22C55E` | access granted |
| `danger` | `#EF4444` | out of time |
| `info` | `#5A9AC8` | informational |
| `purple` | `#8B5CF6` | special state only |

**Amber is the brand accent.** Blue/purple must not compete with it.

### Contrast

Every text tone must clear 4.5:1 against the ground it is actually drawn on — Panel
(`#221A12`) is the common case, not Bg. Measured: Text 11.9:1 on Panel, TextSoft 7.0:1,
Muted 4.5:1. The original `muted` of `#7F765F` failed at 3.9:1 on Panel and 4.3:1 on
Surface while carrying reading copy on the onboarding, stats, and Home screens, so it was
lifted to `#8A8168`. Treat the tone as a floor, not a hue: a future value may move, but
it does not go below 4.5:1 on Panel.

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
│ (ICON) YouTube              ›  │
│        com.google...           │
└────────────────────────────────┘
```

The row gets:
- pixel border
- tiny metadata line
- app icon bare on the row's own ground, in a 40dp slot
- one 48dp framed glyph button — `›` — that opens that app's per-app screen

A target that gates only Reels/Shorts carries a third metadata line,
`REELS ONLY` on Instagram, `SHORTS ONLY` on YouTube and `REELS & STORIES` on Facebook,
under the package id. The
line names the surface the app itself has — one app's label for the other's screen
says the user is gating something that does not exist. Without it a shorts-scoped
YouTube and a whole-app YouTube render identically, and the row would say a
blocked app is blocked in full when it is not. The line is drawn only for a
scoped target, so whole-app rows keep the two-line metadata.

The icon is the one thing in the row that is not framed. The row is already one framed
object, so putting a second border around the icon made a picture inside a box rather
than a list row; the icon reads as the row's own content, the arrow as its control.


The row itself is inert and the arrow is its only control. Editing the rule and removing the
target both live on the screen the arrow opens, so a target is changed in one place and there
is one point of entry to reach it. `REMOVE LIMIT` sits there behind its own thirty-second hold,
so removing a target is never the tap next to the one that saved it. The wait buys time to
think; the question is what the thinking is for.

### The per-app screen commits on SAVE

`›` opens a per-app panel with a bank-size control and, for a catalogued app, a
`[ WHOLE APP ] [ REELS ]`, `[ WHOLE APP ] [ SHORTS ]` or `[ WHOLE APP ] [ REELS & STORIES ]`
scope tab, named for the
surface that app has. Edits buffer in the screen and
land in storage only on a full-width `SAVE`; the screen is not live-writing, so
SAVE is not decoration — it is the only thing that persists.

Everything else on the screen follows from that. The badge next to the title
reads `UNSAVED` while the draft differs from storage and `SAVED` after a commit,
because a button that may or may not have done something is worse than no button.
`SAVE` is a two-press hold: the first press starts the button filling green, and only a second
press once the fill has run its thirty seconds commits the draft — then the label becomes
`SAVED` and the page closes back onto Targets. `SAVE` is disabled when there is nothing to
commit. And leaving with a live draft
— `‹ TARGETS` or system Back — raises a confirmation rather than dropping the
work silently; without that guard, buffering would be strictly worse than writing
each tap straight through.

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

The stakes are named as chips — `2:00`, `5:00`, `10:00` and `ALL IN`, the whole bank — but a
chip here is minutes of the user's own time, never money. No coins, cash, poker tables or
casino felt.

The table opens with **no chip on it**. A bet is a wager the player has to place, so the
chips start unselected and the deal control reads `PLEASE SELECT A CHIP` in `danger` until
one is picked; it becomes `♠ DEAL HAND` the moment there is a bet to deal. A default chip
would be the interface choosing a wager on the user's behalf.

## 10. Blocked screen

The block screen should feel like the user has entered a tiny room.

Background:
- dark apartment
- desk/lamp
- night window
- small character/cat

Foreground:
- framed target card

It has **two faces**, and which one is drawn is the whole of what the screen is saying:
the **table** while the bank holds time, the **wall** when it does not.

Table face — the bank has time in it, so there is a bet to place and a hand to win:

```text
   ▣ PLAYING

        YouTube

   BANK 04:12 OF 10:00

   [ 9♠ ] [ 7♥ ]      YOU 16

  [2MIN][5MIN][10MIN][ALL IN]
  ┌──────────────────┐
  │PLEASE SELECT A CHIP│   ← danger, disabled
  └──────────────────┘

  ┌──────────────────┐
  │  ✕  CLOSE APP    │
  └──────────────────┘
```

Wall face — the bank is spent, so there is nothing to play for:

```text
   ▣ LOCKED

        YouTube

   OUT OF TIME — LOCKED FOR TODAY

  ┌──────────────────────┐
  │ ◷  04:12:33           │
  │    UNTIL YOUR BANK    │
  │    REFILLS            │
  └──────────────────────┘

  ┌──────────────────┐
  │  ✕  CLOSE APP    │
  └──────────────────┘
```

The wall carries **no chips at all**, not even greyed. A control that exists only to say
"no" invites the tap that finds out; the absence of the table is the message, and the
countdown says how long it lasts. The countdown is derived from the clock to the next local
midnight, never from a stored deadline, so it cannot go stale across a process death,
reboot, or timezone change — the same reason the refill itself is a read rather than an
alarm.

The environment is decoration; the framed content is the functional layer.

### The way out

CLOSE is not part of this section's original sketch and is now mandatory. A blocker that
only offers a way *in* is a wall: the user has to know to press Back or Home, and inside an
app that intercepts Back that is not an escape at all. It is rendered once, below the table,
as a full-width secondary button, so the way out is
never competing with the way in and is never the control that gets clipped off the bottom.

It sends Home and then a best-effort `killBackgroundProcesses`. On modern Android that call
does not kill a foreground app, so Home is the real effect; the kill is a nudge for an app
that is already backgrounded.

### The way out on a settled hand

CLOSE is the way out of the block screen, and a *settled hand* keeps it. A settled hand is
the moment the bank moved, and on a short screen the way out must not be the thing pushed
off the fold — so `✕  CLOSE APP` still renders below everything.

`CONTINUE` is the way in and is offered **only on a won hand**. It is keyed to the outcome
rather than to the balance, because a push leaves the bank exactly as it was and a loss
leaves time in it too: a `CONTINUE` offered whenever there was time in the bank would open
the app on a hand nobody won.

`PLAY AGAIN` is offered on every settled hand, won or lost, with the chips beside it — the
chips say for themselves which bets the bank can still cover. A hand that takes the last of
the bank leaves them all greyed and the deal control asking for a chip there is none to
place.

CLOSE is never replaced by the replay controls. It still renders in every state — live hand,
settled hand, an empty bank — so the way out is never something the player has to earn,
outlast or scroll to find.

### Centring

The stack is centred vertically, not top-anchored. Against a 360×800dp baseline the
difference is small, but on a tall screen a top-anchored blocker leaves the whole lower half
empty and reads as a layout that failed rather than as a deliberate screen. The centring
comes from a `Box(contentAlignment = Center)` around the column and **not** from
`Column(verticalArrangement = ...)`: the column scrolls (§19, so CLOSE is never clipped) and
a scrolling column is measured against an unbounded height, which leaves it no spare space
to distribute, so the arrangement never applies.

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

   THE BANK TAKES

       05:00

 ┌───────────────────┐
 │  PLAY AGAIN       │
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
- green for a funded bank
- red for an empty one

Timer digits should use pixel typography.

## 14. Status badges

Four canonical badges:

`✓ GRANTED`
`▣ LOCKED`
`♠ PLAYING`
`⌛ COOLDOWN`

`♠ PLAYING` and `✓ GRANTED` belong to the gate's table face: `PLAYING` is a table with a
hand yet to be won, and `GRANTED` is a hand that was won — the gate never reads `GRANTED`
over a hand nobody won.

Home uses the same two tones for a different question, and it is the bank it is reporting
rather than permission: a funded row renders as `✓` with the time it holds (`✓ 29:00`), and
a spent one as `▣ LOCKED` with no countdown. That distinction matters more now than it did,
because Home's `✓` no longer means the app opens without a hand — the gate is on every open.
Home shows what is in the bank; the table is what decides what it is worth.

`▣ LOCKED` carries the gate's **wall** face and never the table's. It is the badge of an
empty bank, which is a state with a hand in it nowhere — the countdown to the refill is the
whole of what that screen offers. On the table it would be wrong: time in the bank is not a
lock, however far from the app it still is.

`GRANTED` on the gate is keyed to the **win** and not to the balance. A push leaves the bank
untouched and a loss leaves time in it, so a balance-keyed badge would read `GRANTED` over
the one outcome that must not open a door.

Each has:
- icon
- label
- colored border
- dark fill
- pixel frame

## 15. Navigation

Bottom navigation is a floating console dock: a rounded pill that clears the screen
edges by a 16dp margin on the sides and the bottom, so it floats above the gesture
area rather than sitting flush. This is the one deliberate rounded surface in an
otherwise zero-radius world — §5's frame language stays 0px for panels, buttons, and
tabs. The pill is the approved exception, recorded here so the doc never silently
contradicts §5.

```text
   ╭──────────────────────────────────╮
   │   ⌂      ⌖      ▥       ⚙       │
   │  Home  Targets  Stats  Settings  │
   ╰──────────────────────────────────╯
```

The pill keeps §5's hard grammar, only rounded:
- 32dp radius — half the 64dp dock height, so the ends are true semicircles
- 2dp hard frame
- hard-offset shadow, rounded to match so it cannot poke square corners out from under the pill

The bar is divided into four equal segments, one per tab. The selected segment is lit as
raised panel and fills the pill's inner height — inset by the frame stroke and no more, so
the lit key is flush with the dock's own top and bottom edges rather than floating short of
them — and it never reaches into its neighbour, because each segment is exactly its quarter.
The two end segments take the pill's curve on their outer side and stay square on the side
facing the next tab; the middle two are hard squares. The pill clips everything inside it to
its own shape, so no segment can draw outside the bar.

Each tab is a glyph over a label, and the glyph is the icon: in the platform symbol font,
because `⌂ ⌖ ▥ ⚙` are symbols the pixel face has no cut of. The label is 10sp DM Mono —
§4's floor keeps the pixel face at 10sp and above, and the labels are sentence case,
which the pixel face has no lowercase cut for. Under a glyph that size the label is the
caption, not the icon, so it is small and quiet.

Sizes are chosen so the four land at the same optical height, and any future change to the
glyphs has to be checked the same way, by measuring the marks rather than by trusting the
sp value. Measured on an API 36 emulator at 420dpi, ink bounding boxes of the rendered
marks at a shared 26sp:

| tab | glyph | code point | ink |
| --- | --- | --- | --- |
| Home | `⌂` | U+2302 | 43 × 49px |
| Targets | `⌖` | U+2316 | 43 × 43px |
| Stats | `▥` | U+25A5 | 41 × 41px |
| Settings | `⚙` | U+2699 | 58 × 58px |

Targets is `⌖` U+2316 (POSITION INDICATOR — a ring with crosshairs), **not** the `◎` this
section originally specified. `◎` drew a 30px ring at 26sp against `⌂`'s 43, so Targets
read as the tab that had been shrunk. That is not a size the sp value can fix: the gap
comes from how much of its em box the glyph is drawn in, and the Geometric Shapes block is
drawn small — its alternatives `◉` U+25C9 and `◍` U+25CD measure 31px at the same 26sp,
against `⌖`'s 43. `⌖` comes from the Miscellaneous Technical block alongside `⌂`, and its
ink width matches Home's exactly. Any replacement glyph has to be measured the same way —
a per-tab `glyphSize` would paper over the difference, and would then have to be re-tuned
per device if the symbol font ever changed.

Making the size the lever has a hard ceiling besides. The dock is a 64dp pill with 8dp of
padding, so a tab has a 48dp column for a glyph *and* a label. The glyph is measured first
and the label with whatever height is left, so a glyph whose line box fills that column
does not overlap the label — it leaves the label zero height, and the tab renders as a
bare glyph. The 38sp `◎` this section originally chose did exactly that on device: Targets
showed a ring and no "Targets". All four tabs are 26sp, which is the largest size the dock
holds; a larger glyph would need a taller pill, and the 96dp navigation reservation in §19
does not have the room.

Active tab:
- a square of raised panel behind the tab — square on purpose, because §15 rounds the
  pill and not the key inside it, so the two shapes stay legible as two shapes
- amber glyph and label on that ground; no frame of its own, since the pill is already
  the loudest outline on screen

The old full-width amber top/bottom marker bars are gone: they are bar grammar, and
inside a pill they read as a straight edge cutting the curve. Inactive tabs carry no
dividers for the same reason — the active segment is the whole signal.

## 16. Toasts

Toasts should resemble small RPG dialogue/status boxes.

Success:
`✓ Access granted. You have 5 minutes in the bank.`

Error:
`▣ Out of time for today. Win a hand to get back in.`

Info:
`◔ Time's up. Access to YouTube has expired.`

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

### The cards

The table is the one place the UI has objects rather than panels, so it is the one place
motion carries meaning, and both of its motions are still discrete:

**Deal.** A card lands — a 16dp drop and a four-step fade over 200ms, its slot reserved from
the first frame so nothing reflows under it. Cards of the same hand are 90ms apart, so a
hand arrives card by card; a card drawn mid-hand arrives alone and lands at once, because a
HIT has to feel like a HIT rather than like a queue.

**Flip.** The dealer's hole card turns over on `rotationY` through four hard frames, swapping
which face is drawn at the halfway point — a card caught edge-on, not a crossfade. The
duration is 240ms, not the "2 frames" above: at 60fps two frames is 33ms, which is a card
that has already turned over before the eye catches it. Four frames at that length is the
same hard-edged turn, at a speed that can be read as one.

The reveal only works if the hole card is a single composable whose `faceDown` changes.
Composing the back and the revealed card as two different call sites disposes one and builds
the other, and the turn is never seen — it reads as a swap.

### Screen transitions

Screens slide sideways; they do not crossfade. A fade says a screen replaced another with
no relationship to it, where a slide says which way the user is going and which way Back
comes home. Push enters from the right and leaves left; pop reverses. 180ms.

The slide is the one animation not run through `steps()`, and the exception is deliberate:
quantising a colour or a 2dp press to four frames is what makes motion read as pixel-hard,
but quantising a full-width slide to the same four frames lands them 90dp apart and reads
as a stutter rather than as a slide. The duration is still the motion scale's 180ms; only
the easing is continuous.

### The loader

Loading is a **chip stepping round a quarter turn at a time** — the app's own chip, drawn
through the same `ChipIcon` the stakes are bet with, at the same palette. It is the mark the
app is recognised by and the mark it bets with, so a loading screen showing a bar or a clock
would be the one surface not speaking the table's language.

The turn is **five hard 9° steps** — 0°, 9°, 18°, 27°, 36° — each held rather than eased
into. A smoothly spinning chip is the one object in the interface that would move
continuously, and §18's rule is not about duration: it is that this world's motion lands on
frames. The angle is floored to a whole step and never interpolated, which is what makes the
loop read as a stepping sprite rather than a slow rotation.

**9°, not 90°, and this is not a preference.** The chip's rim carries eight inserts evenly
spaced around it, so the drawn chip is bit-for-bit identical under a quarter turn — measured
against the grid, a 90° rotation differs in **0** of its 576 cells. A quarter-turn loader
therefore renders the same frame forever and looks frozen. The angle has to be a value
*modulo* 45°, where five 9° steps give five genuinely different pictures; the wrap from 36°
back to 0° differs in only 40 cells, so the loop closes more gently than it turns.

The tile is marked as loading for screen readers: a decorative mark standing in for a
screen that has not answered yet leaves TalkBack with nothing else to read.

`PixelSkeletonList` is not a loader and is unchanged — it stands in for a list that has not
arrived, where the shape of the missing thing is the information.

### Waiting states

A list that is still reading shows the rows it is about to become — same frame, same icon
slot, same control frames — not a spinner and not a panel in the middle of an empty screen.
The placeholder holds the layout still, so the real rows do not jump into place when they
arrive, and it tells the user what is coming rather than only that something is.

The placeholder blocks take the two nearest grounds (`Panel` to `BorderDark`) and pulse
between them on a 700ms two-frame cycle. They are inert, and the whole list announces
itself to accessibility once as "Loading" rather than exposing a stack of empty boxes.

## 19. Layout and anti-clipping rules

The V2 anti-clipping rules remain mandatory.

Baseline:
**360×800dp portrait**

Safe area:
- 16dp sides
- 16dp top
- 16dp bottom
- 96dp reserved navigation area (16dp margin + 64dp dock + 16dp margin)

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
- AccessRules
- Target
- TimeBank
- EnforcementState

pure and testable.

Native Android owns:
- accessibility
- overlay
- foreground detection
- lifecycle
- persistence

Core rules remain:
- the bank starts at 0 every local day
- win = bank + the staked chip, capped at the app's max
- loss = bank − the staked chip, never below 0
- time is billed only while the app is in front
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
- casino green felt
- fake 3D bevels everywhere
- excessive particle effects
- giant dashboard statistics
- generic stock illustrations

**On the chip.** This list used to ban casino-chip imagery outright, and the launcher icon
was a cat for that reason — the mark was deliberately kept off the table. That is now
reversed: the chip is the brand mark, and it is the launcher icon, the loader, and the art
on every stake. The ban was aimed at a *casino read* — felt, coins, cash, a promise of
money — and the chip it stopped was the one thing on the gate that was already the product's
own. What survives the reversal is the reason the ban existed: a chip here is minutes of the
user's own time, and nothing may ever suggest otherwise. Green felt stays banned, and §9's
rule that a chip is never money is load-bearing for the mark rather than merely adjacent
to it.

## 22. Design target

The desired feeling is:

**Gawkbot's authored pixel-world discipline + Sonder's quiet nighttime self-control + a tiny handheld RPG menu.**

The UI should look like it belongs to a small game that happens to control app access—not like a productivity app with a pixel-art skin.
