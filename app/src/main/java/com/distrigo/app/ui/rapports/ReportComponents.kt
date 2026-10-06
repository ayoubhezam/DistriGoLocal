package com.distrigo.app.ui.rapports

import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import java.util.Locale

// The pieces every report screen is built from, so they all read alike: the blue summary card, the
// figure tiles, the section titles, the empty message, and how counts and shares are written.

/** One half of the summary card's bottom line: a title centred over its value. */
class HeroHalf(val label: String, val value: String, val bold: Boolean = false)

/**
 * The blue card at the top of a report: [title], the main [amount] and a [caption], centred; then, if
 * [halves] are given, a divider and the two halves side by side.
 */
@Composable
fun ReportHeroCard(title: String, amount: String, caption: String, icon: ImageVector, halves: List<HeroHalf> = emptyList()) {
    val white = Color.White
    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Primary)
            .padding(DsSpacing.lg),
    ) {
        // The report's figure, read from the left: its icon, then what it is, how much, and of what.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(DsShapes.pill).background(white.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = white, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), maxLines = 2)
                FitText(amount, fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = white)
                Text(caption, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f))
            }
        }
        if (halves.isNotEmpty()) {
            Spacer(Modifier.height(DsSpacing.md))
            HorizontalDivider(color = white.copy(alpha = 0.25f))
            Spacer(Modifier.height(DsSpacing.md))
            // Each half keeps its side of the card, its title centred over its value.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                halves.forEach { half ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(half.label, fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                        FitText(
                            half.value, fontSize = DsTextSize.body,
                            fontWeight = if (half.bold) FontWeight.Bold else FontWeight.SemiBold,
                            color = white, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A figure: its [icon] in a soft circle of its colour and, across from it, a [badge] pill — a share,
 * a count, a trend — then its title and the figure itself, and an optional [note] under it.
 *
 * Icon and pill share the first line, the title gets its own: a half-width card on a phone has no
 * room for all three side by side.
 */
@Composable
fun KpiTile(
    label: String, value: String, valueColor: Color, modifier: Modifier, badge: String?,
    icon: ImageVector, note: String? = null,
) {
    // Dark text keeps its own figure; the icon and the pill take the Primary then.
    val accent = if (valueColor == DsColors.TextPrimary) DsColors.Primary else valueColor
    Column(
        modifier
            .fillMaxHeight()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .padding(DsSpacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(DsShapes.pill).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (badge != null) Text(
                badge, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = accent,
                maxLines = 1, softWrap = false,
                modifier = Modifier
                    .padding(start = DsSpacing.xs)
                    .clip(DsShapes.pill)
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(DsSpacing.sm))
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
        Spacer(Modifier.height(DsSpacing.xs))
        FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = valueColor)
        if (note != null) Text(note, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
    }
}

/**
 * A ring of shares — each slice its amount and colour, in order from twelve o'clock, clockwise — with
 * [total] in its centre and [caption] under it. An empty total draws a grey ring.
 *
 * The amount shrinks to fit the ring; "DA" stays a unit under it, never larger than the figure.
 */
@Composable
fun ShareRing(slices: List<Pair<Double, Color>>, total: Double, caption: String, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val empty = DsColors.SurfaceSunken
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val at = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            if (total <= 0) {
                drawArc(empty, 0f, 360f, false, at, arc, style = Stroke(stroke))
            } else {
                var start = -90f
                slices.forEach { (amount, color) ->
                    val sweep = (360.0 * amount / total).toFloat()
                    if (sweep > 0f) drawArc(color, start, sweep, false, at, arc, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        // Inside the stroke (14 dp) with room to spare, so a ten-digit total never touches the ring; each
        // line shrinks to stay on one line rather than wrap and push the others off centre.
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(
                money.amount(total), fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold,
                color = DsColors.TextPrimary, textAlign = TextAlign.Center, minScale = 0.35f,
            )
            Text("DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            FitText(caption, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.Center, minScale = 0.7f)
        }
    }
}

/** A report section's heading, between cards. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
        modifier = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.sm),
    )
}

/** A section heading with an [action] on its right, such as "Voir tout". */
@Composable
fun SectionTitle(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            action, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
            modifier = Modifier
                .clip(DsShapes.pill)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
        )
    }
}

/** A line of grey text where a report has nothing to show, or failed to load. */
@Composable
fun ReportMessage(text: String) {
    Text(
        text, fontSize = DsTextSize.body, color = DsColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xl),
    )
}

/** `1 vente`, `0 vente`, `3 ventes`: French puts 0 and 1 in the singular. */
fun plural(count: Int, one: String, many: String) = "$count ${if (count <= 1) one else many}"

/** A rate as a French percentage with one decimal: `16,7 %`. */
fun percent(rate: Double): String = String.format(Locale.FRENCH, "%.1f %%", rate * 100)

/** [part] as a share of [whole], or null when there is no whole to share. */
fun percentOf(part: Double, whole: Double): String? = if (whole > 0) percent(part / whole) else null
