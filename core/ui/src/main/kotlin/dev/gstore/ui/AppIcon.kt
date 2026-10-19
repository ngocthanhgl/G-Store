package dev.gstore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.roundToInt

/**
 * Squircle corner radius for an app tile of [size]: ~46% of the edge reads as
 * a squircle in the store-shelf idiom, snapped to the 4dp grid so tiles never
 * carry a fractional radius.
 */
fun squircleDp(size: Dp): Dp {
    val dp = size.value
    return ((dp * 0.46f / 4f).roundToInt() * 4).coerceAtLeast(4).dp
}

/**
 * Repository / developer icon: the owner's GitHub avatar when a URL is known,
 * monogram tile underneath as loading/error/empty fallback. The image simply
 * covers the monogram when it loads — no state juggling, no blank tiles.
 *
 * The white well keeps transparent app artwork visually consistent in every
 * theme. Decorative: the name is always adjacent text, so no content description.
 */
@Composable
fun AppIcon(
    avatarUrl: String?,
    owner: String,
    size: Dp,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(squircleDp(size))
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The monogram shows only while there is no image to cover it:
            // no URL, still loading, or load failed. A loaded avatar (even a
            // transparent PNG over the well) never shows the letter.
            var loaded by remember(avatarUrl) { mutableStateOf(false) }
            if (avatarUrl.isNullOrBlank() || !loaded) {
                Text(
                    text = owner.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!avatarUrl.isNullOrBlank()) {
                // Downsample to the drawn size: avatars arrive as large PNGs
                // and the full bitmap would sit in memory per row. Remembered
                // so a recomposition does not rebuild the request.
                val px = with(LocalDensity.current) { size.roundToPx() }
                val context = LocalContext.current
                val request = remember(avatarUrl, px, context) {
                    ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .size(px, px)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    onError = { loaded = false },
                )
            }
        }
    }
}