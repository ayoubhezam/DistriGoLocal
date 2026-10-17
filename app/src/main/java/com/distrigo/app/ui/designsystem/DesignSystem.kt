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
// COLORS — the brand's (DsBrand), by meaning, for every screen (the owner's choice, 2026-10-16): the names stay,
// so no screen changes; their values are the onboarding's and Compte's.
// ═══════════════════════════════════════════════════════════
object DsColors {
    // Primary — the brand blue; white on it reads 4.9:1, and it on its light tint 4.3:1
    val Primary        = Color(0xFF1E6BE5)
    val PrimaryLight   = Color(0xFFEAF1FD)
    // The brand navy (DsBrand.Ink): titles and emphasis
    val PrimaryDark    = Color(0xFF0F2D6B)

    // Accent — muted teal-green instead of saturated green
    val Success        = Color(0xFF12B76A)
    val SuccessLight   = Color(0xFFECFDF3)

    // Warning — soft amber
    val Warning        = Color(0xFFF79009)
    val WarningLight   = Color(0xFFFFFAEB)
    // A step before Warning: a debt a month old, between the recent (green) and the two-month (amber)
    val Caution        = Color(0xFFFDB022)

    // Destructive — softer red, less alarming than pure red
    val Danger         = Color(0xFFF04438)
    val DangerLight    = Color(0xFFFEF3F2)
    // A step deeper than DangerLight: an avatar sitting on a DangerLight card
    val DangerSoft     = Color(0xFFFEE2E2)

    // Accents — one hue per section, report or chart series, beside the brand blue and the three above (2026-10-17).
    // Every colour a screen paints is named in this file: none is written out in a screen any more. A chart takes
    // them after the blue, and the slate (TextTertiary) last, for « Autres ».
    val AccentTeal     = Color(0xFF0E9384)
    val AccentViolet   = Color(0xFF7C3AED)
    val AccentRose     = Color(0xFFE5487A)
    val AccentSky      = Color(0xFF0BA5EC)
    // The logo's deep orange: dark enough for an icon on white, where the light one is not
    val AccentOrange   = DsBrand.OrangeDeep

    // Neutrals — the brand's navy and slates. Long text and numbers in a near-black navy (the logo's darkest,
    // 16.6:1), the brand navy kept for titles; secondary text the brand slate (5.1:1); hints 3.1:1
    val TextPrimary    = Color(0xFF0B1D40)
    val TextSecondary  = Color(0xFF5A6F94)
    val TextTertiary   = Color(0xFF8794B0)
    // The cards' hairline, as Compte's
    val Border         = Color(0xFFE3EAF5)
    val Surface        = Color(0xFFFFFFFF)
    // Mist, the onboarding's page; and a deeper Mist for sunken wells
    val SurfaceMuted   = Color(0xFFF6F8FC)
    val SurfaceSunken  = Color(0xFFEDF1F8)

    // ── Navigation: the Shifting Bottom Navigation bar ──
    // Semantic slots rather than raw colours, so a dark theme re-points this block alone and no
    // call site changes. Everything the bar paints comes from here.
    val NavBarContainer  = Surface
    val NavBarSelected   = Primary
    // The brand slate: the tabs not open step back, the open one in blue carries the eye
    val NavBarUnselected = TextSecondary
    val NavBarBorder     = Border

    /**
     * Whether the system-navigation icons drawn over [NavBarContainer] have to be dark to stay
     * legible. Derived from the container rather than hardcoded, so re-theming the bar keeps the
     * gesture handle readable without a second edit.
     */
    val NavBarNeedsDarkSystemIcons: Boolean get() = NavBarContainer.luminance() > 0.5f

    // Category tag palette (soft, for customer type badges)
    val TagRetail      = Primary to PrimaryLight
    val TagWholesale   = AccentViolet to Color(0xFFF5F0FF)
    val TagBusiness    = Warning to WarningLight

    // Not the app's own: WhatsApp's green, on its buttons; and thermal paper, which is not white but a faint warm
    // grey, on the receipt preview
    val WhatsApp       = Color(0xFF25D366)
    val ThermalPaper   = Color(0xFFFDFCF8)
}

// ═══════════════════════════════════════════════════════════
// BRAND — the logo's colours and the font (docs/design/onboarding_ui_review.md):
// the onboarding's and Compte's own pieces use them by name; every other
// screen through DsColors and the theme's typography, which carry them.
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
        // The inner screens' text is mostly regular weight: without these, it would fall to Medium
        jakarta(FontWeight.Light),
        jakarta(FontWeight.Normal),
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
