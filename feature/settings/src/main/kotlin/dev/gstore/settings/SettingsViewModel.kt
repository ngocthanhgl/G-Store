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

/** A curated accent choice. Null ARGB = dynamic/system seed, not a color. */
data class AccentOption(val name: String, val argb: Int?)

val ACCENT_OPTIONS = listOf(
    AccentOption("Automatic", null),
    AccentOption("Baseline", 0xFF6750A4.toInt()),
    AccentOption("Ocean", 0xFF0061A4.toInt()),
    AccentOption("Forest", 0xFF1B6B4A.toInt()),
    AccentOption("Ember", 0xFFB3261E.toInt()),
    AccentOption("Plum", 0xFF7D5260.toInt()),
    AccentOption("Slate", 0xFF424242.toInt()),
    AccentOption("Gold", 0xFF7A5900.toInt()),
    AccentOption("Teal", 0xFF003731.toInt()),
)

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
    fun setAccent(argb: Int?) = launch { prefs.setAccentArgb(argb) }
    fun setLayout(layout: HomeLayout) = launch { prefs.setLayout(layout) }
    fun setDensity(density: CardDensity) = launch { prefs.setDensity(density) }
    fun setCardRadius(radiusDp: Int) = launch { prefs.setCardRadiusDp(radiusDp) }
    fun setReduceMotion(enabled: Boolean) = launch { prefs.setReduceMotion(enabled) }

    fun resetAll() = launch {
        prefs.setTheme(ThemeMode.SYSTEM)
        prefs.setDynamicColor(true)
        prefs.setAccentArgb(null)
        prefs.setLayout(HomeLayout.LIST)
        prefs.setDensity(CardDensity.COMFORTABLE)
        prefs.setCardRadiusDp(12)
        prefs.setReduceMotion(false)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
