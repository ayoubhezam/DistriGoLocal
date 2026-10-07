package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.report.ReportSource
import com.distrigo.app.data.repository.LossByProduct
import com.distrigo.app.data.repository.RestockLine
import com.distrigo.app.data.repository.StockReport
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How many lost products and products to restock the report itself lists. */
private const val TOP_LOST = 5
private const val TOP_RESTOCK = 5

private val LossColors = listOf(Color(0xFFF04438), Color(0xFFF79009), Color(0xFF6366F1), Color(0xFF0E9384), Color(0xFFE91E63), Color(0xFF98A2B3))
private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private val INFO_VALUE = ReportInfo(
    "Valeur du stock",
    "Ce que vaut le stock aujourd'hui, au prix d'achat actuel : quantité × prix d'achat, produit par produit — quelle que soit la période choisie.\n\n" +
        "Au dépôt et au camion selon Tout / Dépôt / Camion. Un stock passé sous zéro (vendu au-delà du stock) ne compte pas : ce sont des marchandises dues, pas une valeur."
)
private val INFO_OUT = ReportInfo(
    "Ruptures",
    "Les produits qui n'ont plus de stock aujourd'hui — ou moins que rien, vendus au-delà — parmi ceux qui ont un minimum ou ont déjà été en stock. Ils sont en tête de « À réapprovisionner »."
)
private val INFO_LOW = ReportInfo(
    "Stock bas",
    "Les produits qui ont encore du stock aujourd'hui, mais moins que le stock minimum fixé sur leur fiche."
)
private val INFO_LOSSES = ReportInfo(
    "Pertes",
    "Ce que les pertes de la période ont coûté — casse, péremption, vol… — chaque perte au prix d'achat qu'avait le produit, et leur nombre. Les pertes liées aux retours y sont, selon leur motif."
)
private val INFO_RATE = ReportInfo(
    "Taux de perte",
    "Pertes de la période ÷ ce qu'ont coûté les marchandises vendues sur la même période × 100 : pour 100 DA de marchandise vendue, ce qui a été perdu."
)
private val INFO_TYPES = ReportInfo(
    "Pertes par type",
    "Les pertes de la période réparties par type — Casse, Péremption, Vol, vos types à vous — selon ce qu'elles ont coûté. Les cinq premiers, le reste dans « Autres types »."
)
private val INFO_LOST = ReportInfo(
    "Produits les plus perdus",
    "Les produits dont les pertes ont coûté le plus sur la période, avec la quantité perdue dans leur unité."
)
private val INFO_RESTOCK = ReportInfo(
    "À réapprovisionner",
    "Les ruptures, puis les produits sous leur stock minimum, des plus vides aux moins vides — aujourd'hui, au dépôt, au camion ou les deux selon le choix."
)

/**
 * Rapports › Stock et pertes: what the stock is worth and what is missing, today; what was lost, over
 * the period. A tap on a product opens its page.
 */
@Composable
fun StockReportScreen(onBack: () -> Unit, onSeeRestock: () -> Unit, viewModel: StockReportViewModel) {
    val state by viewModel.state.collectAsState()
    val drill = LocalDrillDown.current

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Stock et pertes", subtitle = "Ce que vaut le stock, ce qui manque, ce qui se perd", leading = DsTopBarLeading.Back(onBack))
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
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { ValueCard(report, state.filter.source) }
                    item { Tiles(report) }
                    if (report.losses.isNotEmpty()) {
                        item { TypesCard(report) }
                        item { TitleWithInfo("Produits les plus perdus", INFO_LOST) }
                        items(report.lostProducts.take(TOP_LOST), key = { "lost_${it.productId}" }) { p ->
                            LostLine(p) { drill(DrillTarget.Produit(p.productId)) }
                        }
                    }
                    item {
                        TitleWithInfo("À réapprovisionner (${report.restock.size})", INFO_RESTOCK,
                            action = if (report.restock.size > TOP_RESTOCK) "Voir tout" else null, onAction = onSeeRestock)
                    }
                    if (report.restock.isEmpty()) item { ReportMessage("Rien à réapprovisionner : tout est au-dessus de son minimum.") }
                    items(report.restock.take(TOP_RESTOCK), key = { "restock_${it.productId}" }) { r ->
                        RestockRow(r) { drill(DrillTarget.Produit(r.productId)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCard(report: StockReport, source: ReportSource) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        title = "Valeur du stock",
        amount = money.figure(report.stock.total),
        caption = "${plural(report.stock.products, "produit en stock", "produits en stock")}\nau ${LocalDate.now().format(DAY)}",
        info = INFO_VALUE,
        halves = if (source == ReportSource.TOUT) listOf(
            HeroHalf("Au dépôt", money.figure(report.stock.depot)),
            HeroHalf("Au camion", money.figure(report.stock.camion)),
        ) else emptyList(),
    )
}

@Composable
private fun Tiles(report: StockReport) {
    val money = LocalMoneyFormatter.current
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            // Red only when there is something to see to.
            KpiTile("Ruptures", countFigure(report.outOfStock), if (report.outOfStock > 0) DsColors.Danger else DsColors.TextPrimary,
                Modifier.weight(1f), null, Icons.Default.RemoveShoppingCart, INFO_OUT, accent = DsColors.Danger)
            KpiTile("Stock bas", countFigure(report.lowStock), DsColors.TextPrimary, Modifier.weight(1f), null,
                Icons.Default.Warning, INFO_LOW, accent = DsColors.Warning)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Pertes", money.figure(report.lossValue), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.lossCount, "perte", "pertes"), Icons.Default.TrendingDown, INFO_LOSSES, accent = DsColors.Danger)
            KpiTile("Taux de perte", rateFigure(report.lossRate), DsColors.TextPrimary, Modifier.weight(1f), null,
                Icons.Default.Percent, INFO_RATE, accent = DsColors.Primary)
        }
    }
}

