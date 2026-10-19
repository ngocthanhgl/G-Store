# GITHUB_DISCOVERY — strategy, limits, resilience

Authoritative integration: GitHub REST (`Accept: application/vnd.github+json`,
pinned `X-GitHub-Api-Version: 2026-03-10`). No GraphQL at Milestone 3
(ponytail: REST suffices; add only if a measured gap appears).

## 1. Endpoints used

- `GET /search/repositories?q=...&sort=stars|updated&per_page=100&page=n` — seed.
- `GET /repos/{owner}/{repo}` — metadata (+ `default_branch`, topics, archived).
- `GET /repos/{owner}/{repo}/releases?per_page=100&page=n` — full listing.
- `GET /repos/{owner}/{repo}/releases/latest` — shortcut; means most recent
  non-prerelease, non-draft by `created_at`. Never use for prerelease inspection.
- `GET /repos/{owner}/{repo}/readme` + contents API — README resolution.
- Asset bytes via `browser_download_url` (redirects honored only to
  `github.com` / `*.githubusercontent.com` / `*.githubassets.com`).

## 2. Eligibility (all must hold)

1. Repo metadata accessible (not 404/451). 2. ≥1 published release
   (stable; prerelease only when user opts in). 3. ≥1 asset passing APK
   validation (§4). 4. Metadata parseable into an honest entry.

## 3. Coverage limitation (explicit, user-visible in UI)

There is NO public endpoint enumerating every repo with an APK release.
Catalog = trending seeds (`topic:android` + language filters) + incremental
sync of known repos + user-submitted URLs + cached results. Never imply full
indexing; the Search screen states coverage basis. Sync checkpoints
(`DiscoveryQuery`) make indexing resumable; no full-catalog rescans.

Opening a GitHub search result can cache its repository and releases for the
details screen, but that detail-only sync is tagged `SEARCH_ONLY` and excluded
from Home discovery, category counts, and catalog rankings. A user can still
explicitly favorite that repository; it then appears in Favorites without
changing New, Trending, or Famous membership.

## 4. APK validation (not filename-only)

Accept `.apk` with `content_type` APK/zip/octet-stream AND structural check
(ZIP + `AndroidManifest.xml`); AAB/source archives/ZIPs are labeled, never
listed as installable. Split/bundle ZIPs get a separate flow or an
"unsupported bundle" label.

## 5. Rate-limit & caching discipline (verified 2026-10-10)

Unauth 60 req/hr per IP; authed user token 5,000/hr (15,000 GHEC); search
stricter; secondary caps ~100 concurrent / 900 pts-min. Client: conditional
`If-None-Match` reads, `X-RateLimit-*` + `Retry-After` honored, bounded
exponential backoff + jitter, failure classes distinguished
(network/rate-limited/auth/inaccessible/malformed). Never ship a shared token;
auth (if added) = user OAuth/PAT in EncryptedSharedPreferences/Keystore only.

## 6. Scheduled scanner bot → committed catalog (added post-M8, user request)

Because no endpoint enumerates every APK repo (§3), a scheduled GitHub Action
does the broad scan server-side eight times per day and commits the result to
this repo; the app pulls it on launch so Home always has a list.

- Workflow: `.github/workflows/scan.yml` — cron `0 */3 * * *` (every three
  hours, UTC) + `workflow_dispatch`.
  `permissions: contents: write`; 180-min timeout; commits
  `catalog/apps.json` + `catalog/index.json` + `catalog/meta.json` only when bytes changed. The scan
  step records its exit code and the commit step has `if: always()`, so partial
  results are committed even when the run hits its soft deadline or an error —
  while a nonzero exit with zero catalog changes fails the job red.
