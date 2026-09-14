package com.distrigo.app.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Keeps a keyed lazy list where an unkeyed one stays when its items change: at the same index and
 * offset, rather than following the item that was first on screen to wherever it now sits.
 *
 * Keys change more than item identity. When the data changes, a keyed list keeps its first visible
 * item in view by key — so after a new sort or search it would open part-way down, at that item's
 * new place, where the unkeyed list stayed put. Call this beside the list, with the items it shows.
 *
 * The position is re-requested only when [items] differ from those last shown; equal items land in
 * the same place either way. Like requestScrollToItem itself, this stops a fling that is under way at
 * the moment the items change.
 */
@Composable
fun KeepIndexScrollPosition(state: LazyListState, items: List<*>) {
    val shown = remember { ShownItems(items) }
    SideEffect {
        if (shown.items != items) {
            shown.items = items
            state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
}

/** [KeepIndexScrollPosition] for a lazy grid. */
@Composable
fun KeepIndexScrollPosition(state: LazyGridState, items: List<*>) {
    val shown = remember { ShownItems(items) }
    SideEffect {
        if (shown.items != items) {
            shown.items = items
            state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
}

// Not state: it is written after composition and read only there, never to trigger a recomposition.
private class ShownItems(var items: List<*>)
