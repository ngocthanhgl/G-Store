package dev.gstore.model

import kotlinx.serialization.Serializable

/** Stable GitHub repository identity. Mirrors docs/DATA_MODEL.md. */
@Serializable
data class Repository(
    val id: Long,
    val fullName: String,
    val owner: String,
    val avatarUrl: String?,
    val description: String?,
    val stars: Int,
    val forks: Int,
    val topics: List<String> = emptyList(),
    val archived: Boolean = false,
    val defaultBranch: String = "main",
    val updatedAt: String?,
    /** ISO-8601 creation time. Null = unknown age; Famous excludes unknown age. */
    val createdAt: String? = null,
    /** SPDX or license name when the API reports one — never invented. */
    val license: String? = null,
    /** Primary language, open issue count, subscribers, homepage, last push — null when unknown. */
    val language: String? = null,
    val openIssues: Int? = null,
    val watchers: Int? = null,
    val homepage: String? = null,
    val pushedAt: String? = null,
)

/** A published GitHub release. Drafts are never persisted. */
@Serializable
data class Release(
    val id: Long,
    val repoId: Long,
    val tag: String,
    val name: String?,
    val notes: String?,
    val publishedAt: String?,
    /** API-provided release page URL — never constructed by guessing. */
    val htmlUrl: String? = null,
    val prerelease: Boolean = false,
)

@Serializable
data class ReleaseAsset(
    val id: Long,
    val releaseId: Long,
    val filename: String,
    val contentType: String?,
    val size: Long,
    val downloadUrl: String,
    /** GitHub `digest` field — frequently null. Null means UNAVAILABLE, never failure. */
    val digest: String?,
    val downloadCount: Long = 0,
)

/** ABI designations. UNIVERSAL is a packaging label, not an Android ABI. */
enum class AbiType {
    ARM64_V8A,
    ARMEABI_V7A,
    X86,
    X86_64,
    UNIVERSAL,
    UNKNOWN,
}

enum class AbiConfidence { HIGH, MEDIUM, LOW, UNKNOWN }

@Serializable
data class ApkArtifact(
    val assetId: Long,
    val abi: AbiType,
    val confidence: AbiConfidence,
    val minSdk: Int?,
    val versionName: String?,
    val isSplitBundle: Boolean = false,
)

enum class DeviceSupport { YES, NO, UNKNOWN }

data class AndroidCompatibility(
    val minSdk: Int?,
    val deviceSupported: DeviceSupport,
)

/** Checksum honesty states — available digest is never equated with verified. */
enum class VerificationResult {
    CHECKED_OK,
    UNAVAILABLE,
    MISMATCH,
    UNTRUSTED_SOURCE,
}

enum class SyncState { FRESH, STALE, FAILED }

/** Home section keys — visibility/order persisted per user (PRODUCT_SPEC §3). */
enum class HomeSection {
    NEW,
    TOP_CHARTS,
    CATEGORIES,
    FAVORITES,
    TRENDING,
    FAMOUS,
    UPDATED,
}

data class SectionConfig(
    val section: HomeSection,
    val visible: Boolean = true,
    /** Lower rank shows first. */
    val rank: Int = 0,
)

/** Trending weights — defaults from PRODUCT_SPEC §3, user-tunable (M7). */
data class TrendingWeights(
    val stars: Double = 4.0,
    val forks: Double = 2.0,
    val releases30d: Double = 10.0,
    val downloads30d: Double = 1.0,
)

/**
 * Per-repo activity snapshot feeding section membership.
 * ISO-8601 UTC strings compare lexicographically == chronologically.
 */
data class RepoSignals(
    val repo: Repository,
    val releases30d: Int = 0,
    /** Sum of asset download_count on releases published within 30d. */
    val downloads30d: Long = 0,
    /** Releases with >= 1 eligible APK asset. */
    val eligibleReleaseCount: Int = 0,
    /** Earliest eligible release publish time, null when none exists. */
    val firstEligibleReleaseAt: String? = null,
    /** Latest release publish time (any kind), null when none exists. */
    val latestReleaseAt: String? = null,
)

enum class DownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    FAILED,
    VERIFYING,
    DONE,
}

/** Distinguishes rate-limit, auth, absence, and corruption — see DATAFLOW.md. */
sealed interface CatalogFailure {
    data object Network : CatalogFailure
    data class RateLimited(val retryAfterSeconds: Long?) : CatalogFailure
    data object Auth : CatalogFailure
    data object NotFound : CatalogFailure
    data object Malformed : CatalogFailure
    data object Storage : CatalogFailure
}
