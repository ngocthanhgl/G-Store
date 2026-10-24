package dev.forkly.datastore

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
import dev.forkly.model.AppRadius
import dev.forkly.model.HomeSection
import dev.forkly.model.SectionConfig
import dev.forkly.model.TrendingWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.store by preferencesDataStore(name = "forkly_prefs")

enum class ThemeMode { LIGHT, DARK, SYSTEM, AMOLED }
enum class HomeLayout { GRID, LIST }
enum class CardDensity { COMFORTABLE, COMPACT }

/** Validated appearance settings — restored before first frame (flow E). */
data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val dynamicColor: Boolean = false,
    val accentArgb: Int? = DEFAULT_ACCENT_ARGB,
    val density: CardDensity = CardDensity.COMFORTABLE,
    val layout: HomeLayout = HomeLayout.GRID,
    val cardRadiusDp: Int = AppRadius.DEFAULT,
    val reduceMotion: Boolean = false,
    /** Beta builds participate in pick/download/update. Off = stable only. */
    val includePrereleases: Boolean = false,
) {
    companion object {
        /** Forkly brand pink, the midpoint of the logo gradient. */
        const val DEFAULT_ACCENT_ARGB = 0xFFFF4F8B.toInt()
    }
}

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = intPreferencesKey("accent_argb")
        val ACCENT_AUTO = booleanPreferencesKey("accent_auto")
        val DENSITY = stringPreferencesKey("density")
        val LAYOUT = stringPreferencesKey("layout")
        // Key renamed from "card_radius_dp". Every value stored under the old key
    // was meaningless — the slider wrote here and nothing read it — so keeping
    // them would pin existing users to a radius they never chose and would miss
    // the new default. The stale key is intentionally left unread.
    val CARD_RADIUS = intPreferencesKey("card_radius_dp_v2")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val INCLUDE_PRERELEASES = booleanPreferencesKey("include_prereleases")
    val PERMISSION_PROMPTED = booleanPreferencesKey("permission_prompted")
    val SECTION_ORDER = stringPreferencesKey("section_order")
        val SECTION_HIDDEN = stringSetPreferencesKey("section_hidden")
        val W_STARS = floatPreferencesKey("w_stars")
        val W_FORKS = floatPreferencesKey("w_forks")
        val W_RELEASES = floatPreferencesKey("w_releases")
        val W_DOWNLOADS = floatPreferencesKey("w_downloads")
        val CATALOG_ETAG = stringPreferencesKey("catalog_etag")
        val CATALOG_IMPORTED_AT = longPreferencesKey("catalog_imported_at")
        val MANUAL_REPOS = stringSetPreferencesKey("manual_repos")
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
            HomeSection.TOP_CHARTS,
            HomeSection.CATEGORIES,
            HomeSection.FAVORITES,
            HomeSection.TRENDING,
            HomeSection.FAMOUS,
            HomeSection.UPDATED,
        )

        /**
         * Union of a stored order with the current default: sections added by
         * later app versions appear (visible) instead of vanishing for
         * existing installs that already persisted an order.
         */
        fun resolveOrder(stored: List<HomeSection>?): List<HomeSection> {
            val base = stored?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORDER
            return base + (DEFAULT_ORDER - base.toSet())
        }
    }

    val appearance: Flow<AppearanceSettings> = resilientData().map { p ->
        AppearanceSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "LIGHT") }
                .getOrDefault(ThemeMode.LIGHT),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            accentArgb = if (p[Keys.ACCENT_AUTO] == true) {
                null
            } else {
                p[Keys.ACCENT] ?: AppearanceSettings.DEFAULT_ACCENT_ARGB
            },
            density = runCatching { CardDensity.valueOf(p[Keys.DENSITY] ?: "COMFORTABLE") }
                .getOrDefault(CardDensity.COMFORTABLE),
            layout = runCatching { HomeLayout.valueOf(p[Keys.LAYOUT] ?: "GRID") }
                .getOrDefault(HomeLayout.GRID),
            // Clamp keeps customization from breaking layout (UI_UX_SPEC §5).
            cardRadiusDp = AppRadius.clamp(p[Keys.CARD_RADIUS] ?: AppRadius.DEFAULT),
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
            includePrereleases = p[Keys.INCLUDE_PRERELEASES] ?: false,
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
            if (argb == null) {
                p.remove(Keys.ACCENT)
                p[Keys.ACCENT_AUTO] = true
            } else {
                p[Keys.ACCENT] = argb
                p.remove(Keys.ACCENT_AUTO)
            }
        }
    }

    suspend fun setDensity(density: CardDensity) {
        context.store.edit { it[Keys.DENSITY] = density.name }
    }

    suspend fun setCardRadiusDp(radiusDp: Int) {
        context.store.edit { it[Keys.CARD_RADIUS] = AppRadius.clamp(radiusDp) }
    }

    suspend fun setLayout(layout: HomeLayout) {
        context.store.edit { it[Keys.LAYOUT] = layout.name }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.store.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setIncludePrereleases(enabled: Boolean) {
        context.store.edit { it[Keys.INCLUDE_PRERELEASES] = enabled }
    }

    /**
     * Home-section visibility/order (PRODUCT_SPEC §3). Section config is a user
     * preference, not catalog data — hence DataStore, not Room (see DECISIONS.md).
     */
    val sectionConfigs: Flow<List<SectionConfig>> = resilientData().map { p ->
        val order = resolveOrder(p[Keys.SECTION_ORDER]?.split(",")?.mapNotNull {
            runCatching { HomeSection.valueOf(it) }.getOrNull()
        })
        val hidden = p[Keys.SECTION_HIDDEN] ?: emptySet()
        order.mapIndexed { index, section ->
            SectionConfig(section, visible = section.name !in hidden, rank = index)
        }
    }

    val permissionPrompted: Flow<Boolean> =
        resilientData().map { it[Keys.PERMISSION_PROMPTED] ?: false }

    suspend fun markPermissionPrompted() {
        context.store.edit { it[Keys.PERMISSION_PROMPTED] = true }
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
            val order = resolveOrder(p[Keys.SECTION_ORDER]?.split(",")?.mapNotNull {
                runCatching { HomeSection.valueOf(it) }.getOrNull()
            }).toMutableList()
            order.remove(section)
            order.add(toRank.coerceIn(0, order.size), section)
            p[Keys.SECTION_ORDER] = order.joinToString(",") { it.name }
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

    /**
     * Repositories the user added by hand (link or owner/name). Rendered as
     * their own shelf on Installed so hand-added entries stay visible even
     * before any download exists for them.
     */
    val manualRepos: Flow<Set<String>> = resilientData().map { p -> p[Keys.MANUAL_REPOS] ?: emptySet() }

    suspend fun addManualRepo(fullName: String) {
        val name = fullName.trim()
        if (name.isEmpty()) return
        context.store.edit { p ->
            p[Keys.MANUAL_REPOS] = (p[Keys.MANUAL_REPOS] ?: emptySet()) + name
        }
    }

    suspend fun removeManualRepo(fullName: String) {
        context.store.edit { p ->
            p[Keys.MANUAL_REPOS] = (p[Keys.MANUAL_REPOS] ?: emptySet()) - fullName
        }
    }
}
