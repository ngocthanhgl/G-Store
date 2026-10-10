# PRODUCT_SPEC — G Store (native Android GitHub APK marketplace)

Status: Milestone 0 planning. Normative for scope; metrics are definitions, not claims.

## 1. Vision

A genuine native Android (Kotlin + Jetpack Compose + Material 3) marketplace that
discovers, indexes, presents, downloads, and helps users install Android APK
artifacts published through GitHub Releases. No backend. No WebView primary UI.
No embedded credentials.

## 2. Users and non-goals

- Users: Android users who install open-source apps from GitHub Releases and want
  discovery, update tracking, and a safe install handoff.
- Non-goals: paid apps, app piracy, auto-install without the system installer,
  security ratings, mirroring APKs on our own infrastructure, iOS/desktop clients.

## 3. Home sections (each: loading / error / empty / refresh / pagination)

| Section | Definition (testable) | Configurable |
|---|---|---|
| All apps (daily catalog) | Every scanner-catalog entry in Room, ordered by the selected filter: Trending (same formula as below) / Stars desc / New (first eligible release desc, nulls last) / Recently updated desc. First list item on Home; collapses to 5 with show-more. | filter (persisted in-session; sections below persist) |
| New Apps | Eligible repos whose first eligible release was published within N days (default 30), sorted by first-seen publish date desc | hide/show |
| Trending | Score = `w1*ln(1+stars) + w2*ln(1+forks) + w3*releases_30d + w4*ln(1+downloads_30d)` over repos with `releases_30d > 0`; weights in DataStore, defaults `w1=4, w2=2, w3=10, w4=1`. Revision note (M4): delta signals (`stars_7d`) need a signal-history table we do not keep yet; log-dampened totals approximate standing while windowed terms carry velocity. Delta upgrade is a tracked future, not a silent swap. | hide/show, weights (advanced) |
| Famous | `stars >= 1000 AND eligible_release_count >= 3 AND age_days >= 365`, sorted by stars desc | hide/show |
| Recommended (optional) | Famous repos not yet favorited, stars desc — explicit, explainable basis | hide/show |
| Recently Updated | Repos ordered by latest release `published_at` desc (any kind, prereleases flagged) | hide/show |
| Continue Browsing (optional) | Local view-history, most recent first; purely on-device | hide/show, clear |

Section order is user-reorderable and persisted. Never label Trending without this
formula; never present release download counts as install counts.

## 4. Discovery

- Search across cached repo name, description, owner, topics; filters
  (has-APK, ABI, minSdk-known, prerelease-included, archived-hidden-by-default);
  sort by recently released / recently updated / popularity / relevance.
- Category browsing only where defensible metadata exists (GitHub topics mapped
  through a checked-in allowlist, e.g. `android`, `music-player`); never invent
  categories.
- Explicit user-added repository URLs (`owner/repo` or full URL) as a
  first-class discovery mechanism.
- Daily scanner catalog (see GITHUB_DISCOVERY.md §6): `catalog/apps.json`
  committed by `.github/workflows/scan.yml`, pulled on first launch and when
  >24 h stale; FRESH local rows are never downgraded by it.
- Favorites/bookmarks persisted offline. Repository attribution + link to source
  on every card. Badges for prerelease / archived / no-APK.

## 5. Details

Repo name/owner/avatar/description, icon when a valid source exists (owner avatar
fallback), stars/forks, latest eligible release + history, release notes,
artifact list (filename, size, ABI/confidence, checksum state), compatibility
notes, license when available, favorite/share, full README rendering.
Never invent package names, version codes, requirements, licenses, or
verification results — unknown fields render as "Unknown".

## 6. Download and installation (summary; see DATAFLOW.md flows C/D)

Background download with progress/cancel, temp-file + atomic finalize, size
validation, SHA-256 verification only against a trustworthy expected digest,
APK structural validation, history, then explicit user-confirmed handoff to the
system installer. Never silent-install; never claim success without evidence.

## 7. Customization

Light/dark/system theme, dynamic color (API 31+), accent color, card shape and
density, grid/list, section visibility/order, navigation style, content spacing,
reduced motion. Tokens centralized; contrast preserved (see UI_UX_SPEC.md).

## 8. Acceptance gates (product level)

- Builds with documented toolchain; installs on API 26–37 devices/emulators.
- New Apps, Trending, Famous match their §3 definitions against fixtures.
- Offline: favorites + cached catalog render with no network.
- Every APK install goes through the system installer with prior explanation UI.
