package com.distrigo.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

/** The logo's tagline, set as text under it (the logo files don't carry it). */
internal const val BRAND_TAGLINE = "GÉREZ. DISTRIBUEZ. DÉVELOPPEZ."

// The truck's middle in welcome_hero.webp, as a fraction of the photo's height
private const val TRUCK_FOCUS_Y = 0.72f
// brand_logo_on_dark.webp's height over its width
private const val LOGO_ASPECT = 722f / 852f

private val LogoTopPadding    = DsSpacing.lg
private val LogoWidth         = 150.dp
private val TaglineGap        = DsSpacing.sm
private val TaglineLineHeight = 16.sp
private val TopScrimHeight    = 220.dp
private val BottomFadeHeight  = 130.dp
// How far the text panel reaches up over the photo's faded bottom
private val PanelOverlap      = 72.dp
private val ButtonHeight      = 52.dp

/**
 * The onboarding's first page (docs/design/onboarding_ui_review.md): the brand photo, the logo and
 * the two ways in. It draws edge to edge, under the status bar; [OnboardingCarousel] hosts it.
 */
@Composable
internal fun WelcomeScreen(onStart: () -> Unit, onSignIn: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(DsBrand.Navy)) {
        HeroPhoto(Modifier.weight(1f).fillMaxWidth())
        BottomPanel(onStart, onSignIn, Modifier.overlapUp(PanelOverlap))
    }
}

@Composable
private fun HeroPhoto(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        // The truck goes midway between the logo block and the top of the text panel, so the
        // panel's height (screen size, font scale) never pushes either one over it.
        val density = LocalDensity.current
        val logoBottom = WindowInsets.statusBars.getTop(density) + with(density) {
            (LogoTopPadding + LogoWidth * LOGO_ASPECT + TaglineGap + TaglineLineHeight.toDp()).roundToPx()
        }
        val panelTop = with(density) { (maxHeight - PanelOverlap).roundToPx() }
        val truckAlignment = remember(logoBottom, panelTop) { FocusAlignment(TRUCK_FOCUS_Y, (logoBottom + panelTop) / 2) }

        Image(
            painter            = painterResource(R.drawable.welcome_hero),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            alignment          = truckAlignment,
            modifier           = Modifier.fillMaxSize()
        )
        // Darkens the sky behind the status bar and the logo
        Box(
            Modifier
                .fillMaxWidth()
                .height(TopScrimHeight)
                .background(Brush.verticalGradient(listOf(DsBrand.Navy.copy(alpha = 0.45f), Color.Transparent)))
        )
        // Fades the yard into the panel's navy
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomFadeHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, DsBrand.Navy)))
        )
        Column(
            modifier            = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = LogoTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter            = painterResource(R.drawable.brand_logo_on_dark),
                contentDescription = "DistriGo",
                modifier           = Modifier.width(LogoWidth)
            )
            Spacer(Modifier.height(TaglineGap))
            Text(
                BRAND_TAGLINE,
                fontFamily    = DsBrand.Font,
                fontWeight    = FontWeight.Bold,
                fontSize      = 11.sp,
                lineHeight    = TaglineLineHeight,
                letterSpacing = 1.8.sp,
                maxLines      = 1,
                color         = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun BottomPanel(onStart: () -> Unit, onSignIn: () -> Unit, modifier: Modifier) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DsSpacing.xxl, end = DsSpacing.xxl, bottom = DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            // Broken as in the mockup, not where the width happens to fall
            "La solution complète\npour les distributeurs\net grossistes",
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 25.sp,
            lineHeight = 31.sp,
            color      = Color.White,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(DsSpacing.sm + DsSpacing.xs))
        Text(
            "Gérez votre stock, vos ventes, vos tournées et bien plus encore, en toute simplicité.",
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Medium,
            fontSize   = 14.sp,
            lineHeight = 20.sp,
            color      = Color.White.copy(alpha = 0.82f),
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(DsSpacing.xxl))
        Button(
            onClick  = onStart,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape    = DsShapes.pill,
            // The deep end of the logo's orange: white text stays readable on it
            colors   = ButtonDefaults.buttonColors(containerColor = DsBrand.OrangeDeep, contentColor = Color.White)
        ) {
            Text("Commencer", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.width(DsSpacing.sm))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(DsSpacing.md))
        OutlinedButton(
            onClick  = onSignIn,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape    = DsShapes.pill,
            border   = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("J'ai déjà un compte", fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(DsSpacing.lg))
        PageDots(current = 0, active = DsBrand.Orange, inactive = Color.White.copy(alpha = 0.35f))
    }
}

/**
 * Places a photo so that the point [focusY] of the way down it lands at [targetY] px of its box,
 * as far as the photo reaches: it never leaves a gap at the top or bottom.
 */
private class FocusAlignment(private val focusY: Float, private val targetY: Int) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val highest = minOf(space.height - size.height, 0)
        val y = (targetY - focusY * size.height).roundToInt().coerceIn(highest, 0)
        return IntOffset((space.width - size.width) / 2, y)
    }
}

/** Measured at full height but reported [by] shorter, and drawn that much higher: over the photo's faded bottom. */
private fun Modifier.overlapUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overlap   = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
}
