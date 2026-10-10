AGENTS.md — Native Android GitHub App Store
1. Mission and Product Vision

Build a production-quality, Android-only application marketplace that discovers, indexes, presents, downloads, and helps users install Android APK artifacts published through GitHub Releases.

The application must be implemented as a genuine native Android application using Kotlin. It must deliver a highly customizable user interface, excellent performance, robust background downloads, accurate APK variant selection, and a reliable permission and installation experience.

This is a real software engineering project, not a UI prototype. Prioritize correctness, maintainability, accessibility, security, and a polished end-user experience.

Mandatory platform constraints
Platform: Android only.
Primary language: Kotlin.
UI framework: Jetpack Compose with Material 3 as the foundation.
Maximum supported target platform: Android 17, API 37.
Verify Android 17 SDK availability and compatibility with the installed Android Gradle Plugin, Kotlin, Compose, and Gradle versions before configuring the project.
Use a minSdk appropriate for the actual feature requirements. Do not arbitrarily raise it.
Set compileSdk and targetSdk to the highest stable, locally available and compatible SDK appropriate for the project. Prefer API 37 when officially available and supported by the installed toolchain.
Never use Flutter, React Native, Ionic, or a WebView as the primary application interface.
Do not build a backend unless explicitly justified by a documented architectural requirement.
Do not embed GitHub credentials, private keys, or client secrets in the APK.
Do not claim that an APK is safe merely because it is hosted on GitHub.
2. Mandatory Skill-Driven Workflow

Before writing application code, inspect the repository, OpenCode configuration, installed skills, agent definitions, and available MCP tools.

The following skills are expected to be considered and used when actually available:

archify: architectural decomposition, module boundaries, dependency rules, and implementation planning.
impeccable: UI/UX direction, visual consistency, design quality, and interface refinement.
ponytail: inspect its actual installed instructions and use its verified capabilities for the relevant engineering tasks.
graphify: map relationships between requirements, modules, data models, flows, and dependencies using its actual supported capabilities.
caveman: inspect its actual instructions and use it for appropriate implementation, simplification, or review tasks.

These names are not permission to invent skill capabilities. Discover the real installed skill definitions first.

Required skill procedure
Discover all five skills and read their complete applicable instructions.
Identify which tasks each skill supports and any restrictions or dependencies.
Use archify to develop the architecture proposal before implementation.
Use graphify to establish a requirement-to-component dependency graph and the principal dataflow graph.
Use impeccable to define the design direction, interaction principles, component language, and visual acceptance criteria.
Evaluate ponytail and caveman for complementary tasks based on their actual documented capabilities.
Avoid duplicate work when skills overlap. Delegate distinct tasks and reconcile their outputs.
Save the resulting plans and diagrams in the project documentation.
If a skill is unavailable, document that fact and continue with a transparent, equivalent manual workflow.

Do not merely mention these skills in documentation. Invoke their supported workflows and incorporate their results into actual implementation decisions.

Do not skip architectural planning because a skill returns an incomplete or inconvenient result.

3. Required Planning Artifacts

Before implementing features, produce the following documents:

docs/PRODUCT_SPEC.md
docs/ARCHITECTURE.md
docs/DATAFLOW.md
docs/DATA_MODEL.md
docs/UI_UX_SPEC.md
docs/ANDROID_PERMISSIONS.md
docs/SECURITY_MODEL.md
docs/GITHUB_DISCOVERY.md
docs/IMPLEMENTATION_PLAN.md
docs/TEST_PLAN.md
docs/DECISIONS.md

Include Mermaid diagrams where useful and supported.

The dataflow documentation must describe both normal operation and failure paths.

The implementation plan must contain milestones, dependencies, acceptance criteria, and verification commands.

After producing these artifacts, review them for contradictions before implementation begins.

4. Product Scope

The application must provide the following primary capabilities.

4.1 Home

Implement a customizable home screen with independent content sections:

New Apps: recently published eligible releases.
Trending: projects gaining recent attention, using a documented scoring algorithm.
Famous: established projects with strong historical popularity signals.
Recommended or Featured Apps: optional configurable section.
Recently Updated: optional release update feed.
Continue Browsing or Recently Viewed: optional personalization feature.

Each section must have its own loading, error, empty, refresh, and pagination behavior.

Users must be able to reorder, hide, show, and customize supported sections.

