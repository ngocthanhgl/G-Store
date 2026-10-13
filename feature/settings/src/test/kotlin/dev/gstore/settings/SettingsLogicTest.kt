package dev.gstore.settings

import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure label/accent logic — no Android framework needed. */
class SettingsLogicTest {
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
