# Home status hierarchy under a real lockout

`wayfinder:prototype` · parent: [Modernize the Sonder Pixel UI v3 interface](../MAP.md) ·
state: **open** · blocked by: [Icon system](01-icon-system.md)

## Question

`HomeStatusPanel` (`ui/screens/home/HomeScreen.kt:140`) already renders live state, and
the quality-pass design's whole purpose is that "live protection state and the next
useful action are immediately clear." What is not settled is the *hierarchy* — which of
the four states a user is in dominates, what the second element is, and what the panel
does when state is ambiguous (debt outstanding **and** a lockout running, or access
granted with debt owed).

Decide, per state:

- the single dominant element (a timer, a state word, or the next action);
- what occupies the second slot, and whether it is ever the navigation affordance;
- whether an outstanding debt is a first-class status on Home or a line inside Stats;
- what the panel shows when nothing is happening at all, which is the state most users
  see most of the time.

## Prototype

The artifact is the screen itself under each state, not a discussion: build the states
against real fixtures and react. Blocked by the icon-system ticket because the badge
tokens are the vocabulary this hierarchy is written in.
