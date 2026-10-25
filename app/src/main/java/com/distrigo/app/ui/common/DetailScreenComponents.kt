package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.data.model.Amount

/**
 * Shared between ClientDetailScreen and SupplierDetailScreen so the two
 * detail screens never visually diverge.
 */

private val WhatsAppPathData =
    "M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.29.173-1.414-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413z"

val WhatsAppIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WhatsApp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(WhatsAppPathData).toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = DsColors.Primary,
    bg: Color = DsColors.PrimaryLight,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(DsShapes.medium)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = DsSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(DsShapes.pill)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            fontSize = DsTextSize.caption,
            fontWeight = FontWeight.Medium,
            color = DsColors.TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = DsTextSize.caption
        )
    }
}

/**
 * One of the three figures across a party's card: a tinted tile, its icon and label over the amount. A third of the
 * card wide, a full amount to the centime ("11 781 712,80 DA") does not fit at body size, and its no-break spaces
 * leave nowhere to wrap but mid-word ("D" / "A"): so it shrinks to fit on its line instead.
 */
@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    amount: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(DsShapes.medium)
            .background(background)
            .padding(horizontal = DsSpacing.sm, vertical = 10.dp),
        // The amounts share a baseline across the three tiles, whether a label took one line or two
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.size(24.dp).clip(DsShapes.pill).background(DsColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Text(
                label,
                fontSize = DsTextSize.caption,
                fontWeight = FontWeight.Medium,
                color = DsColors.TextPrimary,
                lineHeight = DsTextSize.bodySmall,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(6.dp))
        amount()
    }
}

/** "Total facturé" or "Total payé": [value] in [color], on [background]. */
@Composable
private fun StatCell(label: String, value: Double, icon: ImageVector, color: Color, background: Color, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    StatTile(icon, label, color, background, modifier) {
        FitText(money.da(value), fontSize = DsTextSize.body, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

/** The "Total facturé" tile. */
@Composable
fun BilledCell(value: Double, modifier: Modifier = Modifier) =
    StatCell("Total facturé", value, Icons.Outlined.Description, DsColors.Primary, DsColors.PrimaryLight, modifier)

/** The "Total payé" tile. */
@Composable
fun PaidCell(value: Double, modifier: Modifier = Modifier) =
    StatCell("Total payé", value, Icons.Outlined.Payments, DsColors.SuccessDark, DsColors.SuccessLight, modifier)

/**
 * The party's solde, from its stored [balance]: what it still owes in red, nothing in grey, or — when it has paid
 * more than it was billed — the excess in blue, marked "(AVANCÉ)". The amount is always shown unsigned; the
 * colour and the word carry the direction.
 */
@Composable
fun SoldeCell(balance: Double, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val owed = Amount.owing(balance)
    val advance = Amount.inAdvance(balance)
    val (color, background) = when {
        owed -> DsColors.DangerDark to DsColors.DangerLight
        advance -> DsColors.Primary to DsColors.PrimaryLight
        else -> DsColors.TextSecondary to DsColors.SurfaceMuted
    }
    val icon = if (advance) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Schedule
    StatTile(icon, "Solde", color, background, modifier) {
        // "(AVANCÉ)" gets its own line rather than share one that may shrink or be cut — it is the word that says
        // which way the solde goes.
        Column {
            FitText(money.da(kotlin.math.abs(balance)), fontSize = DsTextSize.body, fontWeight = FontWeight.ExtraBold, color = color)
            if (advance) Text("(AVANCÉ)", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * The terms that stand between the two totals and the balance, under the stat cells.
 *
 * "Total facturé − Total payé" is not what a party owes. The balance also subtracts returns, and a
 * supplier's starts from its opening balance — neither of which either total can show. Without
 * these rows the card did not add up: a client billed 6000 DA and paid 2110 DA was shown owing
 * 1860 DA, with nothing on screen to say where the other 2030 DA went.
 *
 * The totals themselves are left as they are, because "Total facturé" should keep meaning what was
 * billed. This supplies the missing terms instead, a row each, so the three figures and these rows
 * together read as one sum. It draws nothing when there is nothing to reconcile: a party with no
 * returns and no opening balance sees exactly the three tiles.
 *
 * [returns] is the total of the party's returns, as a positive amount; it is shown deducted, and
 * [onReturns], when given, opens them (the row then carries a chevron).
 * [initialBalance] is a supplier's opening balance; clients have none, so it defaults to zero.
 */
@Composable
fun BalanceAdjustments(
    returns: Double,
    initialBalance: Double = 0.0,
    onReturns: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoneyFormatter.current
    val hasInitial = kotlin.math.abs(initialBalance) >= 0.005
    val hasReturns = kotlin.math.abs(returns) >= 0.005
    if (!hasInitial && !hasReturns) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        if (hasInitial) AdjustmentRow(Icons.Outlined.AccountBalanceWallet, "Solde initial", money.signedDa(initialBalance), onClick = null)
        if (hasReturns) AdjustmentRow(Icons.Outlined.Autorenew, "Retours", money.signedDa(-returns), onReturns)
    }
}

@Composable
private fun AdjustmentRow(icon: ImageVector, label: String, amount: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.PrimaryLight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(DsShapes.pill).background(DsColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
        }
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
        FitText(
            amount,
            fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
