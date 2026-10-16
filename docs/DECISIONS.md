# DECISIONS — audit log, consistency review, open items

## 1. Skill audit (M0, verified — no capabilities assumed)

| Skill | Version / evidence | Verdict for this project |
|---|---|---|
| archify | `doctor` PASSED (Node v22.23.3, renderers ok) | Used: system-overview HTML (`../.archify/architecture-gstore-20261010-120000/gstore-architecture.html`, showcase). Mermaid kept for sequence/class-level docs (ponytail: one flagship diagram, not five). |
| impeccable | v4.5.1 `context` → NO_PRODUCT_MD, Operate mode | `init` interview + DESIGN.md deferred to M1 (needs user aesthetic answers); UI_UX_SPEC.md binds until then. Manual detector runs after first UI code. |
| ponytail | full (default) | Applied: minimal module set at M2, DownloadManager-first, REST-only at M3. Ultra's "question the request" reserved for scope creep during build. |
| graphify | pipeline requires real Python; host has only a WindowsApps stub (`python3` not found); corpus = 1 file | Cannot run meaningfully at M0. Equivalent manual workflow used: requirement→component edges live in ARCHITECTURE.md §1 + GITHUB_DISCOVERY.md §1–2. Revisit `graphify .` once Kotlin sources exist (needs Python installed first). |
| caveman | ultra default; Auto-Clarity rule | Excluded from normative docs (terseness risks misread in specs). Available for progress summaries on request. |

## 2. Toolchain findings (measured 2026-10-10)

- JDK 25.0.3 (Microsoft build) present; env `JAVA_HOME=E:\jdk` INVALID → fix before any Gradle/sdkmanager use.
- SDK has `platforms/android-35,36` + `build-tools/34,36`, `adb` 37.0.1. `platform-37` NOT installed → M2 prerequisite for compileSdk/targetSdk 37.
- AGP/Kotlin/Compose versions deliberately unpinned until M2 lock against compileSdk 37 (no fabrication).
- Tentative minSdk 26: justified by the per-source install-permission model (API 26+), FileProvider, and modern WorkManager/Room baselines — not arbitrary. Confirm at M2.

## 3. Research provenance

Android 17 stable 2026-06-16 + target-37 behavior breaks: developer.android.com
setup + behavior-changes pages (fetched). GitHub endpoints/fields/rate limits:
docs.github.com REST reference (fetched; `digest: string|null`, 60/5 000 per hr,
~100 concurrent / 900 pts-min secondary). Two websearch calls hit provider
rate limits; covered by direct doc fetches instead — nothing invented.

## 4. Consistency review (M0 close)

- ARCHITECTURE ↔ DATAFLOW: single-source-of-truth claims match flows A/F; InstallerGateway rule ↔ flow D. ✔
- DATA_MODEL states cover flows C/D/E failure taxonomy; `VerificationResult` matches SECURITY_MODEL §3. ✔
- PRODUCT_SPEC formulas ↔ TEST_PLAN fixtures (scoring, unknown-ABI, digest-null). ✔
- ANDROID_PERMISSIONS declares only what flows C/D + notifications need; no speculative permissions. ✔
- UI_UX_SPEC rail/resize rules ↔ API-37 resizability notes in permissions doc. ✔

## 5. Open decisions (must close by noted milestone)

1. M2: AGP/Kotlin/BOM pins + minSdk confirm. 2. M2: Hilt vs manual DI (default Hilt unless measured build-cost objection). 3. M3: Markdown parser choice (maintained Kotlin lib; candidate evaluated at M5). 4. M6: DownloadManager vs WorkManager+OkHttp (capability test). 5. M6: Play-build install-handoff policy (see SECURITY_MODEL §5). 6. M1: impeccable `init` aesthetic interview.

## 6. M2 build record (2026-10-10, verified by execution)

- Locked: Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21 (JVM modules; Android modules use AGP built-in Kotlin), KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2, Lifecycle 2.11.0, DataStore 1.2.1, activity-compose 1.13.0, OkHttp 5.5.0, coroutines 1.11.0, serialization 1.11.0. minSdk 26 confirmed.
- `platforms;android-37.0` + `build-tools;37.0.0` installed; `compileSdk/targetSdk 37` resolves against it — no dir hack needed.
- Deviations from M0 plan: (a) Retrofit 3.0.0 dropped — its `converter-kotlinx-serialization` coordinates don't exist and raw OkHttp fits the ETag/rate-limit header needs better; (b) manual DI kept (graph fits one file; Hilt revisit deferred); (c) AGP 9 built-in Kotlin migration applied per official docs (removed `kotlin.android`, dropped `kotlinOptions`, jvmTarget inherits targetCompatibility 17); (d) nav icons restricted to material-icons-core (`KeyboardArrowDown` for Downloads) instead of the extended set.
- Verified: `assembleDebug` BUILD SUCCESSFUL (app-debug.apk, 13.8 MB); `:app:lintDebug` SUCCESSFUL; aapt confirms minSdk 26 / targetSdk 37 / INTERNET-only.
- Could not run: emulator launch + navigation test (no emulator/system-image on host); unit-test suites (first tests land with M3 domain logic); post-UI impeccable detector (no user-visible UI beyond shell yet).

## 7. M3 build record (2026-10-10, verified by execution)

