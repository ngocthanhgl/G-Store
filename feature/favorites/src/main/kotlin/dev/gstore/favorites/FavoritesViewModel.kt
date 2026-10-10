package dev.gstore.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.data.CatalogRepository
import dev.gstore.model.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(private val catalog: CatalogRepository) : ViewModel() {
    val favorites: StateFlow<List<Repository>> = catalog.observeFavoriteIds()
        .flatMapLatest { ids -> catalog.observeByIds(ids.toList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(repo: Repository) {
        viewModelScope.launch { catalog.toggleFavorite(repo) }
    }
}
