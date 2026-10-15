package dev.gstore.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * GitHub identity: a user-supplied Personal Access Token plus the login it
 * validated as. The token lives in EncryptedSharedPreferences (AES-256-GCM
 * via AndroidKeyStore) — never in DataStore, never in logs, never in the
 * repo. No scopes are needed: identity + rate-limit quota only.
 */
data class GitHubIdentity(
    val login: String = "",
    val avatarUrl: String? = null,
)

class TokenStore(context: Context) {
    private val appContext = context.applicationContext

    private val encrypted: SharedPreferences by lazy {
        // security-crypto 1.0.0 orders params as (fileName, alias, context).
        EncryptedSharedPreferences.create(
            "gstore_secrets",
            "gstore_master_key",
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _token = MutableStateFlow<String?>(null)
    /** Raw token for the Authorization header. Null = anonymous (60 req/h). */
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _identity = MutableStateFlow(GitHubIdentity())
    val identity: StateFlow<GitHubIdentity> = _identity.asStateFlow()

    /** Load persisted credentials into memory. Call once at startup. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        _token.value = encrypted.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        _identity.value = GitHubIdentity(
            login = encrypted.getString(KEY_LOGIN, "") ?: "",
            avatarUrl = encrypted.getString(KEY_AVATAR, null),
        )
        if (_token.value == null) _identity.value = GitHubIdentity()
    }

    suspend fun save(token: String, login: String, avatarUrl: String?) =
        withContext(Dispatchers.IO) {
            encrypted.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_LOGIN, login)
                .putString(KEY_AVATAR, avatarUrl)
                .apply()
            _token.value = token
            _identity.value = GitHubIdentity(login, avatarUrl)
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        encrypted.edit().clear().apply()
        _token.value = null
        _identity.value = GitHubIdentity()
    }

    private companion object {
        const val KEY_TOKEN = "github_pat"
        const val KEY_LOGIN = "github_login"
        const val KEY_AVATAR = "github_avatar"
    }
}
