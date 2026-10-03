package com.distrigo.app.ui.rapports

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
fun ReportHeroCard(title: String, amount: String, caption: String, halves: List<HeroHalf> = emptyList()) {
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
        Text(title, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        FitText(amount, fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = white, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DsSpacing.xs))
        Text(caption, fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), textAlign = TextAlign.Center)
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

/** A figure with its title above and, if given, a [badge] in the figure's colour under it. */
@Composable
fun KpiTile(label: String, value: String, valueColor: Color, modifier: Modifier, badge: String?) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(DsSpacing.md)
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
        Spacer(Modifier.height(DsSpacing.xs))
        FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = valueColor)
        if (badge != null) {
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                badge, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = valueColor,
                maxLines = 1,
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(valueColor.copy(alpha = 0.12f))
                    .padding(horizontal = DsSpacing.sm, vertical = 3.dp),
            )
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
