package com.distrigo.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws what it wraps mirrored in a right-to-left layout: for a direction-bound icon that has no AutoMirrored version
 * (`SouthEast`, `SubdirectoryArrowRight`). Icons that have one use it instead.
 */
fun Modifier.mirrorInRtl(): Modifier = drawWithContent {
    if (layoutDirection == LayoutDirection.Rtl) scale(scaleX = -1f, scaleY = 1f) { this@drawWithContent.drawContent() }
    else drawContent()
}

/**
 * `TextAlign.End` of the layout, not of the text: right in French, left in Arabic. A bare `TextAlign.End` follows the
 * text's own direction, so a French name or an amount on an Arabic screen would go to the right, the screen's start.
 */
@Composable
@ReadOnlyComposable
fun layoutEnd(): TextAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Left else TextAlign.Right
