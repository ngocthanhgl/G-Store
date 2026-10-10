package dev.gstore.settings

import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun accentOptionsStartWithAutomaticNull() {
        val first = ACCENT_OPTIONS.first()
        assertEquals("Automatic", first.name)
        assertNull(first.argb)
    }

    @Test
    fun accentNamesAreUnique() {
        val names = ACCENT_OPTIONS.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
