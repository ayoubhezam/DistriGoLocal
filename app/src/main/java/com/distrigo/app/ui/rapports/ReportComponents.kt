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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import java.util.Locale

// The pieces every report screen is built from, so they all read alike: the blue summary card, the
// figure tiles, the section titles, the empty message, and how counts and shares are written.

/** One half of the summary card's bottom line: a title centred over its value. */
/** What a report figure is and how it is counted: the title and text its ⓘ opens. */
class ReportInfo(val title: String, val text: String)

/** A small ⓘ, set right after its title, that opens [info] in a dialog; [iconSize] of icon in a little touch around it. */
@Composable
fun InfoButton(info: ReportInfo, tint: Color, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 14.dp) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.size(iconSize + 10.dp).clip(DsShapes.pill).clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = "Comment est calculé « ${info.title} »", tint = tint, modifier = Modifier.size(iconSize))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(info.title, fontWeight = FontWeight.Bold) },
        text = { Text(info.text, fontSize = DsTextSize.body, color = DsColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Compris") } },
        containerColor = DsColors.Surface,
    )
}

class HeroHalf(val label: String, val value: String, val bold: Boolean = false)

/**
 * The blue card at the top of a report: [title], the main [amount] and a [caption], centred; then, if
 * [halves] are given, a divider and the two halves side by side.
 */
@Composable
fun ReportHeroCard(title: String, amount: String, caption: String?, info: ReportInfo, halves: List<HeroHalf> = emptyList()) {
    val white = Color.White
    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Primary)
            .padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The report's figure, centred: what it is, how much, and of what.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // As wide as the ⓘ on the other side, so the title itself sits in the middle.
            Spacer(Modifier.width(24.dp))
            Text(title, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), maxLines = 1,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            InfoButton(info, tint = white.copy(alpha = 0.75f))
        }
        FitText(amount, fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = white, textAlign = TextAlign.Center)
        // A card can be its amount alone: the Ventes report counts its sales in a card of their own.
        if (caption != null) Text(caption, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), textAlign = TextAlign.Center)
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
 * A figure, the way a dashboard card reads at a glance: a small [icon] in its colour and, across from
 * it, a small [badge] pill — a share, a count, a trend; then the title, small and grey, its ⓘ right
 * after it; then the figure, large. Every card has the same four parts, so a row of them lines up.
 */
@Composable
fun KpiTile(
    label: String, value: String, valueColor: Color, modifier: Modifier, badge: String?,
    icon: ImageVector, info: ReportInfo,
    /** The card's own colour, for its icon and pill; the figure stays dark unless it is news. */
    accent: Color = if (valueColor == DsColors.TextPrimary) DsColors.Primary else valueColor,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md)
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            if (badge != null) Text(
                badge, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent,
                maxLines = 1, softWrap = false,
                modifier = Modifier
                    .padding(start = DsSpacing.xs)
                    .clip(DsShapes.small)
                    .background(accent.copy(alpha = 0.10f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(DsSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.TextSecondary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            InfoButton(info, tint = DsColors.TextTertiary, modifier = Modifier.padding(start = 2.dp), iconSize = 13.dp)
        }
        FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = valueColor)
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
