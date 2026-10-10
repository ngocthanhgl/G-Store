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

## 6. Daily scanner bot → committed catalog (added post-M8, user request)

Because no endpoint enumerates every APK repo (§3), a scheduled GitHub Action
does the broad scan server-side once per day and commits the result to this
repo; the app pulls it on launch so Home always has a list.

- Workflow: `.github/workflows/scan.yml` — cron `0 2 * * *` (daily) +
  `workflow_dispatch` + push trigger on `tools/scan.py` / `scan.yml` itself
  (so the first scan runs immediately after merge without waiting for cron).
  `permissions: contents: write`; 30-min timeout; commits
  `catalog/apps.json` + `catalog/meta.json` only when bytes changed. The scan
  step never fails the job and the commit step has `if: always()`, so partial
  results are committed even when the run hits its soft deadline or an error.
- Scanner: `tools/scan.py` (stdlib only, no deps). Three repo-search seeds
  (`topic:android`, `android language:Kotlin stars:>100`,
  `android language:Java stars:>100`), 5 pages × 100 each; dedupe, stars-desc,
  cap `MAX_CHECK=600` candidates (fits the GITHUB_TOKEN 5,000/hr budget with
  headroom). A 22-minute soft deadline (`SCAN_DEADLINE_S`) stops starting new
  repos in time for the 30-min job timeout; meta.json flags `deadline_hit` /
  `complete` / `error` honestly.
  Per repo: `releases?per_page=5`, first stable release with an APK asset wins,
  else newest prerelease-with-APK (flagged `prerelease: true`); archived /
  disabled / draft-only repos skipped. Rate-limit guard: sleeps until reset
  when `X-RateLimit-Remaining < 60` or on 403/429 (honors `Retry-After`).
  Output capped (`ENTRY_CAP=1000`, notes truncated 2,000 chars, ≤20 assets /
  entry) so the committed file stays small enough for a bounded client read.
- Schema (`catalog/apps.json` array; `catalog/meta.json` run stamp): repo
  identity + stars/forks/topics/archived/default_branch/created_at/license,
  one release (id/tag/name/notes/html_url/prerelease/published_at), and per
  asset id/name/content_type/size/download_url/digest/download_count.
- Client (`CatalogClient` → `CatalogRepository.importCatalog`): ETag
  conditional GET against `raw.githubusercontent.com/.../main/catalog/apps.json`
  (2 MiB cap), imported on first empty launch and when >24 h stale; repo
  metadata refreshes but FRESH local rows are never downgraded; releases and
  assets are upsert-only (history only grows); assets without a download URL
  are skipped; `eligibleApk` is recomputed by `classifyAsset`, never trusted
  from the file.
- Honest limits (user-visible where it matters): coverage = search-seed
  surface, NOT all of GitHub; unauthenticated scans see public data and share
  the runner-IP budget, so a run can return fewer entries under contention;
  catalog staleness up to 24 h + client cache; user-added repos and manual
  sync remain the escape hatches for anything the scan misses.
