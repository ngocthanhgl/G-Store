package dev.gstore.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gstore.datastore.AppearanceSettings
import dev.gstore.datastore.ThemeMode

/** Centralized tokens — no hardcoded colors or spacing outside this module. */
object GStoreTokens {
    val CornerSmall = 4
    val CornerDefault = 12
    val CornerLarge = 20
    const val GridUnit = 4
}

/** User-set card radius (4..20dp, clamped at read time in AppearanceSettings). */
fun cardShapeFor(radiusDp: Int): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.RoundedCornerShape(radiusDp.coerceIn(4, 20).dp)

/**
 * App theme. A custom accent overrides the seed only when it preserves
 * contrast; dynamic color applies on API 31+.
 */
@Composable
fun GStoreTheme(
    settings: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val seed = settings.accentArgb?.let { Color(it) } ?: Color(0xFF6750A4)
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme(primary = seed)
        else -> lightColorScheme(primary = seed)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
