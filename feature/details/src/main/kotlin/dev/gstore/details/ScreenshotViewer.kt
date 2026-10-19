package dev.gstore.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import dev.gstore.ui.Fluent
import dev.gstore.ui.GStoreSpace

/**
 * Full-screen screenshot viewer, Play Store style: swipe between images, pinch
 * to zoom, tap to dismiss.
 *
 * Decoding stays bounded ([DecodePx]) — the same cap the inline strip uses, so
 * opening a 4000px screenshot cannot outrun the heap.
 */
@Composable
fun ScreenshotViewer(
    images: List<RepositoryImage>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(url = images[page].url, onDismiss = onDismiss)
            }
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = GStoreSpace.Gutter),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Fluent.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Text(
                images.getOrNull(pagerState.currentPage)?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(GStoreSpace.Medium),
            )
        }
    }
}

/** One image: pager owns one-finger swipes; this image owns pinch and zoomed panning. */
@Composable
private fun ZoomableImage(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(MinScale) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var imageSize by remember(url) { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        val nextScale = (scale * zoom).coerceIn(MinScale, MaxScale)
        scale = nextScale
        if (nextScale > MinScale) {
            val limit = (minOf(imageSize.width, imageSize.height) * (nextScale - 1f)) / 2f
            offset = Offset(
                (offset.x + pan.x).coerceIn(-limit, limit),
                (offset.y + pan.y).coerceIn(-limit, limit),
            )
        } else {
            offset = Offset.Zero
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { imageSize = it }
            .transformable(transformState, canPan = { scale > MinScale })
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double-tap toggles between fit and 2x.
                        if (scale > MinScale) {
                            scale = MinScale
                            offset = Offset.Zero
                        } else {
                            scale = DoubleTapScale
                        }
                    },
                    onTap = { if (scale <= MinScale) onDismiss() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(Size(DecodePx, DecodePx))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

private const val DecodePx = 1600
private const val MinScale = 1f
private const val MaxScale = 4f
private const val DoubleTapScale = 2f