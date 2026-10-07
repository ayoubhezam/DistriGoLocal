package com.distrigo.app.ui.rapports

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * a bar a day (a month, over a long period), and where the clients are and which of them bought, in
 * one commune or all of them.
 */
@Composable
fun VentesReportScreen(
    onBack: () -> Unit,
    /** "Voir tout" of the sectors: every sector, the most clients first. */
    onAllSectors: () -> Unit,
    viewModel: VentesReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

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
            val distribution = state.distribution
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
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
                        item { ReportMessage("Aucune vente sur cette période.") }
                    } else {
                        item { KpiGrid(report) }
                        if (state.filter.source == ReportSource.TOUT) item { SourceSplit(report) }
                        item { ChartCard(state.buckets, splitBySource = state.filter.source == ReportSource.TOUT) }
                    }
                    // Even without a sale: the clients are still there, none of them served.
                    if (distribution != null) {
                        distributionItems(distribution, state.commune, viewModel::setCommune, onAllSectors, viewModel::openSector)
                    }
                }
            }
        }
    }
    SectorSheetHost(state.sheet, viewModel::closeSector)
}

// ── Résumé ──

@Composable
private fun SummaryCard(report: SalesReport) {
    val money = LocalMoneyFormatter.current
    val returns = report.returns
    ReportHeroCard(
        title = "Chiffre d'affaires",
        amount = money.figure(report.all.total),
        caption = null,
        info = ReportInfo("Chiffre d'affaires", "Le total des ventes de la période, au prix de vente — dépôt et camion, ou l'un des deux selon Tout / Dépôt / Camion.\n\nLes retours clients n'en sont pas déduits : « Ventes nettes » = chiffre d'affaires − retours clients de la période. Ils ne s'affichent qu'avec « Tout », un retour ne disant pas d'où venait la marchandise."),
        halves = if (returns != null && returns.count > 0) listOf(
            HeroHalf("Retours clients (${returns.count})", money.figure(returns.total, prefix = "− ")),
            HeroHalf("Ventes nettes", money.figure(report.netTotal ?: report.all.total), bold = true),
        ) else emptyList(),
    )
}

@Composable
private fun KpiGrid(report: SalesReport) {
    val money = LocalMoneyFormatter.current
    // The figures are dark; colour is news: a margin is red only when it is a loss.
    val loss = report.grossMargin < 0
    val marginColor = if (loss) DsColors.Danger else DsColors.TextPrimary
    val sales = report.all.count
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        // Each row as tall as its taller tile, so the pairs line up.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                "Nombre de ventes", countFigure(sales), DsColors.TextPrimary, Modifier.weight(1f), null,
                Icons.Default.Receipt,
                info = ReportInfo("Nombre de ventes", "Le nombre de ventes de la période — dépôt et camion, ou l'un des deux selon Tout / Dépôt / Camion."),
                accent = DsColors.Primary,
            )
            KpiTile(
                "Panier moyen", if (sales > 0) money.figure(report.all.total / sales) else Figure.text("—"), DsColors.TextPrimary, Modifier.weight(1f), null,
                Icons.Default.ShoppingBasket,
                info = ReportInfo("Panier moyen", "Ce qu'une vente rapporte en moyenne sur la période : chiffre d'affaires ÷ nombre de ventes."),
                accent = Color(0xFF0E9384),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                "Marge brute", money.figure(report.grossMargin), marginColor, Modifier.weight(1f), null,
                if (loss) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                info = ReportInfo("Marge brute", "Ventes de la période − ce que les produits vendus avaient coûté, chaque ligne au prix d'achat du produit au moment de la vente.\n\nNi les charges ni les pertes n'en sont déduites."),
                accent = if (loss) DsColors.Danger else DsColors.Primary,
            )
            // The rate on the purchase price, as the Direction du Commerce counts it.
            KpiTile(
                "Taux de marge", rateFigure(report.marginRate), marginColor, Modifier.weight(1f), null,
                Icons.Default.Percent,
                info = ReportInfo("Taux de marge", "Marge brute ÷ ce que les produits vendus avaient coûté × 100 — sur le prix d'achat, comme le compte la Direction du Commerce."),
                accent = if (loss) DsColors.Danger else DsColors.Success,
            )
        }
    }
}

/**
 * Dépôt against camion over the whole period: a ring of their shares with the total inside, and each
 * one's share, amount and number of sales beside it.
 */
@Composable
private fun SourceSplit(report: SalesReport) {
    val money = LocalMoneyFormatter.current
    val total = report.all.total

    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .padding(DsSpacing.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dépôt et camion", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            InfoButton(
                ReportInfo(
                    "Dépôt et camion",
                    "Le chiffre d'affaires de la période, partagé selon d'où sont sorties les marchandises : le dépôt (ventes au dépôt) ou le camion (ventes des tournées).\n\n" +
                        "Au centre, le total ; à côté, pour chacun, sa part du total, son montant et son nombre de ventes. Affiché seulement avec « Tout »."
                ),
                tint = DsColors.TextTertiary,
            )
        }
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(
                slices = listOf(report.depot.total to DsColors.Primary, report.camion.total to CamionColor),
                total = total, caption = "Total", modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DsSpacing.lg))
            Box(Modifier.fillMaxHeight(0.8f).width(1.dp).background(DsColors.Border))
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(DsSpacing.xl)) {
                SourceLine("Dépôt", DsColors.Primary, report.depot, total)
                SourceLine("Camion", CamionColor, report.camion, total)
            }
        }
    }
}

/** One side of the split: its dot, name and share, then its amount and number of sales — each counting when it changes. */
@Composable
private fun SourceLine(label: String, color: Color, figures: SalesFigures, total: Double) {
    val money = LocalMoneyFormatter.current
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(14.dp).clip(DsShapes.pill).background(color))
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                AnimatedFigure(
                    rateFigure(if (total > 0) figures.total / total else 0.0),
                    fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary,
                )
            }
            AnimatedFigure(money.figure(figures.total), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            AnimatedFigure(countFigure(figures.count, "vente", "ventes"), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

// ── Graphique ──

/**
 * A bar per bucket — a dépôt bar and a camion bar side by side when the report shows both, so the two
 * compare at a glance. A tap picks a bucket and the header and legend read its figures; until then
 * they read the best one.
 *
 * When the figures change, the bars move to their new heights — over the same days, each from its old
 * height; over other days, all rising from the baseline. The header and legend do not count: they go
 * from one day to another, and a tap must read at once.
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
    // Each bar as a share of the tallest: the dépôt's then the camion's of each bucket when split.
    val heights = buckets.flatMap { b ->
        if (splitBySource) listOf(b.depot.total, b.camion.total) else listOf(b.all.total)
    }.map { (it / top).toFloat() }
    val drawn = glidingValues(heights, layout = Triple(buckets.firstOrNull()?.start, buckets.size, splitBySource))

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
            val shares = drawn()
            fun bar(color: Color, x: Float, share: Float, alpha: Float) {
                val h = share * size.height
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
                    bar(primary, x, shares.getOrElse(2 * i) { 0f }, alpha)
                    bar(CamionColor, x + barWidth + gap, shares.getOrElse(2 * i + 1) { 0f }, alpha)
                } else {
                    bar(if (b.camion.total > 0) CamionColor else primary, x, shares.getOrElse(i) { 0f }, alpha)
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
