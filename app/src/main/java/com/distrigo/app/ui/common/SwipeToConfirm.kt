package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * A list row that takes one action when swiped to the right, the way the dialer's swipe-to-call
 * does: a green band with [icon] and [label] opens behind the row as it moves, and past
 * [THRESHOLD] of its width [onConfirmed] runs, the phone ticks, and the row springs back. The row is
 * never dismissed — it stays where it is and shows its new status once the list refreshes.
 *
 * With [enabled] false (a bon already received, a sale already delivered) the row does not move at
 * all, so a swipe on it does nothing rather than opening a band that leads nowhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToConfirm(
    enabled     : Boolean,
    label       : String,
    icon        : ImageVector,
    onConfirmed : () -> Unit,
    content     : @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val haptics = LocalHapticFeedback.current
    val confirm by rememberUpdatedState(onConfirmed)
    // Once per swipe. confirmValueChange is asked again on every frame the row is dragged past the
    // threshold, not only when it is let go — without this a single swipe ran the action dozens of
    // times. Re-armed only when the row is back at rest.
    var fired by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        // Refusing the settle is what snaps the row back: the action runs, the row does not leave.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && !fired) {
                fired = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                confirm()
            }
            false
        },
        positionalThreshold = { width -> width * THRESHOLD }
    )
    LaunchedEffect(state) {
        snapshotFlow { state.dismissDirection }.collect { direction ->
            if (direction == SwipeToDismissBoxValue.Settled) fired = false
        }
    }

    SwipeToDismissBox(
        state                       = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent           = {
            Row(
                modifier              = Modifier
                    .fillMaxSize()
                    .clip(DsShapes.large)
                    .background(DsColors.Success)
                    .padding(horizontal = DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(label, color = Color.White, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold)
            }
        }
    ) {
        Box { content() }
    }
}

/**
 * "Vente V-12 marquée comme livrée — Annuler" after a swipe: the swipe acts at once, so an
 * accidental one is a tap to take back. [undo] runs only if "Annuler" is tapped.
 */
suspend fun SnackbarHostState.showUndo(message: String, undo: () -> Unit) {
    if (showSnackbar(message, actionLabel = "Annuler", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
}

/** How far, as a share of the row's width, a swipe has to go before it counts. */
private const val THRESHOLD = 0.4f