Do not label a section as trending without implementing and documenting its scoring method.

Do not confuse GitHub release download counts with application installation counts.

4.2 Application Discovery

Provide:

Search by repository name, description, owner, and available metadata.
Search and discovery filters.
Category browsing where categories can be supported by defensible metadata.
Sort by recently released, recently updated, popularity, and relevance.
Pagination and incremental loading.
Favorites and bookmarks.
Repository attribution and direct links to the original source.
Clear indicators for prereleases, archived projects, and missing APK artifacts.

Support user-added repositories or repository URLs as an explicit discovery mechanism.

Never imply that every public GitHub repository has been indexed.

4.3 Application Details

Every application detail screen should support:

Repository name, owner, avatar, and description.
Project icon or image when a valid source exists.
GitHub stars, forks, and other available metadata.
Latest eligible release and release history.
Release notes.
Supported APK artifacts.
File size and checksum when available.
ABI compatibility information and unknown-ABI warnings.
Minimum Android version when reliably known.
Source repository, license information when available, and release links.
Favorite and sharing actions.
A complete README rendering experience.

Do not invent package names, version codes, Android requirements, licenses, or security verification results.

5. GitHub Discovery and Indexing

Use GitHub's documented REST API as the initial authoritative integration for repository and release metadata.

Review the official documentation for:

Repository search.
Release listing.
Latest release.
Release assets.
Pagination.
API versioning.
Rate-limit headers and error responses.

Reference: https://docs.github.com/en/rest

Important discovery limitation

There is no single ordinary public GitHub Releases endpoint that guarantees enumeration of every public repository with an APK asset.

Design a discovery strategy that combines supported repository search queries, incremental indexing, release inspection, cached results, and optional user-submitted repositories.

Document the coverage limitations and make the indexing process resumable.

Repository eligibility

A repository can enter the app catalog when:

Its repository metadata is accessible.
It has at least one relevant published release or an explicitly supported prerelease.
A release contains a downloadable APK artifact, or a documented supported APK distribution artifact.
Its metadata can be parsed sufficiently to present an honest catalog entry.

Do not classify an AAB, source archive, random ZIP, or unrelated executable as an installable APK merely because of its filename.

For ZIP-based APK bundles or split APK packages, implement explicit format detection and a separate supported installation workflow. If unsupported, clearly label the artifact rather than pretending it is a directly installable APK.

Release selection
Prefer the latest published stable release by default.
Allow users to inspect prereleases when supported.
Preserve access to older releases.
Exclude drafts and inaccessible releases from normal public discovery.
Handle deleted assets, unavailable downloads, archived repositories, and stale cache entries.
Never replace a release with another version silently during a download.
API resilience
Implement pagination and incremental synchronization.
Cache metadata and use conditional requests when supported.
Respect ETag, If-None-Match, Retry-After, and X-RateLimit-* where applicable.
Apply bounded exponential backoff with jitter.
Distinguish network errors, rate limiting, authentication failures, inaccessible repositories, and malformed responses.
Avoid repeated full-catalog scans.
Never ship a shared private GitHub token inside the Android client.
If authenticated GitHub access is implemented, use an appropriate user-authorized flow and secure token storage.
6. Architecture and Dependency Rules

Use a modular, feature-oriented, offline-first architecture.

Suggested structure:

app: application entry point, navigation integration, and dependency composition.
core:model: domain models and shared value types.
core:network: HTTP configuration, GitHub API integration, and serialization.
core:database: Room database and local data access.
core:datastore: persistent user preferences and appearance settings.
core:designsystem: theme engine, design tokens, reusable components, and typography.
core:ui: shared UI primitives and common screen states.
core:common: narrowly scoped shared utilities.
core:testing: reusable test utilities and fixtures.
feature:home: home sections and personalized content.
feature:search: search, filtering, and sorting.
feature:details: application details and release history.
feature:readme: Markdown rendering and README interactions.
feature:downloads: download management and progress.
feature:installation: APK inspection and installation handoff.
feature:favorites: bookmarks and favorites.
feature:settings: theme and behavior customization.
feature:library: local download history and installed-state presentation where supported.

Treat this structure as a proposal, not a reason to create unnecessary modules. Confirm the final boundaries with the architecture skill before creating files.

