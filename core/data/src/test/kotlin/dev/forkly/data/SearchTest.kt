package dev.forkly.data

import dev.forkly.model.Repository
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private fun repo(
        id: Long,
        fullName: String,
        owner: String,
        desc: String? = null,
        topics: List<String> = emptyList(),
    ) = Repository(
        id = id,
        fullName = fullName,
        owner = owner,
        avatarUrl = null,
        description = desc,
        stars = 0,
        forks = 0,
        topics = topics,
        updatedAt = null,
    )

    private val repos = listOf(
        repo(1, "org/musicbox", "org", "A music player", listOf("music-player", "android")),
        repo(2, "vpn-guard/vpn", "vpn-guard", null, listOf("vpn", "android")),
    )

    @Test fun `blank query matches nothing`() {
        assertEquals(emptyList<Repository>(), searchFilter(repos, "   "))
    }

    @Test fun `single token matches name owner description or topics`() {
        assertEquals(listOf(1L), searchFilter(repos, "musicbox").map { it.id })
        assertEquals(listOf(2L), searchFilter(repos, "VPN-GUARD").map { it.id })
        assertEquals(listOf(1L), searchFilter(repos, "player").map { it.id })
        assertEquals(listOf(1L, 2L), searchFilter(repos, "android").map { it.id })
    }

    @Test fun `multi-word queries need every token`() {
        // "music player" finds musicbox (music in name, player in topics)...
        assertEquals(listOf(1L), searchFilter(repos, "music player").map { it.id })
        // ...but "music vpn" matches nowhere: AND, not OR.
        assertEquals(emptyList<Repository>(), searchFilter(repos, "music vpn"))
    }

    @Test fun `owner slash name finds the repo`() {
        assertEquals(listOf(2L), searchFilter(repos, "vpn-guard/vpn").map { it.id })
    }
}
