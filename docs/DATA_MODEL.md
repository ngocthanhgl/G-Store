# DATA_MODEL — Forkly

Room entities mirror these; network DTOs map to them at the data layer boundary.
Identities: repo = GitHub repo ID; release = GitHub release ID (+tag preserved);
asset = asset ID (+filename, content type, size, URL, digest-or-null).

| Entity | Key fields | Notes |
|---|---|---|
| Repository | id, fullName, owner, avatarUrl, description, stars, forks, topics, archived, defaultBranch, updatedAt, lastSyncedAt, syncState | No README body, no progress here |
| Release | id, repoId, tag, name, notes, publishedAt, prerelease, draft(excluded), assets | Drafts never persisted |
| ReleaseAsset | id, releaseId, filename, contentType, size, downloadUrl, digest?, downloadCount | digest nullable (API often null) |
| ApkArtifact | assetId, abi, abiConfidence, minSdk?, versionName?, isSplit/bundle | derived; unknown stays Unknown |
| AbiType | ARM64_V8A, ARMEABI_V7A, X86, X86_64, UNIVERSAL, UNKNOWN | universal/noarch = packaging label, not ABI |
| AndroidCompatibility | minSdk?, targetKnown?, deviceSupported: YES/NO/UNKNOWN | never claim support when unknown |
| ReadmeDocument | repoId, branch, markdown (≤2 MB), fetchedAt | LRU-capped, separate table |
| DownloadTask | assetId UNIQUE-active, state, bytesDone, bytesTotal | at most one active row per asset |
| DownloadRecord | assetId, version, fileRef, sizeVerified, digestState, completedAt | survives restart |
| Favorite | repoId, addedAt | offline-renderable |
| CatalogSection | key, visible, rank, configJson | user order persisted |
| UserPreferences | theme, dynamicColor, accent, density, layout, motion | DataStore, validated |
| DiscoveryQuery | text, filters, sort, checkpoint (page/cursor), createdAt | resumable indexing |
| VerificationResult | CHECKED_OK / UNAVAILABLE / MISMATCH / UNTRUSTED_SOURCE | digest available ≠ verified |

Sealed states: `SyncState { FRESH, STALE, FAILED(cause) }`,
`DownloadState { QUEUED, RUNNING, PAUSED, FAILED(cause), VERIFYING, DONE }`,
`CatalogFailure { Network, RateLimited(retryAfter), Auth, NotFound, Malformed, Storage }`.
Section membership is a query over shared rows, never duplicated repo copies.
