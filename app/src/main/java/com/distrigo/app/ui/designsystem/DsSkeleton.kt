package com.distrigo.app.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeletons: grey shapes where a screen's content will land, so it lands without moving anything (owner,
 * 2026-10-22). [DsShimmer] runs one animation for all of them; each [skeleton] reads it in its draw phase only, so
 * the shimmer redraws without recomposing. With animations off in the system, the transition does not run and the
 * shapes stay still.
 */
private val LocalShimmer = compositionLocalOf<State<Float>?> { null }

/** On a white or Mist surface. */
val SkeletonBase = Color(0xFFE8EDF5)
private val SkeletonHighlight = Color(0xFFF6F8FC)

/** On a coloured card, such as the Dashboard's blue one. */
val SkeletonOnColor = Color.White.copy(alpha = 0.18f)
private val SkeletonOnColorHighlight = Color.White.copy(alpha = 0.32f)

/** Gives the [skeleton]s inside [content] one shared shimmer. */
@Composable
fun DsShimmer(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    CompositionLocalProvider(LocalShimmer provides progress, content = content)
}

/** Draws this element as a skeleton shape: [base], with the shimmer's light band passing over it. */
fun Modifier.skeleton(shape: Shape = DsShapes.small, base: Color = SkeletonBase): Modifier = composed {
    val progress = LocalShimmer.current
    val highlight = if (base == SkeletonOnColor) SkeletonOnColorHighlight else SkeletonHighlight
    clip(shape).drawBehind {
        drawRect(base)
        val p = progress?.value ?: return@drawBehind
        val band = maxOf(size.width * 0.6f, 120.dp.toPx())
        val x = -band + (size.width + 2 * band) * p
        drawRect(Brush.linearGradient(listOf(Color.Transparent, highlight, Color.Transparent), Offset(x, 0f), Offset(x + band, 0f)))
    }
}

/** A bar of text to come: [width] × [height]. */
@Composable
fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier, base: Color = SkeletonBase) {
    Box(modifier.width(width).height(height).skeleton(DsShapes.pill, base))
}
