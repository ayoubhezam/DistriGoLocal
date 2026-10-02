package com.distrigo.app.ui.rapports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.report.ReportSource
import com.distrigo.app.data.repository.SalesFigures
import com.distrigo.app.data.repository.SalesReport
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import java.util.Locale
import kotlin.math.max

/** The camion's colour wherever the report splits dépôt (the primary colour) from camion. */
private val CamionColor = Color(0xFF0E9384)

/**
 * Rapports › Ventes: what was sold over a period, from the dépôt, the camion, or both — the figures,
 * a bar a day (a month, over a long period), and the days one by one.
 */
@Composable
fun VentesReportScreen(onBack: () -> Unit, viewModel: VentesReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Ventes", subtitle = "Rapport des ventes", leading = DsTopBarLeading.Back(onBack))
        // A thin line while a newer report loads over the one on screen.
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading && state.report != null) {
                LinearProgressIndicator(Modifier.fillMaxSize(), color = DsColors.Primary, trackColor = Color.Transparent)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            item { ReportFilterBar(state.filter, viewModel::setFilter) }

            val report = state.report
            when {
                report == null && state.error != null -> item { Message(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { SummaryCard(report) }
                    // A period without a sale shows its summary — returns may still be there — and says so,
                    // instead of a page of zeros.
                    if (report.all.count == 0) {
                        item { Message("Aucune vente sur cette période.") }
                    } else {
                        item { KpiGrid(report) }
                        if (state.filter.source == ReportSource.TOUT) item { SourceSplit(report) }
                        item { ChartCard(state.buckets, splitBySource = state.filter.source == ReportSource.TOUT) }
                        item { SectionTitle(if (state.buckets.size == report.days.size) "Détail par jour" else "Détail par mois") }
                        items(state.buckets.filter { it.all.count > 0 }.asReversed(), key = { it.start.toString() }) { bucket ->
                            BucketRow(bucket)
                        }
                    }
                }
            }
        }
    }
}

// ── Résumé ──

@Composable
private fun SummaryCard(report: SalesReport) {
    val money = LocalMoneyFormatter.current
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
        Text("Chiffre d'affaires", fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        FitText(
            money.da(report.all.total), fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold,
            color = white, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(DsSpacing.xs))
        Text(plural(report.all.count, "vente", "ventes"), fontSize = DsTextSize.bodySmall, color = white.copy(alpha = 0.85f))
        val returns = report.returns
        if (returns != null && returns.count > 0) {
            Spacer(Modifier.height(DsSpacing.md))
            HorizontalDivider(color = white.copy(alpha = 0.25f))
            Spacer(Modifier.height(DsSpacing.md))
            // Each half keeps its side of the card, its title centred over its amount.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Retours clients (${returns.count})", fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                    FitText("− ${money.da(returns.total)}", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = white, textAlign = TextAlign.Center)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ventes nettes", fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                    FitText(money.da(report.netTotal ?: report.all.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = white, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun KpiGrid(report: SalesReport) {
    val money = LocalMoneyFormatter.current
    val approx = if (report.isMarginEstimated) "≈ " else ""
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Payé à la vente", money.da(report.all.paid), DsColors.Success, Modifier.weight(1f), percentOf(report.all.paid, report.all.total))
            KpiTile("À crédit", money.da(report.all.credit), DsColors.Warning, Modifier.weight(1f), percentOf(report.all.credit, report.all.total))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                "Marge brute", approx + money.da(report.grossMargin),
                if (report.grossMargin < 0) DsColors.Danger else DsColors.Primary, Modifier.weight(1f),
                report.marginRate?.let { approx + percent(it) + " du CA" },
            )
            KpiTile("Clients actifs", report.clientsServed.toString(), DsColors.TextPrimary, Modifier.weight(1f), null)
        }
        if (report.isMarginEstimated) {
            // Costs are sums of products, so "all of it" is read with a tolerance, not ==.
            val allEstimated = report.estimatedCost >= report.cost - 0.005
            Text(
                if (allEstimated) {
                    "≈ Ces ventes datent d'avant l'enregistrement du coût : la marge est estimée au prix d'achat actuel."
                } else {
                    "≈ Certaines ventes datent d'avant l'enregistrement du coût : ${money.da(report.estimatedCost)} " +
                        "du coût sur ${money.da(report.cost)} est estimé au prix d'achat actuel."
                },
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, valueColor: Color, modifier: Modifier, caption: String?) {
    Column(
        modifier
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(DsSpacing.md)
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = valueColor)
        if (caption != null) Text(caption, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
    }
}

/** Dépôt against camion: a bar of their shares, then each one's figures. */
@Composable
private fun SourceSplit(report: SalesReport) {
    val total = report.all.total
    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(DsSpacing.md)
    ) {
        Text("Dépôt et camion", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(DsSpacing.sm))
        val depotShare = if (total > 0) (report.depot.total / total).toFloat() else 0f
        Row(Modifier.fillMaxWidth().height(8.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken)) {
            if (depotShare > 0f) Box(Modifier.weight(depotShare).fillMaxSize().background(DsColors.Primary))
            if (depotShare < 1f && total > 0) Box(Modifier.weight(1f - depotShare).fillMaxSize().background(CamionColor))
        }
        Spacer(Modifier.height(DsSpacing.sm))
        SourceLine("Dépôt", DsColors.Primary, report.depot, total)
        SourceLine("Camion", CamionColor, report.camion, total)
    }
}

@Composable
private fun SourceLine(label: String, color: Color, figures: SalesFigures, total: Double) {
    val money = LocalMoneyFormatter.current
    Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(DsShapes.pill).background(color))
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            Text(plural(figures.count, "vente", "ventes"), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            if (figures.credit > 0) {
                Text("crédit ${money.da(figures.credit)}", fontSize = DsTextSize.caption, color = DsColors.Warning)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money.da(figures.total), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            percentOf(figures.total, total)?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary) }
        }
    }
}

