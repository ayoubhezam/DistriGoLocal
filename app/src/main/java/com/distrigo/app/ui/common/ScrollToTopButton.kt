package com.distrigo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * How high the button sits over the bottom of its list's box: the one choice a screen makes.
 * - [BottomBar]: a tab screen whose list runs under the app's bottom bar (about 70dp with labels).
 * - [Edge]: a list whose box ends where something else begins (a form's cart pill or « Suivant »
 *   row below it in the Column, or the screen's edge): just off that edge, never over it.
 */
enum class ScrollTopClearance(val bottom: Dp) {
    BottomBar(84.dp),
    Edge(16.dp),
}

/**
 * A long list with its back-to-top button. Wraps the list in a Box that takes [modifier] (give it
 * the list's weight or size) and puts the button over it, centred, the same in French and Arabic.
 * The list inside must use the same [state].
 */
@Composable
fun ScrollToTopBox(
    state: LazyListState,
    modifier: Modifier = Modifier,
    clearance: ScrollTopClearance = ScrollTopClearance.Edge,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        ScrollToTopButton(state, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance.bottom))
    }
}

@Composable
fun ScrollToTopBox(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    clearance: ScrollTopClearance = ScrollTopClearance.Edge,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        ScrollToTopButton(state, Modifier.align(Alignment.BottomCenter).padding(bottom = clearance.bottom))
    }
}

/** How long the button stays after the list stops, so there is time to reach it. */
private const val LINGER_MS = 1_700L

/**
 * The round "back to top" button of a long list, as in Samsung Contacts: it shows while the list
 * scrolls (either way) and is not at its top, stays [LINGER_MS] after the scroll stops, then fades.
 * A new scroll shows it again and restarts the wait. A tap glides back to the first item
 * ([glideToTop]): quick, easing out, the same length however far down the list is.
 *
 * Place it in the Box that holds the list, centred at the bottom: same spot in both languages.
 */
@Composable
fun ScrollToTopButton(state: LazyListState, modifier: Modifier = Modifier) =
    ScrollToTopButton(state as ScrollableState, { state.glideToTop() }, modifier)

@Composable
fun ScrollToTopButton(state: LazyGridState, modifier: Modifier = Modifier) =
    ScrollToTopButton(state as ScrollableState, { state.glideToTop() }, modifier)

/**
 * Lets the rows the jump brought in draw before the glide's clock starts: the first frame after a
 * jump builds a screenful of new rows, and a clock started under it found its 350 ms gone (measured
 * on the phone: the list froze 330 ms, then snapped).
 */
private suspend fun settle() {
    withFrameNanos { }
    withFrameNanos { }
}

/** How far from the top a long glide starts: farther down, the list first jumps here unseen. */
private const val GLIDE_LEAD_ITEMS = 12

/** The glide: fast at first, settling gently on the first item (Material's emphasized decelerate). */
private val glideSpec = tween<Float>(durationMillis = 350, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

/**
 * Back to the first item in one short glide. Far down, the list first jumps unseen to
 * [GLIDE_LEAD_ITEMS] from the top, so the glide is as short and smooth from item 900 as from 20.
 * The distance left is measured from the rows on screen (rows of one list are near alike); a last
 * snap absorbs what the estimate missed, and a scroll past the top just stops there.
 */
private suspend fun LazyListState.glideToTop() {
    if (firstVisibleItemIndex > GLIDE_LEAD_ITEMS) { scrollToItem(GLIDE_LEAD_ITEMS); settle() }
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return scrollToItem(0)
    val step = items.sumOf { it.size }.toFloat() / items.size + layoutInfo.mainAxisItemSpacing
    animateScrollBy(-(firstVisibleItemIndex * step + firstVisibleItemScrollOffset), glideSpec)
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) scrollToItem(0)
}

private suspend fun LazyGridState.glideToTop() {
    if (firstVisibleItemIndex > GLIDE_LEAD_ITEMS) { scrollToItem(GLIDE_LEAD_ITEMS); settle() }
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return scrollToItem(0)
    val rows = items.groupBy { it.row }.values.map { row -> row.maxOf { it.size.height } }
    val columns = items.maxOf { it.column } + 1
    val step = rows.sum().toFloat() / rows.size + layoutInfo.mainAxisItemSpacing
    animateScrollBy(-((firstVisibleItemIndex / columns) * step + firstVisibleItemScrollOffset), glideSpec)
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) scrollToItem(0)
}

@Composable
private fun ScrollToTopButton(state: ScrollableState, jumpToTop: suspend () -> Unit, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    // The jump is a scroll too: without this the button would flash back while it runs.
    var jumping by remember { mutableStateOf(false) }
    // A tap hides the button at once; the slow fade is for when the list comes to rest.
    var quickExit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        // collectLatest: a new scroll cancels the pending fade, which restarts the wait.
        snapshotFlow { state.isScrollInProgress to state.canScrollBackward }.collectLatest { (scrolling, belowTop) ->
            if (scrolling) {
                if (belowTop && !jumping) quickExit = false
                visible = belowTop && !jumping
            } else if (visible) {
                delay(LINGER_MS)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(150)),
        exit     = fadeOut(tween(if (quickExit) 100 else 300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B2B2B).copy(alpha = 0.72f))
                .clickable(role = Role.Button) {
                    quickExit = true
                    visible = false
                    jumping = true
                    scope.launch {
                        try { jumpToTop() } finally { jumping = false }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VerticalAlignTop,
                contentDescription = stringResource(R.string.common_cd_scroll_to_top),
                tint     = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
