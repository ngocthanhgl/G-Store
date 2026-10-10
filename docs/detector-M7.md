# Impeccable detector pass — M7 (2026-10-10, code-level; no device on host)

Scope: new `feature/settings` (3 files), edited `core/ui` (RepoCard,
RepoRows), `feature/home` (grid, motion gate, dialog scroll),
`feature/search`, `feature/favorites`, app wiring. Method: line-by-line
review against UI_UX_SPEC + AGENTS.md §12 (no screenshots possible —
documented limitation, not a pass).

## Findings (all resolved in code except where noted)

1. DiscoverDialog rendered up to 10 RepoCards in a non-scrolling Column —
   overflow on compact screens. FIXED: dialog content scrolls
   (`verticalScroll`); direct `foundation` dep added to `:feature:home`.
2. `animateContentSize` imported from `androidx.compose.foundation` — does
   not exist there; correct package is `androidx.compose.animation`.
   FIXED (build caught it, recorded so it is not repeated).
3. Card radius was hardcoded `12dp` in RepoCard call sites. FIXED: radius
   and density flow from DataStore appearance into every RepoCard;
   `cardShapeFor` is the single shaper in `core:designsystem`.
4. Settings Slider had no adjacent value label for TalkBack context.
   FIXED: "Corner radius" label + "Ndp" value text in the same row.
5. FilterChip groups (32dp tall) under the 48dp target. ACCEPTED: M3
   platform pattern, rows carry `heightIn(min=48.dp)`; full-row tap is not
   standard for chip groups. Revisit only with device testing.
6. No contentDescription on Slider (M3 Slider exposes none). ACCEPTED:
   adjacent label + value text give TalkBack context; verified by API.
7. ThemeEditor preview Button is inert (`onClick = {}`). ACCEPTED and
   labeled in code: static mock, standard for theme previews.
8. Grid reuses list RepoCard at half width — description clamp (2 lines)
   and monogram scale down without new styles. Verified by reading
   RepoCard constraints; screenshot check pending device.

## Clean

No hardcoded colors/strings-dimensions outside tokens; no new icons beyond
material-icons-core (ArrowBack via automirrored core set); stable lazy
keys kept in both layouts; dismissed-banner/snackbar flows untouched;
`EmptyContent` placeholder for Settings removed (real screen wired).