/** The period's pertes by type: a ring of the first five and the rest, with their shares. */
@Composable
private fun TypesCard(report: StockReport) {
    val money = LocalMoneyFormatter.current
    val shown = report.losses.take(5)
    val rest = report.losses.drop(5)
    val slices = shown.map { it.type to it.value } + (if (rest.isNotEmpty()) listOf("Autres types" to rest.sumOf { it.value }) else emptyList())
    val total = report.lossValue
    CardColumn {
        CardTitle("Pertes par type", INFO_TYPES)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(slices.mapIndexed { i, (_, v) -> v to LossColors[i % LossColors.size] }, total, "Total des pertes", Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                slices.forEachIndexed { i, (name, amount) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(DsShapes.pill).background(LossColors[i % LossColors.size]))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(percentOf(amount, total) ?: percent(0.0), fontSize = DsTextSize.bodySmall,
                                    fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            }
                            FitText(money.da(amount), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** A row of the report: a product's picture and name over a detail on the left and a figure on the right. */
@Composable
private fun ProductRow(imageUri: String?, name: String, detail: String, detailColor: Color, figure: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(imageUri)
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(detail, fontSize = DsTextSize.caption, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(figure, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun LostLine(p: LossByProduct, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    ProductRow(p.imageUri, p.name, "${formatQty(p.quantity)} ${p.unit} perdu(s)", DsColors.TextSecondary, money.da(p.value), onClick)
}

/** A product to restock: its stock against its minimum; red when there is none left. */
@Composable
private fun RestockRow(r: RestockLine, onClick: () -> Unit) {
    val detail = if (r.minStock > 0) "minimum ${formatQty(r.minStock)} ${r.unit}" else "pas de minimum"
    ProductRow(
        r.imageUri, r.name,
        if (r.isOut) "Rupture · $detail" else "Stock bas · $detail",
        if (r.isOut) DsColors.Danger else DsColors.Warning,
        "${formatQty(r.stock)} ${r.unit}", onClick,
    )
}

/** Rapports › Stock et pertes › À réapprovisionner: every product out of stock or under its minimum. */
@Composable
fun RestockScreen(onBack: () -> Unit, viewModel: StockReportViewModel) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val drill = LocalDrillDown.current
    val report = state.report
    val all = report?.restock.orEmpty()
    val shown = remember(all, query) {
        val tokens = com.distrigo.app.ui.common.searchTokens(query)
        all.filter { com.distrigo.app.ui.common.matchesAllTokens(tokens, it.name) }
    }
    var showScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf("") }
    if (showScanner) {
        androidx.activity.compose.BackHandler { showScanner = false }
        com.distrigo.app.ui.scanner.BarcodeScannerScreen(
            onBarcodeScanned = { code -> showScanner = false; viewModel.scan(code) { scanError = it ?: "" } },
            onClose = { showScanner = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = "À réapprovisionner",
            subtitle = report?.let { "${plural(it.outOfStock, "rupture", "ruptures")} · ${it.lowStock} en stock bas" },
            leading = DsTopBarLeading.Back(onBack),
        )
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(value = query, onValueChange = { viewModel.query.value = it; scanError = "" }, placeholder = "Rechercher un produit",
                modifier = Modifier.padding(horizontal = DsSpacing.lg)) {
                com.distrigo.app.ui.common.DsCompactSearchAction(
                    icon = Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres",
                    tint = DsColors.Primary, onClick = { showScanner = true }
                )
            }
            if (scanError.isNotEmpty()) Text(scanError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs))
            Spacer(Modifier.height(8.dp))
            Text(if (query.isBlank()) plural(all.size, "produit", "produits") else "${shown.size} sur ${all.size}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }
        when {
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            shown.isEmpty() -> ReportMessage(if (query.isBlank()) "Rien à réapprovisionner." else "Aucun résultat pour « ${query.trim()} »")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(shown, key = { it.productId }) { r -> RestockRow(r) { drill(DrillTarget.Produit(r.productId)) } }
            }
        }
    }
}