- New modules `:core:data` (SyncEngine, CatalogRepository, Eligibility, Backoff, entity mappers) and `:core:testing` (recorded JSON fixtures — both had to be Android-library modules: pure-JVM modules can't consume Android variants, and `:core:data` needed direct room-runtime for supertype visibility).
- SyncEngine: resumable checkpoints, draft exclusion, ETag/304 short-circuit, Retry-After-first backoff+jitter (5 attempts), in-flight dedup, NotFound checkpoint clearing. Room v2 (repositories/releases/assets/checkpoints) with pre-release destructive migration.
- Verified: `:core:data:testDebugUnitTest` 27/27 pass (classification 12, backoff 4, mapping 3, engine 8 incl. resume/304/rate-limit/dedup); `assembleDebug` BUILD SUCCESSFUL.
- Deferred: DAO instrumentation tests (no emulator; engine tested via fake DAOs); search persistence (M4 decides on sync); ABI-from-content detection (M5/M6).

## 8. M4 build record (2026-10-10, verified by execution)

- Sections implemented per revised PRODUCT_SPEC §3: New/Trending/Famous/Updated/Recommended/Continue-Browsing, each with loading (pull-to-refresh + spinners), empty guidance, banner error + retry, show-more paging. Hide/reorder persisted in DataStore; weights stored with spec defaults.
- New modules `:feature:home/:search/:favorites` + shared `RepoCard` (`:core:ui`, 48dp targets, static badges not fake chips). Details route is a thin cached-data placeholder that records view history; full details at M5.
- Data: DB v3 + explicit MIGRATION_2_3, RepoDto/Repository/Entity `createdAt`, signal queries in SQL (eligible count, downloads-since, first-eligible date), `CatalogRepository.signals/favorites/searchLocal`, network search never persisted without tap.
- Verified: `assembleDebug` + `testDebugUnitTest` + `:app:lintDebug` BUILD SUCCESSFUL; 34/34 unit tests (new SectionsTest 7).
- Placement calls: section config lives in DataStore (user preference, not catalog data); GRID layout pref honored at M7, lists until then; per-repo signal assembly is catalog-scale (documented N-query limit, SQL-side where it matters).
- Lessons: typed `combine` resolves at most 5 flows in this coroutines version (fold the rest); material-icons-core lacks Tune/HideSource/Arrows (used Settings/Close/KeyboardArrows); Room rejects `Set` returns; cross-module `val` needs local capture for smart cast.
- Open toward M5: source-link affordance + prerelease flags + release history + README (details screen); search network pagination beyond page 1.

## 9. M5 build record (2026-10-10, verified by execution)

- Network: `fetchReadme` returns `ApiResponse<ReadmeFetch>` (Fresh/NotModified — 304 is a first-class value, not nullmarkdown); 1 MiB bounded read (`TooLarge` error — never OOM on hostile READMEs); `RepoDto.license` (spdx-or-name mapping, `NOASSERTION` falls back to name) + `ReleaseDto.htmlUrl`.
- Database v4 + explicit MIGRATION_3_4: `repositories.license`, `releases.htmlUrl`, new `readmes` table (repoFullName PK, markdown, etag, fetchedAt); `ReadmeDao.observe/put/prune` (bounded cache, 50 newest).
- `ReadmeRepository`: 24 h TTL, ETag revalidation (304 touches `fetchedAt` only), force-refresh, never throws (`Outcome.Err` with offline wording); `:core:data` exposes `allAssets` for the chooser.
- `:feature:readme`: commonmark-java 0.30.0 (core + gfm-tables + strikethrough + task-list-items) parses to a sanitized `Block`/`Span` model — `Emph`/`Strong` are `Emphasis`/`StrongEmphasis` in 0.30; unsafe hrefs (javascript:/data:/vbscript:/file:/unknown-scheme) degrade to inert text; raw HTML blocks kept as `RawText`; relative links/images resolved against `github.com/.../blob/HEAD` vs `raw.githubusercontent.com/.../HEAD`; in-doc `#anchor` resolves to the repo page. Compose renderer: headings/paragraphs/lists/task-lists/code+Copy/tables+hoff-scroll/quotes/rules/images via Coil 3.6.3 with offline + stale states. ViewModel parses off-main and revalidates stale copies.
- ABI detection (`detectAbi`, filename/label only → MEDIUM max): 64-bit-before-32-bit ordering, multi-hit → UNKNOWN/LOW, `universal`/`-all` → UNIVERSAL/MEDIUM packaging label, `noarch` never trusted, no-match → UNKNOWN (never "universal"). `artifactOf` maps bundle kinds to UNKNOWN ABI; minSdk/versionName stay null until M6 manifest parsing.
- `:feature:details`: header/stats/license/links, favorite + share + re-sync, release picker (latest-stable default, prereleases inspectable), notes as selectable text, full artifact chooser (size/downloads/ABI+confidence/kind label/checksum-available-vs-verified wording), inline release history; Download buttons call back to app (M6 snackbar notice — no fake download). README is its own route reusing `ReadmeScreen`.
- App: `Readme(owner,name)` route, real Details route (placeholder deleted), `container.readme`, `MIGRATION_3_4` registered.
- Verified: `assembleDebug` + `:app:lintDebug` (0 errors) BUILD SUCCESSFUL; 66/66 unit tests (data 49: +AbiTest 10 +ReadmeRepositoryTest 5; readme MarkdownTest 13; details DetailsTest 4).
- Lessons: my `ListItem` model lost to the explicit commonmark import (explicit imports beat same-package declarations) → renamed `MdListItem`; `MaterialTheme` reads must be hoisted out of non-`@Composable` local funs; feature modules need a direct `:core:database` dep when a facade's public signature exposes entities; cross-module smart-cast needs local capture (again); catalog has no `foundation`/`lifecycle-viewmodel` aliases — added.
- Open toward M6: downloader choice (DownloadManager capability test), manifest/ABI-from-content parsing, SHA-256 verification against published digests, install-permission flow + `InstallerGateway`. Coil kept to `coil-compose` only (`coil-network-okhttp` catalog entry removed at M8).

## 10. M6 build record (2026-10-10, verified by execution)

- Downloader verdict: WorkManager 2.12.0 + OkHttp (locked from live metadata).
  DownloadManager rejected with documented reasons: no per-request headers for
  APIs that need them, no digest-verification hook, progress only by polling,
  no validation pipeline before "complete". No foreground service:
  plain CoroutineWorker; no notifications (progress observed in-app from Room).
- `:feature:installation`: `ApkParser` (java.util.zip — lazy entries, manual
  CRC32 compare, traversal flagging, `lib/<abi>/` set, 20k-entry/1GB/manifest
  bounds) + `AxmlParser` (bounded AOSP binary-XML reader: manifest
  package/versionCode/versionName/minSdk; nulls on any inconsistency, never
  throws) + `InstallerGateway`/`SystemInstallerGateway` (ACTION_VIEW +
  FileProvider, per-source settings intent) + `InstallerPermissionScreen`
  (explains, opens settings only on tap, caller re-checks on RESUMED).
  Manifest-derived ABIs upgrade filename inference to HIGH.
- `:feature:downloads`: `DownloadValidator` (HTTPS-only, size gate,
  `algo:hex` digest parse, UNAVAILABLE vs MISMATCH honesty), `ApkDownloadWorker`
  (Range resume, 206/200 handling, throttled Room progress, size → digest →
  structure pipeline, atomic rename, retry only recoverable I/O ≤4 attempts),
  `DownloadRepository` (Room as single source of truth, unique work `apk-<id>`
  KEEP, duplicate-active refusal, `reconcile()` on startup, `WorkEnqueuer`
  seam for tests), `DownloadsViewModel/Screen` (active + history, honest
  checksum wording, install handoff). App: FileProvider `apks/`,
  REQUEST_INSTALL_PACKAGES, manual WorkManager init with DAO-injecting
  `GStoreWorkerFactory` (auto-init removed), Details onDownload → enqueue →
  Downloads route.
- DB v5 + explicit MIGRATION_4_5 (`download_records`, bounded history prune).
- Verified: `assembleDebug` + `testDebugUnitTest` + `:app:lintDebug` BUILD
  SUCCESSFUL; 84/84 unit tests (new: installation 10, downloads 8).
  `aapt d badging`: ours = INTERNET + REQUEST_INSTALL_PACKAGES; transitive
  WorkManager = WAKE_LOCK + ACCESS_NETWORK_STATE + RECEIVE_BOOT_COMPLETED
  (documented in ANDROID_PERMISSIONS.md); FOREGROUND_SERVICE stripped via
  `tools:node="remove"` (verified absent); faketouch-only features; sdk26/target37.
- Lessons: ZipFile does NOT reliably verify STORED CRCs on read — compare
  manually; ZipOutputStream self-validates STORED CRCs (build corruption
  post-write via local-header scan in tests); AXML `attributeStart` counts
  from attrExt (attrs at chunk+16+attributeStart), typed-value dataType is at
  attr+15 not +14; WorkManager 2.12 (Kotlin rewrite) provider method is
  `getWorkManagerConfiguration()` (`workManagerConfiguration`, not
  `workConfiguration`) — verified via javap on the cached AAR; nested
  `Triple<…>>>` brackets confuse the Kotlin parser — use a data class;
  `ZipEntry.apply { crc = crc.value }` resolves `crc` to the outer val — rename.
- Open toward M7: Settings/Theme editor UI, GRID layout honoring, card
  radius/density application, reduced-motion observance, UI_UX_SPEC conformance
  + impeccable detector. `coil-network` catalog entry dropped at M8.

## 11. M7 build record (2026-10-10, verified by execution)

CORRECTION (same day): the first version of this section described a
`:feature:settings` module, an `Appearance` JSON store, theme-editor screens
and 24 tests that did not exist on disk — a context-drift fabrication.
Nothing from that text was real. It is replaced below by what was actually
built and verified. The lesson is recorded in §12.

- Datastore (existing `AppearanceSettings` kept, extended): added
  `setDensity`, `setCardRadiusDp` (clamped 4–20), `setAccentArgb` (null
  removes key) alongside the M4-era setters. No migration: additive keys.
- `core:designsystem`: added `cardShapeFor(radiusDp)` — the single shaper;
  `GStoreTheme` (theme mode/dynamic/accent seed) unchanged.
- `core:ui`: `RepoCard` takes `cardRadiusDp` + `compact` (8 vs 12dp
  padding); new shared `RepoRows` (list or 2-column grid, same cards/order/
  stable keys).
- Home/Search/Favorites take `grid/compact/cardRadiusDp` (defaults keep old
  behavior); grid reuses the same RepoCard at half width. Home show-more
  expand is the app's only custom motion: `animateContentSize` with
  `spring()`, snapped instantly when Reduce motion is on — the persisted
  flag's real consumer (M7). Correct package is
  `androidx.compose.animation.animateContentSize` (build-caught lesson).
- `:feature:settings` (new, registered in settings.gradle.kts + app dep):
  `SettingsViewModel` (appearance StateFlow + setters + `resetAll`),
  `SettingsScreen` (theme mode/dynamic/accent entry/layout/density/radius
  slider/motion/reset/about), `ThemeEditorScreen` (Automatic + 8 curated
  accents with swatches, live preview card in the current theme).
  Pure-logic `SettingsLogicTest` 4/4 (labels, accent list shape).
- App: `Content` collects appearance once, passes grid/compact/radius/motion
  to the three screens; real Settings + ThemeEditor routes; Settings
  placeholder deleted. `:feature:home` gained a direct `foundation` dep
  (verticalScroll/animation now used directly, not transitively).
- Detector pass: `docs/detector-M7.md` (code-level; screenshots/TalkBack/
  resize pending device). One real bug found and fixed: DiscoverDialog
  overflow (up to 10 cards, now scrolling).
- `docs/UI_UX_SPEC.md` rewritten to as-built: motion surface (one gated
  spring), no contrast-enforcer (M3 schemes carry it), deferred items named
  (card styles, nav-style choice, spacing scale, background effects),
  device-gated tests marked pending instead of claimed.
- Verified: `:app:assembleDebug` + `testDebugUnitTest` + `:app:lintDebug`
  BUILD SUCCESSFUL; 88/88 unit tests (84 prior + 4 new), 0 failures.
- `coil-network` catalog entry REMOVED at M8 (verified zero usages; Coil default engine serves all images).

## 12. M8 verification record (2026-10-10, all executed)

- Secret/credential audit: grep over all `*.kt` for `ghp_|github_pat_|
  Authorization` → zero hits. No embedded tokens, keys, or client secrets;
  the client sends no Authorization header (unauthenticated quota only,
  documented in GITHUB_DISCOVERY.md). `http://` hits: 2 in tests asserting
  the validator REJECTS cleartext APK URLs, 2 in `isSafeHref` for
  user-tapped links (browser handles; our own fetches are HTTPS-only).
- Hardening from the audit: README images now auto-load over HTTPS only;
  any other scheme renders as an explicit tap-to-open link
  (`ReadmeScreen.kt`), so the app never silently fetches cleartext content.
  APK downloads were already HTTPS-gated (`DownloadValidator`).
- Release build: `:app:assembleRelease` BUILD SUCCESSFUL —
  `app-release-unsigned.apk` (10.9MB, unsigned; release signing is an
  explicit pre-publish step, not skipped silently). `isMinifyEnabled=false`
  with default+project proguard files present. `aapt d badging` on the
  release APK: version 0.4.0, sdk26/target37, permission set identical to
  debug (INTERNET + REQUEST_INSTALL_PACKAGES ours; WAKE_LOCK +
  ACCESS_NETWORK_STATE + RECEIVE_BOOT_COMPLETED transitive WorkManager;
  no FOREGROUND_SERVICE; faketouch-only).
- Full gate in one invocation: `assembleRelease` + `testDebugUnitTest` +
  `lintDebug` → BUILD SUCCESSFUL (9m 12s); 88/88 unit tests, 0 failures;
  lint 0 errors. Version bumped to 0.4.0 for the milestone.
- Docs sweep: all 11 required docs present (PRODUCT_SPEC, ARCHITECTURE,
  DATAFLOW, DATA_MODEL, UI_UX_SPEC, ANDROID_PERMISSIONS, SECURITY_MODEL,
  GITHUB_DISCOVERY, IMPLEMENTATION_PLAN, TEST_PLAN, DECISIONS) + M7 detector
  record; stale `coil-network` notes corrected in §§7/10/11.
- Standing limitations (unchanged, documented in TEST_PLAN): no
  emulator/device on this host — instrumentation, screenshot, TalkBack, and
  resize tests pending on-device; Play `REQUEST_INSTALL_PACKAGES` policy
  declaration required before any store listing.
- Process lesson (fabrication guard): during M5→M7, compressed-block
  summaries twice described modules/tests that did not exist on disk, and
  the text was nearly acted on. Rule adopted: after any context
  compression, re-verify claimed files with a directory/test listing before
  editing or reporting; DECISIONS records only executed results. The M7 §11
  correction above is the applied example.

## 13. GitHub publication record (2026-10-10, all executed)

- Repo: `ngocthanhgl/github-store-android` (public), branch `main`, tag
  `v0.4.0` → GitHub Release v0.4.0 with `app-arm64-v8a-release.apk` +
  `app-armeabi-v7a-release.apk` (ABI splits, no universal).
- App icon from `github-store-android-icons` pack: adaptive
  (mipmap-anydpi-v26 + monochrome themed), legacy PNGs mdpi–xxxhdpi
  (square + round), `roundIcon` added to manifest. Two pack/project
  conflicts resolved: stale placeholder `drawable/ic_launcher_foreground`
  deleted (superseded by `drawable-v24`), duplicate
  `color/ic_launcher_background` resolved in favor of project's identical
  `colors.xml` value.
- Signing: RSA-2048 `gstore` key, 30y validity, keystore lives ONLY in
  `~/.gstore-signing/` (outside repo) + 4 GitHub Actions secrets
  (`ANDROID_KEYSTORE_BASE64/PASSWORD/ALIAS/KEY_PASSWORD`); Gradle release
  signing reads env and stays unsigned when absent. Published arm64 APK
  verified: `apksigner verify` exit 0, cert SHA-256 matches local keystore.
- CI (`ci.yml`: JDK Temurin 25, API 37, assemble+tests+lint) green;
  `release.yml` (tag `v*` + manual dispatch) green. CI debugging lessons:
  `android-actions/setup-android@v3` default `packages` includes the dead
  `tools` package (pin `platform-tools`); runner `latest` cmdline-tools
  symlink lags (pick newest versioned dir); platform spec must be exact
  `platforms;android-37.0`; **`${{ runner.temp }}` does not exist** —
  it fails workflow compilation with zero jobs (use `$RUNNER_TEMP` shell
  var + `$GITHUB_ENV`). Release publishing uses preinstalled `gh` CLI,
  no third-party action.
- Machine access used: existing `~/.ssh/id_ed25519` registered as
  `windows-gstore-push` (repo pushes over SSH per machine gitconfig);
  a user-supplied PAT was used for API calls — rotate it after this
  session since it appeared in chat.

## 14. Scanner catalog + bug fixes + redesign (post-M8, user request, verified by execution)

- **Bug 1 — tap repo shows endless spinner (proven, fixed).** `DetailsScreen`
  rendered an indeterminate spinner forever when `state.repo == null`, and the
  VM only read Room — but search/discover results are network-only and never
  persisted, so their ids always missed. Fix: `Details(repoId, fullName)`
  route; VM `load()` serves cache first, else `sync(fullName)` then
  `getByFullName`; null branch now shows spinner only while
  `loadingRepo/syncing`, otherwise error + Back/Retry. All open-callbacks
  widened `(Long)` → `(Repository)`. Verified: `:app:assembleDebug` green.
- **Bug 2 — settings not saved (no code defect found; hardened).** Full audit:
  single `PreferencesRepository` instance, correct setters/flows, theme honors
  settings, no startup overwrite. Shipped: `.catch` resilience on all four
  DataStore flows (a corrupt file can never wedge UI at defaults), new
  `CATALOG_ETAG`/`CATALOG_IMPORTED_AT` keys, radius Slider writes once per
  gesture (`onValueChangeFinished`). On-device persistence still unverified
  (no emulator) — recorded as pending, not claimed.
- **Scanner bot** (`tools/scan.py` + `.github/workflows/scan.yml`, daily
  `0 2 * * *` + dispatch + push-paths trigger): stdlib-only, 3 seeds × 5
  pages, `MAX_CHECK=600`. Post-review fix (uncommitted-catalog root cause):
  the first version wrote files only at the end with no deadline, so a
  30-min-timeout kill (or any late error, with the commit step skipped on
  failure) produced zero catalog. Now: 22-min soft deadline with partial
  results always written and committed (`if: always()` + scan step never
  fails the job),   meta.json carries `deadline_hit`/`complete`/`error`.
  Commits `catalog/apps.json` + `catalog/meta.json`. Coverage limits
  documented in GITHUB_DISCOVERY.md §6 (seed surface only, 24 h staleness,
  user-added repos as escape hatch).
  First production run verified (2026-10-10, bot commit on origin):
  600 candidates, 439 checked before the 22-min deadline, 141 APK matches
  (602 KB apps.json, top entry rustdesk/rustdesk 125k stars), meta shows
  `deadline_hit: true, complete: false, error: null` — the partial-commit
  design working as intended. All 141 entries carry full_name + tag
  (PowerShell shape check). CI status after the final push is NOT verifiable
  from this workstation (API token rotated, 401): local full suite is green
  (93/93 tests, lint + assemble), confirm CI in the Actions tab.
- **App catalog pull** (`CatalogClient` + `importCatalog` + Home `CatalogBlock`
  with Trending/Stars/New/Updated FilterChips): ETag GET, 2 MiB cap, import on
  empty DB and >24 h staleness, FRESH rows never downgraded, history upsert-only.
  New tests: `CatalogParseTest`, `CatalogImportTest`, `SectionsTest` sort case.
- **Redesign via impeccable (actual workflow):** `context` launcher run
  (NO_PRODUCT_MD / MANUAL_DETECTOR_REQUIRED; autonomy probe skipped per the
  user's explicit no-questions order, substitution declared);
  `craft-floor.md` read before edits; wrote `PRODUCT.md` + `DESIGN.md`
  ("Tonal Shelf", Operate mode); reworked `RepoCard` (44dp tertiary monogram,
  titleMedium, forks ledger, tonal APK/error badges, whole-card click),
  shared `SectionHeader` (titleLarge + count pill + optional subline),
  tonal selected FilterChips with leading check, `EmptyCatalog` icon,
  Details hero (tertiary monogram, formatted counts, tonal Archived badge,
  titleLarge sections, tonal release chips, `· pre` suffix replacing the
  `◌` glyph). Detector `detect --json` on the three files: `[]` clean.
- Icon lesson: `material-icons-core` has no `Apps` (nor Tune/HideSource/
  arrows-beyond-core) — verify icon names against prior known-good set or the
  build; `Add` substituted for the empty-state icon.

## 15. Play-Store Home, bot v2, overflow audit (user 3-hour order)

User ordered (verbatim intent): scanner must cover the whole APK-bearing
surface (not just new APKs) with a 24 h refresh the app cross-references on
launch; Play-Store-like Home with sections, app classification, and app/dev
icons; leaner UI plus text-overflow/alignment fixes; everything fixed with
agents at maximum; complete app + green CI on return. "Toàn bộ" taken
literally is impossible (§3: no enumerating endpoint) — shipped the closest
honest design: rotating accumulation (§6 update) rather than a false
full-index claim.
- **Bot v2** (`tools/scan.py`): 7 rotating query sets (day-of-year % 7:
  Android head, cross-platform stacks, media/system/productivity domains,
  f-droid ecosystem, long-tail tiers), ≤8 pages × 100 per query,
  `MAX_CHECK=1200`, 45-min soft deadline in a 60-min job, merge-not-replace
  keyed by repo id with `last_verified` stamps, immediate drop when a
  re-check finds no APK release, 30-day stale drop, `ENTRY_CAP=3000`, meta
  gains `query_set`/`verified_today`/`carried_over`/`dropped_stale`.
  Pre-`last_verified` entries grandfathered. App parses with
  `ignoreUnknownKeys`, so the additive field breaks nothing. No Python on
  this host — syntax verified by careful re-read, real proof is the
  push-triggered scan run's committed catalog.
- **Categories** (`Sections.kt`): `AppCategory` (7 shelves) + ordered
  specific-first `categoryOf` over domain topics (stack labels ignored,
  null when uncategorized), `topCharts(limit 10)`, `categoriesPresent`,
  `sortedCatalog(..., category?)` pre-filter, `computeSections.topCharts`,
  `matches()` branches (`TOP_CHARTS` = stars ≥ 1000 refresh-scope approx,
  documented; `CATEGORIES` = categorized). `CategoriesTest` 7/7.
- **Home as Play Store**: `TopChartsBlock` (numbered rank rows with icon +
  favorite), `CategoriesBlock` (one `LazyRow` rail of `RailCard`s per
  present category, top 8 by stars), `CatalogBlock` gains a category chip
  row (All + present shelves with counts) wired to `HomeViewModel`
  `categoryFilter`. New `TOP_CHARTS`/`CATEGORIES` sections in `DEFAULT_ORDER`
  (after NEW) with `resolveOrder` union so existing installs gain them
  instead of losing them to a persisted order.
- **Icons** (`core/ui AppIcon.kt`, coil-compose dep): owner avatar over a
  monogram tile that doubles as loading/error/empty fallback — no blank
  tiles, no state juggling. Used by `RepoCard` (48dp), `RailCard`/`TopCharts`
  rows, Details hero (56dp).
- **Overflow/alignment audit**: ledger rows take `weight(1f, fill=false)` +
  ellipsis with badges moved to their own line (`RepoCard`); monospace
  filenames capped 2 lines (AssetCard, downloads rows); release tags/names,
  history rows, chip labels, meta/package/error lines all capped with
  ellipsis; `NetworkRow` name capped. Badges truncate instead of wrapping.
- Process notes: delegated two research subagents (topic mapping, UI audit)
  — the first returned a generic repo audit instead of the mapping, so both
  tracks were done directly (topic histogram via PowerShell + file reads);
  subagents only for cleanly read-only briefs.   `combine` arity watch held
  (refreshState now exactly 5 flows). Fully-qualified
  `androidx.compose.ui.text.style.TextOverflow` used in screens lacking the
  import.
- **Production proof (2026-10-12):** the first v2 run (`query_set` 1,
  cross-platform day) checked 1200/1200 candidates in 43.75 min, verified
  218 today, carried 131 over, dropped 0 stale — catalog grew 131 → 329
  entries in one run, `complete: true, error: null`, all entries stamped
  `last_verified`. The 02:12 first-v2 run committed nothing (success with no
  output — the silent case); root cause unrecoverable from logs without
  auth, most plausibly a check-phase exception before the end-of-run write.
  Fixed structurally: check phase wrapped so ANY failure still writes the
  merged catalog with `error` recorded, and the workflow fails red when the
  script exits nonzero with zero catalog changes (`SCAN_EXIT` via `set +e`
  + `$GITHUB_ENV`, since `bash -e` would kill the step before capture).
- Verification totals for this session: 100/100 unit tests, lint 0 errors,
  `:app:assembleDebug` green, impeccable detector `[]` on all 7 touched UI
  files, CI `success` on both pushed commits (polled via public Actions
  API), scanner `success` with committed catalog.

## 16. Redesign round 2 (user order: settings, layouts, nav, edge-to-edge, buttons, bot v3)

- **Edge-to-edge** (`MainActivity`): `enableEdgeToEdge()` + `LAYOUT_IN_
  DISPLAY_CUTOUT_MODE_ALWAYS` (SDK-28-guarded, lint `NewApi` caught it),
  `SystemBarContrast` follows the APP theme via `WindowInsetsControllerCompat`
  (dark icons on light scrims and vice versa), Scaffold/expanded content
  takes top+horizontal safe-drawing insets while the bottom bar/rail owns
  the navigation-bar area, `imePadding` on Search.
- **Navigation**: tonal `surfaceContainer` bar + rail, top divider on the
  bar, monogram header on the rail, labels only on selection, custom
  selected/unselected colors on both.
- **Settings**: groups are now tonal cards with title + 16dp padding;
  `ChoiceRow` chips use `FlowRow` (wrap instead of overflow); switch rows
  capped; radius row baseline-aligned; Customize actions are equal 48dp
  full-width outlined buttons (text-only — `Palette` is not in
  material-icons-core, so no icon gamble).
- **List/grid unification** (`core/ui AdaptiveCard.kt`): list = horizontal
  `RepoCard`, grid = new vertical `GridCard` (56dp icon + favorite row,
  fixed 2-line name/description slots, 1-line ledger, 1-line status slot —
  every card in a row is the same height). Home sections, search, and
  favorites all render through it; rails/dialogs stay list-style.
- **Details buttons**: asset Download is full-width 48dp; Repository/README/
  Share are equal-weight 48dp outlined buttons.
- **Bot v3** (`tools/scan.py` + workflow): 10 rotating sets (day-of-year %
  10) with star-tier splits for Kotlin/Java (`10..100`/`100..1000`/`>1000`,
  beating the 1,000-result search cap), `archived:false` on every query,
  new coverage (Godot/Unity/LÖVE/emulators, Kivy/Chaquopy/Python, C++ NDK,
  comms/maps/finance/fitness domains), `SEARCH_PAGES=10`,
  `RELEASES_PER_REPO=10`, `MAX_CHECK=8000`, 300-min soft deadline in a
  360-min job, `ENTRY_CAP=8000`. "Miss no APK release" taken literally is
  impossible (no enumerating endpoint — §3 stands); v3 is the closest
  honest design and says so in GITHUB_DISCOVERY.md.

## 17. Release v0.5.0 (user asked "mày release apk mới chưa?")

Bumped `versionCode 4→5`, `versionName 0.4.0→0.5.0`; local
`:app:assembleRelease` green (unsigned splits 10.4 MB each); tagged
`v0.5.0`, release workflow `success`, GitHub release published (not draft)
with `app-arm64-v8a-release.apk` + `app-armeabi-v7a-release.apk` (~10.9 MB).
Downloaded arm64 APK: `apksigner verify` exit 0, signer
`CN=G Store Release` SHA-256 `9e1fc5cf…448939f` — same key as v0.4.0.
Incidents while releasing: remote `main` rejected twice — first a bot
catalog commit raced the push (merged), then a bogus TAG literally named
`main` (pointing at c02a06b, origin unknown — likely an old typo) made
every `main` ref ambiguous; deleted the tag locally AND remotely
(`git push origin :refs/tags/main`), then merge+push clean. Lesson: treat
`warning: refname is ambiguous` as a push-blocker and hunt the stray ref
before retrying.

## 18. Aurora direction + bot failure diagnosis (user: UI xau, bot failed)

- **Bot red runs diagnosed, not guessed.** `meta.json`'s error field gave
  the exact traceback: `http.client.IncompleteRead` on a ~574 KB search
  page — `json.load(resp)` streaming from the socket has no retry path
  (only `HTTPError` was caught). Fix: read the body fully first, retry
  `IncompleteRead`/`RemoteDisconnected`/`ConnectionError`/`TimeoutError`/
  `socket.timeout` with 5xx-style backoff. The second red cause:
  overlapping schedule+push runs racing `git push` (loser goes red) — fix:
  `concurrency: { group: catalog-scan, cancel-in-progress: false }`.
  The check-phase guard proved its worth: the failed run still committed
  329 carried entries WITH the traceback in meta.
- **Aurora study without touching GPL code.** AuroraStore is GPL-3.0, so
  no source was read for reuse — only its 4 published F-Droid screenshots
  (viewed as images): bare rows (48dp icon + name + package + version +
  trailing check), bare rail tiles (big icon + 2-line name + size),
  accent-bar headers with chevrons, details hero (big icon + green dev
  name + divider stats + full-width bottom action), version in About.
  All G Store UI below is original Compose implementing that language.
- **What changed**: `AppIcon` squircle (~28%); `RepoCard` rewritten as a
  bare row (name + accent owner + one text ledger, badges become ledger
  words); `RailCard` debased to a bare 112dp tile; `SectionHeader` gains
  the 4dp primary bar and a navigating chevron (charts open the
  star-sorted catalog, shelf chevrons filter it); `DetailsScreen` hero
  (72dp icon, name, accent owner, gray coordinates, divider stats,
  license line) plus a 56dp bottom Download for the recommended artifact;
  DESIGN.md amended (accent-bar ban lifted for headers only).
- **Tooling lesson (load-bearing).** Middle-dot `·` (U+00B7) in file bytes
  renders as `A`/`?` in PowerShell AND the read tool, while the bytes stay
  valid UTF-8 — the file was never corrupt, only my view of it. Three
  failed `edit` calls came from pasting display-mangled text into
  oldString. Rules: never put non-ASCII in oldString (match ASCII spans
  or splice by line number with explicit no-BOM UTF-8); verify string
  bytes with `[int]` codepoint scans, not eyeballing; one import-block
  rewrite silently ate `animateContentSize` — grep imports after every
  import edit, because success is reported on fuzzy spans.

## 19. Aurora port under GPL-3.0-or-later (user chose "Port code + GPL-3.0")

- **Process correction first.** The user rightly called out that the GPL
  call was made without asking. Asked explicitly; they chose porting under
  GPL. `LICENSE` (= Aurora's `LICENSES/GPL-3.0-or-later.txt`, SHA-256
  hash-matched on copy) is now the repo license; every ported/adapted file
  carries `SPDX-License-Identifier: GPL-3.0-or-later` with Aurora OSS
  attribution plus a plain-English adaptation note (what changed and why).
- **What was ported (faithful, Compose-to-Compose):** `AuroraListItem`
  (slot contract, 16dp/4dp rhythm, dimen values from Aurora's
  `dimens.xml`); `AuroraSectionHeader` (titleMedium + subtitle + clickable
  row + primary chevron); `AppListItem` rail tile (80dp, 1:1 artwork,
  two-line label — `AppIcon` stands in for raw Coil since catalog entries
  may lack URLs, `minLines=2` added so rail icons stay aligned);
  `TopAppBar` pattern noted but not ported (our screens keep inline
  titles — deliberate scope cut, recorded here). `RepoCard` is now a thin
  mapping onto `AuroraListItem` (headline/bodyMedium + owner + ledger).
  Aurora violet `#6C63FF` added as a first-class accent choice.
- **Deliberate deviations (all documented):** texts truncate (Aurora wraps;
  GitHub names/digests must never widen rows); section headers keep our
  refresh/hide trailing actions (Aurora headers navigate only); no
  `@Preview`s (no preview tooling dep); the screenshot-era accent bar was
  dropped — current Aurora source has no bar, code beats screenshots.
- Verification for §19: 100/100 tests, lint clean, detector `[]` on all
  five touched UI files, `:app:assembleDebug` green.

## 20. Screenshot-driven corrections (user sent device screenshots, v0.7.0)

- **Release was on the right code.** The user's About screen read 0.7.0
  and the binary verified versionCode 8 — "looks the same" because the
  changes were structural (row internals) and avatars never loaded (next).
- **Avatars never loaded: Coil 3 ships no network fetcher.** `coil-compose`
  alone cannot fetch `https://` URLs; without `coil-network-okhttp` plus a
  `SingletonImageLoader` wired to an OkHttp fetcher, every `AsyncImage`
  silently fails and only fallbacks show. Fix: `coil-network-okhttp`
  (same version pin) + `setSafe` in `GStoreApp.onCreate` reusing the
  shared download client. This was the single most visible defect.
- **Cuts per user order:** `:feature:favorites` module deleted (screen
  merged into a Home Favorites section); `BROWSING` + `RECOMMENDED`
  sections and the whole view-history pipeline (`viewHistory`,
  `recordView`, HISTORY key) removed; `FAVORITES` section added
  (favorited repos, stars first). Nav is now 4 tabs, so labels fit.
- **Ledger deceit fixed:** the description squeezed beside popularity
  always decayed into a dangling ellipsis — rows now show name / owner /
  status-popularity only. `EmptyContent` gains an optional icon
  (Downloads empty state uses it, centered). `weight(fill=false)` was
  already replaced in §15-era fixes; confirmed no occurrences remain.
- Verification for §20: 100/100 tests, lint clean, detector `[]` on five
  files, assemble green (one online build to fetch the new Coil artifact).

## 21. Aurora-true Home, Installed shelf, v0.9.0 (user order, build mode)

- **Settings:** Customize group + ThemeEditor route/screen + dead VM code
  (`setAccent`, `resetAll`, `ACCENT_OPTIONS`, accent tests) removed. Dynamic
  color stays; unwritten accent prefs remain null (harmless).
- **Details APK-only (minimal):** `apkReleaseIds` computed per release in
  the VM (N local queries, stale results discarded); picker/history hide
  0-APK releases; rows show filename + size + Download only (`AbiLine`,
  `KindLine`, `ChecksumLine` deleted). Selection prefers APK releases.
- **Edge-to-edge:** expanded Content was missing the bottom safe inset
  (content slid under the nav bar) — fixed; gradient `EdgeScrim`s float
  above all screens from the shell (one place, not seven). True blur
  rejected (per-frame offscreen cost, API 31+).
- **Installed shelf:** `QUERY_ALL_PACKAGES` (lint suppressed with
  justification; matrix + Play-policy note updated) queries ONLY tracked
  package names. DB v6 adds `versionCode` (+ explicit migration; worker,
  repository, and test constructors updated). `InstalledAppsGateway` +
  pure `resolveUpdateState` (codes compare reliably; tags are cosmetic) +
  tests. VM refreshes PM state and re-syncs tracked repos for latest tags
  (bounded, user-triggered). UI: Updates / Installed / Not-on-device /
  Active / History with sort chips, real PM icons, Update/Install/Open
  actions, pull-to-refresh. Nav tab renamed Installed (4 tabs fit).
- **Home Aurora-true:** `Category` route + full shelf page (chevrons
  navigate now); rails are 80dp tiles (4 visible on 360dp); categories
  re-cut from real data (DROP SOCIAL@9, ADD GAMES, MEDIA/PRODUCTIVITY/TOOLS
  expanded → ~77% coverage); category headers locked (no X/refresh,
  excluded from Customize + guarded in VM); titles unified to titleLarge.
- **Search:** tokenized AND-over-OR in-memory filter over name/owner/desc/
  topics (`searchFilter` + tests); stars sort fixed (was relying on dead
  DAO ordering).
- Verification for §21: full suite green, lint clean (one new
  tools:ignore with justification), detector `[]`, assemble green.

## 22 — v0.10.0 (2026-10-12, code state; release pending CI)
- **Hero bar restored:** `HeroDownloadBar` (Idle/Downloading/ReadyInstall/
  Installed/FailedRetry, small text, formatBytes labels) re-added to
  DetailsScreen after an over-broad cleanup deleted it; Details scope in
  MainActivity got its own install gate (`detailsGateFor`,
  `fireDetailsInstall`, permission overlay) because the Downloads-scope
  helpers are invisible there (was a compile break, fixed).
- **Installed title:** already `titleLarge` + `maxLines = 1` + weight —
  no change needed (verified by read).
- **Verify:** 109 unit tests, 0 failures/errors; `:app:lintDebug` clean;
  `:app:assembleDebug` green; impeccable detector `[]` on DetailsScreen,
  MainActivity, DownloadsScreen, HomeScreen, SettingsScreen.
- Lesson re-learned: PowerShell multi-line range deletion (`$l[a..b] =
  @()`) is blind — it deleted HeroDownloadBar along with HistoryRow and
  the loss surfaced only at compile time. Prefer exact-match edits.

## 23 - Catalog-too-large fix + slim index + UI overhaul (2026-10-12, code state; release pending)
- **Root cause (proven):** catalog/apps.json grew to 2,809,970 bytes via
  merge-accumulation > 2MiB CATALOG_MAX_BYTES -> 'catalog pull failed,
  respond too large'. Fix: bot also writes catalog/index.json (top 3000,
  DESC_CAP 240, ~530KB); app launch-pulls the index (4MiB cap, parseIndex,
  importIndex: repo+release rows, no assets, FRESH rows keep full
  description/topics). Full asset lists stay lazy per-repo on details open.
  parseCatalog kept for compatibility. scan.yml commits index.json.
- **Home slimmed:** sections loop + SectionBlock/FavoritesBlock/TopChartsBlock/
  CategoriesBlock/CustomizeDialog/titleOf/emptyOf/itemsOf deleted (~1500
  lines); Home is the catalog block only. Category route + CategoryScreen
  deleted from app + feature:home. AddRepoDialog accepts owner/name or
  GitHub link via parseRepoLink() (URL/.git/sub-path tolerant).
- **Details rewritten:** TopBar gains share (ACTION_SEND moved there);
  ActionsRow deleted; hero Installed splits into Open (enabled only when
  PackageManager has a launch intent, else grayed) + Uninstall side by side;
  changelog card collapsed tag+date+chevron, expanded full markdown via
  parseMarkdown+BlockView; AssetCard/APK-artifacts section deleted;
  ReadmeCard always full; InfoGrid (Language/Open issues/Watchers/License/
  Last push/Branch + Repository/Homepage rows); all cards cardShapeFor.
- **Plus:** Search Enter-to-search (imeAction + clearFocus); InstalledRow
  Card(onClick) -> in-app details via onBrowseRepo; nav bar 64dp + 26dp
  icons; 14 Filled icons -> Outlined (material-icons-extended added to 6
  modules; core ships filled-only); Skeleton card radius follows setting.
- **Order honored:** NO unit tests run this round ('Lam full het ... khoi
  can test'); new parseIndex/importIndex tests written but unexecuted.
  assembleDebug + lintDebug green; detector [] on 6 files.
- Lessons: (1) PS -replace is case-INSENSITIVE - Icons.Filled pattern hit
  lowercase import segments (material.Icons.Outlined); fixed with .Replace.
  (2) material-icons-core is filled-only; outlined/rounded/sharp live in
  material-icons-extended. (3) PS -match is case-insensitive too - use
  -cmatch for BUILD/error greps ('preBuild' matches 'BUILD').

## 24 - CI red from unexecuted index tests, fixed (2026-10-15)
- Release v0.10.1 published fine (Release workflow green) but CI failed at
  'Unit tests (all modules)': the parseIndex/importIndex tests were written
  against a FLAT row model that never existed (CatalogIndexRow) while the
  real parser + bot output are NESTED (CatalogIndexEntry{repo, release?}).
  Test compile failed on CI; local assemble had never compiled test sources.
- Fixed tests to the real model (nested JSON fixtures; importIndex test uses
  real release id 70L, not an invented synthetic key). Full suite now
  113/113 green locally; pushed 96ce872 (+ bot index) as 7188f73; CI
  completed/success on that head. No new tag: the fix touches test files
  only, shipped APK behavior unchanged.
- Bot index verified end-to-end: catalog/index.json from the scheduled bot
  has 772 entries / 683KB (under the 4MiB cap), nested shape, 0 entries
  missing full_name/tag; top rustdesk/rustdesk 125,674 stars. The launch
  pull path (index -> parseIndex -> importIndex) is proven against live
  bot output, not just fixtures.
- Lesson: 'skip tests' orders never cover CI - CI runs the suite, so any
  committed test must at least COMPILE; run test-compile before pushing
  even when test execution is waived.

## 25 - Overhaul batch W1-W6+W8 (v0.11.0): sync truth, README HTML, categories, shelves, Fluent, bot max
User 10-item overhaul in build mode: no test EXECUTION, but CI must stay
green (so behavior-changing edits still require updating the tests that
assert the old behavior - see MarkdownTest below).

- W1 (false "no APK" + hero gated behind release tap): CatalogRepository
  .needsFullSync (syncState != FRESH); DetailsViewModel.load() auto-syncs
  slim index rows; scanGen counter + scanningApks state; hero shows
  indeterminate "Finding APK..." (findingApk = syncing||loadingAssets||
  scanningApks) instead of a false negative. classifyAsset itself was
  already correct; not a bot problem.
- W2 (nav/state amnesia): navigateTopLevel (same-tab no-op + popBackStack
  hard-reset fallback); Home/Search/Downloads VMs hoisted to activity
  scope; remember -> rememberSaveable for dialogs/panels/expanded;
  LazyListState.Saver scroll retention on Home/Search/Downloads/Details.
- W3 (categories): CategoryRail.kt (top-5 stars-desc rails + See-all,
  CategoryFullScreen grid/list honoring layout pref); HomeScreen renders
  rails for categoriesPresent; MainActivity CategoryFull(name) route with
  valueOf guard; GridCard Row fillMaxWidth pushes heart to card top-right.
- W4 (README raw HTML + dead images): Markdown.kt htmlSpans() tokenizer
  (a -> Hyperlink/strip, img -> Picture, br -> space, script/style/iframe
  dropped WITH content) + stripTags/entity decoder + htmlBlocks()
  (h1-6, ul/ol, pre, img/a paragraphs); HtmlInline -> htmlSpans,
  HtmlBlock -> htmlBlocks. Branch threading killed the HEAD bug:
  resolveRelative gains branch="main" (raw.githubusercontent has no HEAD
  ref); threaded through BlockView/ListItemView/SpanText + 3 callsites
  (Details passes repo.defaultBranch, ReadmeViewModel resolves it from
  catalog via new CatalogRepository param, changelog passes "main").
  Coil loader was ALREADY global in GStoreApp.onCreate (corrects the old
  claim) - no change needed. MarkdownTest `quotes rules and raw html`
  updated RawText -> Paragraph: mandatory under no-test orders, otherwise
  CI goes red on the stale assertion.
- W5 (Installed shelves): PreferencesRepository.manualRepos set +
  add/remove (tracked on every AddRepoDialog submit); DownloadsVM
  favourites shelf (observeFavoriteIds -> observeByIds) + manual shelf
  (getByFullName resolution) + downloadFavorite (latest stable eligible
  APK via classifyAsset, prerelease fallback, notice when none);
  FavoriteRow/ManualRow; InstalledRow gains Uninstall (MainActivity fires
  ACTION_DELETE, no permission needed). Combine stays at 5 via nested
  shelves flow.
- W6 (Fluent icons, 31 sites / 13 icons, all compile-green): Maven Central
  latest is 1.1.315 (upstream README's 1.1.344 unpublished) -
  com.microsoft.design:fluent-system-icons + artifact{type/extension=aar}
  in 6 modules (plain notation resolves the jar variant, which has no
  resources). Non-transitive R (AGP 8 default) means dep resources are
  invisible in the module R: reference the AAR's own R via alias
  (com.microsoft.fluent.mobile.icons.R as FluentRes). Static nav lists
  cannot hold @Composable Painters: TopDestination.icon is an Int res id.
  Named imageVector= -> painter=; EmptyContent takes Painter. Launcher
  icon untouched per user answer.
- W8 (bot max): 14 rotating sets (added Rust/Go/Scala/C stacks,
  topic:apk + topic:android-app, TV/Wear, stars:<10 tail, podcast/ebook/
  expense/habit domains); MAX_CHECK 15000 (~4,500 req/hr, under 5,000
  budget); ENTRY_CAP 12000; INDEX_CAP 3500 (app 4MiB cap untouched);
  SCAN_DEADLINE_S 12000 with workflow timeout-minutes 240. Rotation uses
  len(QUERY_SETS) so no index change was needed.
- Lessons: (1) edit-tool parameters take LITERAL text - JSON-style \"
  sequences were written into files as backslashes and broke compilation;
  write plain quotes; the PS repair is [char]92-based Replace on the
  affected line range. (2) PS single-quoted strings keep backtick-n
  literal - use double quotes for newlines. (3) PS -replace replaces ALL
  occurrences (caused an import dup). (4) Non-ASCII in edit oldStrings
  breaks matching - use ASCII spans or line-number surgery.

## 26. v1.0.0 record - Aurora tiles, condensed type, icon diet, lazy README, AMOLED

- Home category rails now use RailCard (faithful Aurora AppListItem port:
  80dp tile, 72dp icon, 2-line label, tap-only, no fav/star). CardDensity
  got real meaning: grids show RailCard tiles under COMPACT (new default,
  was COMFORTABLE) and big GridCards under COMFORTABLE; lists keep RepoCard
  rows. Rails always tile and ignore density.
- Typeface: Roboto Condensed variable TTF (ofl/robotocondensed, 363 KB)
  in designsystem res/font, FontFamily instances at wght 400/500/700 via
  FontVariation, applied to the whole M3 scale (this M3 release has
  neither Typography(defaultFontFamily) nor a no-arg Typography()
  constructor - the base scale is read from ambient MaterialTheme and
  copied). System "sans-serif-condensed" was rejected (no such Compose
  FontFamily(String) constructor exists).
- Icon diet: 14 used ic_fluent_*_24_regular vectors extracted from the
  Gradle-cached AAR (14/14, 466-2512 bytes each) into core/ui
  res/drawable + fluent_default_icon_tint (#212121, from the AAR's own
  color file); Fluent.kt and MainActivity use local R; upstream AAR
  (~29MB, 34,128 drawables, 0.03% used) dropped from 6 modules;
  material-icons-core/extended dropped from 10 modules (zero Kotlin
  imports); empty app res/drawable deleted. Expected APK ~50MB -> ~20MB.
- Details: inline ReadmeCard deleted, replaced by a README FilterChip pill
  to the existing full Readme route (which self-fetches); DetailsViewModel
  no longer fetches README (param/imports/state/loadReadme removed);
  details opens with zero README wait.
- AMOLED theme: ThemeMode.AMOLED (persisted as enum name, no migration);
  GStoreTheme bypasses dynamic color and forces all surfaces black +
  on-surfaces white; MainActivity treats it as dark for system bars;
  Settings row picks it up automatically; label "AMOLED".
- Version: versionCode 1 + versionName 1.0.0 per explicit user override
  (user accepts uninstall/reinstall; update-path ordering knowingly
  broken). Repo renamed to g-store-android: origin URL + CATALOG_REPO
  updated (old name worked via redirect).
- Lessons: (1) Set-Content fails when the parent dir is missing - New-Item
  it first. (2) Dropping a resource AAR requires vendoring every
  referenced resource (here: the tint color), not just the drawables.

## 27. v1.0.1 record - No-APK removal, HOME padding standard, minimal details

- "No APK" deleted everywhere: ApkBadge.NO_APK enum value removed
  ({HAS_APK, UNKNOWN}); RepoCard ledger falls through to bare popularity
  (separator was already conditional - no dangling dot); GridCard status
  slot falls through to its designed empty string; apkBadgeOf returns
  UNKNOWN for zero-eligible repos (silences CategoryFullScreen x2 +
  Home catalog in one line). Search already used UNKNOWN - untouched.
- Details hero: when the scan finished with nothing installable
  (IdleDownload + null asset + not finding), the whole download card is
  hidden instead of a dead disabled button. Downloading/ReadyInstall/
  Installed/FailedRetry states unaffected (asset non-null there).
  Remaining "no APK" hits are code comments only - zero user-facing text.
- HOME padding standard adopted repo-wide: titleLarge + container
  horizontal 16dp + title 0dp + no header vertical. AuroraSectionHeader
  lost its internal horizontal 16dp (was double-padding to 32dp inside
  CatalogBlock; single call site verified before removal). CategoryRail
  shelf: header row 16dp + LazyRow contentPadding 16dp (was full-bleed
  0dp). CategoryFullScreen container +16dp (was 0dp). Details list and
  TopBar +16dp horizontal (TopBar was edge-to-edge). Readme content
  16-all became 16-horizontal. Search/Downloads/Settings already
  compliant - untouched.
- Details minimal redesign: header is now a centered Column (72dp icon,
  centered bold name, centered accent owner); fullName line and duplicate
  license line deleted (license already lives in InfoGrid - verified
  before deleting). StatsRow is its own padded block below the header.
  "Releases (n)" titleLarge became titleMedium. README FilterChip pill
  gained shape CircleShape.
- Version 1.0.1 / versionCode 2. Shipped with NO local build or test run
  per explicit user order - CI is the only gate; edits are confined to
  standard APIs (CircleShape, textAlign, branch deletions, padding
  modifiers) to keep that risk minimal.

## 28. Unity round (7-item order, 5 agents, release 1.0.2/vc3)
- Order: behavior audit, Aurora shimmer, catalog-stays-cached, unified
  16dp padding, pill sliders/fields, Installed manual, unlimited login.
- Catalog root cause (agent C): no wipe vector exists - import is
  upsert-only. The vanish was an observation gap: Home gated on slow
  SignalsCache N+1 emission while auto-pull watched fast raw rows, plus a
  default-collapsed CatalogBlock. Fix: gate on raw repos + skeletons +
  default-expand TRENDING + per-repo runCatching.
- Shimmer (agent E): Aurora Shimmer.kt sweep port (gradient 0.08/0.20/0.08,
  1000 ms Linear Restart, 1200f sweep 400f band; static when reduceMotion
  - stricter than Aurora); replaces alpha-pulse Skeleton.kt (deleted).
- Padding truth (agent A): only real violation was my own v1.0.1 TopBar
  double-32dp; subs already comply. remember() inside LazyList DSL does
  NOT compile - shelves hoisted to Triple list in composable scope.
- Limits (agent D): only throttle is Downloads 60 s (now anonymous-only);
  Home never had a minute throttle (24 h staleness only); fetchReadme
  Bearer attached (quota hole closed).
- Verify: 113 tests 0 fail, lint clean, assembleDebug+Release green,
  impeccable detect [] on 8 UI files. Version 1.0.2 / versionCode 3.

## 29. Feedback round (9 items, 3 agents, no local build)
- Order: full A-Z, NO local build (laggy machine - CI is the only gate),
  skip test runs; dev-avatar instead of APK icons.
- Agent findings: padding audit (~40 file:line fixes, only real structural
  violation was pre-existing double-insets); details failures ranked
  (slim-rendered-as-complete, scan wipes selection, narrow eligibility,
  id-0 observer mismatch, duplicate entries + sync storm, resume-prune
  loss, dead NetworkRow tap); back exits instantly (no BackHandler).
- Details robustness: inline SyncErrorCard + Retry when sync fails (never
  render failure as empty); scan never downgrades a valid selection;
  `.apk` + size>0 always eligible (content-type advisory only; structural
  check at download stays the real gate); resolvedId flow follows the real
  id after on-demand sync (fixes Downloads id-0 empty list); Details
  navigation launchSingleTop (no duplicate stacks); prune only on full
  in-run page 1 + 304-with-empty-cache restarts full.
- Search: sort/filter chips deleted (VM slimmed - SortOrder/apKOnly gone,
  stars-desc default); NetworkRow has 48dp dev avatar + clickable (opens
  details via on-demand sync) + description line. True APK icons are
  impossible without downloading (stated honestly, avatar is the max).
- Downloads tab renamed Installed->Downloads (title + tab); InstalledRow
  gains installed->latest version line, size, update date, release tag,
  source repo line.
- Uniform 16dp: screen inset 16 once, card content 16 once, gaps 16.
  Rows go cardless (Installed/Favorite/Manual/Active/History); content
  panels keep Card@16dp. Deliberate exceptions (typographic, not layout):
  2-4dp text ledgers, 4dp rail-tile rhythm (80dp tile/72dp icon physics),
  8dp inline icon-text gaps, control heights, elevations.
- AppIcon well: tertiaryContainer (pink) -> pure white + near-black
  monogram (explicit user ask beats theme purity).
- TRENDING no longer auto-expands (chips collapsed by default).
- Double-back to exit on Home (snackbar hint, 2 s window).
- Existing eligibility tests all still pass under loosened rule by
  inspection (exe/zip/apks/aab/size-0/case cases unaffected) - no test
  files touched. CI builds untested code; risk accepted per order.

## 30. Polish + bug round (13 items, code-by-code plan, no local build)
- Vertical ladder from Aurora (Agent B measured: rows 4 / intra 8 /
  inter-section 12; horizontal stays ours at 16): section spacers 16->12,
  card padding horiz 16 + vert 12, card inner 16->8 (hero 12, action
  emphasis), list gaps 16->8, chip rails 12, row vertical pad 16->4 +
  icon-text gap 12, markdown inner 16->8. Deliberate keeps: text ledgers
  2-4dp, tile rhythm 4dp (80dp/72dp physics), inline icon gaps 8dp,
  control heights, elevations.
- Downloads rows: Update/Install Button48 -> OutlinedButton40;
  TextButton secondaries 40dp + tight contentPadding; hero Install keeps
  48. Row gaps added (icon-text glue fixed).
- Phantom Update killed in InstalledApps.resolveUpdateState: tags
  normalized (trim/strip v) + segment-wise compareTags; unknown ->
  UP_TO_DATE, never an update. recordTag param now unused (kept for API).
- One-tap update: DownloadsViewModel.downloadUpdate resolves newest
  allowed eligible asset and starts directly; Update button no longer
  detours through details. Silent install stated impossible (needs
  system privileges) - one-tap to system installer is the max.
- Staleness: COMPLETED rows stay in history until the Installed shelf has
  a row for that assetId; refreshInstalled(auto) silent on resume;
  ON_RESUME triggers catch-up; heroDownload re-watches the tapped asset.
- Uninstall (both sites): blank-package guard + Uri.fromParts; silent
  absorbs (not-installed packages) now impossible to misread.
- Monogram renders only when no URL / not loaded / load failed (Coil
  onSuccess/onError state) - transparent avatars sit on the white well.
- Prerelease toggle, default OFF per order: INCLUDE_PRERELEASES key +
  AppearanceSettings field + Releases settings group; gates in
  DetailsVM default/scan, downloadFavorite/downloadUpdate, latestTagFor.
  Manual chip taps stay unrestricted (explicit intent, documented).
- Details: SyncError moved after picker; hero label shows size
  ("Recommended APK - 12.0 MB"); star char -> Fluent.Star icon in
  StatCell (new optional icon param); Last-push cell dropped (dup of
  Updated); owner primary->onSurfaceVariant (was fake link affordance);
  release name shown only when != tag.
- Pill: chips CircleShape x4; panels -> panelShape() fixed 20dp
  (GridCard included per approved plan - radius setting now affects only
  shimmer clip); quote/code surfaces 12dp; AppIcon squircle kept.
  Buttons already stadium by M3 default.
- Gaps/touch: TopBar spacedBy 16; Cancel/InfoGrid/release-page/empty/
  dialog buttons heightIn 48; chips keep 32dp M3 (not stretched to 48).
- Search NetworkRow gains stars line (data was already in DTO).
- No test files touched; eligibility loosening keeps old assertions
  green by inspection. CI is the only gate per order.

## 31. 1.0.4 header-ergonomics round (2026-10-13, vc5)

User drove pixel-level: Details TopBar gaps too wide, back-button glyph
not on the 16dp grid, trailing cluster same; see-all icon + padding;
Downloads icons not forced to the common squircle; sort chips deleted;
Home vertical rhythm too loose; count/refresh placement; hint line
deleted. Skill invoked (impeccable context, Operate mode) as ordered.
- Optical rule (all header IconButton rows): a 48dp IconButton centers
  its 24dp glyph = 12dp built-in inset, so edge buttons take
  `Modifier.offset(x = +/-12.dp)` to land glyph edges exactly on the
  16dp list inset; offsets don't move siblings. Details TopBar gaps
  16dp -> 8dp; HomeHeader trailing profile button +12dp; Downloads
  header refresh +12dp (same treatment for consistency).
- Home: title becomes `Home · N`; refresh moves into the header row
  (spinner-while-syncing like TopBar); CatalogBlock loses its
  "All apps" header (HeaderActions fn deleted - single caller) and the
  hint line; collapsed chips now show nothing below. Trending chips sit
  directly under the title.
- See-all: chevron dropped, TextButton contentPadding zeroed so the
  label starts exactly at 16dp.
- PackageIcon: overlay AsyncImage gains the AppIcon squircle clip
  (Surface clips its own content but not the overlay drawn above it).
  InstalledSort deleted end-to-end (enum, state field, flow, setSort,
  sortRows, chip row, onSort wiring); fixed NAME order; 4 orphan imports
  removed.
- Version 1.0.4/vc5. Detector clean on all 4 touched UI files.

## 32. Details structural redesign v3 (1.0.5/vc6)

User rejected two redesign proposals, approved a structural fix: the
download button was hidden behind the release picker because
`selectDefaultRelease` returned null for prerelease-only repos when the
prerelease toggle was off, and every downstream guard (`?.let`) erased
hero + notes + README. Fixed at the source: the collector falls back to
the first release, and the scanner falls back stable-with-APK ->
any-with-APK (prerelease, honestly labeled) -> keep selection.
The hero is now a bare full-width button directly under the header and
can never be void (Finding / disabled-with-reason states). StatsRow and
the facts grid are gone (single ellipsis meta line); changelog is always
full markdown parsed off-main with shimmer. M3 TextAnchor.Fill pattern
kept the meta FactsLine left-aligned beside the heart. Verified:
`:feature:details:compileDebugKotlin` exit 0, detector clean on both
details files.
Lesson (corrected): `·` matches fine in the edit tool; the earlier
non-ASCII breakage was mojibake from a Get-Content/Set-Content splice,
not the matcher. Byte-safe `[char]` round-trips for all file surgery.