Dependency direction
UI depends on domain-facing interfaces and immutable UI state.
Domain logic must not depend on Compose, Android Activity instances, or concrete network clients.
Data implementations provide repository interfaces to the domain layer.
Feature modules must not reach into other features' private implementations.
Shared infrastructure must not depend on feature modules.
Platform-specific installation behavior must remain behind an explicit abstraction.

Use dependency injection with a documented strategy. Prefer constructor injection and a maintainable DI framework when justified.

Avoid global mutable state, service locators, giant ViewModels, and unnecessary abstraction layers.

7. Dataflow Requirements

Document and implement these principal flows.

Flow A: Discovery

GitHub search or discovery query → repository metadata retrieval → release retrieval → release asset inspection → APK eligibility and metadata extraction → deduplication and normalization → local persistence → repository and release indexes → UI observation → home sections and search results.

Flow B: README

Repository selection → default-branch metadata resolution → README discovery using GitHub API conventions → retrieval of Markdown source and relevant image/link metadata → local cache → Markdown parsing → sanitized rendering model → Compose UI → user interactions for links, code, tables, images, and anchors.

Resolve the README from the repository's actual default branch and support GitHub's README conventions. Do not hardcode main/README.md.

Flow C: Download

User selects a release and an artifact → artifact metadata validation → download confirmation where appropriate → background download scheduling → progress and cancellation → temporary file → completion and size validation → checksum verification when a trusted expected digest exists → APK structural validation → final local file → download history update → optional installation handoff.

Flow D: Installation

Completed download → APK validation → check whether this app is allowed to request package installation → show an explanatory permission screen when necessary → open the appropriate Android settings page only after explicit user action → re-check permission on return → launch the system package installer → display the actual outcome when observable.

The app must never silently install arbitrary APK files or pretend the system installer succeeded without evidence.

Flow E: Preferences

User changes a setting → validate the setting → persist it in DataStore → update the application settings state → propagate the new theme, layout, or behavior → restore it after process recreation.

Flow F: Offline-first UI

Local database and preferences → immediate cached UI → background refresh → database update → observable UI refresh.

Use a single source of truth for persisted catalog state.

8. Domain Model

Design explicit, normalized models for at least:

Repository
Release
ReleaseAsset
ApkArtifact
AbiType
AndroidCompatibility
ReadmeDocument
DownloadTask
DownloadRecord
Favorite
CatalogSection
UserPreferences
DiscoveryQuery
VerificationResult

Repository identity should use the stable GitHub repository ID where available.

Release identity should use stable GitHub release IDs and preserve tag names.

Asset identity should preserve the asset ID, filename, content type, size, download URL, and optional expected digest.

Use sealed types or similarly explicit representations for states and failures.

Do not place large README bodies or mutable download progress in every repository list item.

Avoid duplicated copies of the same repository across home sections. Model section membership as queries or references over shared data.

9. ABI Detection and APK Selection

Implement accurate artifact selection rather than filename-only filtering.

Recognize common Android ABI identifiers:

arm64-v8a
armeabi-v7a
x86
x86_64
universal
noarch, only where its meaning is verified for that artifact.

Treat universal and noarch as packaging labels, not Android ABI names.

Use a confidence-aware detection strategy:

Inspect structured release metadata and maintainer-provided asset labels.
Inspect APK contents when feasible and appropriate.
Use verified APK manifest and native library metadata when available.
Infer ABI from filenames only as a fallback.
Clearly indicate unknown or ambiguous ABI values.

For installed-device compatibility, consider supported ABIs and Android's platform APIs. Distinguish device support from APK-declared native architectures.

Provide an explicit artifact chooser displaying:

Asset filename.
ABI or universal designation.
File size.
Release version.
Compatibility confidence or unknown status.
Checksum verification state, when applicable.

Do not claim a selected APK will work when its ABI or Android compatibility cannot be established.

Support APKs without native libraries where appropriate. Do not assume every APK must declare an ABI.

10. Download Management

Use Android DownloadManager when its capabilities meet the requirements, or implement a robust downloader backed by an appropriate HTTP client and WorkManager when resumability, custom progress, retry policies, and task control justify it.

Document the decision.

Requirements:

