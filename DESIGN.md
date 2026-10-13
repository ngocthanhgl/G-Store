# G Store — Design World: "Tonal Shelf"

Committed visual world for the impeccable skill. Refinement preserves this
page; redesign replaces it (never split the difference).

## Idea

A library shelf, not a storefront. Quiet tonal surfaces, one confident accent,
numbers set like a ledger. The catalog is the hero; chrome recedes.

## Tokens (centralized in `:core:designsystem`)

- **Color:** Material 3 tonal schemes. Custom accent seeds `primary` only;
  dynamic color on API 31+. Status is always tonal, never raw hue-on-white:
  `APK available` = primaryContainer, `Archived` = errorContainer,
  neutral = surfaceContainerHigh. Secondary text is `onSurfaceVariant`
  (never gray).
- **Type:** Default M3 scale, obvious steps: section headers `titleLarge`,
  card titles `titleMedium`, body `bodyMedium/Small`, counts `labelLarge`
  with tabular lining figures where the platform provides them. No kicker or
  eyebrow above any heading — the heading carries its own weight.
- **Shape:** Cards 12dp default (user 4–20). Controls pill or small. One
  elevation language: filled tonal cards, borders only for selected filters.
- **Spacing:** 4dp grid. 16dp screen gutters, 12dp card padding, 24dp before
  a section header, 8dp after it. More space above a heading than below.
- **Motion:** One authored moment — the home show-more expansion
  (`animateContentSize`, spring; snap when reduced-motion is on). Everything
  else crossfades or appears. No entrance choreography.

## Component language

- **RepoCard:** monogram tile (44dp, tertiaryContainer) → title + 2-line
  description → ledger row (star count, fork count, status badges) →
  48dp favorite action. Whole card is one click target.
- **Filters:** single-select FilterChips with leading check when active;
  selected chip is primaryContainer, never an outline-only state.
- **Sections:** `Title · count` header row, horizontal repo rail or list,
  inline show-more. Loading = skeleton rows; empty = icon + one sentence +
  one action; error = problem + recovery button (never a dead spinner).
- **Details hero:** owner monogram, full name, description, stat strip
  (stars / forks / license), honest badges. Release picker as chips;
  assets as tonal cards with ABI confidence + checksum state.
- **Icons:** Material Symbols only (filled, one weight). No emoji, no
  unicode glyphs as icons. Every icon-only button has a contentDescription.

## What this world refuses

Gradient text, glassmorphism, kickers/eyebrows, section numbers, nested
cards, hard offset shadows, sparklines-as-content,
hero metrics, modals for non-blocking choices.

## Aurora port (GPL-3.0-or-later, see LICENSE and DECISIONS.md §19)

Rows, rails, and section headers are ported from Aurora Store's Compose
source (same contracts, rhythm, and chevron behavior) with documented
deviations: truncating text, refresh/hide trailing actions, no previews.
The prior accent-bar experiment is retired — current Aurora source has no
bar, and code beats screenshots.
