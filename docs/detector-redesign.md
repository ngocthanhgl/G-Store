# Detector pass — post-M8 redesign (impeccable, manual detector)

Command (2026-10-10, executed):

```
impeccable.cmd detect --json <RepoCard.kt> <HomeScreen.kt> <DetailsScreen.kt>
```

Result: `[]` — no findings on any of the three redesigned files.

## Self-check against craft-floor (before the run)

- Contrast: all secondary text uses `onSurfaceVariant`; badges pair
  container with its `on*` color. No gray, no raw hue-on-white.
- No kickers/eyebrows; headings carry their own weight (titleLarge).
- No nested cards: sections are plain Columns; cards never contain cards.
- Radii: user radius (4–20 clamp) on cards; pills only for chips/badges.
- Icons: Material filled set only, one weight; `◌` prerelease glyph replaced
  with a `· pre` text suffix; every icon-only button has contentDescription.
- Motion: the single authored moment (show-more expansion, spring/snap)
  untouched; no new motion added.
- States: loading/error/empty paths edited, not removed — null-repo branch
  keeps spinner-while-loading vs error+retry split; empty catalog keeps icon +
  sentence + two actions.

## Play-Store pass (Top charts, rails, icons, overflow audit)

`detect --json` on RepoCard, RailCard, AppIcon, HomeScreen, DetailsScreen,
SearchScreen, DownloadsScreen: `[]` clean. New known-good icons used:
none beyond the set above (width/layout imports only). Overflow fixes are
maxLines+ellipsis caps, no detector involvement.

## Redesign round 2 (edge-to-edge, nav, settings, grid, bot v3)

`detect --json` on GridCard, AdaptiveCard, SettingsScreen, MainActivity:
`[]` clean. Lesson: `Icons.Filled.Palette` is NOT in material-icons-core
(build error) — the settings actions shipped text-only instead of gambling
a second icon name. Lint `NewApi` caught `layoutInDisplayCutoutMode`
(API 28+, minSdk 26) — guarded with an SDK check.

## Fix applied from the build (not the detector)

`Icons.Filled.Apps` is not in `material-icons-core` (compile error) —
substituted `Icons.Filled.Add` for the empty-state icon. Known-good icon set
for this repo is now: Add, Check, Close, KeyboardArrowUp/Down, Refresh,
Settings, Favorite/FavoriteBorder, Star, Share, ArrowBack.
