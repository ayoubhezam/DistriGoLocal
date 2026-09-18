package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

@Composable
fun StatCell(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${"%.2f".format(value)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.ExtraBold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * The party's solde, from its stored [balance]: what it still owes in red, nothing in grey, or — when it has paid
 * more than it was billed — the excess in blue, marked "(AVANCÉ)". The amount is always shown unsigned; the
 * colour and the word carry the direction.
 */
@Composable
fun SoldeCell(balance: Double, modifier: Modifier = Modifier) {
    val owed = balance >= 0.005
    val advance = balance <= -0.005
    val color = when {
        owed -> DsColors.Danger
        advance -> DsColors.Primary
        else -> DsColors.TextSecondary
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${"%.2f".format(kotlin.math.abs(balance))} DA" + if (advance) " (AVANCÉ)" else "",
            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.ExtraBold, color = color, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text("Solde", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * The terms that stand between the two totals and the balance, under the stat cells.
 *
 * "Total facturé − Total payé" is not what a party owes. The balance also subtracts returns, and a
 * supplier's starts from its opening balance — neither of which either total can show. Without
 * this line the card did not add up: a client billed 6000 DA and paid 2110 DA was shown owing
 * 1860 DA, with nothing on screen to say where the other 2030 DA went.
 *
 * The totals themselves are left as they are, because "Total facturé" should keep meaning what was
 * billed. This supplies the missing terms instead, so the three figures and this line together
 * read as one sum. It draws nothing when there is nothing to reconcile: a party with no returns
 * and no opening balance sees exactly the card it always did.
 *
 * [returns] is the total of the party's returns, as a positive amount; it is shown deducted.
 * [initialBalance] is a supplier's opening balance; clients have none, so it defaults to zero.
 */
@Composable
fun BalanceAdjustments(returns: Double, initialBalance: Double = 0.0, modifier: Modifier = Modifier) {
    val parts = buildList {
        if (kotlin.math.abs(initialBalance) >= 0.005) add("Solde initial ${signedAmount(initialBalance)}")
        if (kotlin.math.abs(returns) >= 0.005) add("Retours ${signedAmount(-returns)}")
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        modifier  = modifier.fillMaxWidth().padding(top = DsSpacing.sm, start = DsSpacing.lg, end = DsSpacing.lg),
        fontSize  = DsTextSize.caption,
        color     = DsColors.TextSecondary,
        textAlign = TextAlign.Center
    )
}

/** "+500.00 DA" / "-240.00 DA", signed the way the payment rows already sign "+1.00 DA". */
private fun signedAmount(value: Double): String =
    (if (value < 0) "-" else "+") + "%.2f".format(kotlin.math.abs(value)) + " DA"
