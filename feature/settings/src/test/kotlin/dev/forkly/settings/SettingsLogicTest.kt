package dev.forkly.settings

import dev.forkly.datastore.CardDensity
import dev.forkly.datastore.HomeLayout
import dev.forkly.datastore.AppearanceSettings
import dev.forkly.datastore.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure label/accent logic — no Android framework needed. */
class SettingsLogicTest {
    @Test
    fun defaultsUseAmoledAndRedAccent() {
        assertEquals(ThemeMode.AMOLED, AppearanceSettings().theme)
        assertEquals(AppearanceSettings.DEFAULT_ACCENT_ARGB, AppearanceSettings().accentArgb)
    }

    @Test
    fun themeLabelsCoverAllModes() {
        assertEquals("Light", themeModeLabel(ThemeMode.LIGHT))
        assertEquals("Dark", themeModeLabel(ThemeMode.DARK))
        assertEquals("System", themeModeLabel(ThemeMode.SYSTEM))
    }

    @Test
    fun densityAndLayoutLabels() {
        assertEquals("Comfortable", densityLabel(CardDensity.COMFORTABLE))
        assertEquals("Compact", densityLabel(CardDensity.COMPACT))
        assertEquals("List", layoutLabel(HomeLayout.LIST))
        assertEquals("Grid", layoutLabel(HomeLayout.GRID))
    }
}
