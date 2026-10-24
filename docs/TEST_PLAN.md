# TEST_PLAN — Forkly

No live-GitHub dependence in unit tests (recorded fixtures + fake API).
Coverage targets set at M2; this plan defines scope and fixtures.

## 1. Layers

- Unit: scoring formulas (§PRODUCT_SPEC 3), ABI detection, eligibility,
  pagination/checkpoint math, backoff/jitter bounds, filename sanitization,
  Markdown sanitize mapping, preference validation.
- Integration: repository+DTO serialization, Room DAOs (in-memory), DataStore
  round-trip, download state machine (enqueue→progress→verify→done/cancel/fail),
  retry/cancel semantics.
- UI: Compose tests for section states (loading/error/empty), artifact chooser,
  permission-explanation flow, theme persistence across recreation; nav tests;
  a11y checks (touch target, content-desc, contrast); adaptive tests
  (compact vs expanded, resize reflow for API-37 resizability).
- Platform: `canRequestPackageInstalls` branching (Robolectric), install-intent
  shape (URI scheme, MIME, flags), notification-permission fallback.
- Manual: API 26→37 matrix, offline/rate-limit simulation, large README scroll
  jank profile, startup trace.

## 2. Fixtures (checked into `core:testing`)

No-release repo; release-without-APK; universal APK; per-ABI APKs
(arm64/x86_64/armeabi); misleading names (`app.apk.zip`, `setup.exe` renamed);
multi-asset release; prerelease-only; `digest:null` vs digest-present; malformed
Markdown; 2 MB+ README; 403+`Retry-After` + 429 sequences; interrupted download
(part file + ETag); unknown-ABI; zero-storage; install-permission-denied.

## 3. Reporting

M8 verification report lists every suite, result, and any check that could not
run with reason (emulator absence, device gaps). Failing suites block release.
