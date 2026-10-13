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
    val topCharts: List<RepoSignals> = emptyList(),
    val trending: List<RepoSignals> = emptyList(),
    val famous: List<RepoSignals> = emptyList(),
    val updated: List<RepoSignals> = emptyList(),
    val favorites: List<RepoSignals> = emptyList(),
)

/**
 * Play-Store-style categories derived from GitHub topics. Topics mix domain
 * and tech labels (`kotlin`, `mvvm` describe the stack, not the shelf), so
 * only domain keywords map — everything tech is ignored and uncategorized
 * repos return null instead of a wrong shelf.
 */
enum class AppCategory { MEDIA, PRIVACY, TOOLS, CUSTOMIZE, PRODUCTIVITY, SOCIAL, DEV }

fun categoryLabel(category: AppCategory): String = when (category) {
    AppCategory.MEDIA -> "Music & video"
    AppCategory.PRIVACY -> "Privacy & security"
    AppCategory.TOOLS -> "Tools"
    AppCategory.CUSTOMIZE -> "Personalization"
    AppCategory.PRODUCTIVITY -> "Productivity"
    AppCategory.SOCIAL -> "Social"
    AppCategory.DEV -> "Developer tools"
}

/** Ordered specific-first: the first matching rule wins. */
private val CATEGORY_RULES: List<Pair<AppCategory, Set<String>>> = listOf(
    AppCategory.MEDIA to setOf(
        "music", "music-player", "video", "video-player", "youtube",
        "youtube-music", "newpipe", "podcast", "media", "player", "gallery",
        "camera", "streaming", "iptv",
    ),
    AppCategory.PRIVACY to setOf(
        "privacy", "vpn", "encryption", "adblock", "ad-blocker", "firewall",
        "password", "2fa", "tor", "security",
    ),
    AppCategory.TOOLS to setOf(
        "file-manager", "file-explorer", "launcher", "automation", "backup",
        "root", "apk", "f-droid", "fdroid", "downloader", "keyboard",
        "calculator", "qr", "scanner", "terminal", "widgets",
    ),
    AppCategory.CUSTOMIZE to setOf(
        "wallpaper", "icon-pack", "theme", "material-you", "material-design",
        "customization", "font",
    ),
    AppCategory.PRODUCTIVITY to setOf(
        "notes", "todo", "task", "calendar", "email", "office", "pdf",
        "reader", "rss", "news", "weather", "health", "fitness",
    ),
    AppCategory.SOCIAL to setOf(
        "messaging", "chat", "social", "mastodon", "matrix", "telegram",
        "email-client", "forum", "dating",
    ),
    AppCategory.DEV to setOf(
        "git", "github", "ide", "editor", "code", "developer-tools", "api",
        "database", "server", "self-hosted",
    ),
)

fun categoryOf(topics: List<String>): AppCategory? {
    val lowered = topics.map { it.lowercase().trim() }.toSet()
    for ((category, keywords) in CATEGORY_RULES) {
        if (lowered.any { it in keywords }) return category
    }
    return null
}

/** Top charts: the highest-starred apps, regardless of recency. */
fun topCharts(signals: List<RepoSignals>, limit: Int = 10): List<RepoSignals> =
    signals.sortedByDescending { it.repo.stars }.take(limit)

/** Categories present in the catalog, most populous first. */
fun categoriesPresent(signals: List<RepoSignals>): List<Pair<AppCategory, Int>> =
    signals.mapNotNull { categoryOf(it.repo.topics) }
        .groupingBy { it }.eachCount().toList()
        .sortedByDescending { it.second }

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
        topCharts = topCharts(signals, limit),
        trending = signals.filter { it.releases30d > 0 }
            .sortedByDescending { trendingScore(it, weights) }.take(limit),
        famous = famous.take(limit),
        updated = signals.filter { isRecentlyUpdated(it, now) }
            .sortedByDescending { parseInstant(it.latestReleaseAt) }.take(limit),
        // Favorited repos, stars first — the bookmarks shelf.
        favorites = signals.filter { it.repo.id in favoriteIds }
            .sortedByDescending { it.repo.stars }.take(limit),
    )
}

/** Membership test helper for section-refresh scoping. */
fun HomeSection.matches(signals: RepoSignals, now: Instant = Instant.now()): Boolean = when (this) {
    HomeSection.NEW -> isNewApp(signals, now)
    // Chart membership is rank-based; the threshold below only scopes refresh.
    HomeSection.TOP_CHARTS -> signals.repo.stars >= 1_000
    HomeSection.CATEGORIES -> categoryOf(signals.repo.topics) != null
    HomeSection.TRENDING -> signals.releases30d > 0
    HomeSection.FAMOUS -> isFamous(signals, now)
    HomeSection.UPDATED -> isRecentlyUpdated(signals, now)
    HomeSection.FAVORITES -> false // favorite-driven, never signal-derived
}

/** Home catalog ordering — the filter chips above "All apps". */
enum class CatalogFilter { TRENDING, STARS, NEW, UPDATED }

/**
 * Whole-catalog sort for the filter chips. Pure over [RepoSignals];
 * ISO-8601 strings sort chronologically, nulls (unknown) sort last.
 * A [category] pre-filter narrows to that shelf before sorting.
 */
fun sortedCatalog(
    signals: List<RepoSignals>,
    filter: CatalogFilter,
    weights: TrendingWeights = TrendingWeights(),
    category: AppCategory? = null,
): List<RepoSignals> {
    val scoped = if (category == null) signals
    else signals.filter { categoryOf(it.repo.topics) == category }
    return when (filter) {
        CatalogFilter.TRENDING -> scoped.sortedByDescending { trendingScore(it, weights) }
        CatalogFilter.STARS -> scoped.sortedByDescending { it.repo.stars }
        CatalogFilter.NEW -> scoped.sortedByDescending { it.firstEligibleReleaseAt ?: "" }
        CatalogFilter.UPDATED -> scoped.sortedByDescending { it.latestReleaseAt ?: "" }
    }
}
