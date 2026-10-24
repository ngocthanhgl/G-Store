package dev.forkly.data

import dev.forkly.model.Repository

/**
 * Forgiving local search over cached repos. The query splits on whitespace;
 * EVERY token must appear (case-insensitively) in at least one of the
 * name, owner, description, or topics — so "music player" finds
 * "music-player", and "tor browser" finds by owner or title alike.
 * Blank queries match nothing (the empty state guides, never a dump).
 * In-memory over the cached catalog: exact for hundreds of rows, and the
 * day a 10k catalog needs FTS this function is where it lands.
 */
fun searchFilter(repos: List<Repository>, rawQuery: String): List<Repository> {
    val tokens = rawQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    return repos.filter { repo ->
        val fields = listOf(repo.fullName, repo.owner, repo.description ?: "") + repo.topics
        val lowered = fields.map { it.lowercase() }
        tokens.all { token -> lowered.any { it.contains(token) } }
    }
}
