package dev.gstore.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.datastore.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure label maps — unit-tested without Android (SettingsLogicTest). */
fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

fun densityLabel(density: CardDensity): String = when (density) {
    CardDensity.COMFORTABLE -> "Comfortable"
    CardDensity.COMPACT -> "Compact"
}

fun layoutLabel(layout: HomeLayout): String = when (layout) {
    HomeLayout.LIST -> "List"
    HomeLayout.GRID -> "Grid"
}

class SettingsViewModel(private val prefs: PreferencesRepository) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> = prefs.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    fun setTheme(mode: ThemeMode) = launch { prefs.setTheme(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { prefs.setDynamicColor(enabled) }
    fun setLayout(layout: HomeLayout) = launch { prefs.setLayout(layout) }
    fun setDensity(density: CardDensity) = launch { prefs.setDensity(density) }
    fun setCardRadius(radiusDp: Int) = launch { prefs.setCardRadiusDp(radiusDp) }
    fun setReduceMotion(enabled: Boolean) = launch { prefs.setReduceMotion(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
