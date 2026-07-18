// package com.distrigo.app.ui.screens.rapport

package com.distrigo.app.ui.screens.rapport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/** عنوان قسم صغير مع نقطة ملوّنة، مطابق لتصميم "Ventes / Visites / Performance" */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(DsColors.Primary, CircleShape)
        )
        Text(text = title, fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
}

/** بطاقة الملاحظة/التوصية التلقائية أعلى الشاشة */
@Composable
fun InsightCard(
    message: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = DsColors.Warning,
    accentBg: Color = DsColors.WarningLight
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        // شريط تمييز على اليسار (يحاكي border-left في التصميم الأصلي)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor, DsShapes.small)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accentBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = message, fontSize = DsTextSize.bodySmall, color = Color(0xFF344054), lineHeight = 19.sp)
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCtaClick
                    )
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = ctaLabel, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * حلقة تقدّم دائرية (Goal Progress Ring) مرسومة يدويًا عبر Canvas،
 * تكافئ stroke-dasharray/dashoffset في تصميم SVG الأصلي.
 */
@Composable
fun GoalProgressRing(
    progress: Float, // 0f..1f
    centerLabel: String,
    centerSubLabel: String,
    ringColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 140.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 12.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)

            // المسار الخلفي (كامل الدائرة)
            drawArc(
                color = DsColors.PrimaryLight,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // مسار التقدم
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerLabel, fontSize = DsTextSize.display, fontWeight = FontWeight.Bold, color = ringColor)
            Text(text = centerSubLabel, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.TextTertiary)
        }
    }
}

/** بطاقة Hero الكاملة: الحلقة + نص الهدف/المحقق + مؤشر الوتيرة + رسالة المتبقي */
@Composable
fun HeroKpiCard(data: TableauDeBordData, modifier: Modifier = Modifier) {
    val ringColor = when {
        data.tauxAtteinte >= 100 -> DsColors.Success
        data.tauxAtteinte >= 70 -> DsColors.Primary
        data.tauxAtteinte >= 40 -> DsColors.Warning
        else -> DsColors.Danger
    }
    val paceColor = if (data.enRetard) DsColors.Danger else DsColors.Success
    val paceLabel = if (data.enRetard) "En retard de ${data.retardJours} jours" else "Dans les temps"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
    ) {
        GoalProgressRing(
            progress = data.progressFraction,
            centerLabel = "${data.tauxAtteinte}%",
            centerSubLabel = "atteint",
            ringColor = ringColor
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Taux d'atteinte de l'objectif",
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = DsColors.TextPrimary
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LabeledAmount("Objectif:", formatCurrency(data.objectifMontant))
                LabeledAmount("Réalisé:", formatCurrency(data.realiseMontant))
            }
        }

        // Pace indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = DsColors.Border, shape = DsShapes.small)
                .padding(top = 0.dp)
        ) {}
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Jour ${data.jourActuel}/${data.jourTotal}", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Text(paceLabel, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = paceColor)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 8.dp)
                    .background(DsColors.SurfaceSunken, DsShapes.pill)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(data.paceFraction)
                        .background(DsColors.TextTertiary, DsShapes.pill)
                )
            }
        }

        // Gap to target
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DsColors.SurfaceMuted, DsShapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = buildString {
                    append("Il reste ")
                    append(formatCurrency(data.gapMontant))
                    append(" à réaliser — environ ")
                    append(data.gapClientsEstimes)
                    append(" clients au rythme actuel")
                },
                fontSize = DsTextSize.bodySmall,
                color = Color(0xFF344054),
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun LabeledAmount(label: String, amount: String) {
    Row {
        Text(text = "$label ", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(text = amount, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
}

/** رسم بياني شريطي مبسّط لاتجاه المبيعات عبر الفترة */
@Composable
fun SalesTrendChart(bars: List<SalesBarData>, modifier: Modifier = Modifier) {
    val maxAmount = bars.maxOfOrNull { it.amount }?.coerceAtLeast(1) ?: 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg)
    ) {
        Text("Tendance des ventes", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .border(width = 0.dp, color = Color.Transparent) // placeholder لتوضيح الحدود لاحقًا إن لزم
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                val heightFraction = bar.amount.toFloat() / maxAmount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightFraction.coerceIn(0.04f, 1f))
                        .background(DsColors.Primary, DsShapes.small)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEach { bar ->
                Text(
                    text = bar.dayLabel,
                    modifier = Modifier.weight(1f),
                    fontSize = DsTextSize.caption,
                    fontWeight = FontWeight.Medium,
                    color = DsColors.TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** بطاقة توزيع الزيارات (Donut + Legend قابل للنقر لـ Clients sans vente) */
@Composable
fun VisitsBreakdownCard(
    clientsAvecVente: Int,
    clientsSansVente: Int,
    onSansVenteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = (clientsAvecVente + clientsSansVente).coerceAtLeast(1)
    val avecFraction = clientsAvecVente.toFloat() / total
    val avecPercent = percentOf(clientsAvecVente, total)
    val sansPercent = percentOf(clientsSansVente, total)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt)
                val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
                val topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)
                val sweepAvec = 360f * avecFraction

                drawArc(DsColors.Success, -90f, sweepAvec, false, topLeft, arcSize, style = stroke)
                drawArc(DsColors.Warning, -90f + sweepAvec, 360f - sweepAvec, false, topLeft, arcSize, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text("visités", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendRow(
                iconTint = DsColors.Success,
                iconBg = DsColors.SuccessLight,
                label = "Clients avec vente",
                valueText = "$clientsAvecVente · $avecPercent%",
                clickable = false,
                onClick = {}
            )
            LegendRow(
                iconTint = DsColors.Warning,
                iconBg = DsColors.WarningLight,
                label = "Clients sans vente",
                valueText = "$clientsSansVente · $sansPercent%",
                clickable = true,
                onClick = onSansVenteClick
            )
        }
    }
}

@Composable
private fun LegendRow(
    iconTint: Color,
    iconBg: Color,
    label: String,
    valueText: String,
    clickable: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (clickable) it
                    .background(DsColors.SurfaceMuted, DsShapes.small)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                else it
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(28.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (clickable) Icons.Filled.WarningAmber else Icons.Filled.Check,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(text = label, fontSize = DsTextSize.bodySmall, color = Color(0xFF344054), modifier = Modifier.weight(1f))
        Text(text = valueText, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        if (clickable) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

/** صف عميل "في خطر" (اعتاد الشراء ولم يشترِ هذه الفترة) */
@Composable
fun AtRiskClientRow(client: AtRiskClient, onClick: (AtRiskClient) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.WarningLight, DsShapes.small)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick(client) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(DsColors.Warning, CircleShape))
        Text(text = client.name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(15.dp))
    }
}

/** بطاقة "أفضل عميل" — تخطيط أفقي مختلف عن StatCard العادية */
@Composable
fun BestClientCard(clientName: String, amount: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface, DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(DsColors.TagWholesale.first.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DsColors.TagWholesale.first, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(clientName, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
            Text("Meilleur client", fontSize = DsTextSize.caption, color = DsColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
        Text(formatCurrency(amount), fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TagWholesale.first)
    }
}