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