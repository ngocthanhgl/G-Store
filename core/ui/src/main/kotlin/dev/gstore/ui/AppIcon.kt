package dev.gstore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Repository / developer icon: the owner's GitHub avatar when a URL is known,
 * monogram tile underneath as loading/error/empty fallback. The image simply
 * covers the monogram when it loads — no state juggling, no blank tiles.
 * Squircle corners (~28% radius) in the store-shelf idiom; decorative (the
 * name is always adjacent text), so no content description.
 */
@Composable
fun AppIcon(
    avatarUrl: String?,
    owner: String,
    size: Dp,
    modifier: Modifier = Modifier,
    monogramSize: Dp = 44.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.28f),
        color = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The monogram shows only while there is no image to cover it:
            // no URL, still loading, or load failed. A loaded avatar (even a
            // transparent PNG over the white well) never shows the letter.
            var loaded by remember(avatarUrl) { mutableStateOf(false) }
            if (avatarUrl.isNullOrBlank() || !loaded) {
                Text(
                    text = owner.take(1).uppercase(),
                    style = if (size >= monogramSize) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = Color(0xFF1B1B1B),
                )
            }
            if (!avatarUrl.isNullOrBlank()) {
                // Downsample to the drawn size: avatars arrive as large
                // PNGs and the full bitmap would sit in memory per row.
                val px = with(LocalDensity.current) { size.roundToPx() }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .size(px, px)
                        .build(),
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
