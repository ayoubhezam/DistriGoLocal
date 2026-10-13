package com.distrigo.app.ui.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.distrigo.app.R

// ═══════════════════════════════════════════════════════════
// COLORS — Softer, Stripe/Linear-inspired palette
// ═══════════════════════════════════════════════════════════
object DsColors {
    // Primary — softer indigo-blue instead of saturated blue
    val Primary        = Color(0xFF5B6EF5)
    val PrimaryLight   = Color(0xFFEEF0FE)
    val PrimaryDark    = Color(0xFF4453D1)

    // Accent — muted teal-green instead of saturated green
    val Success        = Color(0xFF12B76A)
    val SuccessLight   = Color(0xFFECFDF3)

    // Warning — soft amber
    val Warning        = Color(0xFFF79009)
    val WarningLight   = Color(0xFFFFFAEB)

    // Destructive — softer red, less alarming than pure red
    val Danger         = Color(0xFFF04438)
    val DangerLight    = Color(0xFFFEF3F2)

    // Neutrals — Stripe-like gray scale (cooler, less flat gray)
    val TextPrimary    = Color(0xFF101828)
    val TextSecondary  = Color(0xFF667085)
    val TextTertiary   = Color(0xFF98A2B3)
    val Border         = Color(0xFFEAECF0)
    val Surface        = Color(0xFFFFFFFF)
    val SurfaceMuted   = Color(0xFFF9FAFB)
    val SurfaceSunken  = Color(0xFFF2F4F7)

    // ── Navigation: the Shifting Bottom Navigation bar ──
    // Semantic slots rather than raw colours, so a dark theme re-points this block alone and no
    // call site changes. Everything the bar paints comes from here.
    val NavBarContainer  = Surface
    val NavBarSelected   = Primary
    val NavBarUnselected = TextPrimary
    val NavBarBorder     = Border

    /**
     * Whether the system-navigation icons drawn over [NavBarContainer] have to be dark to stay
     * legible. Derived from the container rather than hardcoded, so re-theming the bar keeps the
     * gesture handle readable without a second edit.
     */
    val NavBarNeedsDarkSystemIcons: Boolean get() = NavBarContainer.luminance() > 0.5f

    // Category tag palette (soft, for customer type badges)
    val TagRetail      = Color(0xFF5B6EF5) to Color(0xFFEEF0FE)
    val TagWholesale   = Color(0xFF9333EA) to Color(0xFFF5F0FF)
    val TagBusiness    = Color(0xFFF79009) to Color(0xFFFFFAEB)
}

// ═══════════════════════════════════════════════════════════
// BRAND — the logo's colours and heading font, for the onboarding
// screens (docs/design/onboarding_ui_review.md). The rest of the
// app keeps DsColors until the brand colours are adopted app-wide.
// ═══════════════════════════════════════════════════════════
object DsBrand {
    // The logo's darkest navy; the welcome photo's dusk fades into it
    val Navy       = Color(0xFF0B1D40)
    // The logo's "Go": light to deep orange
    val Orange     = Color(0xFFFB9C30)
    val OrangeDeep = Color(0xFFEE6A04)

    // The light pages: the illustrations' navy and blue, so text and drawings match
    val Ink        = Color(0xFF0F2D6B)
    val Blue       = Color(0xFF1E6BE5)
    // Secondary text on white, 5:1
    val Slate      = Color(0xFF5A6F94)
    val Mist       = Color(0xFFF6F8FC)
    val CardBorder = Color(0xFFE3EAF5)

    /**
     * Plus Jakarta Sans, one variable font file for every weight (SIL Open Font License 1.1,
     * docs/licenses/PlusJakartaSans-OFL.txt).
     */
    val Font = FontFamily(
        jakarta(FontWeight.Medium),
        jakarta(FontWeight.SemiBold),
        jakarta(FontWeight.Bold),
        jakarta(FontWeight.ExtraBold),
    )

    // Compose still marks variation settings experimental; they are how a variable font picks its weight
    @OptIn(ExperimentalTextApi::class)
    private fun jakarta(weight: FontWeight) = Font(
        resId             = R.font.plus_jakarta_sans,
        weight            = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

// ═══════════════════════════════════════════════════════════
// SPACING — 4dp base unit, consistent scale
// ═══════════════════════════════════════════════════════════
object DsSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    // Clears the edge-to-edge ShiftingBottomNavBar: 48dp bar + 40dp breathing room. The system
    // navigation-bar inset sits under the bar itself and is added on top of this at the call site.
    val bottomNavClearance = 88.dp

    // Bottom offset for a FAB on a tab screen: clears the nav bar with a comfortable gap above it,
    // so the button never crowds or overlaps the bar the way a plain lg-padded FAB does.
    val fabBottomClearance = bottomNavClearance + lg
}

// ═══════════════════════════════════════════════════════════
// GRADIENTS — content fade behind floating chrome
// ═══════════════════════════════════════════════════════════
object DsGradients {
    val BottomFade = Brush.verticalGradient(colors = listOf(Color.Transparent, DsColors.Surface))
}

// ═══════════════════════════════════════════════════════════
// SHAPES — Slightly larger radii, softer feel
// ═══════════════════════════════════════════════════════════
object DsShapes {
    val small  = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(14.dp)
    val large  = RoundedCornerShape(18.dp)
    val pill   = RoundedCornerShape(50)
}

// ═══════════════════════════════════════════════════════════
// TYPOGRAPHY SIZES — consistent scale (sp values only,
// actual TextStyle composition done at call site to keep
// this file free of @Composable dependencies)
// ═══════════════════════════════════════════════════════════
object DsTextSize {
    val caption   = 11.sp
    val bodySmall = 13.sp
    val body      = 14.sp
    val bodyLarge = 15.sp
    val title     = 17.sp
    val headline  = 20.sp
    val display   = 26.sp
}

// ═══════════════════════════════════════════════════════════
// ELEVATION — subtle, Stripe-like (barely-there shadows)
// ═══════════════════════════════════════════════════════════
object DsElevation {
    val none   = 0.dp
    val low    = 1.dp
    val medium = 3.dp
}