// ── Graphique ──

/**
 * A bar per bucket — a dépôt bar and a camion bar side by side when the report shows both, so the two
 * compare at a glance. A tap picks a bucket and the header and legend read its figures; until then
 * they read the best one.
 */
@Composable
private fun ChartCard(buckets: List<SalesBucket>, splitBySource: Boolean) {
    val money = LocalMoneyFormatter.current
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val best = buckets.indices.maxByOrNull { buckets[it].all.total }
    val shown = selected ?: best
    // Side by side, each bar is measured against the tallest single bar, not the tallest pair.
    val top = max(
        buckets.maxOfOrNull { if (splitBySource) max(it.depot.total, it.camion.total) else it.all.total } ?: 0.0,
        1.0,
    )
    val primary = DsColors.Primary
    val sunken = DsColors.SurfaceSunken

    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (shown != null) {
            val b = buckets[shown]
            Text(
                (if (selected == null) "Meilleur · " else "") + b.title,
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
            )
            FitText(
                money.da(b.all.total), fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold,
                color = DsColors.TextPrimary, textAlign = TextAlign.Center,
            )
            Text(plural(b.all.count, "vente", "ventes"), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        Spacer(Modifier.height(DsSpacing.md))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.size - 1)
                        selected = if (selected == index) null else index
                    }
                }
        ) {
            val slot = size.width / buckets.size
            // A day's two bars touch and the days stand apart, so each pair reads as one day.
            val group = (slot * 0.7f).coerceAtLeast(1f)
            val gap = 0f
            val barWidth = if (splitBySource) (group / 2).coerceAtLeast(1f) else group
            val radius = CornerRadius(minOf(barWidth / 2, 6.dp.toPx()))
            fun bar(color: Color, x: Float, value: Double, alpha: Float) {
                val h = (value / top * size.height).toFloat()
                if (h > 0f) drawRoundRect(color.copy(alpha = alpha), Offset(x, size.height - h), Size(barWidth, h), radius)
            }
            buckets.forEachIndexed { i, b ->
                val x = i * slot + (slot - group) / 2
                val alpha = if (selected != null && i != selected) 0.35f else 1f
                // A faint stub marks a day with no sale, so the days still read as days.
                if (b.all.total <= 0) {
                    drawRoundRect(sunken, Offset(x, size.height - 2.dp.toPx()), Size(group, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                    return@forEachIndexed
                }
                if (splitBySource) {
                    bar(primary, x, b.depot.total, alpha)
                    bar(CamionColor, x + barWidth + gap, b.camion.total, alpha)
                } else {
                    bar(if (b.camion.total > 0) CamionColor else primary, x, b.all.total, alpha)
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = listOf(buckets.first(), buckets[buckets.size / 2], buckets.last()).distinct()
            labels.forEach { Text(it.label, fontSize = DsTextSize.caption, color = DsColors.TextTertiary) }
        }
        if (splitBySource && shown != null) {
            val b = buckets[shown]
            Spacer(Modifier.height(DsSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                Legend("Dépôt", DsColors.Primary, money.da(b.depot.total), Modifier.weight(1f))
                Legend("Camion", CamionColor, money.da(b.camion.total), Modifier.weight(1f))
            }
        }
    }
}

/** A colour dot and its name, and under them the amount of the bucket on show. */
@Composable
private fun Legend(label: String, color: Color, amount: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(DsSpacing.xs))
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        FitText(amount, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, textAlign = TextAlign.Center)
    }
}

// ── Détail ──

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
        modifier = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.sm),
    )
}

@Composable
private fun BucketRow(bucket: SalesBucket) {
    val money = LocalMoneyFormatter.current
    val all = bucket.all
    Row(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(bucket.title, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            Text(plural(all.count, "vente", "ventes"), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money.da(all.total), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            if (all.credit > 0) Text("crédit ${money.da(all.credit)}", fontSize = DsTextSize.caption, color = DsColors.Warning)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text, fontSize = DsTextSize.body, color = DsColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xl),
    )
}

private fun plural(count: Int, one: String, many: String) = "$count ${if (count <= 1) one else many}"

private fun percent(rate: Double) = String.format(Locale.FRENCH, "%.1f %%", rate * 100)

private fun percentOf(part: Double, whole: Double): String? = if (whole > 0) percent(part / whole) else null
