package dev.forkly.data

import dev.forkly.model.RepoSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Catalog signals computed ONCE per database change and shared by every
 * consumer (Home, Category pages). Per-repo signal assembly is N+1 DAO
 * queries — doing it per ViewModel made every navigation pay seconds.
 * The cache pays it once; screens open with data already in memory.
 */
class SignalsCache(
    private val catalog: CatalogRepository,
    scope: CoroutineScope,
) {
    val signals: StateFlow<List<RepoSignals>> = catalog.observeRepositories()
        .mapLatest { repos ->
            // One bad repo must never zero the whole Home: drop failures.
            repos.mapNotNull { repo -> runCatching { catalog.signals(repo) }.getOrNull() }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
