package dev.gstore.data

import dev.gstore.model.HomeSection
import dev.gstore.model.RepoSignals
import dev.gstore.model.TrendingWeights
import java.time.Duration
import java.time.Instant
import kotlin.math.ln

/**
 * Home-section membership — pure functions over [RepoSignals].
 * Implements PRODUCT_SPEC §3 with the snapshot-signal revision (see DECISIONS.md):
 * delta signals (stars_7d) need history we don't keep yet, so magnitude terms
 * use log-dampened totals while velocity terms stay windowed.
 *
 * score = w.stars*ln(1+stars) + w.forks*ln(1+forks)
 *       + w.releases30d*releases30d + w.downloads30d*ln(1+downloads30d)
 */
fun trendingScore(signals: RepoSignals, weights: TrendingWeights = TrendingWeights()): Double =
    weights.stars * ln(1.0 + signals.repo.stars) +
        weights.forks * ln(1.0 + signals.repo.forks) +
        weights.releases30d * signals.releases30d +
        weights.downloads30d * ln(1.0 + signals.downloads30d)

private fun parseInstant(raw: String?): Instant? =
    raw?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun daysBetween(from: String?, now: Instant): Long? {
    val start = parseInstant(from) ?: return null
    return Duration.between(start, now).toDays()
}

/** stars >= 1000 AND >= 3 eligible releases AND age >= 365d. Unknown age never qualifies. */
fun isFamous(signals: RepoSignals, now: Instant = Instant.now()): Boolean {
    if (signals.repo.stars < 1_000 || signals.eligibleReleaseCount < 3) return false
    return (daysBetween(signals.repo.createdAt, now) ?: return false) >= 365
}

/** First eligible release published within [windowDays]. No eligible release: not new. */
fun isNewApp(
    signals: RepoSignals,
    now: Instant = Instant.now(),
    windowDays: Long = 30,
): Boolean {
    val days = daysBetween(signals.firstEligibleReleaseAt, now) ?: return false
    return days in 0..windowDays
}

/** Latest release (any kind) published within [windowDays]. */
fun isRecentlyUpdated(
    signals: RepoSignals,
    now: Instant = Instant.now(),
    windowDays: Long = 30,
): Boolean {
    val days = daysBetween(signals.latestReleaseAt, now) ?: return false
    return days in 0..windowDays
}

data class HomeSections(
    val new: List<RepoSignals> = emptyList(),
    val trending: List<RepoSignals> = emptyList(),
    val famous: List<RepoSignals> = emptyList(),
    val updated: List<RepoSignals> = emptyList(),
    val recommended: List<RepoSignals> = emptyList(),
)

fun computeSections(
    signals: List<RepoSignals>,
    favoriteIds: Set<Long> = emptySet(),
    weights: TrendingWeights = TrendingWeights(),
    now: Instant = Instant.now(),
    limit: Int = 20,
): HomeSections {
    val famous = signals.filter { isFamous(it, now) }.sortedByDescending { it.repo.stars }
    return HomeSections(
        new = signals.filter { isNewApp(it, now) }
            .sortedBy { parseInstant(it.firstEligibleReleaseAt) }.take(limit),
        // Recent release activity required — a high total alone is not "trending".
        trending = signals.filter { it.releases30d > 0 }
            .sortedByDescending { trendingScore(it, weights) }.take(limit),
        famous = famous.take(limit),
        updated = signals.filter { isRecentlyUpdated(it, now) }
            .sortedByDescending { parseInstant(it.latestReleaseAt) }.take(limit),
        // Famous, not yet favorited — explicit, explainable recommendation basis.
        recommended = famous.filter { it.repo.id !in favoriteIds }.take(10),
    )
}

/** Membership test helper for section-refresh scoping. */
fun HomeSection.matches(signals: RepoSignals, now: Instant = Instant.now()): Boolean = when (this) {
    HomeSection.NEW -> isNewApp(signals, now)
    HomeSection.TRENDING -> signals.releases30d > 0
    HomeSection.FAMOUS -> isFamous(signals, now)
    HomeSection.UPDATED -> isRecentlyUpdated(signals, now)
    HomeSection.RECOMMENDED -> isFamous(signals, now)
    HomeSection.BROWSING -> false // history-driven, never signal-derived
}

/** Home catalog ordering — the filter chips above "All apps". */
enum class CatalogFilter { TRENDING, STARS, NEW, UPDATED }

/**
 * Whole-catalog sort for the filter chips. Pure over [RepoSignals];
 * ISO-8601 strings sort chronologically, nulls (unknown) sort last.
 */
fun sortedCatalog(
    signals: List<RepoSignals>,
    filter: CatalogFilter,
    weights: TrendingWeights = TrendingWeights(),
): List<RepoSignals> = when (filter) {
    CatalogFilter.TRENDING -> signals.sortedByDescending { trendingScore(it, weights) }
    CatalogFilter.STARS -> signals.sortedByDescending { it.repo.stars }
    CatalogFilter.NEW -> signals.sortedByDescending { it.firstEligibleReleaseAt ?: "" }
    CatalogFilter.UPDATED -> signals.sortedByDescending { it.latestReleaseAt ?: "" }
}
