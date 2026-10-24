package dev.forkly.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * Fluent System Icons (Microsoft, MIT) — 14 regular-24 vectors vendored
 * locally (see res/drawable/ic_fluent_*); the 29 MB upstream AAR was dropped.
 * Soft rounded regular-24 glyphs replace every Material icon in the app; the
 * launcher icon is untouched. Getters are @Composable because painterResource
 * is; call sites stay positional (`Icon(Fluent.Home, ...)`) so the swap is
 * token-for-token. Favorite states use outlined and filled heart assets.
 */
object Fluent {
    val Add: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_add_24_regular)
    val ArrowDownload: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_arrow_download_24_regular)
    val ArrowLeft: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_arrow_left_24_regular)
    val ArrowSync: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_arrow_sync_24_regular)
    val Checkmark: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_checkmark_24_regular)
    val Close: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_dismiss_24_regular)
    val ChevronDown: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_chevron_down_24_regular)
    val ChevronRight: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_chevron_right_24_regular)
    val Heart: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_heart_24_regular)
    val HeartFilled: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_heart_24_filled)
    val Home: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_home_24_regular)
    val Person: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_person_24_regular)
    val Search: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_search_24_regular)
    val Settings: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_settings_24_regular)
    val Share: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_share_24_regular)
    val Star: Painter
        @Composable get() = painterResource(R.drawable.ic_fluent_star_24_regular)
}
