package dev.gstore.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.gstore.model.HomeSection
import dev.gstore.model.SectionConfig
import dev.gstore.model.TrendingWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.store by preferencesDataStore(name = "gstore_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class HomeLayout { GRID, LIST }
enum class CardDensity { COMFORTABLE, COMPACT }

/** Validated appearance settings — restored before first frame (flow E). */
data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentArgb: Int? = null,
    val density: CardDensity = CardDensity.COMFORTABLE,
    val layout: HomeLayout = HomeLayout.LIST,
    val cardRadiusDp: Int = 12,
    val reduceMotion: Boolean = false,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = intPreferencesKey("accent_argb")
        val DENSITY = stringPreferencesKey("density")
        val LAYOUT = stringPreferencesKey("layout")
        val RADIUS = intPreferencesKey("card_radius_dp")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SECTION_ORDER = stringPreferencesKey("section_order")
        val SECTION_HIDDEN = stringSetPreferencesKey("section_hidden")
        val HISTORY = stringSetPreferencesKey("view_history")
        val W_STARS = floatPreferencesKey("w_stars")
        val W_FORKS = floatPreferencesKey("w_forks")
        val W_RELEASES = floatPreferencesKey("w_releases")
        val W_DOWNLOADS = floatPreferencesKey("w_downloads")
        val CATALOG_ETAG = stringPreferencesKey("catalog_etag")
        val CATALOG_IMPORTED_AT = longPreferencesKey("catalog_imported_at")
    }

    /**
     * A corrupt or unreadable prefs file must never wedge the UI at defaults
     * forever (the classic “settings don’t stick” report): fall back to empty
     * prefs so every flow keeps emitting, and later writes re-create the file.
     */
    private fun resilientData(): Flow<Preferences> =
        context.store.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    companion object {
        val DEFAULT_ORDER = listOf(
            HomeSection.NEW,
            HomeSection.TRENDING,
            HomeSection.FAMOUS,
            HomeSection.UPDATED,
            HomeSection.RECOMMENDED,
            HomeSection.BROWSING,
        )
        const val HISTORY_MAX = 20
    }

    val appearance: Flow<AppearanceSettings> = resilientData().map { p ->
        AppearanceSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            accentArgb = p[Keys.ACCENT],
            density = runCatching { CardDensity.valueOf(p[Keys.DENSITY] ?: "COMFORTABLE") }
                .getOrDefault(CardDensity.COMFORTABLE),
            layout = runCatching { HomeLayout.valueOf(p[Keys.LAYOUT] ?: "LIST") }
                .getOrDefault(HomeLayout.LIST),
            // Clamp keeps customization from breaking layout (UI_UX_SPEC §5).
            cardRadiusDp = (p[Keys.RADIUS] ?: 12).coerceIn(4, 20),
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.store.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccentArgb(argb: Int?) {
        context.store.edit { p ->
            if (argb == null) p.remove(Keys.ACCENT) else p[Keys.ACCENT] = argb
        }
    }

    suspend fun setDensity(density: CardDensity) {
        context.store.edit { it[Keys.DENSITY] = density.name }
    }

    suspend fun setCardRadiusDp(radiusDp: Int) {
        context.store.edit { it[Keys.RADIUS] = radiusDp.coerceIn(4, 20) }
    }

    suspend fun setLayout(layout: HomeLayout) {
        context.store.edit { it[Keys.LAYOUT] = layout.name }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.store.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    /**
     * Home-section visibility/order (PRODUCT_SPEC §3). Section config is a user
     * preference, not catalog data — hence DataStore, not Room (see DECISIONS.md).
     */
    val sectionConfigs: Flow<List<SectionConfig>> = resilientData().map { p ->
        val order = (p[Keys.SECTION_ORDER]?.split(",")?.mapNotNull {
            runCatching { HomeSection.valueOf(it) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORDER)
        val hidden = p[Keys.SECTION_HIDDEN] ?: emptySet()
        order.mapIndexed { index, section ->
            SectionConfig(section, visible = section.name !in hidden, rank = index)
        }
    }

    suspend fun setSectionVisible(section: HomeSection, visible: Boolean) {
        context.store.edit { p ->
            val hidden = (p[Keys.SECTION_HIDDEN] ?: emptySet()).toMutableSet()
            if (visible) hidden.remove(section.name) else hidden.add(section.name)
            p[Keys.SECTION_HIDDEN] = hidden
        }
    }

    suspend fun moveSection(section: HomeSection, toRank: Int) {
        context.store.edit { p ->
            val order = (p[Keys.SECTION_ORDER]?.split(",")?.mapNotNull {
                runCatching { HomeSection.valueOf(it) }.getOrNull()
            }?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORDER).toMutableList()
            order.remove(section)
            order.add(toRank.coerceIn(0, order.size), section)
            p[Keys.SECTION_ORDER] = order.joinToString(",") { it.name }
        }
    }

    /** Recently viewed repos, newest first, bounded (Continue Browsing input). */
    val viewHistory: Flow<List<String>> = resilientData().map { p ->
        (p[Keys.HISTORY] ?: emptySet())
            .mapNotNull { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep < 0) null else entry.substring(sep + 1).toLongOrNull()?.let { ts ->
                    entry.substring(0, sep) to ts
                }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    suspend fun recordView(fullName: String, nowMillis: Long = System.currentTimeMillis()) {
        context.store.edit { p ->
            val kept = (p[Keys.HISTORY] ?: emptySet())
                .filter { !it.startsWith("$fullName|") }
                .toMutableSet()
            kept.add("$fullName|$nowMillis")
            val trimmed = kept.mapNotNull { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep < 0) null else entry.substring(sep + 1).toLongOrNull()?.let { entry to it }
            }.sortedByDescending { it.second }.take(HISTORY_MAX).map { it.first }.toSet()
            p[Keys.HISTORY] = trimmed
        }
    }

    val trendingWeights: Flow<TrendingWeights> = resilientData().map { p ->
        val defaults = TrendingWeights()
        TrendingWeights(
            stars = (p[Keys.W_STARS] ?: defaults.stars.toFloat()).toDouble(),
            forks = (p[Keys.W_FORKS] ?: defaults.forks.toFloat()).toDouble(),
            releases30d = (p[Keys.W_RELEASES] ?: defaults.releases30d.toFloat()).toDouble(),
            downloads30d = (p[Keys.W_DOWNLOADS] ?: defaults.downloads30d.toFloat()).toDouble(),
        )
    }

    suspend fun setTrendingWeights(weights: TrendingWeights) {
        context.store.edit { p ->
            p[Keys.W_STARS] = weights.stars.toFloat()
            p[Keys.W_FORKS] = weights.forks.toFloat()
            p[Keys.W_RELEASES] = weights.releases30d.toFloat()
            p[Keys.W_DOWNLOADS] = weights.downloads30d.toFloat()
        }
    }

    /**
     * Scanner-catalog freshness (flow A). The ETag makes repeat pulls cheap
     * (304 → no import); imported-at drives the 24h stale check on Home.
     */
    val catalogEtag: Flow<String?> = resilientData().map { p -> p[Keys.CATALOG_ETAG] }

    val catalogImportedAt: Flow<Long?> = resilientData().map { p -> p[Keys.CATALOG_IMPORTED_AT] }

    suspend fun setCatalogState(etag: String?, importedAt: Long) {
        context.store.edit { p ->
            if (etag == null) p.remove(Keys.CATALOG_ETAG) else p[Keys.CATALOG_ETAG] = etag
            p[Keys.CATALOG_IMPORTED_AT] = importedAt
        }
    }
}
