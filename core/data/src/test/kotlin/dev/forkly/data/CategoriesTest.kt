package dev.forkly.data

import dev.forkly.model.RepoSignals
import dev.forkly.model.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {
    private fun repo(id: Long, stars: Int = 0, topics: List<String> = emptyList()) =
        Repository(
            id = id,
            fullName = "o/r$id",
            owner = "o",
            avatarUrl = null,
            description = null,
            stars = stars,
            forks = 0,
            topics = topics,
            updatedAt = null,
        )

    private fun signals(id: Long, stars: Int = 0, topics: List<String> = emptyList()) =
        RepoSignals(repo(id, stars, topics))

    @Test fun `domain topics map to the right shelf`() {
        assertEquals(AppCategory.MEDIA, categoryOf(listOf("music-player", "android")))
        assertEquals(AppCategory.MEDIA, categoryOf(listOf("YouTube"))) // case-insensitive
        assertEquals(AppCategory.PRIVACY, categoryOf(listOf("vpn", "kotlin")))
        assertEquals(AppCategory.PRIVACY, categoryOf(listOf("encryption")))
        assertEquals(AppCategory.TOOLS, categoryOf(listOf("file-manager")))
        assertEquals(AppCategory.TOOLS, categoryOf(listOf("root", "magisk")))
        assertEquals(AppCategory.CUSTOMIZE, categoryOf(listOf("icon-pack")))
        assertEquals(AppCategory.PRODUCTIVITY, categoryOf(listOf("notes", "todo")))
        assertEquals(AppCategory.PRODUCTIVITY, categoryOf(listOf("ai", "llm")))
        assertEquals(AppCategory.DEV, categoryOf(listOf("git", "self-hosted")))
        assertEquals(AppCategory.GAMES, categoryOf(listOf("godot")))
        assertEquals(AppCategory.GAMES, categoryOf(listOf("emulator")))
        assertEquals(AppCategory.MEDIA, categoryOf(listOf("manga", "anime")))
    }

    @Test fun `retired social topics stay uncategorized`() {
        // SOCIAL was dropped as a shelf: 9 repos cannot carry one, and a
        // wrong shelf is worse than none. These surface in All apps only.
        assertNull(categoryOf(listOf("mastodon", "matrix")))
    }

    @Test fun `tech-only topics never invent a category`() {
        assertNull(categoryOf(listOf("kotlin", "android", "mvvm", "jetpack-compose")))
        assertNull(categoryOf(emptyList()))
    }

    @Test fun `first matching rule wins on multi-domain topics`() {
        // Media outranks tools even when both match — deterministic shelf.
        assertEquals(AppCategory.MEDIA, categoryOf(listOf("player", "downloader")))
    }

    @Test fun `top charts are stars-descending and capped`() {
        val list = listOf(
            signals(1, stars = 100),
            signals(2, stars = 50_000),
            signals(3, stars = 5_000),
        )
        assertEquals(listOf(2L, 3L, 1L), topCharts(list).map { it.repo.id })
        assertEquals(2, topCharts(list, limit = 2).size)
    }

    @Test fun `categories present counts most-populous first`() {
        val list = listOf(
            signals(1, topics = listOf("music-player")),
            signals(2, topics = listOf("youtube")),
            signals(3, topics = listOf("vpn")),
            signals(4, topics = listOf("kotlin")),
        )
        assertEquals(
            listOf(AppCategory.MEDIA to 2, AppCategory.PRIVACY to 1),
            categoriesPresent(list),
        )
    }

    @Test fun `catalog category filter narrows before sorting`() {
        val list = listOf(
            signals(1, stars = 100_000, topics = listOf("vpn")),
            signals(2, stars = 10, topics = listOf("music-player")),
        )
        val filtered = sortedCatalog(list, CatalogFilter.STARS, category = AppCategory.MEDIA)
        assertEquals(listOf(2L), filtered.map { it.repo.id })
        assertTrue(sortedCatalog(list, CatalogFilter.STARS).map { it.repo.id }.size == 2)
    }

    @Test fun `compute sections carries the chart`() {
        val sections = computeSections(
            listOf(signals(1, stars = 9_000), signals(2, stars = 100)),
        )
        assertEquals(listOf(1L, 2L), sections.topCharts.map { it.repo.id })
    }
}
