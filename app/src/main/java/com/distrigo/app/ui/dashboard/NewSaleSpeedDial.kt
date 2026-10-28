package com.distrigo.app.ui.dashboard

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/** The whole speed-dial, open to closed, in ms; the second action starts this much after the first. */
private const val SpeedDialMs = 220
private const val SpeedDialStaggerMs = 40

/**
 * What « Nouvelle vente » offers while a tournée is open: « Vente dépôt » or « Vente tournée ». Owned by the
 * Dashboard, which shows it with [open] and gets [onClosed] once it has folded away.
 */
class NewSaleSpeedDialState {
    var visible by mutableStateOf(false); internal set
    /** Asked open (true) or closed (false): the dial animates towards it. */
    var target by mutableStateOf(false); internal set
    fun open() { visible = true; target = true }
    fun close() { target = false }
}

/**
 * The speed-dial over the whole window, the bottom bar included: a scrim, the « Nouvelle vente » tile drawn again
 * where it sits ([tile], in window pixels) with its icon turned to ✕, and the two actions coming out of it to its
 * right, as in the owner's sketch. Back, a tap on the scrim, anywhere else or on the tile close it and start nothing;
 * an action closes it at once and starts its sale.
 */
@Composable
fun NewSaleSpeedDial(
    state       : NewSaleSpeedDialState,
    tile        : Rect,
    tileLabelSize: TextUnit,
    tourneeLabel: String,
    onDepot     : () -> Unit,
    onTournee   : () -> Unit,
) {
    if (!state.visible) return
    val context = LocalContext.current
    // « Supprimer les animations »: opened and closed at once
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.target) {
        val to = if (state.target) 1f else 0f
        if (animate) progress.animateTo(to, tween((SpeedDialMs * kotlin.math.abs(to - progress.value)).roundToInt(), easing = FastOutSlowInEasing))
        else progress.snapTo(to)
        if (!state.target) state.visible = false
    }
    val start = { action: () -> Unit ->
        // Gone at once, not folded: the sale's form opens over it
        scope.launch { progress.snapTo(0f) }
        state.target = false
        state.visible = false
        action()
    }
    // A window over the whole screen, the bottom bar and the system bars included (a popup stops above the
    // navigation bar), with no dim or animation of its own: the scrim and the motion below are the dial's.
    // Back reaches it as a dismiss request.
    Dialog(
        onDismissRequest = { state.close() },
        properties       = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        val p = progress.value
        val density = LocalDensity.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        // [tile] is in window pixels from the left in either direction: everything here is placed from the left
        Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
            // The scrim: in over the first half of the opening; out over the last half of the closing, once the
            // actions are nearly back in the tile
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = (p * 2f).coerceAtMost(1f) }
                    .background(DsColors.TextPrimary.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.close() }
            )

            // The tile, lit above the scrim, its icon turning to ✕
            Box(
                Modifier
                    .absoluteOffset { IntOffset(tile.left.roundToInt(), tile.top.roundToInt()) }
                    .size(with(density) { tile.width.toDp() }, with(density) { tile.height.toDp() })
            ) {
                QuickAction(
                    icon = Icons.Default.PointOfSale, label = stringResource(R.string.dashboard_new_sale), accent = DsColors.PrimaryDark,
                    fontSize = tileLabelSize, modifier = Modifier.fillMaxSize(),
                    clickLabel = stringResource(R.string.dashboard_cd_close_choice),
                    iconContent = {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, tint = DsColors.PrimaryDark,
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 90f * p; alpha = 1f - p })
                        Icon(Icons.Default.Close, contentDescription = null, tint = DsColors.PrimaryDark,
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -90f * (1f - p); alpha = p })
                    },
                    onClick = { state.close() },
                )
            }

            // The two actions, out of the tile's centre to its end (right in French, left in Arabic, towards the
            // screen's middle either way): up for the dépôt, down for the tournée
            val from = tile.center
            val gap = with(density) { DsSpacing.lg.toPx() + ActionSize.toPx() / 2 }
            val x = if (isRtl) tile.left - gap else tile.right + gap
            val dy = with(density) { (ActionSize / 2 + DsSpacing.sm).toPx() }
            SpeedDialAction(
                icon = Icons.Default.Storefront, label = stringResource(R.string.dashboard_sale_depot), description = stringResource(R.string.dashboard_sale_depot),
                progress = stagger(p, 0), from = from, to = androidx.compose.ui.geometry.Offset(x, from.y - dy),
                onClick = { start(onDepot) },
            )
            SpeedDialAction(
                icon = Icons.Default.LocalShipping, label = stringResource(R.string.dashboard_sale_tournee), description = stringResource(R.string.dashboard_cd_sale_tournee, tourneeLabel),
                progress = stagger(p, 1), from = from, to = androidx.compose.ui.geometry.Offset(x, from.y + dy),
                onClick = { start(onTournee) },
            )
        }
    }
}

private val ActionSize = 48.dp

/** The [index]th action's own progress: it starts [SpeedDialStaggerMs] after the one before, and ends with the dial. */
private fun stagger(p: Float, index: Int): Float {
    val delay = index * SpeedDialStaggerMs.toFloat() / SpeedDialMs
    return ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
}

/**
 * A mini action: a navy circle and its white icon, its name in a pill to the right. [progress] 0 is inside the tile
 * (at [from], small and faded), 1 its place ([to], the circle's centre).
 */
@Composable
private fun SpeedDialAction(
    icon: ImageVector, label: String, description: String, progress: Float,
    from: androidx.compose.ui.geometry.Offset, to: androidx.compose.ui.geometry.Offset, onClick: () -> Unit,
) {
    if (progress <= 0f) return
    val density = LocalDensity.current
    var rowHeight by remember { mutableStateOf(0) }
    var rowWidth by remember { mutableStateOf(0) }
    // In Arabic the row flips: its circle is at the right end, the label to its left
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val half = with(density) { ActionSize.toPx() / 2 }
    val cx = lerp(from.x, to.x, progress)
    val cy = lerp(from.y, to.y, progress)
    Row(
        modifier = Modifier
            .absoluteOffset {
                val left = if (isRtl) cx + half - rowWidth else cx - half
                IntOffset(left.roundToInt(), (cy - rowHeight / 2f).roundToInt())
            }
            .onSizeChanged { rowHeight = it.height; rowWidth = it.width }
            .graphicsLayer {
                alpha = progress
                val s = lerp(0.4f, 1f, progress)
                scaleX = s; scaleY = s
                // Grows out of its circle's centre, not the row's
                val circleX = if (isRtl) size.width - half else half
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(circleX / size.width.coerceAtLeast(1f), 0.5f)
            }
            .clip(DsShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
        Box(
            Modifier.size(ActionSize).shadow(4.dp, CircleShape).clip(CircleShape).background(DsColors.PrimaryDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            modifier = Modifier.shadow(2.dp, DsShapes.pill).clip(DsShapes.pill).background(DsColors.Surface)
                .padding(horizontal = DsSpacing.md, vertical = 6.dp),
            color = DsColors.PrimaryDark, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
            fontFamily = com.distrigo.app.ui.designsystem.LocalArabicTrialFonts.current?.ui,
        )
    }
}