- Scanner: `tools/scan.py` (stdlib only, no deps). Fourteen rotating query
  sets across languages (Kotlin/Java/Dart/C#/Python/C++/
  Rust/Go/Scala/C/Lua/GDScript/JS/TS), game engines (Godot/Unity/LÖVE/emulators),
  APK topics (`topic:apk`, `topic:android-app`), form factors (TV/Wear),
  app domains (media, system, productivity, comms, maps, finance, fitness,
  FOSS ecosystem), and star tiers including the low-star tail (`stars:<10`).
  Major languages are split into tiers
  (`stars:<10` / `10..100` / `100..1000` / `>1000`) because search pagination caps
  at 1,000 results per query — one query can never cover a whole language.
  Every run selects three query sets and advances by three every scheduled
  slot, covering all 14 sets within five scans (12 hours from the first run).
  Query results
  are interleaved before checks so a broad search cannot crowd out smaller
  topics. Every query carries `archived:false` so dead repos never consume
  check budget. Up to 20 pages × 50 per query; dedupe, rank within each query,
  interleave, and cap `MAX_CHECK=8000` candidates per run to leave API headroom
  across eight daily runs. A 150-minute soft deadline
  (`SCAN_DEADLINE_S=9000`) stops starting new repos before the
  180-min job timeout; meta.json flags `deadline_hit` / `complete` /
  `error` honestly. Each run MERGES into the previous catalog (keyed by
  repo id): re-checked entries refresh and stamp `last_verified`; repos
  re-checked with no APK release are dropped; entries unseen for
  `STALE_DAYS=30` are dropped; pre-`last_verified` entries were
  grandfathered with the merge date. Cap `ENTRY_CAP=12000`.
  Per repo: `releases?per_page=10`, first stable release with an APK asset
  wins, else newest prerelease-with-APK (flagged `prerelease: true`);
  archived / disabled / draft-only repos skipped. Rate-limit guard: sleeps
  until reset when `X-RateLimit-Remaining < 60` or on 403/429 (honors
  `Retry-After`). A check-phase guard means ANY unexpected failure still
  writes the merged catalog with `error` recorded; the workflow fails red
  only when the script exits nonzero with zero catalog changes (loud empty
  runs are never silently green).
  Output capped (notes truncated 2,000 chars, ≤20 assets / entry) so the
  committed file stays small enough for a bounded client read.
- Schema (`catalog/apps.json` array; `catalog/meta.json` run stamp): repo
  identity + stars/forks/topics/archived/default_branch/created_at/license,
  one release (id/tag/name/notes/html_url/prerelease/published_at), and per
  asset id/name/content_type/size/download_url/digest/download_count.
  `last_verified` (ISO date) and meta `query_set` (first set for backward
  compatibility), `query_sets` (all sets scanned), `verified_today` /
  `carried_over` / `dropped_stale` were added with the rotation design;
  the app parses with `ignoreUnknownKeys`, so old clients keep working.
- Client (`CatalogClient` → `CatalogRepository.importCatalog`): ETag
  conditional GET against `raw.githubusercontent.com/.../main/catalog/apps.json`
  (2 MiB cap), imported on first empty launch and when >24 h stale; repo
  metadata refreshes but FRESH local rows are never downgraded; releases and
  assets are upsert-only (history only grows); assets without a download URL
  are skipped; `eligibleApk` is recomputed by `classifyAsset`, never trusted
  from the file.
- Honest limits (user-visible where it matters): coverage = rotating-seed
  surface accumulated over days, NOT all of GitHub (no endpoint enumerates
  every APK repo — §3); a run can return fewer entries under runner-IP
  budget contention; catalog staleness up to 24 h + client cache;
  user-added repos and manual sync remain the escape hatches for anything
  the scan misses.


## 7 - Slim launch index (2026-10-12)
The app no longer pulls catalog/apps.json at launch (it passed 2.8MB and
tripped the 2MiB response cap). The bot also writes catalog/index.json
(top 3500 by stars, descriptions capped at 240 chars, no asset lists,
~0.5MB); the app pulls the index and imports repo+release rows only.
Full per-release asset lists lazy-load when a details screen opens.
scan.yml commits both files.

## 8 - Catalog categories and repository screenshots
Index imports refresh repository topics from each current scan instead of
preserving stale cached topics indefinitely; this keeps category membership
aligned with the scanner's current GitHub metadata. Locally enriched
descriptions remain intact when the slim index omits the full description.

Details screenshots are a best-effort presentation feature, not catalog
eligibility evidence. The client currently considers image files in the
repository root, prioritizes screenshot/preview/demo-style names, excludes
common logo/banner/icon/cover names, and accepts only HTTPS
`raw.githubusercontent.com` URLs. Ambiguous assets and images nested below the
root may be missed; no measured precision percentage is claimed.