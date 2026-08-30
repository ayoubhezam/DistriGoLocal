package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * Drives a block of content that slides up and shrinks away as the list under it scrolls, leaving
 * the top app bar above it pinned.
 *
 * Three screens hand-rolled this: the client and supplier details (identity card + balances + quick
 * actions) and the tournée screen (its stats banner). Their nested-scroll rules had drifted apart —
 * two of them consumed scroll in both directions inside `onPreScroll`, so a downward drag deep in
 * the list was eaten by an off-screen header trying to re-expand instead of the list actually
 * scrolling. This keeps the tournée screen's two-phase rule, which is the one that got that right:
 * collapse before the list moves, expand only after the list has run out.
 *
 * Create with [rememberDsCollapsingHeaderState], hand [nestedScrollConnection] to the scrolling
 * container and put [dsCollapsingHeader] on the block that should collapse.
 */
@Stable
class DsCollapsingHeaderState internal constructor(
    /**
     * Optional, and only the tournée screen passes one: reports whether the list is currently at or
     * above the boundary that a fling must not coast past. Null disables the guard entirely.
     */
    private val isAtFlingBoundary: State<(() -> Boolean)?>
) {
    /** How far the header is pushed up, in pixels. 0f expanded, -[heightPx] fully collapsed. */
    internal var offsetPx by mutableFloatStateOf(0f)

    /** Full expanded height, measured by [dsCollapsingHeader] on every layout pass. */
    internal var heightPx by mutableFloatStateOf(0f)

    /** 0f fully expanded, 1f fully collapsed — for fading header content as it goes. */
    val collapsedFraction: Float
        get() = if (heightPx <= 0f) 0f else (-offsetPx / heightPx).coerceIn(0f, 1f)

    /**
     * Recomputed once per fling: true only when that fling started deeper in the list than the
     * boundary, meaning it is this fling's own momentum that would carry it across. A fling that
     * starts at or past the boundary already — a fresh, separate swipe — is left free to run.
     */
    private var flingStartedBelowBoundary = false

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {

        // Collapsing (drag up, negative delta): consume into the header BEFORE the list scrolls,
        // so the header shrinks first and the list only starts moving once it is fully collapsed.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f) {
                val newOffset = (offsetPx + available.y).coerceIn(-heightPx, 0f)
                val consumed  = newOffset - offsetPx
                offsetPx = newOffset
                return Offset(0f, consumed)
            }

            // Scrolling toward the top: a fling (post-release momentum) that started deeper in the
            // list and is only now reaching the boundary gets stopped there for the rest of its
            // run. A live drag is never blocked.
            val guard = isAtFlingBoundary.value
            if (source == NestedScrollSource.SideEffect &&
                flingStartedBelowBoundary &&
                guard != null &&
                guard()
            ) {
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val guard = isAtFlingBoundary.value
            flingStartedBelowBoundary = guard != null && !guard()
            return super.onPreFling(available)
        }

        // Expanding (drag down, positive delta): only consume what is left AFTER the list itself
        // has consumed it — i.e. only once the list is already at its own top.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val newOffset = (offsetPx + available.y).coerceIn(-heightPx, 0f)
            val consumedByHeader = newOffset - offsetPx
            offsetPx = newOffset
            return Offset(0f, consumedByHeader)
        }
    }
}

/**
 * @param isAtFlingBoundary see [DsCollapsingHeaderState.isAtFlingBoundary]. Leave null unless the
 *   screen has a section a fling must not coast into; [rememberDsFlingBoundary] builds the usual one.
 */
@Composable
fun rememberDsCollapsingHeaderState(
    isAtFlingBoundary: (() -> Boolean)? = null
): DsCollapsingHeaderState {
    val guard = rememberUpdatedState(isAtFlingBoundary)
    return remember { DsCollapsingHeaderState(guard) }
}

/** The usual fling guard: "the list is at or above item [boundaryItemIndex]". */
fun rememberDsFlingBoundary(listState: LazyListState, boundaryItemIndex: Int): () -> Boolean =
    { listState.firstVisibleItemIndex <= boundaryItemIndex }

/**
 * Put on the content that should collapse. It measures its own natural height, reports it to
 * [state], then reports a shrunken height to the parent and draws itself shifted up by the current
 * offset, so the content below slides up to meet it.
 */
fun Modifier.dsCollapsingHeader(state: DsCollapsingHeaderState): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            state.heightPx = placeable.height.toFloat()
            val collapsedHeight = (placeable.height + state.offsetPx).coerceAtLeast(0f).roundToInt()
            layout(placeable.width, collapsedHeight) {
                placeable.placeRelative(0, state.offsetPx.roundToInt())
            }
        }
