package dev.forkly.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.forkly.datastore.AppearanceSettings
import dev.forkly.datastore.ThemeMode
import dev.forkly.model.AppRadius

/** App-wide typeface: Roboto Condensed variable font (Apache-2.0, 363 KB),
 * instances at 400/500/700 cover the M3 scale. Code blocks keep Monospace. */
private val Condensed = FontFamily(
    Font(
        R.font.roboto_condensed,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.roboto_condensed,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.roboto_condensed,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Default M3 scale re-typed in the condensed face. The base scale is read
 * from the ambient theme (this M3 release has no no-arg Typography
 * constructor); every style keeps size/weight/leading, only family changes. */
@Composable
private fun condensedType(): Typography {
    val t = MaterialTheme.typography
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Condensed),
        displayMedium = t.displayMedium.copy(fontFamily = Condensed),
        displaySmall = t.displaySmall.copy(fontFamily = Condensed),
        headlineLarge = t.headlineLarge.copy(fontFamily = Condensed),
        headlineMedium = t.headlineMedium.copy(fontFamily = Condensed),
        headlineSmall = t.headlineSmall.copy(fontFamily = Condensed),
        titleLarge = t.titleLarge.copy(fontFamily = Condensed),
        titleMedium = t.titleMedium.copy(fontFamily = Condensed),
        titleSmall = t.titleSmall.copy(fontFamily = Condensed),
        bodyLarge = t.bodyLarge.copy(fontFamily = Condensed),
        bodyMedium = t.bodyMedium.copy(fontFamily = Condensed),
        bodySmall = t.bodySmall.copy(fontFamily = Condensed),
        labelLarge = t.labelLarge.copy(fontFamily = Condensed),
        labelMedium = t.labelMedium.copy(fontFamily = Condensed),
        labelSmall = t.labelSmall.copy(fontFamily = Condensed),
    )
}

/**
 * The one place the user's corner-radius setting lives at runtime.
 *
 * The Settings slider used to write to DataStore and change nothing on screen:
 * every surface hardcoded its own radius. Threading an Int through every
 * composable signature is what caused that in the first place, so the value
 * goes through a CompositionLocal instead — any component reads it with no
 * plumbing, which is what makes "drag the slider, the whole app follows" work.
 *
 * Provided by [ForklyTheme], so it is always in scope.
 */
val LocalCardRadius = staticCompositionLocalOf { AppRadius.DEFAULT }

/**
 * Shape for cards and content surfaces. Follows the user's slider.
 *
 * Hybrid model: controls (buttons, chips, filters, badges) use [pillShape] and
 * are unaffected by the slider, because a chip that is "12dp rounded" instead
 * of fully round reads as a mistake. Only large surfaces follow the slider.
 */
@Composable
fun cardShapeFor(radiusDp: Int = LocalCardRadius.current): Shape =
    RoundedCornerShape(AppRadius.clamp(radiusDp).dp)

/** Large content surface — same slider-driven scale as [cardShapeFor]. */
@Composable
fun panelShape(): Shape = cardShapeFor()

/**
 * Shape for IMAGES and other fixed-size visual surfaces: screenshots, gallery
 * tiles, code and quote surfaces.
 *
 * Deliberately capped at [AppRadius.IMAGE_MAX]. The slider defaults to 24dp,
 * and 24dp on a full-width screenshot turned every image into a lens and cut
 * into its content; at 12dp an image still reads as rounded but nothing is
 * clipped. Images are not a "card", so the card slider should not reach them.
 */
@Composable
fun imageShapeFor(radiusDp: Int = LocalCardRadius.current): Shape =
    RoundedCornerShape(AppRadius.clamp(radiusDp).coerceAtMost(AppRadius.IMAGE_MAX).dp)

/**
 * Fully rounded control shape. Radius is half the height, so it stays a true
 * stadium at any control size. Deliberately NOT slider-driven.
 */
val pillShape: Shape = RoundedCornerShape(percent = 50)

/**
 * App theme. A custom accent overrides the seed only when it preserves
 * contrast; dynamic color applies on API 31+.
 */
@Composable
fun ForklyTheme(
    settings: AppearanceSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val seed = settings.accentArgb?.let { Color(it) }
        ?: Color(AppearanceSettings.DEFAULT_ACCENT_ARGB)
    val scheme = when {
        // AMOLED: every surface pure black, every text pure white. Dynamic
        // color is bypassed — it would wash out the black.
        settings.theme == ThemeMode.AMOLED -> darkColorScheme(
            primary = seed,
            secondary = seed,
            tertiary = seed,
            onPrimary = onAccent(seed),
            onSecondary = onAccent(seed),
            onTertiary = onAccent(seed),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color.Black,
            onSurfaceVariant = Color.White,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            primaryContainer = seed,
            secondaryContainer = seed,
            tertiaryContainer = seed,
            onPrimaryContainer = onAccent(seed),
            onSecondaryContainer = onAccent(seed),
            onTertiaryContainer = onAccent(seed),
        )
        settings.dynamicColor && settings.accentArgb == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme(
            primary = seed,
            secondary = seed,
            tertiary = seed,
            onPrimary = onAccent(seed),
            onSecondary = onAccent(seed),
            onTertiary = onAccent(seed),
            primaryContainer = seed,
            secondaryContainer = seed,
            tertiaryContainer = seed,
            onPrimaryContainer = onAccent(seed),
            onSecondaryContainer = onAccent(seed),
            onTertiaryContainer = onAccent(seed),
        )
        else -> lightColorScheme(
            primary = seed,
            secondary = seed,
            tertiary = seed,
            onPrimary = onAccent(seed),
            onSecondary = onAccent(seed),
            onTertiary = onAccent(seed),
            primaryContainer = seed,
            secondaryContainer = seed,
            tertiaryContainer = seed,
            onPrimaryContainer = onAccent(seed),
            onSecondaryContainer = onAccent(seed),
            onTertiaryContainer = onAccent(seed),
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = condensedType(),
        content = { CompositionLocalProvider(LocalCardRadius provides settings.cardRadiusDp, content = content) },
    )
}

private fun onAccent(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White
