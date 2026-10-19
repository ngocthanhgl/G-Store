package dev.gstore.data

import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.model.TrendingWeights
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {
    private val now = Instant.parse("2026-10-10T00:00:00Z")

    private fun repo(
        id: Long = 1,
        stars: Int = 0,
        forks: Int = 0,
        createdAt: String? = "2020-01-01T00:00:00Z",
    ) = Repository(
        id = id,
        fullName = "o/r$id",
        owner = "o",
        avatarUrl = null,
        description = null,
        stars = stars,
        forks = forks,
        updatedAt = null,
        createdAt = createdAt,
    )

    @Test fun `famous needs stars, eligible releases, and a year of age`() {
        val ok = RepoSignals(repo(1, stars = 5_000), eligibleReleaseCount = 3)
        assertTrue(isFamous(ok, now))
        assertFalse(isFamous(ok.copy(eligibleReleaseCount = 2), now)) // < 3 releases
        assertFalse(isFamous(ok.copy(repo = repo(1, stars = 999)), now)) // < 1000 stars
        assertFalse(isFamous(ok.copy(repo = repo(1, createdAt = null)), now)) // unknown age
        assertFalse(
            isFamous(ok.copy(repo = repo(1, createdAt = "2026-06-01T00:00:00Z")), now),
        ) // too young
    }

    @Test fun `new app means first eligible release within 30 days`() {
        assertTrue(
            isNewApp(RepoSignals(repo(), firstEligibleReleaseAt = "2026-09-20T00:00:00Z"), now),
        )
        assertFalse(
            isNewApp(RepoSignals(repo(), firstEligibleReleaseAt = "2026-08-01T00:00:00Z"), now),
        )
        assertFalse(isNewApp(RepoSignals(repo()), now)) // no eligible release
        assertFalse(
            isNewApp(RepoSignals(repo(), firstEligibleReleaseAt = "not-a-date"), now),
        ) // malformed
    }

    @Test fun `recently updated tracks the latest release`() {
        assertTrue(
            isRecentlyUpdated(RepoSignals(repo(), latestReleaseAt = "2026-10-01T00:00:00Z"), now),
        )
        assertFalse(
            isRecentlyUpdated(RepoSignals(repo(), latestReleaseAt = "2026-01-01T00:00:00Z"), now),
        )
    }

    @Test fun `trending rewards recent activity over lifetime totals`() {
        val dormant = RepoSignals(repo(1, stars = 50_000, forks = 5_000))
        val active = RepoSignals(
            repo(2, stars = 500, forks = 50),
            releases30d = 2,
            downloads30d = 20_000,
        )
        assertTrue(trendingScore(active) > trendingScore(dormant))
        // Velocity term is linear: one more release moves the needle.
        assertTrue(
            trendingScore(active.copy(releases30d = 3)) > trendingScore(active),
        )
    }

    @Test fun `trending excludes repos with no recent releases`() {
        val sections = computeSections(
            listOf(RepoSignals(repo(1, stars = 50_000))),
            now = now,
        )
        assertTrue(sections.trending.isEmpty())
    }

    @Test fun `favorites shelf holds favorited repos stars-first`() {
        val a = RepoSignals(repo(7, stars = 9_000))
        val b = RepoSignals(repo(8, stars = 1_000))
        val sections = computeSections(listOf(a, b), favoriteIds = setOf(8, 7), now = now)
        assertEquals(listOf(7L, 8L), sections.favorites.map { it.repo.id })
        assertEquals(
            0,
            computeSections(listOf(a, b), favoriteIds = emptySet(), now = now)
                .favorites.size,
        )
    }

    @Test fun `search-only repositories appear only in favorites when explicitly saved`() {
        val searchOnly = RepoSignals(
            repo(9, stars = 2_000).copy(catalogVisible = false),
            releases30d = 2,
            eligibleReleaseCount = 3,
            firstEligibleReleaseAt = "2026-10-01T00:00:00Z",
            latestReleaseAt = "2026-10-02T00:00:00Z",
        )
        val sections = computeSections(
            listOf(searchOnly),
            favoriteIds = setOf(9),
            now = now,
        )

        assertTrue(sections.new.isEmpty())
        assertTrue(sections.trending.isEmpty())
        assertTrue(sections.famous.isEmpty())
        assertTrue(sections.updated.isEmpty())
        assertEquals(listOf(9L), sections.favorites.map { it.repo.id })
        assertTrue(sortedCatalog(listOf(searchOnly), CatalogFilter.STARS).isEmpty())
    }

    @Test fun `local favorites never change trending or top stars`() {
        val signals = listOf(
            RepoSignals(repo(1, stars = 900), releases30d = 2),
            RepoSignals(repo(2, stars = 9_000), releases30d = 1),
        )
        val withoutFavorites = computeSections(signals, now = now)
        val withFavorites = computeSections(signals, favoriteIds = setOf(1), now = now)
        assertEquals(withoutFavorites.trending, withFavorites.trending)
        assertEquals(withoutFavorites.topCharts, withFavorites.topCharts)
        assertEquals(listOf(1L), withFavorites.favorites.map { it.repo.id })
    }

    @Test fun `custom weights change the ranking`() {
        val forkHeavy = RepoSignals(repo(1, stars = 100, forks = 10_000), releases30d = 1)
        val starHeavy = RepoSignals(repo(2, stars = 10_000, forks = 100), releases30d = 1)
        val default = computeSections(
            listOf(forkHeavy, starHeavy),
            weights = TrendingWeights(),
            now = now,
        )
        assertEquals(2L, default.trending.first().repo.id)
        val forkFirst = computeSections(
            listOf(forkHeavy, starHeavy),
            weights = TrendingWeights(stars = 0.0, forks = 20.0),
            now = now,
        )
        assertEquals(1L, forkFirst.trending.first().repo.id)
    }

    @Test fun `catalog sort honors every filter and nulls sort last`() {
        val a = RepoSignals(
            repo(1, stars = 100),
            releases30d = 3,
            firstEligibleReleaseAt = "2026-09-01T00:00:00Z",
            latestReleaseAt = "2026-10-01T00:00:00Z",
        )
        val b = RepoSignals(
            repo(2, stars = 9_000),
            firstEligibleReleaseAt = "2026-10-05T00:00:00Z",
            latestReleaseAt = "2026-10-06T00:00:00Z",
        )
        val c = RepoSignals(repo(3, stars = 500)) // no dates: never first
        val all = listOf(a, b, c)
        // b has no recent releases so its trending score loses to active a.
        assertEquals(listOf(1L, 2L, 3L), sortedCatalog(all, CatalogFilter.TRENDING).map { it.repo.id })
        assertEquals(listOf(2L, 3L, 1L), sortedCatalog(all, CatalogFilter.STARS).map { it.repo.id })
        assertEquals(listOf(2L, 1L, 3L), sortedCatalog(all, CatalogFilter.NEW).map { it.repo.id })
        assertEquals(listOf(2L, 1L, 3L), sortedCatalog(all, CatalogFilter.UPDATED).map { it.repo.id })
    }
}
