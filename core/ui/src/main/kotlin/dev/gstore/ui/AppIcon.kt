package dev.gstore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

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
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = owner.take(1).uppercase(),
                style = if (size >= monogramSize) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