Downloads survive Activity recreation.
Appropriate work can survive process death and device restarts.
Users can observe progress and cancel tasks.
Retry only recoverable failures.
Prevent duplicate active downloads for the same asset.
Validate HTTP responses, expected size, and file completeness.
Handle redirects, expired URLs, storage exhaustion, and network loss.
Use temporary files and atomic finalization where feasible.
Prevent path traversal and unsafe filenames.
Never trust a filename or HTTP content type as the sole APK validation.
Verify expected SHA-256 digests when supplied by a trustworthy source.
Clearly distinguish an available digest from an actually verified digest.
Do not claim authenticity solely from a checksum published by the same untrusted source.
Never execute downloaded content automatically.

Keep the download queue and history observable through persistent state.

11. README Rendering

The README must render the repository's actual Markdown structure, not display raw text or rely on a WebView.

Use a maintained Kotlin/Android-compatible Markdown parser and build Compose renderers for the supported elements.

Support:

Headings and heading anchors.
Paragraphs and line breaks.
Ordered and unordered lists.
Nested lists.
Task lists.
Inline code and fenced code blocks.
Language labels and code-copy actions.
Tables with horizontal overflow where necessary.
Blockquotes.
Emphasis and strikethrough.
Links.
Images with loading and error states.
Thematic separators.
Inline HTML according to a strict safe subset, or render unsupported HTML safely.
Relative links and images resolved against the correct repository context.
README files from the repository's actual default branch.
Loading, unavailable, malformed, and empty states.
Long documents without unnecessary recomposition.

Preserve the meaning and ordering of the source document.

Sanitize Markdown-generated content and do not execute HTML, scripts, JavaScript URLs, or arbitrary embedded content.

Open external links through safe Android navigation mechanisms with clear user interaction.

Do not fetch arbitrary private-network URLs or blindly proxy untrusted links.

Cache README content with bounded storage and sensible invalidation.

12. Design System and Deep Customization

The visual quality of the app is a core product requirement.

Use the UI skill to create a documented visual direction rather than accepting default Material components without refinement.

Build a centralized design system containing:

Color tokens.
Typography scale.
Spacing scale.
Shape tokens.
Elevation and tonal hierarchy.
Motion and animation guidelines.
Iconography rules.
Responsive layout rules.
Component states.
Accessibility specifications.
Customization features

Implement a persistent appearance settings model with appropriate support for:

Light, dark, and system themes.
Dynamic colors where supported.
Custom accent colors.
Background and surface appearance.
Card shapes and density.
Grid or list layouts.
Repository card styles.
Home section visibility and ordering.
Navigation presentation.
Content spacing.
Reduced-motion preferences.
Optional background and visual effects, subject to performance constraints.

Keep design tokens centralized. Avoid hardcoded colors, arbitrary spacing, and one-off component styles throughout feature modules.

Use a coherent visual language. Customization must not destroy contrast, readability, accessibility, or consistency.

Interaction quality

Implement:

Proper loading skeletons.
Useful empty states.
Recoverable error states.
Pull-to-refresh where appropriate.
Predictable navigation and back behavior.
Subtle, purposeful motion.
Haptic feedback only where appropriate.
Touch targets meeting accessibility recommendations.
Responsive layouts for phones, tablets, foldables, and resizable windows.

Android 17/API 37 must be treated as a large-screen compatibility milestone. Test adaptive layouts under resizing rather than relying on fixed orientation or fixed dimensions.

13. Navigation and Screens

Define the navigation graph before implementing the screens.

Minimum destinations:

Home
Search
Application Details
README
Release History
Downloads
Favorites
Settings
Theme Customization
Repository Information
Permission Explanation

Prefer a navigation model that works on compact and expanded window sizes.

Use a bottom navigation pattern on compact layouts and an appropriate adaptive navigation rail or equivalent on expanded layouts where beneficial.

Keep navigation state and deep links predictable.

14. Android Permissions and Platform Compliance

Create docs/ANDROID_PERMISSIONS.md and maintain it as a living document.

For every permission or privileged operation, record:

Purpose.
Android version applicability.
Manifest declaration.
Runtime or settings-based grant mechanism.
Exact feature that requires it.
Whether it is optional.
User-facing explanation.
Denial and recovery behavior.
Test cases.
Baseline permissions

Declare android.permission.INTERNET for network access.

Declare android.permission.ACCESS_NETWORK_STATE only if the implementation uses connectivity-state inspection to improve behavior.

Use WorkManager constraints and normal network APIs appropriately.

Download storage

Prefer app-specific storage and suitable system-managed download destinations.

Use MediaStore or a system-provided document/destination picker when the product requires user-visible file export.

