# UI/UX Spec — Forkly (as-built, M7)

Mode = Operate (task completion; scanability over expression) with Read-mode
rules for README/release notes. Verified against code at M7; deviations from
the M0 draft are marked.

## 1. Visual direction

Material 3 baseline, refined — not default components unstyled. Centralized
tokens in `core:designsystem` (`ForklyTokens`, `cardShapeFor`): 4dp spacing
grid, user-set card radius 4–20dp (clamped at read), tonal elevation.
No hardcoded colors/spacing in feature modules (lint-clean, detector-checked).

Motion: the app ships almost no custom motion. List expand/collapse is the
single animated element (`animateContentSize`: spring by default, `snap()`
when Reduce motion is on). Everything else is instant state changes;
ripples and pull-to-refresh physics are Material-controlled and minimal.
There are no custom 200/300ms durations anywhere — the M0 draft was wrong.

Dynamic color on API 31+ via system schemes; custom accent replaces the seed
primary and takes precedence over wallpaper dynamic color. The curated dark
seeds keep primary actions legible; there is no arbitrary color picker.
Contrast is carried by M3 tonal schemes, not by an in-app checker —
no contrast-ratio enforcement code exists (M0 draft was aspirational).

## 2. Customization model (persisted in DataStore, restored pre-first-frame)

Implemented and wired at M7: theme (light/dark/system), dynamic-color
toggle, accent (Automatic + 8 curated), layout (list/grid 2-col, same
cards/order/count), density (comfortable/compact padding), card radius
slider, reduce motion. Section visibility + order (unchanged from M4).
`SettingsScreen` edits them; `ThemeEditorScreen` previews them live.

Deferred (M0 draft listed, not built): card styles beyond radius, nav-style
choice (auto compact→bar / expanded→rail), content-spacing scale, background
effects. The spec no longer promises them.

## 3. Component language & states

Repository cards (`core:ui RepoCard`, shared by home/search/favorites):
monogram, owner/name, 2-line description clamp, star count, static badges
(Archived / APK / No APK — cached availability, never a guarantee), 48dp
favorite target, contentDescription on every icon action. No PRERELEASE /
ABI-UNKNOWN card badges exist; prerelease is marked ◌ in the details release
picker, ABI confidence in the asset rows. No freshness stamp on cards.

Async surfaces: loading indicator | content | empty (actionable: add/discover)
| error banner + retry. Pull-to-refresh on Home; stable lazy keys everywhere;
grids reuse the same keyed cards.

Accessibility: touch targets ≥48dp on icon actions (verified in code);
contentDescriptions on all icon-only buttons; TalkBack pass and large-screen
resize test are PENDING — no emulator/device on the build host (see
TEST_PLAN.md). The M0 "scan pass at M7" claim was premature.

## 4. Navigation & adaptive (as-built)

Routes: Home, Search, Downloads, Favorites, Settings, ThemeEditor, Details,
Readme. Release history is an inline section of Details (not a separate
destination); repository info is the Details header; the install-permission
explanation is an overlay gate in Downloads (opens system settings only on
tap, re-checks grant on return). Bottom bar on compact; nav rail on
expanded (sw≥600dp via BoxWithConstraints — reflows under API-37 forced
resizability; orientation never assumed). Details/Readme VMs are keyed per
repo so back-stack state survives. Route changes use a short fade; repeated
direct navigation to the same Settings, category, or README route is single-top.

## 5. Acceptance (visual)

No one-off styles; customization clamps (radius 4–20dp) so it cannot break
layout; automated checks at M7 = unit tests + lint + code-level detector
pass (`.agents/skills/impeccable/detector/M7-20261010.md`). Screenshot,
TalkBack, and resize tests remain device-gated (TEST_PLAN.md).

## 6. M8 detail-screen refinements

- Count pills center their value on both axes. Details content uses the shared
  spacing tokens consistently; the APK-discovery indicator is separated from
  preceding content.
- Release notes are collapsed by default and expand inline from the selected
  version chip. Screenshots appear after About; the full-screen viewer supports
  horizontal paging while preserving pinch zoom and pan.
- Repository images combine README-referenced images with root image files;
  README references and screenshot/screen/preview/demo filenames rank first.
  Common logo, banner, icon, avatar, and promotional-image names are excluded,
  and decoded images must be at least 500px on one axis. This improves precision
  but is not a measured 80% accuracy guarantee; subdirectories not referenced
  by the README and ambiguous image names may be missed.
- Markdown tables use shared per-column widths and horizontal scrolling.
- When a download completes while Details or Downloads remains foregrounded,
  Forkly opens Android's installer automatically; Android still requires the
  user to confirm. If the process is killed first, the completed APK remains
  available for manual installation from Downloads.
- Installation permission explanation is inset from safe drawing areas. When
  the user grants install permission in Android settings and returns, the app
  re-checks the grant and opens the system installer for the pending APK.
