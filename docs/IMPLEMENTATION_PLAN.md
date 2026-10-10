# IMPLEMENTATION_PLAN — milestones, dependencies, acceptance

Toolchain lock at M2: fix `JAVA_HOME` (currently invalid `E:\jdk`; real JDK at
`...\Microsoft\jdk-25.0.3.9-hotspot`), install `platform-37` + `build-tools;37`
via sdkmanager, pin AGP/Kotlin/Compose-BOM proven against compileSdk 37
(verify against AGP release notes at execution; nothing fabricated here).
Verify commands below are run, never claimed.

## M0 — Audit & planning (this doc set) ✅ acceptance: 11 docs + archify HTML exist, decisions logged, review stop.
## M1 — Architecture & dataflow sign-off

Deps: M0. Diagrams reviewed; contracts frozen. Acceptance: no contradiction
issues open in DECISIONS.md. Verify: doc lint pass.
## M2 — Foundation

Gradle+version catalog, `app`, core modules (model/ui/designsystem/common, then
network/database/datastore), nav shell, DI, theme tokens. Acceptance: builds,
launches, navigates empty destinations on API 26 + 37 emulators.
Verify: `./gradlew assembleDebug lintDebug`.
## M3 — GitHub catalog

Discovery, releases, classification, pagination, cache, rate-limit handling.
Acceptance: seeded + user-added repos persist; airplane-mode shows cache.
Verify: `./gradlew :core:network:test :core:database:test` with fixtures.
## M4 — Home & search

Sections per PRODUCT_SPEC §3 formulas, filters/sorts, favorites.
Acceptance: formula tests green; reorder/hide persists across restart.
## M5 — Details & README

Details, history, sanitized Markdown rendering, artifact chooser with ABI
confidence. Acceptance: malformed/2 MB+ README fixtures render safely.
## M6 — Download & installation

Downloader decision (DownloadManager unless capability gap → WorkManager+OkHttp,
documented), progress/cancel, validation, install handoff + explanation UI.
Acceptance: interrupted-download resume, mismatch quarantine, denied-permission path.
## M7 — Customization & adaptive

Appearance editor, rail layout, a11y + resize passes.
Acceptance: UI tests + manual resize matrix on sw>600dp.
## M8 — Security & quality

Full TEST_PLAN run, permission audit, perf profile, release config.
Acceptance: verification report; all P0 defects closed.

Cross-cutting: small increments, real behavior over placeholders; any skipped
requirement → DECISIONS.md entry with reason + safe alternative + remaining work.
