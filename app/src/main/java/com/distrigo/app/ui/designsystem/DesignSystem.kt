package com.distrigo.app.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

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

    // Category tag palette (soft, for customer type badges)
    val TagRetail      = Color(0xFF5B6EF5) to Color(0xFFEEF0FE)
    val TagWholesale   = Color(0xFF9333EA) to Color(0xFFF5F0FF)
    val TagBusiness    = Color(0xFFF79009) to Color(0xFFFFFAEB)
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
