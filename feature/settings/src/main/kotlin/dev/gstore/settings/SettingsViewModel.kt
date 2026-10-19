package dev.gstore.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.AppIconStyle
import dev.gstore.datastore.CardDensity
import dev.gstore.datastore.GitHubIdentity
import dev.gstore.datastore.HomeLayout
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.datastore.ThemeMode
import dev.gstore.datastore.TokenStore
import dev.gstore.network.GitHubApi
import dev.gstore.network.GitHubException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pure label maps — unit-tested without Android (SettingsLogicTest). */
fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
    ThemeMode.AMOLED -> "AMOLED"
}

fun densityLabel(density: CardDensity): String = when (density) {
    CardDensity.COMFORTABLE -> "Comfortable"
    CardDensity.COMPACT -> "Compact"
}

fun layoutLabel(layout: HomeLayout): String = when (layout) {
    HomeLayout.LIST -> "List"
    HomeLayout.GRID -> "Grid"
}

fun appIconLabel(style: AppIconStyle): String = when (style) {
    AppIconStyle.CLASSIC -> "Classic"
    AppIconStyle.RED_LIGHT -> "Red Light"
    AppIconStyle.RED_DARK -> "Red Dark"
}

class SettingsViewModel(
    private val prefs: PreferencesRepository,
    private val tokens: TokenStore,
    private val github: GitHubApi,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> = prefs.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    /** GitHub identity for the profile chip and quota display. */
    val identity: StateFlow<GitHubIdentity> = tokens.identity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GitHubIdentity())

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun dismissLoginError() {
        _loginError.value = null
    }

    fun setTheme(mode: ThemeMode) = launch { prefs.setTheme(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { prefs.setDynamicColor(enabled) }
    fun setAccentArgb(argb: Int?) = launch { prefs.setAccentArgb(argb) }
    fun setLayout(layout: HomeLayout) = launch { prefs.setLayout(layout) }
    fun setDensity(density: CardDensity) = launch { prefs.setDensity(density) }
    fun setCardRadius(radiusDp: Int) = launch { prefs.setCardRadiusDp(radiusDp) }
    fun setReduceMotion(enabled: Boolean) = launch { prefs.setReduceMotion(enabled) }
    fun setIncludePrereleases(enabled: Boolean) = launch { prefs.setIncludePrereleases(enabled) }
    fun setAppIcon(style: AppIconStyle) = launch { prefs.setAppIcon(style) }

    /**
     * Log in with a user-supplied Personal Access Token (no scopes needed).
     * The token is validated against `GET /user` before anything is stored;
     * a bad token surfaces as text, never a crash. Stored encrypted.
     */
    fun loginWithToken(rawToken: String) {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            _loginError.value = "Paste a token first."
            return
        }
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            github.token = token
            try {
                val user = github.fetchUser()
                if (user.login.isBlank()) throw GitHubException.Auth("user")
                tokens.save(token, user.login, user.avatarUrl)
            } catch (e: GitHubException.Auth) {
                github.token = tokens.token.value
                _loginError.value = "GitHub rejected that token. Check it and retry."
            } catch (e: Exception) {
                github.token = tokens.token.value
                _loginError.value = "Could not reach GitHub: ${e.message}"
            }
            _loginBusy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokens.clear()
            github.token = null
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
