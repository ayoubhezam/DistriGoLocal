package com.distrigo.app.ui.products

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.ProductImage
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The viewer's ground. Near-black rather than pure black, matching the app's ink. */
private val ViewerBackground = Color(0xFF0B0D12)

/**
 * Full-screen photo viewer: swipe between the product's photos, pinch or double-tap to zoom.
 *
 * ### This goes through Coil like everything else
 *
 * It used to decode the whole file by hand, on the composition thread, on the argument that a
 * full-screen surface wants every pixel the file has. That argument does not survive the
 * measurements: the viewer lays out at about 990 px on a 1080 px screen, so a 400 px file is
 * already being upscaled 2.5x before anyone pinches anything — the whole file was never the
 * question. What the hand-rolled decode did buy was a main-thread stall that grew with the stored
 * resolution, which is exactly the thing that would have broken as soon as the stored resolution
 * went up.
 *
 * So it uses [EntityImage] now. Coil sizes the decode to the layout, does it off the composition
 * thread, and serves it from the same memory cache the rest of the app shares — and because Coil
 * never upscales, the bitmap the gallery already decoded for this photo satisfies the viewer's
 * larger request too. One decode, reused.
 *
 * ### Zoom and paging have to agree
 *
 * A pinch and a page-swipe are the same gesture until one of them wins. The pager is disabled
 * while any page is zoomed in, so dragging a magnified photo pans it rather than flicking to the
 * next one; zooming back out hands paging back. Changing page resets the zoom, so a photo is never
 * inherited mid-magnification.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductImageViewer(
    images       : List<ProductImage>,
    initialIndex : Int,
    onClose      : () -> Unit
) {
    if (images.isEmpty()) {
        // Nothing to show. Closing rather than drawing an empty black screen is the only sensible
        // answer, and it is reachable: deleting the last photo while the viewer is open.
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.size - 1),
        pageCount   = { images.size }
    )
    val stripState = rememberLazyListState()

    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    fun resetZoom() { zoom = 1f; offset = Offset.Zero }

    // A new page always starts unzoomed.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            resetZoom()
            if (page < images.size) stripState.animateScrollToItem(page)
        }
    }

    BackHandler {
        // Back steps out of zoom first, then out of the viewer — the same order the user got in.
        if (zoom > 1f) resetZoom() else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Opaque, not a translucent scrim. This draws over the detail screen rather than in a
            // window of its own, so any transparency lets the screen underneath show through and
            // the photo ends up competing with the product card behind it.
            .background(ViewerBackground)
            // Swallows taps so nothing underneath reacts to a press meant for the viewer.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { if (zoom > 1f) resetZoom() else onClose() }
    ) {
        HorizontalPager(
            state             = pagerState,
            modifier          = Modifier.fillMaxSize(),
            userScrollEnabled = zoom <= 1f,
            pageSpacing       = DsSpacing.lg
        ) { page ->
            val isCurrent = page == pagerState.currentPage

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EntityImage(
                    ref                = images[page].ref,
                    contentDescription = "Photo ${page + 1} sur ${images.size}",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(DsSpacing.lg)
                        // Only the visible page is interactive, so a neighbour half-dragged
                        // into view cannot quietly take the gesture.
                        .then(
                            if (!isCurrent) Modifier else Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoom > 1f) resetZoom() else zoom = 2.5f
                                        },
                                        onTap = { if (zoom <= 1f) onClose() }
                                    )
                                }
                                // detectTransformGestures would be the obvious thing here, and it
                                // was — but once past touch slop it consumes every change that
                                // moved, one finger included. The pager sits above this in the
                                // tree and only ever sees what this leaves unconsumed, so a
                                // single-finger drag was swallowed and the photos could not be
                                // swiped. This is that detector with one condition added: the
                                // gesture is claimed only when a second finger is down, or when
                                // the photo is already magnified — which is exactly when the pager
                                // is disabled anyway. A one-finger drag at rest is left alone and
                                // reaches the pager. Zoom and pan are unchanged.
                                .pointerInput(Unit) {
                                    val scope = this
                                    awaitEachGesture {
                                        var pastTouchSlop = false
                                        var zoomAccum = 1f
                                        var panAccum = Offset.Zero
                                        val touchSlop = viewConfiguration.touchSlop

                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val canceled = event.changes.any { it.isConsumed }
                                            if (!canceled && (event.changes.size > 1 || zoom > 1f)) {
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()

                                                if (!pastTouchSlop) {
                                                    zoomAccum *= zoomChange
                                                    panAccum += panChange
                                                    val centroidSize =
                                                        event.calculateCentroidSize(useCurrent = false)
                                                    val zoomMotion = abs(1 - zoomAccum) * centroidSize
                                                    val panMotion = panAccum.getDistance()
                                                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                        pastTouchSlop = true
                                                    }
                                                }

                                                if (pastTouchSlop) {
                                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                                        val next = (zoom * zoomChange).coerceIn(1f, 5f)
                                                        // Panning is only meaningful while magnified,
                                                        // and the travel is bounded by how far the
                                                        // image overflows, so it can never be dragged
                                                        // off screen entirely.
                                                        offset = if (next <= 1f) {
                                                            Offset.Zero
                                                        } else {
                                                            val maxX = scope.size.width * (next - 1f) / 2f
                                                            val maxY = scope.size.height * (next - 1f) / 2f
                                                            Offset(
                                                                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                                                (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                                            )
                                                        }
                                                        zoom = next
                                                    }
                                                    event.changes.forEach {
                                                        if (it.positionChanged()) it.consume()
                                                    }
                                                }
                                            }
                                        } while (!canceled && event.changes.any { it.pressed })
                                    }
                                }
                        )
                        // Zoom is a draw-time transform, so it does not change the layout bounds
                        // Coil sizes its request from. A magnified photo is the decoded bitmap
                        // scaled up — exactly what it was when this decoded the file by hand.
                        .graphicsLayer {
                            if (isCurrent) {
                                scaleX = zoom; scaleY = zoom
                                translationX = offset.x; translationY = offset.y
                            }
                        }
                ) {
                    // Drawn while the decode is in flight as well as when the file is gone. Today
                    // it is never seen: the gallery behind the viewer has already put this exact
                    // bitmap in Coil's memory cache, and the entry is valid at this larger size
                    // because Coil does not upscale — one decode serves every surface.
                    Icon(
                        Icons.Default.ImageNotSupported,
                        contentDescription = "Photo indisponible",
                        tint     = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // ── Top bar: counter + close ──
        // A scrim behind the controls, because they sit over whatever the photo happens to be.
        // Zoomed into a bright label, a translucent pill on its own leaves the close button barely
        // visible — which is the moment the user most needs to find it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(ViewerBackground.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
        ) {
            if (images.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(DsShapes.pill)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.xs)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .clip(DsShapes.pill)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
            }
        }

        // ── Bottom strip ──
        // Hidden while zoomed: the photo is the whole point at that moment, and the strip would
        // sit over the part being examined.
        if (images.size > 1 && zoom <= 1f) {
            LazyRow(
                state = stripState,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                contentPadding = PaddingValues(horizontal = DsSpacing.lg),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = DsSpacing.xl)
                    .fillMaxWidth()
            ) {
                items(images, key = { it.id }) { image ->
                    val index = images.indexOf(image)
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(DsShapes.medium)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = DsShapes.medium
                            )
                            .graphicsLayer { alpha = if (active) 1f else 0.55f }
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                        contentAlignment = Alignment.Center
                    ) {
                        EntityImage(
                            ref                = image.ref,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
                        ) {
                            Icon(
                                Icons.Default.ImageNotSupported,
                                contentDescription = null,
                                tint     = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