Do not request MANAGE_EXTERNAL_STORAGE for ordinary APK downloads.

Do not request broad storage permissions merely to read or write files within the app's own storage.

Do not assume that public Downloads-directory access behaves identically across Android versions.

APK installation

For a direct system-installer flow, investigate the requirements of:

android.permission.REQUEST_INSTALL_PACKAGES
PackageManager.canRequestPackageInstalls()
Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
Intent.ACTION_VIEW
PackageManager.EXTRA_NOT_UNKNOWN_SOURCE only if justified by a documented platform requirement.

Use the correct APK MIME type and a FileProvider content URI for locally stored APK files.

Grant temporary URI read access as required.

On Android 8.0/API 26 and above, installation authorization is source-specific. Check the actual permission state before initiating installation.

Provide an explanatory UI before directing users to system settings.

The app must never bypass Android's package installer or security checks.

Package visibility

Avoid QUERY_ALL_PACKAGES unless a documented, essential product requirement makes broad package visibility unavoidable.

Do not inspect all installed applications just to determine whether an APK can be downloaded.

Use targeted package visibility declarations or permitted PackageManager queries when a specific feature requires them.

Notifications

If download notifications are implemented, assess POST_NOTIFICATIONS on Android 13/API 33 and above.

Request runtime notification permission only when appropriate and explain the benefit.

The core download feature should have a sensible fallback when notifications are denied.

Foreground services

Prefer WorkManager or DownloadManager when appropriate.

If a foreground service is genuinely required, verify the current Android requirements for its service type, manifest declarations, runtime behavior, and notification obligations.

Do not add foreground-service permissions speculatively.

Other permissions

Do not request camera, microphone, contacts, location, accessibility-service, VPN, device-administrator, or boot-related permissions without a demonstrated feature requirement.

No permission should be added simply because another application commonly declares it.

Review current Android documentation for every permission affected by API 37 behavior changes.

15. Security and Trust

Treat repositories, release notes, README content, filenames, asset metadata, and downloaded APKs as untrusted input.

Requirements:

Use HTTPS.
Validate URLs and redirect destinations.
Never embed privileged credentials.
Avoid insecure TLS settings.
Sanitize Markdown and external links.
Prevent path traversal and unsafe file operations.
Bound network responses and parsing workloads.
Handle maliciously large Markdown documents and malformed API responses.
Do not automatically execute downloaded files.
Clearly distinguish repository metadata from locally verified facts.
Show the original repository and release for every artifact.
Display available checksums and verification status.
Avoid presenting GitHub popularity as a security rating.
Provide a way to report broken or suspicious repository entries.

APK signing-certificate inspection may be added as a defense-in-depth feature. If implemented, explain precisely what was verified and never equate a certificate's presence with trusted authorship.

16. Persistence and Offline Support

Use Room for structured catalog data and DataStore for user preferences.

Define caching policies for:

Repository metadata.
Release metadata.
Asset metadata.
README documents.
Home section configuration.
Favorites.
Download history.
Discovery synchronization checkpoints.

Implement explicit cache freshness and invalidation behavior.

Keep useful catalog content available offline when already cached.

Never require network connectivity to render persisted favorites or previously downloaded file metadata.

Do not persist sensitive authorization tokens in ordinary preferences.

17. Performance and Reliability
Use Kotlin coroutines and structured concurrency.
Use Flow for observable data where appropriate.
Keep disk and network operations off the main thread.
Use stable lazy-list keys.
Avoid unnecessary recomposition.
Load images asynchronously with caching.
Keep image sizes and memory usage controlled.
Use bounded concurrency for discovery and downloads.
Implement request deduplication and backpressure.
Prevent unbounded database growth and cache growth.
Handle process recreation, cancellation, and lifecycle transitions.
Profile startup, scrolling, memory, and large README rendering.
Avoid premature optimization without measurements.
18. Testing Strategy

Implement:

Unit tests for domain logic.
Repository and API integration tests.
Database tests.
JSON serialization tests.
ABI detection tests.
APK eligibility and file validation tests.
README parser and rendering tests.
Download state-machine tests.
Retry and cancellation tests.
Theme persistence tests.
Navigation tests.
Permission-state tests.
Installation intent tests.
Compose UI tests.
Accessibility checks.
Adaptive-layout tests.
Offline and rate-limit tests.

Include fixtures for:

