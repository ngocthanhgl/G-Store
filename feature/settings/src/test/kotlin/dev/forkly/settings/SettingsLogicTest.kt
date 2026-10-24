package dev.forkly.settings

import dev.forkly.datastore.CardDensity
import dev.forkly.datastore.HomeLayout
import dev.forkly.datastore.AppearanceSettings
import dev.forkly.datastore.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pure label/accent logic — no Android framework needed. */
class SettingsLogicTest {
    @Test
    fun defaultsUseLightThemeAndBrandAccent() {
        assertEquals(ThemeMode.LIGHT, AppearanceSettings().theme)
        assertFalse(AppearanceSettings().dynamicColor)
        assertEquals(AppearanceSettings.DEFAULT_ACCENT_ARGB, AppearanceSettings().accentArgb)
        assertEquals(0xFFFF4F8B.toInt(), AppearanceSettings.DEFAULT_ACCENT_ARGB)
    }

    @Test
    fun accentSwatchesComeFromTheBrandPalette() {
        assertEquals(4, brandAccentSwatches.size)
        assertEquals(AppearanceSettings.DEFAULT_ACCENT_ARGB, brandAccentSwatches.first().second)
        assertEquals(
            listOf(0xFFFF4F8B, 0xFFFF7A59, 0xFFFF9A56, 0xFF7B4DFF).map { it.toInt() },
            brandAccentSwatches.map { it.second },
        )
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
