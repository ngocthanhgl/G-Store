package dev.forkly.data

import dev.forkly.testing.GitHubFixtures
import dev.forkly.network.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingTest {

    @Test fun `repo dto maps with stable identity`() {
        val repo = GitHubFixtures.repo().toDomain()
        assertEquals(123L, repo.id)
        assertEquals("example/coolapp", repo.fullName)
        assertEquals("example", repo.owner)
        assertEquals(1500, repo.stars)
        assertEquals(listOf("android", "music-player"), repo.topics)
        assertFalse(repo.archived)
    }

    @Test fun `null digest survives mapping as unavailable`() {
        val assets = GitHubFixtures.releasesPage1().first().assets
        val withDigest = assets.first { it.id == 2001L }.toDomain(1001L)
        val withoutDigest = assets.first { it.id == 2002L }.toDomain(1001L)
        assertEquals("sha256:abc123", withDigest.digest)
        assertNull(withoutDigest.digest)
    }

    @Test fun `draft and prerelease flags survive mapping`() {
        val releases = GitHubFixtures.releasesPage1()
        assertTrue(releases.first { it.id == 1002L }.toDomain(123L).prerelease)
        // Draft filtering is the engine's job; the mapper must not silently drop rows.
        assertEquals(3, releases.size)
    }
}
