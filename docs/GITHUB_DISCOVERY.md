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