Repositories with no releases.
Releases without APK assets.
Universal APKs.
ABI-specific APKs.
Misleading filenames.
Multiple assets for one release.
Prereleases.
Missing checksums.
Malformed README Markdown.
Very large README files.
Rate-limited API responses.
Interrupted downloads.
Unknown ABI.
Insufficient storage.
Installation permission denied.

Do not depend on live GitHub availability for every unit test.

19. Build and Verification

Use the project's actual Gradle wrapper and installed SDK.

Before declaring completion:

Run the appropriate Gradle configuration checks.
Compile the project.
Run unit tests.
Run relevant Android instrumentation or Compose UI tests when an emulator/device is available.
Run static analysis and formatting checks configured by the project.
Inspect the final manifest and permission declarations.
Verify the release build configuration.
Confirm the app handles API-level differences.
Review logs for warnings, crashes, leaked secrets, and accidental network calls.
Document any checks that could not run and why.

Never claim that a command succeeded unless its actual result confirms success.

20. Implementation Milestones
Milestone 0 — Repository and Skill Audit

Inspect the existing project, all five skills, installed toolchains, and available integrations.

Deliver: skill audit, project audit, risks, and implementation plan.

Milestone 1 — Architecture and Dataflow

Produce architecture diagrams, domain models, dependency rules, and dataflow documentation.

Deliver: reviewed design documents and acceptance criteria.

Milestone 2 — Application Foundation

Set up Gradle, Kotlin, Compose, navigation, dependency injection, Room, DataStore, networking, testing, and the design system.

Deliver: a building application with an initial navigation shell.

Milestone 3 — GitHub Catalog

Implement discovery, repository metadata, release retrieval, asset classification, pagination, caching, and rate-limit handling.

Deliver: functional repository discovery and a persistent catalog.

Milestone 4 — Home and Search

Implement New Apps, Trending, Famous, search, filters, favorites, and configurable sections.

Deliver: fully navigable catalog experience.

Milestone 5 — Details and README

Implement application details, release history, README rendering, release notes, and artifact selection.

Deliver: a complete application details experience.

Milestone 6 — Download and Installation

Implement background downloads, progress, cancellation, validation, ABI-aware artifact selection, and system installation handoff.

Deliver: a reliable end-to-end download and installation flow.

Milestone 7 — Customization and Adaptive UI

Complete appearance settings, theme editor, layout customization, accessibility, animation refinement, and large-screen behavior.

Deliver: a cohesive, deeply customizable interface.

Milestone 8 — Security and Quality

Run the complete test plan, inspect permissions, fix defects, profile performance, and verify Android compatibility.

Deliver: release-ready candidate and verification report.

21. Agent Collaboration Rules

When multiple agents are available:

Assign distinct tasks with explicit ownership.
Have architecture work establish shared contracts before parallel implementation.
Define data models and interfaces before splitting dependent features.
Allow parallel work on independent modules after contracts are stable.
Require each agent to report changed files, decisions, tests, and unresolved issues.
Review cross-module integration before merging.
Avoid multiple agents editing the same architectural files concurrently.
Use the installed skills to coordinate and validate work when their documented capabilities support it.

Do not parallelize tasks whose dependencies are unresolved.

22. Definition of Done

The application is complete only when:

It is a genuine native Kotlin Android application.
It builds using the documented toolchain.
GitHub repository and release discovery work within documented coverage limits.
New Apps, Trending, and Famous have meaningful and testable definitions.
Users can search and inspect repository details.
README Markdown is rendered accurately and safely.
Users can select actual available APK artifacts and understand ABI compatibility.
Downloads support progress, cancellation, error recovery, and persistence.
Installation follows the Android system security model.
Permissions are justified, documented, and requested at the correct time.
Theme and layout preferences persist across restarts.
Adaptive layouts work across compact and expanded screens.
Important features have automated tests.
Security and rate-limit limitations are documented.
All implementation milestones have recorded verification results.
23. Operating Rule

Work in small, reviewable increments.

First inspect and plan. Then implement the smallest coherent milestone, run its verification, document the outcome, and continue.

Do not generate the entire application in one uncontrolled code-generation step.

Do not silently replace real functionality with placeholders.

If a requirement cannot be completed, state the limitation, explain the reason, implement the safest useful alternative, and record the remaining work.

The objective is a reliable, extensible, secure, visually excellent Android marketplace—not merely a successful compilation.