package com.distrigo.app.ui.onboarding

import com.distrigo.app.core.format.approximateLength
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How this phone got its license: what the success screen's subtitle says. */
enum class ActivationOrigin { NEW_ACCOUNT, SIGN_IN, CODE }

/**
 * What the success screen tells: how the phone was activated, and the license it now holds
 * (data/license/License.kt: plan, validFrom, validTo).
 */
class ActivationSummary(
    val origin: ActivationOrigin,
    val trial: Boolean,
    val validFromMillis: Long,
    val validToMillis: Long,
)

// As on the Compte screen: « 9 nov. 2026 »
private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

// A paler band behind the benefits, under the card
private val BenefitsBackground = Color(0xFFEAF1FD)

/** The end of the onboarding, as in the mockup: the check, the offer and its dates, then the app. */
@Composable
internal fun SuccessPage(summary: ActivationSummary, onContinue: () -> Unit) {
    // Nothing to go back to: Back enters the app like the button
    BackHandler(onBack = onContinue)
    Box(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                modifier            = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = FormGutter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(DsSpacing.lg))
                Celebration()
                Spacer(Modifier.height(DsSpacing.md))
                Text(
                    "DistriGo est activé !",
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 26.sp,
                    lineHeight = 32.sp,
                    color      = DsBrand.Ink,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(DsSpacing.xs))
                Text(
                    when (summary.origin) {
                        ActivationOrigin.NEW_ACCOUNT -> "Votre compte a été créé avec succès."
                        // Signing in on a new phone activates it too, but creates nothing
                        ActivationOrigin.SIGN_IN     -> "Bon retour ! Ce téléphone est activé."
                        ActivationOrigin.CODE        -> "Votre code a été activé avec succès."
                    },
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 15.sp,
                    lineHeight = 21.sp,
                    color      = DsBrand.Blue,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(DsSpacing.xl))
                OfferCard(summary)
                Spacer(Modifier.height(DsSpacing.md))
                Benefits()
                Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.xl))
                BrandButton("Accéder à l'application", onClick = onContinue)
                Spacer(Modifier.height(DsSpacing.md))
                Text(
                    "Merci de votre confiance !",
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = DsBrand.Blue
                )
                Spacer(Modifier.height(DsSpacing.lg))
            }
        }
    }
}

/** The offer, its length and its end: a trial, or a subscription bought with a code. */
@Composable
private fun OfferCard(summary: ActivationSummary) {
    val end = DAY.format(Instant.ofEpochMilli(summary.validToMillis).atZone(ZoneId.systemDefault()))
    val days = ((summary.validToMillis - summary.validFromMillis) / DAY_MILLIS).roundToInt()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(Color.White)
            .border(1.dp, DsBrand.CardBorder, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (summary.trial) "Offre découverte" else "Abonnement",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 20.sp,
                color      = DsBrand.Ink,
                modifier   = Modifier.weight(1f)
            )
            CrownBadge()
        }
        Spacer(Modifier.height(DsSpacing.md))
        OfferLine(Icons.Outlined.CalendarMonth, "Durée :", approximateLength(days))
        Spacer(Modifier.height(DsSpacing.md))
        OfferLine(Icons.Outlined.EventAvailable, if (summary.trial) "Expire le :" else "Valable jusqu'au :", end)
    }
}


@Composable
private fun OfferLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(DsSpacing.lg))
        Column {
            Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DsBrand.Slate)
            Text(value, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = DsBrand.Blue)
        }
    }
}

/** What every plan includes. The trial opens the whole app; plans differ in limits, not features. */
@Composable
private fun Benefits() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(BenefitsBackground)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
    ) {
        listOf("Accès à toutes les fonctionnalités", "Support inclus").forEachIndexed { i, benefit ->
            if (i > 0) Spacer(Modifier.height(DsSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.md))
                Text(benefit, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DsBrand.Ink)
            }
        }
    }
}

/** The mockup's orange disc with a white crown. */
@Composable
private fun CrownBadge() {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(DsBrand.Orange, DsBrand.OrangeDeep))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.width / 24f
            val crown = Path().apply {
                moveTo(3 * u, 17 * u)
                lineTo(3 * u, 7 * u)
                lineTo(8 * u, 11 * u)
                lineTo(12 * u, 4 * u)
                lineTo(16 * u, 11 * u)
                lineTo(21 * u, 7 * u)
                lineTo(21 * u, 17 * u)
                close()
            }
            drawPath(crown, Color.White)
            drawRoundRect(Color.White, topLeft = Offset(3 * u, 18.5f * u), size = Size(18 * u, 2.5f * u), cornerRadius = CornerRadius(u))
        }
    }
}

/** One confetti piece: where it lands (fractions of the area), its turn, colour and shape. */
private class Confetto(val x: Float, val y: Float, val angle: Float, val color: Color, val dot: Boolean)

private val CONFETTI = listOf(
    Confetto(0.24f, 0.18f, 30f, DsBrand.Orange, false),
    Confetto(0.15f, 0.42f, -25f, DsBrand.Blue, false),
    Confetto(0.28f, 0.66f, 50f, DsBrand.OrangeDeep, false),
    Confetto(0.12f, 0.72f, 0f, DsColors.Success, true),
    Confetto(0.33f, 0.88f, 0f, DsBrand.Blue.copy(alpha = 0.6f), true),
    Confetto(0.20f, 0.08f, 0f, DsBrand.Blue.copy(alpha = 0.35f), true),
    Confetto(0.76f, 0.14f, -35f, DsColors.Success, false),
    Confetto(0.86f, 0.36f, 20f, DsBrand.Orange, false),
    Confetto(0.72f, 0.70f, -45f, DsBrand.OrangeDeep, false),
    Confetto(0.88f, 0.62f, 0f, DsBrand.Blue, true),
    Confetto(0.80f, 0.86f, 0f, DsBrand.Blue.copy(alpha = 0.6f), true),
    Confetto(0.92f, 0.12f, 0f, DsBrand.Ink.copy(alpha = 0.3f), true),
)

/**
 * The green check, popping in while the confetti flies out from behind it. Once, on arrival: it
 * celebrates a moment, so it does not loop.
 */
@Composable
private fun Celebration() {
    val pop = remember { Animatable(0.6f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        burst.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val center = Offset(size.width / 2, size.height / 2)
            CONFETTI.forEach { c ->
                val at = Offset(center.x + (c.x * size.width - center.x) * t, center.y + (c.y * size.height - center.y) * t)
                val color = c.color.copy(alpha = c.color.alpha * t)
                if (c.dot) {
                    drawCircle(color, radius = 3.5.dp.toPx(), center = at)
                } else {
                    val w = 12.dp.toPx()
                    val h = 5.dp.toPx()
                    rotate(c.angle, pivot = at) {
                        drawRoundRect(color, topLeft = Offset(at.x - w / 2, at.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(1.5.dp.toPx()))
                    }
                }
            }
        }
        // The halo, then the disc
        Box(Modifier.size(116.dp).scale(pop.value).clip(CircleShape).background(DsColors.Success.copy(alpha = 0.12f)))
        Box(
            Modifier.size(88.dp).scale(pop.value).clip(CircleShape).background(DsColors.Success),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
        }
    }
}
